package io.lpin.android.sdk.plac.scanner

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.annotation.RequiresPermission

object ScannerValidate {
    /**
     * 권한 여부
     */
    private fun hasPermission(context: Context, permission: String): Boolean {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 권한 여부
     */
    fun hasPermission(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (!hasPermission(context, permission)) {
                return false
            }
        }
        return true
    }

    @Deprecated("권한 MOCK_ACCESS 필요")
    private fun isMockLocationOnCheckPackage(context: Context, packageString: String): Boolean {
        val opsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val opsMockLocation = AppOpsManager.OPSTR_MOCK_LOCATION
        val opsMockUid = Process.myUid()
        return opsManager.checkOp(
            opsMockLocation,
            opsMockUid,
            packageString
        ) == AppOpsManager.MODE_ALLOWED
    }

    /**
     * GPS 조작 체크
     */
    @SuppressLint("MissingPermission")
    fun isMockLocationOn(context: Context): Boolean {
        // 1. Provider 에서 Mock 체크
        var isMock = isLastLocationIsMock(context)
        if (isMock) {
            return true
        }
        // 2. Package 를 불러와서 패키지별로 체크
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        // Mock 을 사용하는 패키지를 가져온다.
        val mockPackages = packages.filter {
            try {
                val packageInfo =
                    pm.getPackageInfo(it.packageName, PackageManager.GET_PERMISSIONS)
                val packagePermissions = packageInfo.requestedPermissions
                return@filter packagePermissions.any { permission -> permission == "android.permission.ACCESS_MOCK_LOCATION" }
            } catch (ignore: Exception) {
                return@filter false
            }
        }
        // Mock 을 사용하는 패키지중 활성화 되어있는것을 체크한다
        isMock = mockPackages.any {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    isMockLocationOnCheckPackage(context, it.packageName)
                } catch (ignore: Exception) {
                    false
                }
            } else {
                false
            }
        }
        if (isMock) {
            return true
        }
        // 3. Android 설정에서 Mock 이 활성화 되었는지 확인한다.
        val opsManager = (context.getSystemService(Context.APP_OPS_SERVICE)) as AppOpsManager
        return try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    opsManager.unsafeCheckOp(
                        AppOpsManager.OPSTR_MOCK_LOCATION,
                        Process.myUid(),
                        context.packageName
                    ) == AppOpsManager.MODE_ALLOWED
                } else {
                    opsManager.checkOp(
                        AppOpsManager.OPSTR_MOCK_LOCATION,
                        Process.myUid(),
                        context.packageName
                    ) == AppOpsManager.MODE_ALLOWED
                }
            } else {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ALLOW_MOCK_LOCATION
                ) != "0"
            }
        } catch (ignore: Exception) {
            false
        }
    }

    /**
     * 마지막 Location 에서 위치 Mock 인지 판단한다.
     */
    @SuppressLint("MissingPermission")
    fun isLastLocationIsMock(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationProviders =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        // 1. Provider 에서 Mock 체크
        locationProviders
            .mapNotNull {
                locationManager.getLastKnownLocation(it)
            }
            .forEach {
                if (it.isFromMockProvider) {
                    return true
                }
            }
        return false
    }

    fun isCurrLocationIsMock(context: Context, f: (Boolean) -> Unit) {
        val scannerParams = ScannerParams.Builder()
            .setBluetoothScanEnabled(false)
            .setWifiScanEnabled(false)
            .setCellScanEnable(false)
            .setLocationScanEnabled(true)
            .build()
        val scanner = Scanner.Builder(context)
            .setScannerParams(scannerParams)
            .setScannerListener(object : ScannerListener {
                override fun onLocationFailure(e: ScannerException) {
                    if (e.scannerType == ScannerType.LOCATION) {
                        f.invoke(false)
                    }
                }

                override fun onLocationPackage(locationPackage: LocationPackage) {
                    f.invoke(locationPackage.location?.isFromMockProvider ?: false)
                }
            })
        scanner.build().run()
    }
}