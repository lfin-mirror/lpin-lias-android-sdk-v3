package io.lpin.android.sdk.face.liveness

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.View
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.internal.FaceDetectorImpl

class LivenessEyeBlink
@JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private lateinit var mFaces: List<Face>

    private var leftEyeOpenProbability: Float = -1.0F
    private var rightEyeOpenProbability: Float = -1.0F
    private var blinkCount = 0
    private var detector: FaceDetector? = null

    fun init() {
        val options = FaceDetectorOptions.Builder()
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        detector = try {
            FaceDetection.getClient(options)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
    }

    /**
     * 현재 사용자 Live 판단.
     */
    fun detecting(bitmap: Bitmap, f: () -> Unit) {
        detector
            ?.process(InputImage.fromBitmap(bitmap, 0))
            ?.addOnFailureListener {
                f.invoke()
            }
            ?.addOnCanceledListener {
                f.invoke()
            }
            ?.addOnSuccessListener {
                mFaces = it.toList()
                val blinked = isEyeBlinked
                // val toggled = isEyeToggled
                if (blinked) {
                    Log.d("isEyeBlinked", "eye blink is observed")
                    blinkCount++
                    // Log.d("EyeBlinked", "Count: $blinkCount")
                }
                f.invoke()
            }
    }

    /**
     * 현재 사용자가 Live 상태인지 확인
     */
    fun isLive(): Boolean {
        return blinkCount >= 2
    }

    private val isEyeBlinked: Boolean
        get() {
            if (mFaces.isEmpty())
                return false
            val face = mFaces[0]
            val currentLeftEyeOpenProbability = face.leftEyeOpenProbability ?: -1.0F
            val currentRightEyeOpenProbability = face.rightEyeOpenProbability ?: -1.0F
            if (currentLeftEyeOpenProbability == -1.0F || currentRightEyeOpenProbability == -1.0F) {
                return false
            }
            // Log.d("EyeBlinked", "LEFT : $currentLeftEyeOpenProbability / RIGHT : $currentRightEyeOpenProbability")

            // 이전 상태가 눈을 뜬 상태이고
            val isBeforeEyeOpen = leftEyeOpenProbability > 0.95F || rightEyeOpenProbability > 0.95F
            val isCurrentEyeClose =
                currentLeftEyeOpenProbability < 0.4F || currentRightEyeOpenProbability < 0.4F

            val blinked = isBeforeEyeOpen && isCurrentEyeClose

            leftEyeOpenProbability = currentLeftEyeOpenProbability
            rightEyeOpenProbability = currentRightEyeOpenProbability

            return blinked
        }
}