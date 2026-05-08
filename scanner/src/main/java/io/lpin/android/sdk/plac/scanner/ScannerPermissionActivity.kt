package io.lpin.android.sdk.plac.scanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import java.util.*

class ScannerPermissionActivity : Activity() {

    private var listener: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState)
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            // Remove title bar
            this.requestWindowFeature(Window.FEATURE_NO_TITLE)
            this.actionBar?.hide()
        } catch (ignore: Exception) {
        }

        listener = listeners?.pop()
        if (listeners?.isEmpty() == true) {
            listeners = null
        }

        requestIgnoringBatteryOptimizations()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0);
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BATTERY) {
            Handler(Looper.getMainLooper()).post { listener?.invoke(isWhiteList(applicationContext)) }
            finish()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoringBatteryOptimizations() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            startActivityForResult(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")), REQUEST_BATTERY)
        } else {
            finish()
        }
    }

    companion object {
        private const val REQUEST_BATTERY = 0x123
        private var TAG: String = ScannerPermissionActivity::class.java.simpleName
        private var listeners: ArrayDeque<(Boolean) -> Unit>? = null

        /**
         * 배터리 최적화 중지
         */
        fun requestIgnoringBatteryOptimizations(context: Context, listener: ((Boolean) -> Unit) = {}) {
            if (isWhiteList(context))
                return

            if (listeners == null) {
                listeners = ArrayDeque()
            }
            listeners?.add(listener)
            val intent = Intent(context, ScannerPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }
            context.startActivity(intent)
        }

        // 앱 화이트 리스트 등록 되어있는 지 검사
        fun isWhiteList(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            var isWhiteListing = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                isWhiteListing = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
            return isWhiteListing
        }
    }
}