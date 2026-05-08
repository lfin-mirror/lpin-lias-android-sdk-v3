package io.lpin.android.sdk.plac.scanner

import android.location.Location


data class LocationPackage(
    var cell: CellData? = null,

    /** 위치 데이터   */
    var location: Location? = null,

    /** 위치 데이터 수집시 발생한 에러 **/
    var locationError: ScannerException.Type? = null,

    /** 와이파이 데이터가 성공적으로 수집되었는가 **/
    var isWifiScanningEnabled: Boolean = false,

    /** 와이파이 데이터 */
    var wifiScanResults: List<WifiData>? = null,

    /** 비콘 데이터가 성공적으로 수집되었는가  */
    var isBleScanningEnabled: Boolean = false,

    /** 비콘 데이터 **/
    var bleScanResults: List<BeaconData>? = null
) {
    override fun toString(): String = StringBuilder().apply {
        append("Scanner Result = \n")
        if (cell != null) {
            append("cell = [cid = ${cell?.cid}, lac = ${cell?.lac}, telecom = ${cell?.telecom}]")
        } else {
            append("cell = null")
        }
        append("\n")
        append("location = ")
        if (location != null) {
            append("[lat : ${location?.latitude}, lon : ${location?.longitude}, alt : ${location?.altitude}]")
        } else {
            append("null")
        }
        append("\n")
        append("wifiScanResults = ")
        if (isWifiScanningEnabled) {
            append("[")
            if (wifiScanResults != null) {
                wifiScanResults?.forEachIndexed { index, wifiData ->
                    append(wifiData.toString())
                    if (index != (wifiScanResults!!.size - 1))
                        append(", ")
                }
            } else {
                append("[]")
            }
            append("]")
        } else {
            append("[]")
        }
        append("\n")
        append("bleScanResults = ")
        if (isBleScanningEnabled) {
            append("[")
            if (bleScanResults != null) {
                bleScanResults?.forEachIndexed { index, beaconData ->
                    append(beaconData.toString())
                    if (index != (bleScanResults!!.size - 1))
                        append(", ")
                }
            }
            append("]")
        } else {
            append("[]")
        }
    }.toString()
}