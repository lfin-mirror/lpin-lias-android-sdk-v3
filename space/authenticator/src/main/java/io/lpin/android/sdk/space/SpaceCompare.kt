package io.lpin.android.sdk.space

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

fun Bitmap.getSpaceFeature(spaceEngine: SpaceEngine): FloatArray {
    return spaceEngine.getFeature(this)
}

private fun distance(a: FloatArray, b: FloatArray): Float {
    return try {
        val featureSize = 512
        var distance = 0.0f
        for (i in 0 until featureSize) {
            val error = (a[i] - b[i]) * (a[i] - b[i])
            distance += error
        }
        distance
    } catch (e: Exception) {
        e.printStackTrace()
        0.0F
    }
}

//fun Context.featureComparison(a: Bitmap, b: Bitmap): Float {
//    val engine = SpaceEngine.getInstance(this)
//    return featureComparison(engine.getFeature(a), engine.getFeature(b))
//}

fun featureComparison(a: FloatArray, b: FloatArray): Float {
    return distance(a, b)
}