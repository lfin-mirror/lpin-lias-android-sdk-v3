package io.lpin.android.sdk.plac.scanner

import android.content.Context
import android.os.Build

data class WifiThrottlingData(
        private var context: Context? = null,
        var createdAt: Long = AVAILABLE_TIME,
        var updatedAt: Long,
        var scanningCount: Int
) {
    private fun isInTwoMin(): Boolean = ((createdAt + TWO_MIN) >= System.currentTimeMillis())

    /**
     * 현재 스캐닝 카운트와 시간을 이용하여 쓰로틀링 여부를 판단한다.
     */
    fun isThrottling(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            return false
        if (createdAt == AVAILABLE_TIME) {
            return false
        }
        return isInTwoMin() && (scanningCount >= 4)
    }

    fun save() {
        context?.setWifiThrottlingCache(this)
    }

    companion object {
        private const val TWO_MIN = (2 * 60 * 1000)
        const val AVAILABLE_TIME = 0L
        const val THROTTLING_TIME = -1L

        fun getInstance(context: Context): WifiThrottlingData {
            return context.getWifiThrottlingCache()
        }
    }
}