package io.lpin.android.sdk.plac.scanner

import org.junit.Test


class WifiDataUtilsTest {
    @Test
    fun _중복데이터_제외추가() {
        val a = listOf(
                WifiData("", "A", -10),
                WifiData("", "B", -20),
                WifiData("", "C", -30),
                WifiData("", "D", -40)
        )
        val b = listOf(
                WifiData("", "B", -10),
                WifiData("", "C", -30),
                WifiData("", "E", -30)
        )
        println(addDuplicateExclusion(a, b))
    }

    @Test
    fun _중복데이터_평균() {
        val a = listOf(
                WifiData("", "A", -10),
                WifiData("", "B", -20),
                WifiData("", "C", -30),
                WifiData("", "D", -40)
        )
        val b = listOf(
                WifiData("", "B", -14),
                WifiData("", "C", -30),
                WifiData("", "E", -30)
        )
        println(calDuplicateAverage(a, b))
    }

    @Test
    fun _중복데이터_평균_추가() {
        val a = listOf(
                WifiData("", "A", -10),
                WifiData("", "B", -20),
                WifiData("", "C", -30),
                WifiData("", "D", -40)
        )
        val b = listOf(
                WifiData("", "B", -14),
                WifiData("", "C", -30),
                WifiData("", "E", -30)
        )
        val dup = calDuplicateAndAddDuplicateExclusion(a, b)
        print(dup)
    }
}