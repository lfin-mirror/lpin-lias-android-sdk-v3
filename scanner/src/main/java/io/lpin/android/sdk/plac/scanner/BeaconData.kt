package io.lpin.android.sdk.plac.scanner

data class BeaconData(
        val ssid: String,
        val bssid: String,
        val major: Int,
        val minor: Int,
        val rssi: Int
) {
    override fun toString(): String {
        return "$bssid (major=${major}, minor=${minor}, rssi=${rssi})"
    }

    override fun hashCode(): Int = (ssid + bssid).hashCode()

    override fun equals(other: Any?): Boolean {
        return if (other is BeaconData) {
            this.hashCode() == other.hashCode()
        } else false
    }
}