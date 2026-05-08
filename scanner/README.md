# lpin-android-sdk-scanner
> 와이파이, 비콘, 기지국, 위치 등 위치인증에 필요한 데이터를 수집하기 위한 SDK 모듈

### 사용방법
Manifest 설정

```xml
 <!-- 와이파이 스캐닝 --> 
 <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
 <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
 <!-- 와이파이, 위치 스캐닝 -->
 <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 <!-- 비콘 스캐닝 -->
 <uses-permission android:name="android.permission.BLUETOOTH" />
 <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 <!-- 기지국 스캐닝 -->
 <uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

> Java
```java
// 기본적으로 와이파이, 비콘, 위치, 기지국 수집 활성화 상태 
ScannerParams scannerParams = new ScannerParams.Builder().build();
// 스캐닝 
new Scanner.Builder(getApplicationContext())
        .setScannerParams(scannerParams)
        .setScannerListener(new ScannerListener() {
            @Override
            public void onLocationFailure(ScannerException e) {
                e.printStackTrace();
            }
            @Override
            public void onLocationPackage(LocationPackage locationPackage) {
                Log.d("Scanner", locationPackage.toString());
            }
        })
        .build()
        .run();
```

> Kotlin 
```kotlin
/** Scanner Parameters **/
val params = ScannerParams
        .Builder()
        .setBluetoothScanEnabled(true)
        .setBluetoothMaxScanResults(100)
        .setBluetoothFlushResultsTimeoutMs(1000)
        .setBluetoothScanDurationMs(1200)
        .setWifiScanEnabled(true)
        .setWifiActiveScanForced(true)
        .setLocationScanEnabled(true)
        .setLocationProviders(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER
        )
        .build()

/** Scanner 동작 **/
Scanner.Builder(context)
        .setScannerListener(object : ScannerListener {
            override fun onLocationFailure(e: ScannerException) {
                when (e.scannerExceptionType) {
                    ScannerException.Type.NOT_SUPPORTED -> context.alert("경고", "현재 지원하지 않는 단말입니다.")
                    ScannerException.Type.PERMISSION_DENIED -> {
                        when (e.scannerType) {
                            ScannerType.BEACON -> context.alert("경고", "블루투스 권한이 필요합니다")
                            ScannerType.WIFI -> context.alert("경고", "위치 권한이 필요합니다")
                            ScannerType.LOCATION -> context.alert("경고", "위치 권한이 필요합니다")
                            ScannerType.CELL -> context.alert("경고", "전화 권한이 필요합니다")
                        }
                    }
                    ScannerException.Type.DISABLED -> {
                        when (e.scannerType) {
                            ScannerType.BEACON -> context.alert("경고", "현재 블루투스가 비활성화 되어있습니다.")
                            ScannerType.WIFI -> context.alert("경고", "현재 위치가 비활성화 되어있습니다.")
                            ScannerType.LOCATION -> context.alert("경고", "현재 위치가 비활성화 되어있습니다.")
                            ScannerType.CELL -> context.alert("경고", "")
                        }
                    }
                    ScannerException.Type.SCAN_ALREADY_IN_PROGRESS -> {

                    }
                    ScannerException.Type.UNKNOWN_ERROR -> {
                        context.alert("경고", "알수없는 에러가 발생했습니다. 다시 시도해 주세요.")
                    }
                    ScannerException.Type.TIMEOUT -> {
                        context.alert("경고", "수집가능한 시간이 초과되었습니다.")
                    }
                }
            }

            override fun onLocationPackage(locationPackage: LocationPackage) {
                Log.d("SpaceAuthenticator", locationPackage.toString())
                
                locationPackage.bleScanResults // 비콘 목록
                locationPackage.cell // 기지국 
                locationPackage.location // 현재 위치 
                locationPackage.wifiScanResults // 와이파이 목록
            }
        })
        .setScannerParams(params)
        .build()
        .run()
```

## ScannerParams 

기본적으로 위치, 와이파이, 비콘, 기지국 수집이 활성화 되어있다. 필요에 따라 값을 변경하여 스캐닝을 수행할 수 있다.

```kotlin
/** 위치 데이터 받기 활성화 **/
var isLocationScanEnabled = DEFAULT_LOCATION_ENABLED

/** 위치 Provider **/
var locationProviders = DEFAULT_LOCATION_PROVIDERS

/** 위치 Max Accuracy **/
var locationMaxAccuracyMeters = DEFAULT_LOCATION_MAX_ACCURACY_METERS

/** 위치 Max 업데이트 Timeout **/
var locationRequestTimeoutMs = DEFAULT_LOCATION_REQUEST_TIMEOUT_MS

/** 위치 최신 데이터 Alive 타임 **/
var lastLocationMaxAgeMs = DEFAULT_LAST_LOCATION_MAX_AGE_MS
    private set

/** 와이파이 스캐닝 활성화 **/
var isWifiScanEnabled = DEFAULT_WIFI_ENABLED

/** 와이파이 데이터 Alive 시간  **/
var wifiScanMaxAgeMs = DEFAULT_WIFI_SCAN_MAX_AGE_MS

/** 와이파이 Max 데이터 개수 **/
var wifiMaxScanResults = DEFAULT_WIFI_MAX_SCAN_RESULTS

/** 와이파이 데이터 스캐닝 시간 **/
var wifiScanTimeoutMs = DEFAULT_WIFI_SCAN_TIMEOUT_MS

/** 와이파이 데이터 데이터가 없을 때 업데이트 처리할건지 **/
var isWifiActiveScanAllowed = DEFAULT_WIFI_ACTIVE_SCAN_ALLOWED

/** 와이파이 데이터 강제 업데이트 ( 현재 데이터로 업데이트 처리 ) **/
var isWifiActiveScanForced = DEFAULT_WIFI_ACTIVE_SCAN_FORCED

/** 비콘 스캐닝 활성화 **/
var isBluetoothScanEnabled = DEFAULT_BLUETOOTH_ENABLED

/** 비콘 스캐닝 콜백 호출 대기 시간 **/
var bluetoothScanDurationMs = DEFAULT_BLUETOOTH_SCAN_DURATION_MS

/** 비콘 스캐닝 데이터 사이즈 **/
var bluetoothMaxScanResults = DEFAULT_BLUETOOTH_MAX_SCAN_RESULTS

/** 비콘 스캐닝 대기 시간 **/
var bluetoothFlushResultsTimeoutMs = DEFAULT_BLUETOOTH_FLUSH_RESULTS_TIMEOUT_MS

/** 기지국 스캐닝 활성화 **/
var isCellScanEnable = DEFAULT_CELL_ENABLE
```
