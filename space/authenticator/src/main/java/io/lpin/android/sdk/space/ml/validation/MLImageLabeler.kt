package io.lpin.android.sdk.space.ml.validation

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class MLImageLabeler {
    companion object {
        private var TAG: String = MLImageLabeler::class.java.simpleName
        private var MAX_TIMEOUT = 5 * 1000L
        private val ALLOW_FILTER_CONFIDENCE = 0.8

        private val ALLOW_FILTER_OBJECTS_INDEX = listOf(7, 8, 13, 16, 25, 26, 29, 30, 42, 47, 54, 64, 69, 74, 78, 81, 88, 89, 90, 91, 92, 93, 101, 111, 115, 117, 125, 126, 154, 156, 157, 158, 160, 169, 171, 172, 173, 176, 180, 199, 201, 205, 210, 215, 217, 219, 225, 226, 228, 229, 230, 236, 243, 244, 246, 253, 255, 257, 259, 265, 266, 273, 278, 279, 280, 289, 290, 293, 310, 314, 317, 318, 320, 322, 330, 343, 344, 350, 352, 362, 363, 366, 368, 370, 376, 384, 391, 392, 394, 401, 406, 410, 411, 412, 434)

        // ObjectDetection
        private val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build()
        private val detection: ObjectDetector = ObjectDetection.getClient(options)

        // Image Labeler
        private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        private fun detectObjectRect(image: Bitmap): List<Rect> {
            val processResult = ArrayList<Rect>()
            val processLock = Object()
            // 이미지 Detection
            detection
                    .process(InputImage.fromBitmap(image, 0))
                    .addOnFailureListener { ex ->
                        ex.printStackTrace()
                        synchronized(processLock) {
                            processLock.notify()
                        }
                    }
                    .addOnCanceledListener {
                        synchronized(processLock) {
                            processLock.notify()
                        }
                    }
                    .addOnSuccessListener {
                        try {
                            processResult.addAll(it.map { detectedObject -> detectedObject.boundingBox })
                        } catch (ignore: Exception) {
                        }
                        synchronized(processLock) {
                            processLock.notify()
                        }
                    }
            try {
                synchronized(processLock) {
                    processLock.wait(MAX_TIMEOUT)
                }
            } catch (ignore: InterruptedException) {
            }
            return processResult
        }

        private fun detectImageLabel(image: Bitmap, bounds: List<Rect>): List<Pair<String, Float>> {
            val processResult = ArrayList<Pair<String, Float>>()
            val processLock = Object()
            // Bounding 된 이미지를 이용하여 Image Labeling
            val boundImages = bounds.map { Bitmap.createBitmap(image, it.left, it.top, it.width(), it.height()) }
            boundImages.forEach { boundImage ->
                labeler.process(InputImage.fromBitmap(boundImage, 0))
                        .addOnFailureListener { ex ->
                            ex.printStackTrace()
                            synchronized(processLock) {
                                processLock.notify()
                            }
                        }
                        .addOnCanceledListener {
                            synchronized(processLock) {
                                processLock.notify()
                            }
                        }
                        .addOnSuccessListener { labels ->
                            val label = labels.filter { label -> label.confidence >= ALLOW_FILTER_CONFIDENCE }.maxByOrNull { label -> label.confidence }
                            // 라벨이 존재하고, Allow Index에 존재할 때
                            if (label != null && ALLOW_FILTER_OBJECTS_INDEX.any { index -> index == label.index }) {
                                processResult.add(Pair(label.text, label.confidence))
                            }
                            synchronized(processLock) {
                                processLock.notify()
                            }
                        }
                try {
                    synchronized(processLock) {
                        processLock.wait(MAX_TIMEOUT)
                    }
                } catch (ignore: InterruptedException) {
                }
            }
            return processResult
        }

        // ObjectDetection & ImageLabeling
        fun detect(image: Bitmap): List<Pair<String, Float>> {
            val bounds = detectObjectRect(image)
            if (bounds.isNotEmpty()) {
                return detectImageLabel(image, bounds)
            }
            return emptyList()
        }

        // 두개 사물 검사
        fun compareScore(
                regObjectList: List<Pair<String, Float>>,
                curObjectList: List<Pair<String, Float>>): Float {
//            Log.d(TAG, "regObjectList ${regObjectList.map { it.first }}")
//            Log.d(TAG, "curObjectList ${curObjectList.map { it.first }}")
            try {
                // 동일한 라벨이 있는 이미지 개수
                var compareSize = 0
                regObjectList.forEach { a ->
                    if (curObjectList.any { b -> b.first == a.first }) {
                        compareSize += 1
                    }
                }

                // 오브젝트가 많은 리스트의 개수
                val featureMaxSize = regObjectList.size.coerceAtLeast(curObjectList.size)
//                Log.d(TAG, "CompareSize(${compareSize}) / FeatureMaxSize(${featureMaxSize})")

                // 인증 점수 계산
                return compareSize.toFloat() / featureMaxSize.toFloat()
            } catch (e: Exception) {
                e.printStackTrace()
                return 0.0F
            }
        }

        // 두개 사물 검사
        fun compareSize(
                regObjectList: List<Pair<String, Float>>,
                curObjectList: List<Pair<String, Float>>): Int {
//            Log.d(TAG, "regObjectList ${regObjectList.map { it.first }}")
//            Log.d(TAG, "curObjectList ${curObjectList.map { it.first }}")
            return try {
                regObjectList.filter { reg -> curObjectList.any { cur -> cur.first == reg.first } }.size
            } catch (ignore: Exception) {
                0
            }
        }
    }
}