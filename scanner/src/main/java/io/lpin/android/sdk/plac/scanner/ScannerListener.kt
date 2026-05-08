package io.lpin.android.sdk.plac.scanner

import org.jetbrains.annotations.NotNull

interface ScannerListener {
    @NotNull
    fun onLocationFailure(e: ScannerException)

    @NotNull
    fun onLocationPackage(locationPackage: LocationPackage)
}