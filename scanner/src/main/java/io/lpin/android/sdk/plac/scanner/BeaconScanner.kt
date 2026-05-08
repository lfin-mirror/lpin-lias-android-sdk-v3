package io.lpin.android.sdk.plac.scanner

interface BeaconScanner {
    /** 비콘 스캐너 초기화 **/
    @Throws(ScannerException::class)
    fun init()

    /** 비콘 스캐닝 시작 **/
    @Throws(ScannerException::class)
    fun startScanning()

    /** 비콘 스캐닝 종료 **/
    @Throws(ScannerException::class)
    fun stopScanning()

    /** 비콘 스캐너 에러 코드 **/
    fun getErrorCode(): Int

    /** 비콘 데이터 **/
    fun getScanResults(): List<BeaconData>
}