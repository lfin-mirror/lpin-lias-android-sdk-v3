package io.lpin.android.sdk.face

import android.graphics.Bitmap
import io.lpin.android.sdk.face.extenstions.toJpegByteArray

data class FaceData(
        val faceUuid: String,
        val hashFace: String,
        private val similarity: Float,
        val thumbnail: Bitmap? = null
) {
    fun getSimilarity(): Float {
        return try {
            // 소숫점 두자리 고정을 위해 Format 후 Float 변경
            String.format("%.2f", (similarity * 100.0F)).toFloat()
        } catch (ignore: Exception) {
            0.0F
        }
    }

    @JvmOverloads
    fun getThumbnailJpeg(quality: Int = 85): ByteArray? {
        return thumbnail?.toJpegByteArray(quality)
    }
}
