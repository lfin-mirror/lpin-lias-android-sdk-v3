package io.lpin.android.sdk.face.core.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Point
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import io.lpin.android.sdk.face.LFaceCameraFragmentListener
import io.lpin.android.sdk.face.R
import io.lpin.android.sdk.face.core.camera.Camera2Helper
import io.lpin.android.sdk.face.core.camera.CameraErrorListener
import io.lpin.android.sdk.face.extenstions.PermissionCheckFragment
import io.lpin.android.sdk.face.widget.RoundBorderView
import io.lpin.android.sdk.face.widget.RoundTextureView
import kotlin.math.min

@SuppressLint("ValidFragment")
class CameraFragment(
    val listener: LFaceCameraFragmentListener
) : PermissionCheckFragment(), OnGlobalLayoutListener, CameraErrorListener {
    // Camera Preview
    private lateinit var preview: RoundTextureView
    private lateinit var guide: View

    // Camera Preview Rounder
    private var rounder: RoundBorderView? = null
    private var radius: Int = 0

    private var cameraFacingId = CAMERA_BACK

    // Camera1 API
    private var camera1: Camera1? = null

    // Camera2 API
    private var camera2: Camera2? = null

    private var previewSize = Point(1, 1)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        // 카메라 View 생성
        val layout = inflater.inflate(R.layout.camera_content, container, false)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        guide = layout.findViewById(R.id.guide)
        // 카메라 PreView 생성
        preview = layout.findViewById(R.id.texture_preview)
        preview.viewTreeObserver.addOnGlobalLayoutListener(this)
        // 카메라 PreView Outline 생성
        rounder = RoundBorderView(requireActivity())
        (preview.parent as FrameLayout).addView(rounder, preview.layoutParams)
        rounder?.setProgressWidth(15f)
        rounder?.setProgressShader(
            intArrayOf(
                Color.MAGENTA,
                Color.CYAN,
                Color.BLUE,
                Color.CYAN,
                Color.MAGENTA
            )
        )
        rounder?.setProgress(20F, 0F)
        return layout
    }

    private fun init() {
        camera1 = Camera1()
        camera1?.init(
            requireActivity(),
            if (this.cameraFacingId == CAMERA_FRONT)
                CAMERA1_ID_FRONT
            else
                CAMERA1_ID_BACK,
            requireActivity().windowManager.defaultDisplay.rotation,
            preview,
            previewSize,
            listener,
            this
        )
    }

    override fun onPause() {
        camera1?.onPause()
        camera2?.onPause()
        super.onPause()
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    override fun onResume() {
        camera1?.onResume()
        camera2?.onResume()
        super.onResume()
    }

    override fun onStop() {
        camera1?.onStop()
        camera2?.onStop()
        super.onStop()
    }

    /**
     * View 가 다 그려지고 난 후 처리
     */
    override fun onGlobalLayout() {
        preview.viewTreeObserver.removeOnGlobalLayoutListener(this)
        //
        val layoutParams = preview.layoutParams
        // val sideLength = min(preview.width, preview.height) * 3 / 4
        val sideLength = min(preview.width, preview.height)
        layoutParams.width = sideLength
        layoutParams.height = sideLength
        // preview.setMeasure(1, 1)
        // preview.layoutParams = layoutParams
        preview.radius = this.radius
        preview.turnRound()

        rounder?.invalidate()
        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                NEEDED_PERMISSIONS,
                ACTION_REQUEST_PERMISSIONS
            )
        } else {
            init()
        }
    }

    fun getProgress(): Float {
        return rounder?.getProgress() ?: 0F
    }

    fun setProgress(max: Float, progress: Float) {
        try {
            rounder?.setProgress(max, progress)
            rounder?.invalidate()
        } catch (ignore: Exception) {
        }
    }

    fun setFacing(id: Int) {
        this.cameraFacingId = id
    }

    fun setRadius(radius: Int) {
        this.radius = radius
    }

    fun setGuideEnable(isEnable: Boolean) {
        try {
            this.guide.visibility = if (isEnable) View.VISIBLE else View.GONE
        } catch (ignore: Exception) {
        }
    }

    fun setPreviewSize(previewSize: Int) {
        this.previewSize = when (previewSize) {
            RATIO_16_9 -> Point(9, 16)
            RATIO_4_3 -> Point(3, 4)
            else -> Point(1, 1)
        }
    }

    companion object {
        fun isCamera2Available(context: Context) = isLegacyCamera(context).not()

        /**
         * There were more issues than benefits when using Legacy camera with Camera2 API.
         * I found it to be working much better with deprecated Camera1 API instead.
         *
         * @see CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
         */
        private fun isLegacyCamera(context: Context): Boolean {
            return try {
                val cameraManager =
                    context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val characteristics =
                    cameraManager?.cameraIdList?.map { cameraManager.getCameraCharacteristics(it) }
                val characteristic = characteristics?.firstOrNull {
                    it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: characteristics?.get(0)
                val hardwareLevel =
                    characteristic?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
            } catch (t: Throwable) {
                false
            }
        }

        private val TAG = CameraFragment::class.java.simpleName
        private const val ACTION_REQUEST_PERMISSIONS = 1
        private val NEEDED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )

        const val CAMERA_FRONT = 0x01
        const val CAMERA_BACK = 0x02

        const val RATIO_1_1 = 0x11
        const val RATIO_4_3 = 0x43
        const val RATIO_16_9 = 0x169

        // Camera1
        private const val CAMERA1_ID_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT
        private const val CAMERA2_ID_FRONT = Camera2Helper.CAMERA_ID_FRONT
        private const val CAMERA1_ID_BACK = Camera.CameraInfo.CAMERA_FACING_BACK
        private const val CAMERA2_ID_BACK = Camera2Helper.CAMERA_ID_BACK
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    override fun onCameraError(e: Exception) {
        try {
            // Camera1 API 제거
            camera1?.onPause()
            camera1?.onStop()
            // Camera1 API 소용이 없을 때 사용
            camera2 = Camera2()
            camera2?.init(
                requireActivity(),
                if (this.cameraFacingId == CAMERA_FRONT)
                    CAMERA2_ID_FRONT
                else
                    CAMERA2_ID_BACK,
                requireActivity().windowManager.defaultDisplay.rotation,
                preview,
                listener
            )
        } catch (ignore: Exception) {
        }
    }
}