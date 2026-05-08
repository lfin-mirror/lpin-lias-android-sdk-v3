package io.lpin.android.sdk.plac.scanner


interface CellScanner {
    @Throws(ScannerException::class)
    fun init()

    @Throws(ScannerException::class)
    fun getCellData(): LocationPackage?
}