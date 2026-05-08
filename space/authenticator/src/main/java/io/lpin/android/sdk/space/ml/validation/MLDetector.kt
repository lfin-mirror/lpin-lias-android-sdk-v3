package io.lpin.android.sdk.space.ml.validation

import android.graphics.Bitmap
import android.util.Log

class MLDetector {
    companion object {
        private const val ALLOW_FILTER_OBJECT_SIZE = 1
    }

    var process: Boolean = false
        private set
    val detections: ArrayList<Pair<String, Float>> = arrayListOf()

    var addFailureCount = 0

    fun clear() {
        detections.clear()
        addFailureCount = 0
    }

    fun add(bitmap: Bitmap): Boolean {
        val detected = MLImageLabeler.detect(bitmap)
        Log.d("MLValidation Resize", "detected size : ${detected.size}")

        if (detected.size < ALLOW_FILTER_OBJECT_SIZE) {
            addFailureCount += 1
            return false
        }
        detected.forEach { b ->
            // 있는지 보기
            val a = detections.firstOrNull { it.first == b.first }
            if (a != null) {
                // b가 더 크면 a제거 후 b추가
                if (a.second < b.second) {
                    detections.remove(a)
                    detections.add(b)
                }
            } else {
                detections.add(b)
            }
        }
        addFailureCount = 0
        return true
    }
}