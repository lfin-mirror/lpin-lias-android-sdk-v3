package io.lpin.android.sdk.face.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import io.lpin.android.sdk.face.v1.legacy.view.utils.ImageUtils

class CameraFrameData {

    // 현재 Frame 데이터
    private lateinit var bitmap: Bitmap

    // Frame → Crop(224x224) Transform
    private lateinit var frameToCropTransform: Matrix

    // Crop 된 데이터
    private lateinit var croppedBitmap: Bitmap

    // Crop(224x224) → Frame Transform
    private lateinit var cropToFrameTransform: Matrix

    // Preview Width Size
    private var width: Int = -1

    // Preview Height Size
    private var height: Int = -1

    // 이미지 컨버터
    private lateinit var imageConverter: Runnable
    var pixels: IntArray? = null
    var orientation: Int = 0

    fun init(
        previewWith: Int,
        previewHeight: Int,
        cropWidth: Int,
        cropHeight: Int,
        orientation: Int
    ) {

        this.width = previewWith
        this.height = previewHeight
        this.orientation = orientation
        pixels = IntArray(previewWith * previewHeight)

        bitmap =
            Bitmap.createBitmap(previewWith, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(
            cropWidth,
            cropHeight,
            Bitmap.Config.ARGB_8888
        )
        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWith,
            previewHeight,
            cropWidth,
            cropHeight,
            orientation,
            true
        )
        cropToFrameTransform = Matrix()
        frameToCropTransform.invert(cropToFrameTransform)
    }

    /**
     * Camera Api 1
     */
    fun setFramePixels(data: ByteArray) {
        // YUV to RGB
        imageConverter = Runnable {
            ImageUtils.convertYUV420SPToARGB8888(data, width, height, pixels)
        }
        imageConverter.run()
        // Bitmap 생성
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Camera Api 2
     */
    fun setFramePixels(
        y: ByteArray,
        u: ByteArray,
        v: ByteArray,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int
    ) {
        // YUV to RGB
        imageConverter = Runnable {
            ImageUtils.convertYUV420ToARGB8888(
                y,
                u,
                v,
                width,
                height,
                yRowStride,
                uvRowStride,
                uvPixelStride,
                pixels
            )
        }
        imageConverter.run()
        // Bitmap 생성
        bitmap.setPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )
    }

    fun getScreenBitmap(): Bitmap = bitmap

    fun getCroppedBitmap(): Bitmap? {
        return try {
            val canvas = Canvas(croppedBitmap)
            canvas.drawBitmap(bitmap, frameToCropTransform, null)
            croppedBitmap
        } catch (ignore: Exception) {
            null
        }
    }
}