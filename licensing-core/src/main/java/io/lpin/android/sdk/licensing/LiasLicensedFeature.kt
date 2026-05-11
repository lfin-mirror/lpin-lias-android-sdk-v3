package io.lpin.android.sdk.licensing

enum class LiasLicensedFeature(val claimValue: String) {
    FACE("face"),
    SCANNER("scanner"),
    SPACE("space"),
    PIXEL_MATCHING("pixel-matching");

    companion object {
        fun fromClaimValue(value: String): LiasLicensedFeature? {
            return entries.firstOrNull { it.claimValue.equals(value.trim(), ignoreCase = true) }
        }
    }
}
