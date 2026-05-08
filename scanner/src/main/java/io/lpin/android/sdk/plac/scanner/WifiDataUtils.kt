package io.lpin.android.sdk.plac.scanner

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

private const val DISTANCE_MHZ_M = 27.55
private const val MIN_RSSI = -100
private const val MAX_RSSI = -55
private const val QUOTE = "\""

fun calculateDistance(frequency: Int, level: Int): Double =
        10.0.pow((DISTANCE_MHZ_M - 20 * log10(frequency.toDouble()) + abs(level)) / 20.0)

fun calculateSignalLevel(rssi: Int, numLevels: Int): Int = when {
    rssi <= MIN_RSSI -> 0
    rssi >= MAX_RSSI -> numLevels - 1
    else -> (rssi - MIN_RSSI) * (numLevels - 1) / (MAX_RSSI - MIN_RSSI)
}

fun convertSSID(ssid: String): String = ssid.removePrefix(QUOTE).removeSuffix(QUOTE)

fun convertIpAddress(ipAddress: Int): String {
    return try {
        val value: Long = when (ByteOrder.LITTLE_ENDIAN) {
            ByteOrder.nativeOrder() -> Integer.reverseBytes(ipAddress).toLong()
            else -> ipAddress.toLong()
        }
        InetAddress.getByAddress(value.toBigInteger().toByteArray()).hostAddress
    } catch (e: Exception) {
        ""
    }
}

/**
 * 현재 와이파이 쓰로틀링 여부 리턴
 * 마지막 Throttling 시간을 이용하여 계산한다.
 */

private const val WIFI_THROTTLING_CACHE_NAME = "WIFI_THROTTLING_CACHE_NAME"
private const val WIFI_THROTTLING_CACHE_DATA = "WIFI_THROTTLING_CACHE_DATA"
private const val WIFI_THROTTLING_FIELD_CREATED_AT = "WIFI_THROTTLING_FIELD_CREATED_AT"
private const val WIFI_THROTTLING_FIELD_UPDATED_AT = "WIFI_THROTTLING_FIELD_UPDATED_AT"
private const val WIFI_THROTTLING_FIELD_SCANNING_COUNT = "WIFI_THROTTLING_FIELD_SCANNING_COUNT"

private fun Context.getWifiManager(): WifiManager {
    return applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
}

fun Context.getWifiThrottlingCache(): WifiThrottlingData {
    val sp = applicationContext.getSharedPreferences(WIFI_THROTTLING_CACHE_NAME, Context.MODE_PRIVATE)
    val fieldCreatedAt = sp.getLong(WIFI_THROTTLING_FIELD_CREATED_AT, 0)
    val fieldUpdatedAt = sp.getLong(WIFI_THROTTLING_FIELD_UPDATED_AT, 0)
    val fieldScanningCount = sp.getInt(WIFI_THROTTLING_FIELD_SCANNING_COUNT, 0)
    return WifiThrottlingData(
            context = this,
            createdAt = fieldCreatedAt,
            updatedAt = fieldUpdatedAt,
            scanningCount = fieldScanningCount).apply { save() }
}

fun Context.setWifiThrottlingCache(cache: WifiThrottlingData) {
    val sp = applicationContext.getSharedPreferences(WIFI_THROTTLING_CACHE_NAME, Context.MODE_PRIVATE)
    sp.edit().let {
        it.putLong(WIFI_THROTTLING_FIELD_CREATED_AT, cache.createdAt)
        it.putLong(WIFI_THROTTLING_FIELD_UPDATED_AT, cache.updatedAt)
        it.putInt(WIFI_THROTTLING_FIELD_SCANNING_COUNT, cache.scanningCount)
    }.apply()
}


/**
 * 중복된 데이터를 제외하고, 추가한다.
 */
fun addDuplicateExclusion(a: List<WifiData>, b: List<WifiData>): List<WifiData> {
    val aDiffs = a.filter { aWifi -> !(b.any { bWifi -> aWifi.bssid == bWifi.bssid }) }
    val bDiffs = b.filter { bWifi -> !(a.any { aWifi -> bWifi.bssid == aWifi.bssid }) }
    val dDiffs = ArrayList<WifiData>()
    dDiffs.addAll(aDiffs)
    dDiffs.addAll(bDiffs)
    return dDiffs.sortedByDescending { it.rssi }
}

/**
 * 중복 데이터 평균 계산
 */
fun calDuplicateAverage(a: List<WifiData>, b: List<WifiData>): List<WifiData> {
    // 중복된 데이터 중 평균
    val cWifis: ArrayList<WifiData> = arrayListOf()
    for (i in a.indices) {
        // 중복된 데이터 평균을 구한다.
        val aWifi = a[i]
        val aRssi = aWifi.rssi
        val bWifis = b.filter { aWifi.bssid == it.bssid }
        if (bWifis.isEmpty())
            continue
        val bRssi = bWifis.map { it.rssi }.average()
        aWifi.rssi = ((aRssi + bRssi) / 2).toInt()
        cWifis.add(aWifi)
    }
    return cWifis
}

/**
 * 중복 데이터는 평균으로 계산하고, 중복되지 않는 데이터를 추가한다.
 */
fun calDuplicateAndAddDuplicateExclusion(a: List<WifiData>, b: List<WifiData>): List<WifiData> {
    val avg = calDuplicateAverage(a, b)
    val dup = ArrayList<WifiData>()
    dup.addAll(avg)
    dup.addAll(addDuplicateExclusion(avg, a))
    dup.addAll(addDuplicateExclusion(avg, b))
    return dup.sortedByDescending { it.rssi }
}