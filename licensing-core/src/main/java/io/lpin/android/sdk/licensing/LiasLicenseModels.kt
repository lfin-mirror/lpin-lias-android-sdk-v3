package io.lpin.android.sdk.licensing

data class LiasLicenseSubject(
    val packageName: String,
    val signingCertSha256: String,
)

data class LiasVerifiedLicense(
    val licenseId: String,
    val customer: String?,
    val packageName: String,
    val signingCertSha256: String,
    val features: Set<LiasLicensedFeature>,
    val notBeforeEpochMillis: Long,
    val notAfterEpochMillis: Long,
    val keyId: String,
    val payloadJson: String,
)

data class LiasCompactLicenseClaims(
    val appPkgId: String,
    val issuedAtEpochMillis: Long,
    val expireAtEpochMillis: Long,
    val signingCertSha256: String?,
    val features: Set<LiasLicensedFeature>?,
    val payloadJson: String,
)
