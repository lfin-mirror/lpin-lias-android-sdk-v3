package io.lpin.android.sdk.licensing

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.FileNotFoundException
import java.security.MessageDigest

object LiasLicenseGate {
    private val lock = Any()

    @Volatile
    private var cachedLicense: LiasVerifiedLicense? = null

    @Volatile
    private var cachedFailure: LiasLicenseException? = null

    @JvmStatic
    fun requireFeature(context: Context, feature: LiasLicensedFeature) {
        val verifiedLicense = loadVerifiedLicense(context.applicationContext)
        LiasLicensePolicy.requireFeature(verifiedLicense, feature)
    }

    @JvmStatic
    fun invalidateCacheForTests() {
        synchronized(lock) {
            cachedLicense = null
            cachedFailure = null
        }
    }

    private fun loadVerifiedLicense(context: Context): LiasVerifiedLicense {
        cachedLicense?.let { return it }
        cachedFailure?.let { throw it }

        synchronized(lock) {
            cachedLicense?.let { return it }
            cachedFailure?.let { throw it }

            return try {
                val envelopeJson = readLicenseEnvelope(context)
                val subject = buildSubject(context)
                val verifiedLicense = LiasLicenseVerifier.verifyEmbedded(envelopeJson, subject)
                cachedLicense = verifiedLicense
                verifiedLicense
            } catch (exception: LiasLicenseException) {
                cachedFailure = exception
                throw exception
            }
        }
    }

    private fun readLicenseEnvelope(context: Context): String {
        return try {
            context.assets.open(BuildConfig.LICENSE_ASSET_FILE_NAME).bufferedReader().use { it.readText() }
        } catch (exception: FileNotFoundException) {
            throw LiasLicenseException(
                "Missing asset '${BuildConfig.LICENSE_ASSET_FILE_NAME}'. Add the signed license file to the host app assets directory.",
                exception,
            )
        }
    }

    private fun buildSubject(context: Context): LiasLicenseSubject {
        return LiasLicenseSubject(
            packageName = context.packageName,
            signingCertSha256 = readSigningCertSha256(context),
        )
    }

    @SuppressLint("PackageManagerGetSignatures")
    private fun readSigningCertSha256(context: Context): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }

        val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            val signers = signingInfo?.apkContentsSigners ?: emptyArray()
            signers.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.firstOrNull()?.toByteArray()
        } ?: throw LiasLicenseException("Unable to read app signing certificate for license verification")

        val digest = MessageDigest.getInstance("SHA-256").digest(signatureBytes)
        return digest.joinToString(separator = "") { byte -> "%02X".format(byte) }
    }
}
