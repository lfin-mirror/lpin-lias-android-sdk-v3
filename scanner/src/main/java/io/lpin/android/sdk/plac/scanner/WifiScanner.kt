package io.lpin.android.sdk.plac.scanner

import io.lpin.android.sdk.plac.scanner.ScannerException
import io.lpin.android.sdk.plac.scanner.WifiData

interface WifiScanner {
    /** 초기화 **/
    @Throws(ScannerException::class)
    fun init()

    /** 현재 와이파이 스캐닝 동작중인지 확인 **/
    fun isWifiScanningAvailable(): Boolean

    /** 현재 와이파이 목록 호출 **/
    @Throws(ScannerException::class)
    fun getScanResults(): List<WifiData>
}
