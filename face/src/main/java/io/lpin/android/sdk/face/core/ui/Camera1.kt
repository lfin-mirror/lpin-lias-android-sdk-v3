package io.lpin.android.sdk.face.core.ui

import android.content.Context
import android.graphics.Point
import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import io.lpin.android.sdk.face.Constants
import io.lpin.android.sdk.face.LFaceCameraFragmentListener
import io.lpin.android.sdk.face.core.camera.Camera1Helper
import io.lpin.android.sdk.face.core.camera.Camera1Listener
import io.lpin.android.sdk.face.core.camera.CameraErrorListener
import io.lpin.android.sdk.face.model.CameraFrameData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Camera1 : Camera1Listener {
    private val TAG: String = Camera1::class.java.simpleName

    private var rotation: Int = -1

    // Camera1 Helper
    private lateinit var helper: Camera1Helper

    // CameraFragment parent context
    private lateinit var context: Context

    // CameraFragment parent texture view
    private lateinit var preview: TextureView

    private var cameraId: Int = -1
    private var displayOrientation: Int = -1

    private var isProcessing = false

    // 다음 이미지 처리
    private lateinit var doNextImageHandler: Runnable
    private lateinit var listener: LFaceCameraFragmentListener
    private lateinit var listenerError: CameraErrorListener

    private var frameData: CameraFrameData? = null

    fun init(
        context: Context,
        cameraId: Int,
        rotation: Int,
        preview: TextureView,
        previewSize: Point = Point(1, 1),
        listener: LFaceCameraFragmentListener,
        listenerError: CameraErrorListener
    ) {
        this.listenerError = listenerError
        this.preview = preview
        this.context = context
        this.listener = listener
        this.rotation = rotation

        helper = Camera1Helper.Builder()
            .cameraListener(this)
            .specificCameraId(cameraId)
            .previewOn(preview)
            // .previewViewSize(Point(preview.layoutParams.width, preview.layoutParams.height))
            // .previewViewSize(Point(9, 16))
            .previewViewSize(previewSize)
            .rotation(rotation)
            .build()
        helper.start()
    }

    fun onPause() {
        helper.stop()
    }

    fun onResume() {
        helper.start()
    }

    fun onStop() {
        helper.release()
    }

    override fun onCameraOpened(
        camera: Camera,
        cameraId: Int,
        displayOrientation: Int,
        isMirror: Boolean
    ) {
        this.cameraId = cameraId
        this.displayOrientation = displayOrientation
        // this.displayOrientation = displayOrientation
//         sensorOrientation = rotation + screenOrientation;
        this.displayOrientation = when (rotation) {
            Surface.ROTATION_0 -> displayOrientation + 180  // 90
            Surface.ROTATION_90 -> displayOrientation + 90  // 180
            Surface.ROTATION_180 -> displayOrientation      // 270
            Surface.ROTATION_270 -> displayOrientation - 90 // 360
            else -> displayOrientation
        }

        Log.d(
            TAG,
            "displayOrientation : $rotation, $displayOrientation, ${this.displayOrientation}"
        )
        Handler(Looper.getMainLooper()).post {
            val layoutParams = preview.layoutParams
            //가로
            if (displayOrientation % 180 == 0) {
                layoutParams.height =
                    layoutParams.width * camera.parameters.previewSize.height / camera.parameters.previewSize.width
            } else {
                layoutParams.height =
                    layoutParams.width * camera.parameters.previewSize.width / camera.parameters.previewSize.height
            }
            preview.layoutParams = layoutParams
        }
    }

    override fun onPreview(data: ByteArray, camera: Camera) {
        // 다음 이미지 처리
        doNextImageHandler = Runnable {
            camera.addCallbackBuffer(data)
            isProcessing = false
        }
        // 현재 데이터 처리중 이라면 Preview 업데이트
        if (isProcessing) {
            doNextImageHandler.run()
            return
        }

        val previewSize = camera.parameters.previewSize
        if (frameData == null) {
            frameData = CameraFrameData()
            frameData?.init(
                previewSize.width,
                previewSize.height,
                previewSize.width,
                previewSize.height,
//                    Constants.CROP_SIZE,
//                    Constants.CROP_SIZE,
                displayOrientation
            )
        }
        // 현재 데이터 프로세싱 준비
        isProcessing = true
        CoroutineScope(Dispatchers.Main).launch {
            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                // 이미지 저장
                frameData?.setFramePixels(data)
                // 다음 이미지 처리 동작
                doNextImageHandler.run()
            }
            // 다음 프로세스 동작
            // listener.processImage(frameData?.getCroppedBitmap()!!)
            if (frameData != null)
                listener.processImage(frameData!!)
        }
    }

    override fun onCameraClosed() {}
    override fun onCameraError(e: Exception) {
        listenerError.onCameraError(e)
    }
    override fun onCameraConfigurationChanged(cameraID: Int, displayOrientation: Int) {}
}