package io.lpin.android.sdk.plac.scanner

import android.location.Location

interface LocationScanner {
    /** 초기화 **/
    @Throws(ScannerException::class)
    fun init()

    /** 현재 위치 데이터 받기 **/
    @Throws(ScannerException::class)
    fun getLocation(): Location?
}