package io.lpin.android.sdk.plac.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log


class WifiScannerImpl(
    private val context: Context,
    private val params: ScannerParams
) : WifiScanner {
    private var wifiManager: WifiManager? = null
    private lateinit var wifiThrottlingData: WifiThrottlingData
    private var broadcastReceiver: ScanResultBroadcastReceiver? = null
    private val scanLock = Object()

    /**
     * 스캐닝 결과 입력
     */
    private inner class ScanResultBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent.action) {
                // 현재 대기중인 Lock 해제
                synchronized(scanLock) {
                    scanLock.notify()
                }
                unregisterBroadcastReceiver()
            }
        }
    }

    override fun init() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            throw  ScannerException(ScannerType.WIFI, ScannerException.Type.NOT_SUPPORTED)
        }

        if (!ScannerValidate.hasPermission(context, permissions)) {
            throw ScannerException(ScannerType.WIFI, ScannerException.Type.PERMISSION_DENIED)
        }

        if (wifiManager == null) {
            wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }

        wifiThrottlingData = WifiThrottlingData.getInstance(context.applicationContext)

        val isWifiScanningAlwaysOn: Boolean = isWifiScanningAlwaysOn()

        if (!isWifiScanningAlwaysOn && !wifiManager!!.isWifiEnabled) {
            throw ScannerException(ScannerType.WIFI, ScannerException.Type.DISABLED)
        }
    }

    override fun isWifiScanningAvailable(): Boolean {
        try {
            init()
            if (ScannerValidate.hasPermission(context, permissions)) {
                return true
            }
        } catch (e: ScannerException) {
            // ignore
        }
        return false
    }

    /** 와이파이 데이터 호출 **/
    override fun getScanResults(): List<WifiData> {
        // 현재 Cache 된 데이터를 가져온다.
        var scanResults: List<WifiData> = getCachedScanResults()
        val scanResultsIsEmpty = scanResults.isEmpty()
        // 강제 업데이트 혹은 데이터가 없을 떄 업데이트할지 여부
        val scanEnable =
            params.isWifiActiveScanForced || (params.isWifiActiveScanAllowed && scanResultsIsEmpty)
        if (scanEnable)
            scanResults = getActiveScanResults()
        return scanResults
    }

    /** 현재 디바이에 캐시된 데이터를 가져온다 **/
    @SuppressLint("MissingPermission")
    @Throws(ScannerException::class)
    private fun getCachedScanResults(): List<WifiData> {
        return try {
            wifiManager
                ?.scanResults
                ?.filteredByMaxAge(params.wifiScanMaxAgeMs)
                ?.filteredByMaxScanSize(params.wifiMaxScanResults)
                ?.filter { !isWifiSsidBlacklisted(it.SSID) }
                ?.filter {
                    if (params.isWifi5GHzOnly && params.isWifi2GHzOnly)
                        true
                    else {
                        when {
                            params.isWifi2GHzOnly -> {
                                it.frequency in 2000..3000
                            }
                            params.isWifi5GHzOnly -> {
                                it.frequency >= 5000
                            }
                            else -> {
                                true
                            }
                        }
                    }
                }
                ?.map {
                    WifiData(
                        it.SSID, it.BSSID, it.level, it.frequency
//                                if (Build.VERSION.SDK_INT >= Scanner.OS_VERSION_JELLY_BEAN_MR1) {
//                                    TimeUnit.MICROSECONDS.toMillis(it.timestamp)
//                                } else {
//                                    SystemClock.elapsedRealtime()
//                                },
//                                it.frequency
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            throw ScannerException(ScannerType.WIFI, ScannerException.Type.UNKNOWN_ERROR, e)
        }
    }

    /** 현재 와이파이 데이터 호출 **/
    @Throws(ScannerException::class)
    private fun getActiveScanResults(): List<WifiData> {
        if (!ScannerValidate.hasPermission(context, permissions))
            throw ScannerException(ScannerType.WIFI, ScannerException.Type.PERMISSION_DENIED)
        try {
            // 와이파이 스캐닝 시작
            // 와이파이 레지스터 등록
            registerBroadcastReceiver()
            val isScanStarted = wifiManager?.startScan() ?: false
            // Log.d(TAG, "isScannerStarted: $isScanStarted")
            // Log.d(TAG, "wifiThrottling : ${wifiThrottlingData.isThrottling()}")

            if (isScanStarted) {
                if (wifiThrottlingData.scanningCount >= 4 ||
                    wifiThrottlingData.createdAt == 0L ||
                    wifiThrottlingData.isThrottling()
                ) {
                    wifiThrottlingData.scanningCount = 0
                    wifiThrottlingData.createdAt = System.currentTimeMillis()
                }
                wifiThrottlingData.scanningCount += 1
                wifiThrottlingData.save()

//                Log.d(TAG, "wifiThrottling Data : $wifiThrottlingData")

                try {
                    synchronized(scanLock) {
                        scanLock.wait(params.wifiScanTimeoutMs)
                    }
                } catch (ignore: InterruptedException) {
                    // ignore
                }
                return getCachedScanResults()
            } else {
                wifiThrottlingData.scanningCount = 4
                wifiThrottlingData.save()
            }
        } catch (ignore: Exception) {

        } finally {
            unregisterBroadcastReceiver()
        }
        return emptyList()
    }

    /** 스캐닝 사이즈만큼 데이터 추출 **/
    private fun List<ScanResult>.filteredByMaxScanSize(maxScanResultSize: Int): List<ScanResult> {
        val output: MutableList<ScanResult>
        synchronized(this) {
            val tmpOutput = this.sortedBy { -it.level }
            if (tmpOutput.size > maxScanResultSize) {
                output = ArrayList(maxScanResultSize)
                output.addAll(tmpOutput.subList(0, maxScanResultSize))
            } else {
                output = ArrayList(tmpOutput.size)
                output.addAll(tmpOutput)
            }
        }
        return output
    }

    /** 와이파이 데이터 생성 시간을 보고 데이터 사용여부를 결정한다. **/
    private fun List<ScanResult>.filteredByMaxAge(maxAgeMs: Long): List<ScanResult> {
        val filtered: MutableList<ScanResult> = ArrayList()
        if (Build.VERSION.SDK_INT < Scanner.OS_VERSION_JELLY_BEAN_MR1) {
            filtered.addAll(this)
        } else {
            val nowSinceBootMs = SystemClock.elapsedRealtime()
            for (scanResult in this) {
                // 현재 시간과 스캐닝된 데이터의 생성 시간을 비교한다.
                var ageMs = nowSinceBootMs - scanResult.timestamp / 1000
                if (ageMs < 0) {
                    ageMs = System.currentTimeMillis() - scanResult.timestamp
                }
                if (ageMs < maxAgeMs) {
                    filtered.add(scanResult)
                }
            }
        }
        return filtered
    }

    /** 와이파이 스캐닝 레지스터 등록 **/
    private fun registerBroadcastReceiver() {
        if (broadcastReceiver != null) {
            unregisterBroadcastReceiver()
        }
        broadcastReceiver = ScanResultBroadcastReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    /** 와이파이 스캐닝 레지스터 해제 **/
    private fun unregisterBroadcastReceiver() {
        if (broadcastReceiver != null) {
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (e: Exception) {
                // ignore
            }
            broadcastReceiver = null
        }
    }

    /** 와이파이 항상 허용인지 확인 **/
    private fun isWifiScanningAlwaysOn(): Boolean {
        return if (Build.VERSION.SDK_INT >= Scanner.OS_VERSION_JELLY_BEAN_MR2) {
            wifiManager!!.isScanAlwaysAvailable
        } else false
    }

    /** 와이파이 블랙리스트 ( 없는 데이터 제거등 ) **/
    private fun isWifiSsidBlacklisted(ssid: String?): Boolean {
        if (ssid != null) {
            if (ssid.endsWith(SSID_NOMAP) || ssid.contains(SSID_OPTOUT)) {
                return true
            }
        }
        return false
    }

    companion object {
        private var TAG: String = WifiScanner::class.java.simpleName
        private const val SSID_NOMAP = "_nomap"
        private const val SSID_OPTOUT = "_optout"
        var permissions = arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}