package io.lpin.android.sdk.plac.scanner

/** 와이파이 데이터 **/
data class WifiData(
    var ssid: String,
    var bssid: String,
    var rssi: Int,
    /** 수집된 시간 **/
    // var timestampMs: Long,
    val frequency: Int = 0
) {
    override fun toString(): String {
        return "[${ssid}/${bssid}](${rssi})"
    }
}