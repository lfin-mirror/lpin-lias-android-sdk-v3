package io.lpin.android.sdk.licensing

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone

object LiasLicenseVerifier {
    @JvmStatic
    fun verifyEmbedded(envelopeJson: String, subject: LiasLicenseSubject): LiasVerifiedLicense {
        return verify(
            envelopeJson = envelopeJson,
            subject = subject,
            expectedKeyId = BuildConfig.LICENSE_PUBLIC_KEY_ID,
            publicKeyBase64Url = BuildConfig.LICENSE_PUBLIC_KEY_BASE64URL,
        )
    }

    @JvmStatic
    fun verify(
        envelopeJson: String,
        subject: LiasLicenseSubject,
        expectedKeyId: String,
        publicKeyBase64Url: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): LiasVerifiedLicense {
        if (publicKeyBase64Url.isBlank()) {
            throw LiasLicenseException(
                "Embedded license public key is empty. Set lpinLicensePublicKeyBase64Url before building the SDK."
            )
        }

        val envelope = try {
            JsonParser.parseString(envelopeJson).asJsonObject
        } catch (exception: Exception) {
            throw LiasLicenseException("License envelope is not valid JSON", exception)
        }

        val version = envelope.getAsJsonPrimitive("version")?.asInt ?: -1
        if (version != 1) {
            throw LiasLicenseException("Unsupported license envelope version: $version")
        }

        val algorithm = envelope.getAsJsonPrimitive("algorithm")?.asString ?: ""
        if (algorithm != "Ed25519") {
            throw LiasLicenseException("Unsupported license algorithm: $algorithm")
        }

        val keyId = envelope.getAsJsonPrimitive("keyId")?.asString?.trim().orEmpty()
        if (keyId.isBlank()) {
            throw LiasLicenseException("License keyId is missing")
        }
        if (expectedKeyId.isNotBlank() && expectedKeyId != keyId) {
            throw LiasLicenseException("License keyId '$keyId' does not match embedded verifier '$expectedKeyId'")
        }

        val payloadObject = envelope.getAsJsonObject("payload")
            ?: throw LiasLicenseException("License payload object is missing")
        val payloadJson = LiasLicenseCanonicalJson.canonicalize(payloadObject)
        val signatureBase64Url = envelope.getAsJsonPrimitive("signature")?.asString?.trim().orEmpty()
        if (signatureBase64Url.isBlank()) {
            throw LiasLicenseException("License signature is missing")
        }

        verifySignature(publicKeyBase64Url, payloadJson, signatureBase64Url)

        val packageName = payloadObject.getAsJsonPrimitive("packageName")?.asString?.trim().orEmpty()
        if (packageName.isEmpty()) {
            throw LiasLicenseException("License packageName is missing")
        }
        if (packageName != subject.packageName) {
            throw LiasLicenseException(
                "License packageName '$packageName' does not match app package '${subject.packageName}'"
            )
        }

        val signingCertSha256 = normalizeDigest(payloadObject.getAsJsonPrimitive("signingCertSha256")?.asString.orEmpty())
        if (signingCertSha256.isEmpty()) {
            throw LiasLicenseException("License signingCertSha256 is missing")
        }
        if (signingCertSha256 != normalizeDigest(subject.signingCertSha256)) {
            throw LiasLicenseException("License signingCertSha256 does not match the installed app certificate")
        }

        val notBeforeEpochMillis = parseIsoInstant(payloadObject.getAsJsonPrimitive("notBefore")?.asString.orEmpty(), "notBefore")
        val notAfterEpochMillis = parseIsoInstant(payloadObject.getAsJsonPrimitive("notAfter")?.asString.orEmpty(), "notAfter")
        if (nowEpochMillis < notBeforeEpochMillis) {
            throw LiasLicenseException("License is not active yet")
        }
        if (nowEpochMillis > notAfterEpochMillis) {
            throw LiasLicenseException("License has expired")
        }

        val featuresJson = payloadObject.getAsJsonArray("features") ?: JsonArray()
        val features = LinkedHashSet<LiasLicensedFeature>()
        for (element in featuresJson) {
            val featureValue = element.asString
            val feature = LiasLicensedFeature.fromClaimValue(featureValue)
                ?: throw LiasLicenseException("Unknown licensed feature: $featureValue")
            features.add(feature)
        }
        if (features.isEmpty()) {
            throw LiasLicenseException("License features are missing")
        }

        return LiasVerifiedLicense(
            licenseId = payloadObject.getAsJsonPrimitive("licenseId")?.asString?.ifBlank { packageName } ?: packageName,
            customer = payloadObject.getAsJsonPrimitive("customer")?.asString?.ifBlank { null },
            packageName = packageName,
            signingCertSha256 = signingCertSha256,
            features = features,
            notBeforeEpochMillis = notBeforeEpochMillis,
            notAfterEpochMillis = notAfterEpochMillis,
            keyId = keyId,
            payloadJson = payloadJson,
        )
    }

    private fun verifySignature(publicKeyBase64Url: String, payloadJson: String, signatureBase64Url: String) {
        val publicKeyBytes = decodeBase64Url(publicKeyBase64Url, "public key")
        if (publicKeyBytes.size != 32) {
            throw LiasLicenseException("Embedded Ed25519 public key must be 32 bytes, but was ${publicKeyBytes.size}")
        }
        val signatureBytes = decodeBase64Url(signatureBase64Url, "signature")
        if (signatureBytes.size != 64) {
            throw LiasLicenseException("Ed25519 signature must be 64 bytes, but was ${signatureBytes.size}")
        }

        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKeyBytes, 0))
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        verifier.update(payloadBytes, 0, payloadBytes.size)
        if (!verifier.verifySignature(signatureBytes)) {
            throw LiasLicenseException("License signature verification failed")
        }
    }

    private fun decodeBase64Url(value: String, label: String): ByteArray {
        return try {
            Base64.getUrlDecoder().decode(value.trim())
        } catch (exception: IllegalArgumentException) {
            throw LiasLicenseException("License $label is not valid base64url", exception)
        }
    }

    private fun parseIsoInstant(value: String, fieldName: String): Long {
        if (value.isBlank()) {
            throw LiasLicenseException("License $fieldName is missing")
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return try {
            format.parse(value)?.time ?: throw IOException("null parse")
        } catch (exception: Exception) {
            throw LiasLicenseException("License $fieldName is not a valid UTC timestamp", exception)
        }
    }

    internal fun normalizeDigest(value: String): String {
        return value.replace(":", "").trim().uppercase(Locale.US)
    }
}
