package io.lpin.android.sdk.plac.scanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

class ScannerPermission(private val activity: Activity) {
    private val manager: WifiManager =
        activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * 권한 요청
     */
    @SuppressLint("ObsoleteSdkInt")
    fun requestScanAlwaysAvailable() {
        if (!manager.isScanAlwaysAvailable) {
            activity.startActivityForResult(
                Intent(WifiManager.ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE),
                1
            )
        }
    }

    fun requestIgnoringBatteryOptimizations(listener: ((Boolean) -> Unit)) {
        ScannerPermissionActivity.requestIgnoringBatteryOptimizations(activity, listener)
    }
}