package io.lpin.android.sdk.plac.scanner

import android.content.Context
import android.os.AsyncTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit


class Scanner(
    val context: Context,
    private val listener: ScannerListener?,
    private val params: ScannerParams,
) {

    private constructor(builder: Builder) : this(
        builder.context,
        builder.listener!!,
        builder.params
    )

    /**
     * 스캐닝이 가능한 상태인지 확인
     */
    fun isAvailableScan(): Boolean {
        return try {
            // 위치
            if (params.isLocationScanEnabled)
                newLocationScanner().init()
            // 블루투스
            if (params.isBleScanEnabled)
                newBleScanner().init()
            // 와이파이
            if (params.isWifiScanEnabled)
                newWifiScanner().init()
            // 기지국
            if (params.isCellScanEnable)
                newCellScanner().init()
            true
        } catch (ignore: Exception) {
            false
        }
    }

    /** 위치를 포함한 주변 데이터를 요청 **/
    fun run() {
        CoroutineScope(Dispatchers.Default).launch {
            val locationPackage = LocationPackage()

            /** 현재 기지국 **/
            val cellJob = launch {
                if (params.isCellScanEnable) {
                    locationPackage.apply {
                        try {
                            val cellScanner = newCellScanner()
                            cellScanner.init()
                            cellScanner.getCellData()?.apply {
                                locationPackage.cell = this.cell
                            }
                        } catch (e: ScannerException) {
                            listener?.onLocationFailure(e)
                        }
                    }
                } else {
                    locationPackage.locationError = ScannerException.Type.NOT_SUPPORTED
                    listener?.onLocationFailure(
                        ScannerException(ScannerType.CELL, ScannerException.Type.NOT_SUPPORTED)
                    )
                    cancel()
                }
            }

            /** 현재 위치 **/
            val locationJob = launch {
                if (params.isLocationScanEnabled) {
                    val locationScanner = newLocationScanner()
                    locationPackage.apply {
                        try {
                            locationScanner.init()
                        } catch (e: ScannerException) {
                            this.locationError = e.scannerExceptionType
                            listener?.onLocationFailure(e)
                            return@apply
                        }

                        try {
                            this.location = locationScanner.getLocation()
                        } catch (e: ScannerException) {
                            this.locationError = e.scannerExceptionType
                            listener?.onLocationFailure(e)
                        } catch(e: NoSuchElementException) {
                            this.locationError = ScannerException.Type.UNKNOWN_ERROR
                            listener?.onLocationFailure(ScannerException(ScannerType.LOCATION, ScannerException.Type.UNKNOWN_ERROR))
                        }
                    }
                }
            }

            /** 비콘 스캐닝 **/
            val beaconJob = launch {
                if (params.isBleScanEnabled) {
                    locationPackage.apply {
                        val bleScanner = newBleScanner(context.applicationContext, params)
                        try {
                            bleScanner.init()
                            try {
                                bleScanner.startScanning()
                                try {
                                    delay(params.bluetoothScanDurationMs)
                                } catch (ignore: Exception) {
                                }
                            } catch (e: ScannerException) {
                                listener?.onLocationFailure(e)
                            } finally {
                                bleScanner.stopScanning()
                            }
                            this.isBleScanningEnabled = bleScanner.getErrorCode() == 0
                            this.bleScanResults = bleScanner.getScanResults()
                        } catch (e: ScannerException) {
                            listener?.onLocationFailure(e)
                        }
                    }
                }
            }

            /** 와이파이 스캐닝 **/
            val wifiJob = launch {
                if (params.isWifiScanEnabled) {
                    locationPackage.apply {
                        val wifiScanner = newWifiScanner()
                        try {
                            wifiScanner.init()
                            this.isWifiScanningEnabled = wifiScanner.isWifiScanningAvailable()
                            if (this.isWifiScanningEnabled) {
                                this.wifiScanResults = wifiScanner.getScanResults()
                            }
                        } catch (e: ScannerException) {
                            this.isWifiScanningEnabled = false
                            listener?.onLocationFailure(e)
                        }
                    }
                }
            }
            joinAll(cellJob, locationJob, beaconJob, wifiJob)
            listener?.onLocationPackage(locationPackage)
        }
//        executor.execute {
//            val locationPackage = LocationPackage()
//
//            /** 현재 기지국 **/
//            val cellScanner = CellScannerImpl(context)
//            val cellTask: FutureTask<LocationPackage>? = if (params.isCellScanEnable) {
//                FutureTask {
//                    val tmpLocationPackage = LocationPackage()
//                    try {
//                        cellScanner.init()
//                        cellScanner.getCellData()?.apply {
//                            tmpLocationPackage.cell = this.cell
//                        }
//                    } catch (e: ScannerException) {
//                        listener?.onLocationFailure(e)
//                    }
//                    tmpLocationPackage
//                }
//            } else {
//                null
//            }?.apply {
//                executor.execute(this)
//            }
//            cellTask?.get(5, TimeUnit.SECONDS)?.apply {
//                locationPackage.cell = this.cell
//            }
//
//            /** 현재 위치 **/
//            try {
//                val locationScanTask = if (params.isLocationScanEnabled) {
//                    newLocationScanTask()
//                } else {
//                    null
//                }?.apply {
//                    executor.execute(this)
//                }
//                locationScanTask?.get(5, TimeUnit.SECONDS)?.apply {
//                    locationPackage.locationError = this.locationError
//                    locationPackage.location = this.location
//                }
//            } catch (e: Exception) {
//                locationPackage.locationError = ScannerException.Type.UNKNOWN_ERROR
//                listener?.onLocationFailure(
//                        ScannerException(ScannerType.LOCATION, ScannerException.Type.UNKNOWN_ERROR)
//                )
//                return@execute
//            }
//            /** 비콘 스캐닝 **/
//            try {
//                val bleScanTask = if (params.isBleScanEnabled) {
//                    newBleScanTask(params)
//                } else {
//                    null
//                }?.apply {
//                    executor.execute(this)
//                }
//                bleScanTask?.get(5, TimeUnit.SECONDS)?.apply {
//                    locationPackage.bleScanResults = this.bleScanResults
//                    locationPackage.isBleScanningEnabled = this.isBleScanningEnabled
//                }
//            } catch (e: Exception) {
//                listener?.onLocationFailure(
//                        ScannerException(ScannerType.BEACON, ScannerException.Type.UNKNOWN_ERROR)
//                )
//                return@execute
//            }
//
//            /** 와이파이 스캐닝 **/
//            try {
//                val wifiScanTask = if (params.isWifiScanEnabled) {
//                    newWifiScanTask()
//                } else {
//                    null
//                }?.apply {
//                    executor.execute(this)
//                }
//                wifiScanTask?.get(5, TimeUnit.SECONDS)?.apply {
//                    locationPackage.wifiScanResults = this.wifiScanResults
//                    locationPackage.isWifiScanningEnabled = this.isWifiScanningEnabled
//                }
//            } catch (e: Exception) {
//                listener?.onLocationFailure(
//                        ScannerException(ScannerType.WIFI, ScannerException.Type.UNKNOWN_ERROR)
//                )
//                return@execute
//            }
//            try {
//                listener?.onLocationPackage(locationPackage)
//            } catch (ignore: Exception) {
//            }
//        }
    }

    /** 기지국 스캐너 생성 **/
    private fun newCellScanner(): CellScanner = CellScannerImpl(context)

    /** 위치 스캐너 생성 **/
    private fun newLocationScanner(): LocationScanner = LocationScannerImpl(context, params)

    /** 위치 스캐너 Task 생성 **/
    private fun newLocationScanTask(): FutureTask<LocationPackage> {
        return FutureTask {
            val locationScanner = newLocationScanner()
            LocationPackage().apply {
                try {
                    locationScanner.init()
                } catch (e: ScannerException) {
                    this.locationError = e.scannerExceptionType
                    listener?.onLocationFailure(e)
                    return@apply
                }

                try {
                    this.location = locationScanner.getLocation()
                } catch (e: ScannerException) {
                    this.locationError = e.scannerExceptionType
                    return@apply
                }
            }
        }
    }

    /**
     * 비콘 스캐너 생성
     */
    private fun newBleScanner() = newBleScanner(context, params)


    /**
     * 비콘 스캐너 Task 생성
     */
    private fun newBleScanTask(params: ScannerParams): FutureTask<LocationPackage> {
        return FutureTask {
            LocationPackage().apply {
                val bleScanner = newBleScanner(context.applicationContext, params)
                try {
                    bleScanner.init()
                    try {
                        bleScanner.startScanning()
                        try {
                            Thread.sleep(params.bluetoothScanDurationMs)
                        } catch (ignore: Exception) {

                        }
                    } catch (e: ScannerException) {
                        listener?.onLocationFailure(e)
                    } finally {
                        bleScanner.stopScanning()
                    }
                    this.isBleScanningEnabled = bleScanner.getErrorCode() == 0
                    this.bleScanResults = bleScanner.getScanResults()
                } catch (e: ScannerException) {
                    listener?.onLocationFailure(e)
                }
            }
        }
    }

    /**
     * 와이파이 스캐너 생성
     */

    private fun newWifiScanner(): WifiScanner = WifiScannerImpl(context, params)


    /**
     * 와이파이 스캐너 Task 생성
     */
    private fun newWifiScanTask(): FutureTask<LocationPackage> {
        return FutureTask {
            LocationPackage().apply {
                val wifiScanner = newWifiScanner()
                try {
                    wifiScanner.init()
                    this.isWifiScanningEnabled = wifiScanner.isWifiScanningAvailable()
                    if (this.isWifiScanningEnabled) {
                        this.wifiScanResults = wifiScanner.getScanResults()
                    }
                } catch (e: ScannerException) {
                    this.isWifiScanningEnabled = false
                    listener?.onLocationFailure(e)
                }
            }
        }
    }

    private fun cancel() {
    }

    class Builder(
        val context: Context,
    ) {
        var listener: ScannerListener? = null
            private set
        var params: ScannerParams = ScannerParams.Builder().build()
            private set

        fun setScannerListener(listener: ScannerListener) = apply {
            this.listener = listener
        }

        fun setScannerParams(params: ScannerParams) = apply {
            this.params = params
        }

        fun build(): Scanner {
            if (listener == null) {
                throw IllegalArgumentException("You must setScannerListener() on Scanner");
            }
            return Scanner(this)
        }
    }

    companion object {
        const val OS_VERSION_LOLLIPOP = 21
        const val OS_VERSION_S = 31
        const val OS_VERSION_JELLY_BEAN_MR2 = 18
        const val OS_VERSION_JELLY_BEAN_MR1 = 17
        private var TAG: String = Scanner::class.java.simpleName
        private val executor = AsyncTask.THREAD_POOL_EXECUTOR

        fun newCellScanner(context: Context): CellScanner = CellScannerImpl(context)
        fun newLocationScanner(context: Context, params: ScannerParams): LocationScanner =
            LocationScannerImpl(context, params)

        fun newBleScanner(context: Context, params: ScannerParams): BeaconScanner =
            BeaconScannerImpl(context, params)

        fun newWifiScanner(context: Context, params: ScannerParams): WifiScanner =
            WifiScannerImpl(context, params)
    }
}
