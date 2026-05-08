package io.lpin.android.sdk.face.core.ui

import android.Manifest
import android.content.Context
import android.graphics.Point
import android.hardware.camera2.CameraDevice
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.TextureView
import androidx.annotation.RequiresPermission
import io.lpin.android.sdk.face.Constants
import io.lpin.android.sdk.face.LFaceCameraFragmentListener
import io.lpin.android.sdk.face.core.camera.Camera2Helper
import io.lpin.android.sdk.face.core.camera.Camera2Listener
import io.lpin.android.sdk.face.core.camera.CameraErrorListener
import io.lpin.android.sdk.face.model.CameraFrameData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Camera2 : Camera2Listener() {
    private var TAG: String = Camera2::class.java.simpleName

    // Camera2 Helper
    private lateinit var helper: Camera2Helper

    // CameraFragment parent context
    private lateinit var context: Context

    // CameraFragment parent texture view
    private lateinit var preview: TextureView


    private var rotation: Int = -1
    private var cameraId: String = ""
    private var displayOrientation: Int = -1

    // 다음 이미지 처리
    private lateinit var doNextImageHandler: Runnable
    private var frameData: CameraFrameData? = null
    private var yuv: Camera2Helper.YUV? = null

    private lateinit var listener: LFaceCameraFragmentListener

    @RequiresPermission(Manifest.permission.CAMERA)
    fun init(
        context: Context,
        cameraId: String,
        rotation: Int,
        preview: TextureView,
        listener: LFaceCameraFragmentListener
    ) {
        this.preview = preview
        this.context = context
        this.listener = listener
        this.rotation = rotation

        helper = Camera2Helper.Builder()
            .cameraListener(this)
            .specificCameraId(cameraId)
            .context(context)
            .previewOn(preview)
            .previewViewSize(Point(preview.layoutParams.width, preview.layoutParams.height))
            .rotation(rotation)
            .build()
        helper.start()
    }

    fun onPause() {
        helper.stop()
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun onResume() {
        helper.start()
    }

    fun onStop() {
        helper.release()
    }

    override fun onCameraOpened(
        cameraDevice: CameraDevice,
        cameraId: String,
        previewSize: Size,
        displayOrientation: Int,
        isMirror: Boolean
    ) {
        this.cameraId = cameraId
        this.displayOrientation = displayOrientation
//        this.displayOrientation = when (rotation) {
//            Surface.ROTATION_0 -> displayOrientation + 180  // 90
//            Surface.ROTATION_90 -> displayOrientation + 90  // 180
//            Surface.ROTATION_180 -> displayOrientation      // 270
//            Surface.ROTATION_270 -> displayOrientation - 90 // 360
//            else -> displayOrientation
//        }
        Log.d(
            TAG,
            "displayOrientation : $rotation, $displayOrientation, ${this.displayOrientation}"
        )
        Handler(Looper.getMainLooper()).post {
            val layoutParams = preview.layoutParams
            if (displayOrientation % 180 == 0) {
                layoutParams.height = layoutParams.width * previewSize.height / previewSize.width
            } else {
                layoutParams.height = layoutParams.width * previewSize.width / previewSize.height
            }
            preview.layoutParams = layoutParams
        }
    }

    override fun onPreview(image: Image, size: Size) {
        if (frameData == null) {
            frameData = CameraFrameData()
            frameData!!.init(
                size.width,
                size.height,
                Constants.CROP_SIZE,
                Constants.CROP_SIZE,
                displayOrientation
            )
        }

        // 다음 이미지 처리
        doNextImageHandler = Runnable {
            image.close()
            stopProcessingFrame()
        }
        // 이미지 처리중일때 다음 프로세스 실행
        if (isProcessingFrame) {
            doNextImageHandler.run()
            return
        }
        // 이미지 프로세스 실행
        startProcessingFrame()

        CoroutineScope(Dispatchers.Main).launch {
            val planes = image.planes
            if (yuv == null) {
                yuv = Camera2Helper.YUV()
            }
            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                // YUV
                Camera2Helper.getYUVBytes(yuv, planes)
                // 이미지 저장
                frameData?.setFramePixels(
                    yuv!!.y,
                    yuv!!.u,
                    yuv!!.v,
                    planes[0].rowStride,
                    planes[1].rowStride,
                    planes[2].pixelStride
                )
            }
            // 다음 이미지 처리 동작
            doNextImageHandler.run()
            //
            if (frameData != null)
                listener.processImage(frameData!!)
        }
    }

    override fun onCameraClosed() {
        Log.d(TAG, "onCameraClosed")
    }

    override fun onCameraError(e: Exception) {
        e.printStackTrace()
    }
}