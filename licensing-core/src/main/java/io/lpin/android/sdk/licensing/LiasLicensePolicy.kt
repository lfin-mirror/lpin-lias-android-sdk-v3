package io.lpin.android.sdk.licensing

object LiasLicensePolicy {
    @JvmStatic
    fun requireFeature(license: LiasVerifiedLicense, feature: LiasLicensedFeature) {
        if (!license.features.contains(feature)) {
            throw LiasLicenseException(
                "License does not include feature '${feature.claimValue}'. Licensed features: ${license.features.joinToString { it.claimValue }}"
            )
        }
    }
}
