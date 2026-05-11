package io.lpin.android.sdk.licensing

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

class LiasLicenseVerifierTest {
    private val subject = LiasLicenseSubject(
        packageName = "io.lpin.app.lias.lias",
        signingCertSha256 = "AABBCCDD00112233",
    )

    @Test
    fun verifiesValidLicense() {
        val keyPair = generateKeyPair()
        val envelopeJson = buildEnvelopeJson(keyPair)

        val verified = LiasLicenseVerifier.verify(
            envelopeJson = envelopeJson,
            subject = subject,
            expectedKeyId = "main",
            publicKeyBase64Url = publicKey(keyPair),
            nowEpochMillis = 1_715_000_000_000,
        )

        assertEquals(subject.packageName, verified.packageName)
        assertTrue(verified.features.contains(LiasLicensedFeature.FACE))
        assertTrue(verified.features.contains(LiasLicensedFeature.SCANNER))
    }

    @Test(expected = LiasLicenseException::class)
    fun rejectsExpiredLicense() {
        val keyPair = generateKeyPair()
        val envelopeJson = buildEnvelopeJson(keyPair, notAfter = "2024-01-01T00:00:00Z")

        LiasLicenseVerifier.verify(
            envelopeJson = envelopeJson,
            subject = subject,
            expectedKeyId = "main",
            publicKeyBase64Url = publicKey(keyPair),
            nowEpochMillis = 1_715_000_000_000,
        )
    }

    @Test(expected = LiasLicenseException::class)
    fun rejectsWrongPackageName() {
        val keyPair = generateKeyPair()
        val envelopeJson = buildEnvelopeJson(keyPair, packageName = "com.example.other")

        LiasLicenseVerifier.verify(
            envelopeJson = envelopeJson,
            subject = subject,
            expectedKeyId = "main",
            publicKeyBase64Url = publicKey(keyPair),
            nowEpochMillis = 1_715_000_000_000,
        )
    }

    @Test(expected = LiasLicenseException::class)
    fun rejectsWrongSigningCertificate() {
        val keyPair = generateKeyPair()
        val envelopeJson = buildEnvelopeJson(keyPair, signingCertSha256 = "DEADBEEF")

        LiasLicenseVerifier.verify(
            envelopeJson = envelopeJson,
            subject = subject,
            expectedKeyId = "main",
            publicKeyBase64Url = publicKey(keyPair),
            nowEpochMillis = 1_715_000_000_000,
        )
    }

    @Test(expected = LiasLicenseException::class)
    fun rejectsMissingFeatureAtPolicyLayer() {
        val keyPair = generateKeyPair()
        val envelopeJson = buildEnvelopeJson(keyPair, features = listOf("face"))

        val verified = LiasLicenseVerifier.verify(
            envelopeJson = envelopeJson,
            subject = subject,
            expectedKeyId = "main",
            publicKeyBase64Url = publicKey(keyPair),
            nowEpochMillis = 1_715_000_000_000,
        )

        LiasLicensePolicy.requireFeature(verified, LiasLicensedFeature.SCANNER)
    }

    @Test(expected = LiasLicenseException::class)
    fun rejectsTamperedPayload() {
        val keyPair = generateKeyPair()
        val payload = buildPayloadMap()
        val payloadJson = LiasLicenseCanonicalJson.canonicalize(payload)
        val signature = sign(payloadJson, keyPair.private as Ed25519PrivateKeyParameters)
        val tamperedPayload = LinkedHashMap(payload)
        tamperedPayload["packageName"] = "tampered.package"
        val envelope = mapOf(
            "version" to 1,
            "algorithm" to "Ed25519",
            "keyId" to "main",
            "payload" to tamperedPayload,
            "signature" to Base64.getUrlEncoder().withoutPadding().encodeToString(signature),
        )

        LiasLicenseVerifier.verify(
            envelopeJson = LiasLicenseCanonicalJson.canonicalize(envelope),
            subject = subject,
            expectedKeyId = "main",
            publicKeyBase64Url = publicKey(keyPair),
            nowEpochMillis = 1_715_000_000_000,
        )
    }

    @Test(expected = LiasLicenseException::class)
    fun rejectsUnknownKeyId() {
        val keyPair = generateKeyPair()
        val envelopeJson = buildEnvelopeJson(keyPair, keyId = "secondary")

        LiasLicenseVerifier.verify(
            envelopeJson = envelopeJson,
            subject = subject,
            expectedKeyId = "main",
            publicKeyBase64Url = publicKey(keyPair),
            nowEpochMillis = 1_715_000_000_000,
        )
    }

    private fun generateKeyPair(): AsymmetricCipherKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        return generator.generateKeyPair()
    }

    private fun publicKey(keyPair: AsymmetricCipherKeyPair): String {
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        return Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.encoded)
    }

    private fun buildEnvelopeJson(
        keyPair: AsymmetricCipherKeyPair,
        keyId: String = "main",
        packageName: String = subject.packageName,
        signingCertSha256: String = subject.signingCertSha256,
        features: List<String> = listOf("face", "scanner", "space", "pixel-matching"),
        notBefore: String = "2024-01-01T00:00:00Z",
        notAfter: String = "2027-01-01T00:00:00Z",
    ): String {
        val payload = buildPayloadMap(
            packageName = packageName,
            signingCertSha256 = signingCertSha256,
            features = features,
            notBefore = notBefore,
            notAfter = notAfter,
        )
        val payloadJson = LiasLicenseCanonicalJson.canonicalize(payload)
        val signatureBytes = sign(payloadJson, keyPair.private as Ed25519PrivateKeyParameters)
        val envelope = mapOf(
            "version" to 1,
            "algorithm" to "Ed25519",
            "keyId" to keyId,
            "payload" to payload,
            "signature" to Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes),
        )
        return LiasLicenseCanonicalJson.canonicalize(envelope)
    }

    private fun buildPayloadMap(
        packageName: String = subject.packageName,
        signingCertSha256: String = subject.signingCertSha256,
        features: List<String> = listOf("face", "scanner", "space", "pixel-matching"),
        notBefore: String = "2024-01-01T00:00:00Z",
        notAfter: String = "2027-01-01T00:00:00Z",
    ): LinkedHashMap<String, Any> {
        return linkedMapOf(
            "licenseId" to "test-license",
            "customer" to "Acme",
            "packageName" to packageName,
            "signingCertSha256" to signingCertSha256,
            "features" to features,
            "notBefore" to notBefore,
            "notAfter" to notAfter,
        )
    }

    private fun sign(payloadJson: String, privateKey: Ed25519PrivateKeyParameters): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val bytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        signer.update(bytes, 0, bytes.size)
        return signer.generateSignature()
    }
}
