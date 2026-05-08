package io.lpin.android.sdk.plac.scanner

import android.location.LocationManager


data class ScannerParams(private val builder: Builder) {
    /** 위치 데이터 받기 활성화 **/
    var isLocationScanEnabled: Boolean = false

    /** 위치 Provider **/
    var locationProviders: Array<String>

    /** 위치 Max Accuracy **/
    var locationMaxAccuracyMeters: Float = 0f

    /** 위치 Max 업데이트 Timeout **/
    var locationRequestTimeoutMs: Long = 0

    /** 위치 최신 데이터 Alive 타임 **/
    var lastLocationMaxAgeMs: Long = 0

    /** 마지막 위치데이터를 가져올지 여부 **/
    var lastLocation: Boolean = true

    /** 와이파이 스캐닝 활성화 **/
    var isWifiScanEnabled: Boolean = false

    /** 와이파이 데이터 Alive 시간  **/
    var wifiScanMaxAgeMs: Long = 0

    /** 와이파이 Max 데이터 개수 **/
    var wifiMaxScanResults: Int = 0

    /** 와이파이 데이터 스캐닝 시간 **/
    var wifiScanTimeoutMs: Long = 0

    /** 와이파이 데이터 데이터가 없을 때 업데이트 처리할건지 **/
    var isWifiActiveScanAllowed: Boolean = false

    /** 와이파이 데이터 강제 업데이트 ( 현재 데이터로 업데이트 처리 ) **/
    var isWifiActiveScanForced: Boolean = false

    /** 비콘 스캐닝 활성화 **/
    var isBleScanEnabled: Boolean = false

    /** 비콘 스캐닝 콜백 호출 대기 시간 **/
    var bluetoothScanDurationMs: Long = 0

    /** 비콘 스캐닝 데이터 사이즈 **/
    var bluetoothMaxScanResults: Int = 0

    /** 비콘 스캐닝 대기 시간 **/
    var bluetoothFlushResultsTimeoutMs: Long = 0

    /** 기지국 스캐닝 활성화 **/
    var isCellScanEnable: Boolean = true

    /** 와이파이 2.4GHz 만 스캔 **/
    var isWifi2GHzOnly: Boolean = false

    /** 와이파이 5GHz 만 스캔 **/
    var isWifi5GHzOnly: Boolean = false

    init {
        /**
         * 위치(Location) 스캔
         */
        isLocationScanEnabled = builder.isLocationScanEnabled
        locationProviders = builder.locationProviders
        locationMaxAccuracyMeters = builder.locationMaxAccuracyMeters
        locationRequestTimeoutMs = builder.locationRequestTimeoutMs
        lastLocationMaxAgeMs = builder.lastLocationMaxAgeMs
        lastLocation = builder.lastLocation

        /**
         * 와이파이 스캔
         */
        isWifiScanEnabled = builder.isWifiScanEnabled
        wifiScanMaxAgeMs = builder.wifiScanMaxAgeMs
        wifiMaxScanResults = builder.wifiMaxScanResults
        wifiScanTimeoutMs = builder.wifiScanTimeoutMs
        isWifiActiveScanAllowed = builder.isWifiActiveScanAllowed
        isWifiActiveScanForced = builder.isWifiActiveScanForced
        isWifi2GHzOnly = builder.isWifi2GHzOnly
        isWifi5GHzOnly = builder.isWifi5GHzOnly

        /**
         * Beacon 스캔
         */
        isBleScanEnabled = builder.isBluetoothScanEnabled
        bluetoothScanDurationMs = builder.bluetoothScanDurationMs
        bluetoothMaxScanResults = builder.bluetoothMaxScanResults
        bluetoothFlushResultsTimeoutMs = builder.bluetoothFlushResultsTimeoutMs

        /**
         * 기지국
         */
        isCellScanEnable = builder.isCellScanEnable
    }

    class Builder {
        /** 위치 데이터 받기 활성화 **/
        var isLocationScanEnabled = DEFAULT_LOCATION_ENABLED
            private set

        /** 위치 Provider **/
        var locationProviders = DEFAULT_LOCATION_PROVIDERS
            private set

        /** 위치 Max Accuracy **/
        var locationMaxAccuracyMeters = DEFAULT_LOCATION_MAX_ACCURACY_METERS
            private set

        /** 위치 Max 업데이트 Timeout **/
        var locationRequestTimeoutMs = DEFAULT_LOCATION_REQUEST_TIMEOUT_MS
            private set

        /** 위치 최신 데이터 Alive 타임 **/
        var lastLocationMaxAgeMs = DEFAULT_LAST_LOCATION_MAX_AGE_MS
            private set

        /** 마지막 위치 데이터 조회 여부 **/
        var lastLocation = DEFAULT_LAST_LOCATION
            private set

        /** 와이파이 스캐닝 활성화 **/
        var isWifiScanEnabled = DEFAULT_WIFI_ENABLED
            private set

        /** 와이파이 데이터 Alive 시간  **/
        var wifiScanMaxAgeMs = DEFAULT_WIFI_SCAN_MAX_AGE_MS
            private set

        /** 와이파이 Max 데이터 개수 **/
        var wifiMaxScanResults = DEFAULT_WIFI_MAX_SCAN_RESULTS
            private set

        /** 와이파이 데이터 스캐닝 시간 **/
        var wifiScanTimeoutMs = DEFAULT_WIFI_SCAN_TIMEOUT_MS
            private set

        /** 와이파이 데이터 데이터가 없을 때 업데이트 처리할건지 **/
        var isWifiActiveScanAllowed = DEFAULT_WIFI_ACTIVE_SCAN_ALLOWED
            private set

        /** 와이파이 데이터 강제 업데이트 ( 현재 데이터로 업데이트 처리 ) **/
        var isWifiActiveScanForced = DEFAULT_WIFI_ACTIVE_SCAN_FORCED
            private set

        /** 비콘 스캐닝 활성화 **/
        var isBluetoothScanEnabled = DEFAULT_BLUETOOTH_ENABLED
            private set

        /** 비콘 스캐닝 콜백 호출 대기 시간 **/
        var bluetoothScanDurationMs = DEFAULT_BLUETOOTH_SCAN_DURATION_MS
            private set

        /** 비콘 스캐닝 데이터 사이즈 **/
        var bluetoothMaxScanResults = DEFAULT_BLUETOOTH_MAX_SCAN_RESULTS
            private set

        /** 비콘 스캐닝 대기 시간 **/
        var bluetoothFlushResultsTimeoutMs = DEFAULT_BLUETOOTH_FLUSH_RESULTS_TIMEOUT_MS
            private set

        /** 기지국 스캐닝 활성화 **/
        var isCellScanEnable = DEFAULT_CELL_ENABLE
            private set

        /** 와이파이 2.4GHz 만 스캔 **/
        var isWifi2GHzOnly = DEFAULT_WIFI_2_4GHZ_ONLY
            private set

        /** 와이파이 5GHz 만 스캔 **/
        var isWifi5GHzOnly = DEFAULT_WIFI_5GHZ_ONLY
            private set

        fun setLocationScanEnabled(locationScanEnabled: Boolean): Builder = apply {
            isLocationScanEnabled = locationScanEnabled
        }

        fun setLastLocationMaxAgeMs(lastLocationMaxAgeMs: Long): Builder = apply {
            this.lastLocationMaxAgeMs = lastLocationMaxAgeMs
        }

        fun setLocationProviders(vararg locationProviders: String) = apply {
            this.locationProviders = locationProviders.toList().toTypedArray()
        }

        fun setLastLocation(lastLocation: Boolean) = apply {
            this.lastLocation = lastLocation
        }

        fun setLocationMaxAccuracyMeters(locationMaxAccuracyMeters: Float): Builder = apply {
            this.locationMaxAccuracyMeters = locationMaxAccuracyMeters
        }

        fun setLocationRequestTimeoutMs(locationRequestTimeoutMs: Long): Builder = apply {
            this.locationRequestTimeoutMs = locationRequestTimeoutMs
        }

        fun setWifiScanEnabled(wifiScanEnabled: Boolean): Builder = apply {
            isWifiScanEnabled = wifiScanEnabled
        }

        fun setWifiScanMaxAgeMs(wifiScanMaxAgeMs: Long): Builder = apply {
            this.wifiScanMaxAgeMs = wifiScanMaxAgeMs
        }

        fun setWifiMaxScanResults(wifiMaxScanResults: Int): Builder = apply {
            this.wifiMaxScanResults = wifiMaxScanResults
        }

        fun setWifiScanTimeoutMs(wifiScanTimeoutMs: Long): Builder = apply {
            this.wifiScanTimeoutMs = wifiScanTimeoutMs
        }

        fun setWifiActiveScanAllowed(wifiActiveScanAllowed: Boolean): Builder = apply {
            isWifiActiveScanAllowed = wifiActiveScanAllowed
        }

        fun setWifiActiveScanForced(wifiActiveScanForced: Boolean): Builder = apply {
            isWifiActiveScanForced = wifiActiveScanForced
        }

        fun setWifiScan2GHzOnly(only: Boolean): Builder = apply {
            isWifi2GHzOnly = only
        }

        fun setWifiScan5GHzOnly(only: Boolean): Builder = apply {
            isWifi5GHzOnly = only
        }

        fun setBluetoothScanEnabled(bluetoothScanEnabled: Boolean): Builder = apply {
            isBluetoothScanEnabled = bluetoothScanEnabled
        }

        fun setBluetoothScanDurationMs(bluetoothScanDurationMs: Long): Builder = apply {
            this.bluetoothScanDurationMs = bluetoothScanDurationMs
        }

        fun setBluetoothMaxScanResults(bluetoothMaxScanResults: Int): Builder = apply {
            this.bluetoothMaxScanResults = bluetoothMaxScanResults
        }

        fun setBluetoothFlushResultsTimeoutMs(bluetoothFlushResultsTimeoutMs: Long) = apply {
            this.bluetoothFlushResultsTimeoutMs = bluetoothFlushResultsTimeoutMs
        }

        fun setCellScanEnable(value: Boolean) = apply {
            this.isCellScanEnable = value
        }

        fun build(): ScannerParams {
            return ScannerParams(this)
        }
    }

    companion object {
        private const val DEFAULT_LOCATION_ENABLED = true
        private val DEFAULT_LOCATION_PROVIDERS = arrayOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        private const val DEFAULT_LOCATION_MAX_ACCURACY_METERS = 100f
        private const val DEFAULT_LOCATION_REQUEST_TIMEOUT_MS = 6 * 1000.toLong()
        private const val DEFAULT_LAST_LOCATION = true
        private const val DEFAULT_LAST_LOCATION_MAX_AGE_MS = 60 * 1000.toLong()
        private const val DEFAULT_WIFI_ENABLED = true
        private const val DEFAULT_WIFI_SCAN_MAX_AGE_MS = 30 * 1000.toLong()
        private const val DEFAULT_WIFI_SCAN_TIMEOUT_MS = 6 * 1000.toLong()
        private const val DEFAULT_WIFI_MAX_SCAN_RESULTS = 25
        private const val DEFAULT_WIFI_ACTIVE_SCAN_ALLOWED = true
        private const val DEFAULT_WIFI_ACTIVE_SCAN_FORCED = false
        private const val DEFAULT_WIFI_2_4GHZ_ONLY = false
        private const val DEFAULT_WIFI_5GHZ_ONLY = false

        private const val DEFAULT_BLUETOOTH_ENABLED = true
        private const val DEFAULT_BLUETOOTH_SCAN_DURATION_MS: Long = 500
        private const val DEFAULT_BLUETOOTH_MAX_SCAN_RESULTS = 25
        private const val DEFAULT_BLUETOOTH_FLUSH_RESULTS_TIMEOUT_MS: Long = 300
        private const val DEFAULT_CELL_ENABLE = true
    }
}
