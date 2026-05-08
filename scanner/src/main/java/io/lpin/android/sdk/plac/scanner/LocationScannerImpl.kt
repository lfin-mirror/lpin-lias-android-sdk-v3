package io.lpin.android.sdk.plac.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import kotlin.jvm.Throws


@SuppressLint("MissingPermission")
class LocationScannerImpl(
    val context: Context,
    val params: ScannerParams,
) : LocationScanner, LocationListener {
    private var locationManager: LocationManager? = null

    /** 제일 최근 데이터 **/
    private var freshLocation: Location? = null
    private val scanLock = Object()

    /** GPS Providers **/
    private var providers: ArrayList<String> = arrayListOf()

    init {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun init() {
        // 권한 확인
        if (!ScannerValidate.hasPermission(context, permissions)) {
            throw ScannerException(ScannerType.LOCATION, ScannerException.Type.PERMISSION_DENIED)
        }

        // 현재 사용할 수 있는 Providers
        providers = ArrayList(params.locationProviders.size)
        for (provider in params.locationProviders) {
            if (locationManager!!.isProviderEnabled(provider)) {
                providers.add(provider)
            }
        }
        if (providers.isEmpty()) {
            throw ScannerException(ScannerType.LOCATION, ScannerException.Type.DISABLED)
        }
    }

    /**
     * 마지막 위치 데이터를 가져온다.
     */
    private fun getLastLocation(provider: String): Location? {
        val lastLocation = locationManager?.getLastKnownLocation(provider)
        if (lastLocation != null) {
            val lastLocationTs = lastLocation.time
            val locationAgeMs = System.currentTimeMillis() - lastLocationTs
            if (locationAgeMs < params.lastLocationMaxAgeMs && lastLocation.accuracy < params.locationMaxAccuracyMeters) {
                return lastLocation
            }
        }
        return null
    }

    override fun getLocation(): Location? {
        val lastLocation = providers.mapNotNull { getLastLocation(it) }.minByOrNull { it.accuracy }

        var location: Location? = try {
            // 최신 위치데이터 조회
            lastLocation ?: getFreshLocation()
        } catch (ignore: Exception) {
            // 현재 저장된 위치데이터 조회 시간을 기준으로 필터링 하여 데이터를 가져온다.
            null
        }
        // 현재 저장된 위치데이터중 accuracy 값이 제일 작은 데이터를 가져온다.
        if (params.lastLocation && location == null) {
            location = providers
                .mapNotNull { provider -> locationManager?.getLastKnownLocation(provider) }
                .filter { it.accuracy < params.locationMaxAccuracyMeters }
                .minByOrNull { it.accuracy }
        }
        return location
    }

    @Throws(ScannerException::class)
    private fun getFreshLocation(): Location? {
        freshLocation = null
        val handler = HandlerThread("LocationScanner")
        try {
            handler.start()
            for (provider in providers) {
                locationManager!!.requestLocationUpdates(
                    provider,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_BETWEEN_UPDATES,
                    this,
                    handler.looper
                )
            }
            try {
                synchronized(scanLock) {
                    scanLock.wait(params.locationRequestTimeoutMs)
                }
            } catch (e: Exception) {
                // ignore
            }
        } finally {
            locationManager!!.removeUpdates(this)
            handler.quit()
        }
        if (freshLocation == null) {
            throw ScannerException(ScannerType.LOCATION, ScannerException.Type.TIMEOUT)
        }
        return freshLocation
    }

    override fun onLocationChanged(location: Location) {
        if (freshLocation == null) {
            if (location.accuracy < params.locationMaxAccuracyMeters) {
                synchronized(scanLock) {
                    freshLocation = location
                    // wait 종료
                    scanLock.notify()
                }
            }
        }
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {

    }

    override fun onProviderEnabled(provider: String) {

    }

    override fun onProviderDisabled(provider: String) {

    }

    companion object {
        var permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        private const val MIN_TIME_BETWEEN_UPDATES = 100L
        private const val MIN_DISTANCE_BETWEEN_UPDATES = 0f
    }
}