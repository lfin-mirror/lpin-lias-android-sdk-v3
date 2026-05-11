package io.lpin.android.sdk.space

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import io.lpin.android.sdk.face.LFaceCameraFragmentListener
import io.lpin.android.sdk.face.core.ui.CameraFragment
import io.lpin.android.sdk.face.extenstions.toBitmap
import io.lpin.android.sdk.face.model.CameraFrameData
import io.lpin.android.sdk.licensing.LiasLicensedFeature
import io.lpin.android.sdk.licensing.LiasLicenseException
import io.lpin.android.sdk.licensing.LiasLicenseGate
import io.lpin.android.sdk.plac.scanner.WifiThrottlingData
import io.lpin.android.sdk.space.databinding.ActivitySpaceAuthenticatorV2Binding
import io.lpin.android.sdk.space.ml.validation.MLDetector
import io.lpin.android.sdk.space.ml.validation.MLImageLabeler
import io.lpin.android.sdk.space.pixel.matching.OpenCVAdaptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

class SpaceAuthenticatorV2 : AppCompatActivity(), LFaceCameraFragmentListener {
    private val toastHandler = Handler {
        when (it.what) {
            TOAST_MESSAGE_MORE_OBJECT -> {
                lastCode = Constants.CLIENT_ERROR_MORE_OBJECT_CODE
                lastMessage = Constants.CLIENT_ERROR_MORE_OBJECT_MESSAGE

                Handler(Looper.getMainLooper()).post { processStop() }
                Handler(Looper.getMainLooper()).post {
                    toast?.cancel()
                    toast =
                        Toast.makeText(this@SpaceAuthenticatorV2, lastMessage, Toast.LENGTH_SHORT)
                    toast?.show()
                }
            }
            // 실내인증을 3회 실패하셨습니다. 재시도 하시겠습니까?
//            TOAST_MESSAGE_RETRY_AUTH -> {
//                Handler(Looper.getMainLooper()).post { processStop() }
//                Handler(Looper.getMainLooper()).post {
//                    dialog?.dismiss()
//                    dialog = null
//                    AlertDialog.Builder(this@SpaceAuthenticatorV2).apply {
//                        setTitle("안내")
//                        setCancelable(false)
//                        setMessage("실내인증을 3회 실패하셨습니다. 재시도 하시겠습니까?")
//                        setNegativeButton("취소") { dialog, _ ->
//                            dialog.dismiss()
//                            runOnUiThread {
//                                listener.takePictureFailure(
//                                        Constants.CLIENT_ERROR_INVALID_SPACE_CODE,
//                                        Constants.CLIENT_ERROR_INVALID_SPACE_MESSAGE)
//                                finish()
//                            }
//                        }
//                        setPositiveButton("확인") { dialog, _ ->
//                            dialog.dismiss()
//                        }
//                    }.show().apply {
//                        dialog = this
//                    }
//                }
//            }
            // 화면에 더 많은 사물이 보이도록 찍어주세요
//            TOAST_MESSAGE_NO_SPACE -> {
//                Handler(Looper.getMainLooper()).post { processStop() }
//                Handler(Looper.getMainLooper()).post {
//                    toast?.cancel()
//                    toast = Toast.makeText(this@SpaceAuthenticatorV2, Constants.CLIENT_ERROR_INVALID_SPACE_MESSAGE, Toast.LENGTH_SHORT)
//                    toast?.show()
//                }
//            }
            else -> return@Handler false
        }
        return@Handler true
    }

    private val opencv by lazy {
        OpenCVAdaptor()
    }

    private lateinit var binding: ActivitySpaceAuthenticatorV2Binding
    private lateinit var cameraFragment: CameraFragment
    private var dialog: AlertDialog? = null
    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val gyroX = event.values[0].toDouble()
            val gyroY = event.values[1].toDouble()
            val gyroZ = event.values[2].toDouble()

            if (isComparison || isCapture)
                return

            isCaptureAvailable =
                gyroX >= -4 && gyroX <= 4
                        && gyroY >= 6
                        && gyroZ >= -2 && gyroZ <= 3
            runOnUiThread {
                // 인증 가능할 때
                if (isCaptureAvailable) {
                    binding.previewBlackOut.visibility = View.GONE
                    binding.previewBlackOutText.visibility = View.GONE
                    binding.captureButton.isEnabled = true
                } else {
                    binding.previewBlackOut.visibility = View.VISIBLE
                    binding.previewBlackOutText.visibility = View.VISIBLE
                    binding.captureButton.isEnabled = false
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

        }
    }

    // Timeout
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutCallback: Runnable? = null

    // 자동 인증
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var backgroundOnce = false
    private fun backgroundRun(runnable: Runnable) {
        if (backgroundOnce)
            return
        this.backgroundHandler?.post {
            backgroundOnce = true
            runnable.run()
            backgroundOnce = false
        }
    }

    private lateinit var listener: SpaceAuthenticatorListener

    // 실패 카운팅
    // https://lfincorp.atlassian.net/browse/NHIS-126
    // private var failureComparisonCount = 0
    private var firstFailure: Boolean = true

    // 캡쳐 가능 여부
    private var isCaptureAvailable = false

    // 켭쳐
    private var isCapture = false

    // 인증 가능 여부
    private var isComparison = false

    // 종료 여부
    private var isDestroy = false

    // 타임아웃 여부
    private var isTimeout = false

    private val mlDetector: MLDetector = MLDetector()

    // MLValidation 처리
    private var mlRegLabelList: List<Pair<String, Float>> = emptyList()

    // MLValidation 처리 프로세스
    private var isMLDetectProcessing = false

    private var curSpaceFeature = SpaceFeature(emptyList(), emptyList(), "")

    // 타 OS인지 확인한다.
    private var isOtherOS = false

    private lateinit var spaceEngine: SpaceEngine

    private lateinit var regSpaceFeature: SpaceFeature
    private lateinit var regSpaceImage: Bitmap
    private var toast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        this.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        this.requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        //Remove title bar
        this.actionBar?.hide()
        this.supportActionBar?.hide()

        // 센서 매니저 초기화
        sensorManager = try {
            getSystemService(Context.SENSOR_SERVICE) as SensorManager
        } catch (ignore: Exception) {
            null
        }?.apply {
            sensor = getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        if (sensorManager == null) {
            toast?.cancel()
            toast = Toast.makeText(this, "센서를 지원하지 않는 단말입니다", Toast.LENGTH_SHORT)
            toast?.show()
        }

        //
        listener = listeners?.pop() ?: return finish()
        if (listeners?.size == 0) {
            listeners = null
        }
        try {
            LiasLicenseGate.requireFeature(applicationContext, LiasLicensedFeature.SPACE)
        } catch (exception: LiasLicenseException) {
            listener.takePictureFailure(Constants.CLIENT_ERROR_SDK_INIT, exception.message ?: Constants.CLIENT_ERROR_SDK_MESSAGE)
            finish()
            return
        }
        binding = DataBindingUtil.setContentView(this, R.layout.activity_space_authenticator_v2)
        binding.lifecycleOwner = this

        // 등록/인증 구분은 preview 의 여부로 판단한다.
        // var preview = intent.getStringExtra(EXTRA_PREVIEW)?.apply { isPreview = true }

        // 등록 이미지를 가져온 이후 제거
        val uuid = intent.getStringExtra(EXTRA_UUID) ?: UUID.randomUUID().toString()
        val preview: String? = getTempSharedPreferences(this)
            .getString(uuid + EXTRA_PREVIEW, null)?.apply { isComparison = true }
        val feature = getTempSharedPreferences(this)
            .getString(uuid + EXTRA_FEATURE, null)

        /** Preview 데이터 초기화 **/
        if (preview != null) {
            try {
                getTempSharedPreferences(this).edit().remove(uuid).apply()
            } catch (ignore: Exception) {
            }
            val regSpaceImageBitmap = preview.toBitmap()?.apply { regSpaceImage = this }
            if (regSpaceImageBitmap == null) {
                listener.takePictureFailure(
                    Constants.CLIENT_ERROR_INVALID_DATA_CODE,
                    Constants.CLIENT_ERROR_INVALID_DATA_MESSAGE
                )
                return finish()
            }
            binding.preview.visibility = View.VISIBLE
            binding.preview.alpha = 0.3f

            // 화면 90도 회전
            // https://lfincorp.atlassian.net/browse/NHIS-62
            // val matrix = Matrix().apply { postRotate(90F) }
            try {
                // 비교 이미지 생성
                // val previewBitmap = Bitmap.createBitmap(previewBitmap, 0, 0, previewBitmap.width, previewBitmap.height, matrix, true)
                // 비교 이미지 표현
                binding.preview.setImageBitmap(regSpaceImage)
                binding.previewGuide.setImageBitmap(regSpaceImage)
                binding.previewGuide.setOnClickListener {
                    previewAlert()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                finish()
                return listener.takePictureFailure(
                    Constants.CLIENT_ERROR_INVALID_DATA_CODE,
                    Constants.CLIENT_ERROR_INVALID_DATA_MESSAGE
                )
            }

            // 캡쳐버튼 활성화
            // binding.captureButton.visibility = View.GONE

            // 타임아웃 실행
            startTimeout()
        } else {
            binding.preview.visibility = View.GONE
            // 캡쳐버튼 비활성화
            // binding.captureButton.visibility = View.VISIBLE
            // 안내 가이드 출력하기
            AlertDialog.Builder(this)
                .setTitle("촬영 가이드")
                .setMessage(
                    "" +
                            "1. 방, 거실, 주방 등의 공간을 대표하는 사물들을 최대한 많이 담아주세요.\n\n" +
                            "2. 하나의 사물만 크게 찍으면 안됩니다.\n\n" +
                            "3. 사진안에 사람, 동물이 있으면 안됩니다.\n\n" +
                            "4. 사진안에 창문이 있으면 인식률이 떨어질 수 있습니다.\n\n" +
                            "5. 지하에서 GPS 수신이 잘되지 않는 경우 등록이 안될 수 있습니다."
                )
                .setCancelable(false)
                .setPositiveButton("확인") { dialog, _ ->
                    dialog.dismiss()
                    // 타임아웃 실행
                    startTimeout()
                }
                .show().apply { dialog = this }
        }

        /** Feature 데이터 초기화 **/
        if (feature != null) {
            try {
                val featureDecoded = featureDecoded(feature)
                if (featureDecoded != null) {
                    regSpaceFeature = featureDecoded
                    // 성공시 CBOR 데이터 이용
                    mlRegLabelList = regSpaceFeature.`object`
                } else {
                    // 실패시 Preview 데이터 이용
                    CoroutineScope(Dispatchers.IO).launch {
                        mlRegLabelList = MLImageLabeler.detect(regSpaceImage)
//                        Log.d(TAG, "MLValidation mlCompareDetectedItems : ${mlRegLabelList.size}")
                    }
                }
                // 다른 OS 인지 확인한다.
                val iphone = "iphone"
                isOtherOS = try {
                    regSpaceFeature.device.toLowerCase(Locale.getDefault()).contains(iphone)
                } catch (ignore: Exception) {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                finish()
                return listener.takePictureFailure(
                    Constants.CLIENT_ERROR_INVALID_DATA_CODE,
                    Constants.CLIENT_ERROR_INVALID_DATA_MESSAGE
                )
            }

//            Log.d(TAG, "spaceFeature(feature) : ${regSpaceFeature.space}")
//            Log.d(TAG, "spaceFeature(objects) : ${regSpaceFeature.`object`}")
        }

        // 캡쳐
        binding.captureButton.setOnClickListener {
            if (this.isCapture)
                return@setOnClickListener

            this.isCapture = true

            if (!isThrottlingCheck()) {
                // 위치 수집/인증 시작
                if (listener.getLocationAuthStatus() == listener.getStatusNone())
                    listener.takePictureStarted()
            }
        }

        for (i in 0 until 3) {
            try {
                this.spaceEngine = SpaceEngine(this)
                this.spaceEngine.init()
                break
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        //
        this.cameraFragment = CameraFragment(this)
        this.cameraFragment.setFacing(CameraFragment.CAMERA_BACK)
        this.cameraFragment.setProgress(0F, 0f)
        this.cameraFragment.setPreviewSize(CameraFragment.RATIO_1_1)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, cameraFragment)
            .commit()

        opencv.initialize(9999.0)
        isThrottlingCheck()
    }

    override fun onBackPressed() {
        dialog?.dismiss()
        dialog = null

        AlertDialog.Builder(this).apply {
            setTitle("경고")
            setMessage("취소하시겠습니까?")
            setCancelable(false)
            setNegativeButton("취소") { view: DialogInterface, which: Int ->
                view.dismiss()
                // 인증 취소하기
                processStop()
            }
            setPositiveButton("확인") { view: DialogInterface, which: Int ->
                view.dismiss()
                // 인증 취소하기
                processStop()
                // 에러 코드 전달.
                listener.takePictureCompareResult(0.0, -1.0F)
                listener.takePictureFailure(
                    Constants.CLIENT_ERROR_CANCEL_SPACE_CODE,
                    Constants.CLIENT_ERROR_CANCEL_SPACE_MESSAGE
                )
                // 취소
                finish()
            }
            show().apply { dialog = this }
        }

        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        backgroundThread = HandlerThread("interface")
            .apply { start() }
            .apply { backgroundHandler = Handler(looper) }
        try {
            sensorManager?.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (ignore: Exception) {
        }
        try {
            this.cameraFragment.setGuideEnable(false)
        } catch (ignore: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
//        if (!isFinishing) {
//            listener.takePictureFailure(Constants.CLIENT_ERROR_CAN, Constants.CLIENT_ERROR_INVALID_DATA_MESSAGE)
//            finish()
//        }
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
        }
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (ignore: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        dialog?.dismiss()
        dialog = null
        toast?.cancel()
        toast = null

        try {
            spaceEngine.destroy()
        } catch (ignore: Exception) {
        }

        if (timeoutCallback != null)
            timeoutHandler.removeCallbacks(timeoutCallback!!)
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (ignore: Exception) {
        }
    }

    private fun processStart() {
        runOnUiThread {
            binding.validationProgress.visibility = View.VISIBLE
            binding.captureButton.isEnabled = false
        }
    }

    /**
     * 타임아웃 작동
     */
    private var lastCode: String = Constants.CLIENT_ERROR_TIMEOUT_CODE
    private var lastMessage: String = Constants.CLIENT_ERROR_TIMEOUT_MESSAGE
    private fun startTimeout() {
        try {
            timeoutCallback = Runnable { timeoutFailure() }
            timeoutHandler.postDelayed(
                timeoutCallback!!,
                intent.getLongExtra(EXTRA_TIMEOUT, DEFAULT_TIMEOUT)
            )
        } catch (ignore: Exception) {
        }
    }

    private fun timeoutFailure() {
        isTimeout = true
        isDestroy = true
        cameraFragment.onPause()
        cameraFragment.onStop()
        listener.takePictureFailure(lastCode, lastMessage)
        if (!isFinishing) {
            finish()
        }
    }

    private fun processStop() {
        // 캡쳐버튼 활성화
        isCapture = false
        // 사물 프로세스 종료
        isMLDetectProcessing = false
        // 사뭍 데이터 초기화
        mlDetector.clear()
        // 공간 이미지 데이터 초기화
        curSpaceFeature.space = emptyList()
        // 사물 데이터 초기화
        curSpaceFeature.`object` = emptyList()
        // 캡쳐버튼 활성화
        runOnUiThread {
            binding.validationProgress.visibility = View.GONE
            binding.captureButton.isEnabled = true
        }
    }

    private fun isThrottlingCheck(): Boolean {
        val throttlingData = WifiThrottlingData.getInstance(applicationContext)
        Log.d(TAG, "throttlingData : $throttlingData")
        val isThrottling = throttlingData.isThrottling()
        if (isThrottling) {
            // 모든것을 정지
            try {
                toast?.cancel()
                dialog?.dismiss()

                if (timeoutCallback != null)
                    timeoutHandler.removeCallbacks(timeoutCallback!!)
            } catch (ignore: Exception) {
            } finally {
                AlertDialog.Builder(this)
                    .setTitle(if (isComparison) "인증을 시도할 수 없습니다" else "등록을 시도할 수 없습니다")
                    .setMessage("안드로이드 정책에 따른 인증 횟수 제한으로 2분 후에 재시도 해 주세요")
                    .setCancelable(false)
                    .setPositiveButton("확인") { dialog, _ ->
                        dialog.dismiss()
                        try {
                            processStop()
                            cameraFragment.onPause()
                            cameraFragment.onStop()
                            finish()
                            listener.takePictureFailure(
                                Constants.CLIENT_ERROR_WIFI_THROTTLING_CODE,
                                Constants.CLIENT_ERROR_WIFI_THROTTLING_MESSAGE
                            )
                        } catch (ignore: Exception) {

                        }
                    }
                    .show().apply { dialog = this }
            }
        }
        return isThrottling
    }

    @SuppressLint("SimpleDateFormat")
    override fun processImage(frameData: CameraFrameData) {
        if (!isCapture)
            return
        if (isTimeout)
            return

        backgroundRun(Runnable {
            if (isDestroy || isMLDetectProcessing || !isCapture)
                return@Runnable
            val curBitmapOrigin = frameData.getScreenBitmap().rotate(90F).let {
                val cropSize = min(it.width, it.height)
                Bitmap.createBitmap(it, 0, 0, cropSize, cropSize)
            }
            val curBitmapResize256 = curBitmapOrigin.resize(BITMAP_ORIGIN_SIZE, BITMAP_ORIGIN_SIZE)
            val curBitmapResize256Base64Encoded = curBitmapResize256.base64Encoding()
            processStart()
            /** 사물인증 **/
            // 캡쳐중 && 사물탐지 실패 && 사물탐지 실패 카운트 > 5
            isMLDetectProcessing = true
            // 인증시에는 현재
            if (!isComparison) {
                if (!mlDetector.add(curBitmapOrigin) && mlDetector.addFailureCount > MAX_DETECTED_COUNT) {
                    toastHandler.sendEmptyMessage(TOAST_MESSAGE_MORE_OBJECT)
                    return@Runnable
                }
            }
            curSpaceFeature.`object` = mlDetector.detections
            isMLDetectProcessing = false
            val mlCompareScore = MLImageLabeler.compareScore(mlRegLabelList, mlDetector.detections)
//            Log.d(TAG, "regSpaceFeature compare score : $mlCompareScore")
            val mlCompareSize = MLImageLabeler.compareSize(mlRegLabelList, mlDetector.detections)
//            Log.d(TAG, "regSpaceFeature compare size : $mlCompareSize")

            /** 공간인증 **/
            val curBitmapResize112 = Bitmap.createScaledBitmap(
                curBitmapResize256,
                BITMAP_FEATURE_SIZE,
                BITMAP_FEATURE_SIZE,
                false
            )
            val curBitmapResize112Feature = curBitmapResize112.getSpaceFeature(spaceEngine)
            curSpaceFeature.device =
                (Build.MANUFACTURER.toString() + " " + Build.MODEL + " " + Build.VERSION.RELEASE + " " + Build.VERSION_CODES::class.java.fields[Build.VERSION.SDK_INT].name)

            // 데이터 추가
            val tmpArrayList: ArrayList<List<Float>> = arrayListOf()
            tmpArrayList.addAll(curSpaceFeature.space)
            tmpArrayList.add(curBitmapResize112Feature.toList())
            curSpaceFeature.space = tmpArrayList
//            Log.d(TAG, "curSpaceFeature space append size : ${curSpaceFeature.space.size}")

            /** 인증 처리 **/
            if (isComparison) {
                // 인증 처리
                // 다른 OS 인지 확인한다.
                // 인증시 다른 OS면 실내유사도 distance < 0.04 인것 6개 이상.
                // 인증시 동일 OS면 실내유사도 distance < 0.03 인것 7개 이상, (기존과 동일)
                val allowDistance = if (isOtherOS) {
                    0.04
                } else {
                    0.03
                }
                var emailMessage = ""
                emailMessage += "\n\n=*=*=*=*=*=*=*=*=*=*=\n\n"
                emailMessage += "등록시 단말 정보 : ${regSpaceFeature.device}\n"
                emailMessage += "인증시 단말 정보 : ${curSpaceFeature.device}\n\n"
                // emailMessage += "index, distance, result(< 0.03)\n"
                emailMessage += "index, distance\n"
                var spaceDistanceSuccessSize = 0
                // 데이터가 10개 이상일 때 10개로 자른다.
                if (regSpaceFeature.space.size > 10) {
                    try {
                        regSpaceFeature.space = regSpaceFeature.space.subList(0, 10)
                    } catch (ignore: Exception) {
                    }
                }
                val spaceDistanceAvg = regSpaceFeature.space.map {
                    featureComparison(
                        it.toFloatArray(),
                        curBitmapResize112Feature
                    )
                }.also {
                    spaceDistanceSuccessSize =
                        it.filter { distance -> distance < allowDistance }.size
                    it.forEachIndexed { index, distance ->
                        // emailMessage += "${index + 1}, ${String.format("%.4f", distance)}, ${distance < allowDistance}\n"
                        emailMessage += "${index + 1}, ${String.format("%.4f", distance)}\n"
                    }
                }.average()

                /** 픽셀매칭 **/
                opencv.startRetriveTargetData()
                opencv.process(regSpaceImage)
                opencv.endRetriveTargetData()
                opencv.startProcessQuery()
                val opencvRes = opencv.process(curBitmapResize256)
                val opencvCon = opencv.confidenceRate
                val opencvRate = if (opencvRes == 0)
                    -1.0F
                else
                    opencvCon.toFloat()
                opencv.endProcessQuery()

                /** 좌우반전을 구분 **/
                // 등록 이미지 절반
                val regBitmapHalfPainting =
                    halfPainting(regSpaceImage.resize(BITMAP_FEATURE_SIZE, BITMAP_FEATURE_SIZE))
                val regBitmapHalfPaintingFeature =
                    regBitmapHalfPainting.getSpaceFeature(spaceEngine)
                val regBitmapHalfPaintingImage = regBitmapHalfPainting.base64Encoding()

                // 현재 이미지 절반 이미지 페인팅
                val curBitmapOriginalHalfPainting = halfPainting(curBitmapResize112)
                val curBitmapOriginalHalfPaintingFeature =
                    curBitmapOriginalHalfPainting.getSpaceFeature(spaceEngine)
                val curBitmapOrgHalfPaintingImage = curBitmapOriginalHalfPainting.base64Encoding()
                val curBitmapOrgHalfPaintingFeatureDistance = featureComparison(
                    regBitmapHalfPaintingFeature,
                    curBitmapOriginalHalfPaintingFeature
                )
                // curBitmapOriginalHalfPainting.recycle()

                val curBitmapReverse = Bitmap.createBitmap(
                    curBitmapResize112,
                    0,
                    0,
                    curBitmapResize112.width,
                    curBitmapResize112.height,
                    Matrix().apply { setScale(-1.0F, 1.0F) },
                    false
                )
                val curBitmapReverseHalfPainting = halfPainting(curBitmapReverse)
                val curBitmapReverseHalfPaintingFeature =
                    curBitmapReverseHalfPainting.getSpaceFeature(spaceEngine)
                val curBitmapRevHalfPaintingImage = curBitmapReverseHalfPainting.base64Encoding()
                val curBitmapRevHalfPaintingFeatureDistance = featureComparison(
                    regBitmapHalfPaintingFeature,
                    curBitmapReverseHalfPaintingFeature
                )
                // curBitmapReverse.recycle()
                // curBitmapReverseHalfPainting.recycle()

                getTempSharedPreferences(applicationContext).edit().apply {
                    this.putString("O", regBitmapHalfPaintingImage)
                    this.putString("A", curBitmapOrgHalfPaintingImage)
                    this.putString("B", curBitmapRevHalfPaintingImage)
                    apply()
                }

                // 사물 인증 유무
//                val isSuccessObjectCompareSize = try {
//                    // 최소 개수가 1개일 때
//                    if (min(regSpaceFeature.`object`.size, curSpaceFeature.`object`.size) == 1) {
//                        (mlCompareSize >= 1)
//                    } else {
//                        (mlCompareSize >= 2)
//                    }
//                } catch (ignore: Exception) {
//                    (mlCompareSize >= 2)
//                }
//                // 공간 인증 유무
//                val isSuccessSpaceCompare = if (isOtherOS) {
//                    ((spaceDistanceSuccessSize >= 6) && isSuccessObjectCompareSize)
//                } else {
//                    ((spaceDistanceSuccessSize >= 7) && isSuccessObjectCompareSize)
//                }


                emailMessage += "\n"
                emailMessage += "공간인증 평균 distance : ${String.format("%.5f", spaceDistanceAvg)}\n"
                emailMessage += "공간인증 성공 개수 : ${spaceDistanceSuccessSize}/10\n\n"
                emailMessage += "등록시 사물 개수 : ${regSpaceFeature.`object`.size}\n"
                emailMessage += "인증시 사물 개수 : ${curSpaceFeature.`object`.size}\n"
                // emailMessage += "사물 인증 결과  (개수 : $mlCompareSize 개 인증) : ${if (isSuccessObjectCompareSize) "성공" else "실패"}\n\n"
                // emailMessage += "공간 인증 score : $spaceScore 점\n"
                // emailMessage += "사물 인증 score : $mlCompareScore 점\n"
                // emailMessage += "공간 인증 결과 : ${if (isSuccessSpaceCompare) "성공" else "실패"}\n"

                var diffMessage = "\n"
                diffMessage += "# 공간이미지 유사도\n"
                diffMessage += "A : ${
                    String.format(
                        "%.5f",
                        curBitmapOrgHalfPaintingFeatureDistance
                    )
                }\n"
                diffMessage += "B : ${
                    String.format(
                        "%.5f",
                        curBitmapRevHalfPaintingFeatureDistance
                    )
                }\n"
                diffMessage += if (curBitmapOrgHalfPaintingFeatureDistance > curBitmapRevHalfPaintingFeatureDistance)
                    "다른 이미지일 가능성 높음"
                else
                    "동일 이미지일 가능성 높음"

                diffMessage += "\n\n픽셀매칭 유사도 : $opencvRate"

                emailMessage += diffMessage


                getTempSharedPreferences(applicationContext).edit().putString("DIFF", diffMessage)
                    .apply()
                getTempSharedPreferences(applicationContext).edit()
                    .putString("REG", regSpaceFeature.device).apply()
                getTempSharedPreferences(applicationContext).edit()
                    .putString("CUR", curSpaceFeature.device).apply()


//                val regSpaceFeatureDevice = if (isOtherOS) "iOS" else "AOS"
//                val curSpaceFeatureDevice = "AOS"

                // https://lfincorp.atlassian.net/browse/NHIS-111
                // (공간인증 차이값 < 0.07 && 사물탐지 유사도 >= 0.5 ) || (공간인증 차이값 < 0.02)
//                Log.d(TAG, "option isOtherOS : $isOtherOS")
//                Log.d(TAG, "option allowDistance : $allowDistance")
//                Log.d(TAG, "result isSuccessObjectCompareSize : $isSuccessObjectCompareSize")
//                Log.d(TAG, "result isSuccessSpaceCompare : $isSuccessSpaceCompare")
//                Log.d(TAG, "result space compare size : $spaceDistanceSuccessSize")
//                Log.d(TAG, "result space compare avg distance : $spaceDistanceAvg")

                val resResult =
                    listener.takePictureCompareResult(spaceDistanceAvg, opencvRate, emailMessage)
                // 실패 시
                if (!resResult && firstFailure) {
                    firstFailure = false
                    Handler(Looper.getMainLooper()).post { processStop() }
                    Handler(Looper.getMainLooper()).post {
                        lastCode = Constants.CLIENT_ERROR_INVALID_SPACE_CODE
                        lastMessage = Constants.CLIENT_ERROR_INVALID_SPACE_MESSAGE

                        toast?.cancel()
                        toast = Toast.makeText(
                            this@SpaceAuthenticatorV2,
                            "등록된 실내 사진과 다릅니다. 다시 시도해 주세요.",
                            Toast.LENGTH_SHORT
                        )
                        toast?.show()
                    }
                    return@Runnable
                }

//                listener.takePictureCompareResult(true)
//                listener.takePictureCompareResult(true, spaceDistanceString)
//
//                var isLocationAuthDone = listener.getLocationAuthStatus() != listener.getStatusWaiting()
//                // 2초 대기 후 인증
//                if (!isLocationAuthDone) {
//                    try {
//                        Thread.sleep(2 * 1000L)
//                    } catch (ignore: Exception) {
//                    }
//                }
//                isLocationAuthDone = listener.getLocationAuthStatus() != listener.getStatusWaiting()
//                Log.d(TAG, "ML Compare Result : $mlCompareScore")
//                // 인증 성공
//                if (!isSuccessSpaceCompare) {
//                    // 인증 실패
//                    failureComparisonCount += 1
//                    if (failureComparisonCount > 3) {
//                        failureComparisonCount = 0
//
//                        // 위치인증 실패/중일때만
//                        // 재시도 여부 묻기
//                        if (!isLocationAuthDone)
//                            toastHandler.sendEmptyMessage(TOAST_MESSAGE_RETRY_AUTH)
//                    } else {
//                        // 위치인증 실패/중일때만
//                        // 메시지 인증 실패
//                        if (!isLocationAuthDone)
//                            toastHandler.sendEmptyMessage(TOAST_MESSAGE_NO_SPACE)
//                    }
//                    // 위치인증 실패/중일때만
//                    if (!isLocationAuthDone)
//                        return@Runnable
//                }
//                // 결과 상관 없음
//                // 위치인증 실패/중일때만
//                if (!isLocationAuthDone) {
//                    listener.takePictureCompareResult(isSuccessSpaceCompare)
//                    listener.takePictureCompareResult(isSuccessSpaceCompare, spaceDistanceString)
//                } else {
//                    listener.takePictureCompareResult(true)
//                    listener.takePictureCompareResult(true, spaceDistanceString)
//                }
            }

            /** 등록 처리 **/
            else {
                if (curSpaceFeature.space.size < 10) {
                    return@Runnable
                }
            }

            /** 결과 처리 **/
            val curSpaceFeatureValue = curSpaceFeature.featureEncoded()
            if (curSpaceFeatureValue == null) {
                toastHandler.sendEmptyMessage(TOAST_MESSAGE_MORE_OBJECT)
                return@Runnable
            }
            Log.d(TAG, "curSpaceFeature : $curSpaceFeature")
            Log.d(TAG, "curSpaceFeatureValue: $curSpaceFeatureValue")
            // 실내인증 이미지 촬영 결과
            listener.takePictureSuccess(
                bitmap = curBitmapResize256,
                spaceImage = curBitmapResize256Base64Encoded,
                spaceFeature = spaceFeatureHASH(curBitmapResize112Feature),
                spaceFeatureValue = curSpaceFeatureValue
            )
            //
            processStop()
            runOnUiThread { binding.validationProgress.visibility = View.VISIBLE }
            runOnUiThread { binding.captureButton.visibility = View.GONE }
            isComparison = false
            isCapture = false
            isDestroy = true
            cameraFragment.onPause()
            cameraFragment.onStop()
            finish()
        })
    }

    /**
     * Preview Alert Dialog 창
     */
    private fun previewAlert() {
        try {
            ImageDialog(regSpaceImage).show(supportFragmentManager, TAG)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 비트맵의 절반을 페인트 칠한다.
     */
    private fun halfPainting(bitmap: Bitmap): Bitmap {
        val canvasBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(canvasBitmap)
        val p = Paint()
        p.style = Paint.Style.FILL_AND_STROKE
        p.isAntiAlias = true
        p.isFilterBitmap = true
        p.isDither = true
        p.color = Color.BLACK
        canvas.drawRect(Rect(0, 0, bitmap.width / 2, bitmap.height), p)
        val result = canvasBitmap.copy(Bitmap.Config.ARGB_8888, false)
        canvasBitmap.recycle()
        return result
    }


    companion object {
        const val BITMAP_FEATURE_SIZE = 112
        private var TAG: String = SpaceAuthenticatorV2::class.java.simpleName
        private var listeners: ArrayDeque<SpaceAuthenticatorListener>? = null
        private const val DEFAULT_TIMEOUT = 10 * 1000L
        private const val MAX_DETECTED_COUNT = 5
        private const val MIN_DETECTED_OBJECT_SIZE = 3
        const val EXTRA_TIMEOUT = "EXTRA_TIMEOUT"
        const val EXTRA_PREVIEW = "EXTRA_PREVIEW"
        const val EXTRA_FEATURE = "EXTRA_FEATURE"


        const val EXTRA_UUID = "EXTRA_UUID"
        const val TOAST_MESSAGE_MORE_OBJECT = 0x01
        const val TOAST_MESSAGE_RETRY_AUTH = 0x02
        const val TOAST_MESSAGE_NO_SPACE = 0x03

        fun getTempSharedPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        // const val BITMAP_SIZE = 112
        const val BITMAP_ORIGIN_SIZE = 256
        fun startActivity(context: Context, intent: Intent, listener: SpaceAuthenticatorListener) {
            if (listeners == null) {
                listeners = ArrayDeque()
            }
            listeners!!.add(listener)
            context.startActivity(intent)
        }
    }

    class Builder(
        private val context: Context
    ) {
        private var listener: SpaceAuthenticatorListener? = null
        private var preview: String? = null
        private var feature: String? = null
        private var timeoutMs: Long = DEFAULT_TIMEOUT
        private var uuid: String = UUID.randomUUID().toString()

        fun setSpaceAuthenticatorUuid(uuid: String) = apply { this.uuid = uuid }
        fun setSpaceAuthenticatorListener(listener: SpaceAuthenticatorListener) =
            apply { this.listener = listener }

        fun setSpaceAuthenticatorPreviewGuide(preview: String) = apply { this.preview = preview }
        fun setSpaceFeatureData(feature: String) = apply { this.feature = feature }
        fun setTimeout(timeoutMs: Long) = apply { this.timeoutMs = timeoutMs }

        fun run() {
            val intent = Intent(context, SpaceAuthenticatorV2::class.java)
            intent.putExtra(EXTRA_TIMEOUT, timeoutMs)
            if (listener == null) {
                throw IllegalArgumentException("You must setSpaceAuthenticatorListener() on SpaceAuthenticator");
            }
            try {
                LiasLicenseGate.requireFeature(context.applicationContext, LiasLicensedFeature.SPACE)
            } catch (exception: LiasLicenseException) {
                listener?.takePictureFailure(Constants.CLIENT_ERROR_SDK_INIT, exception.message ?: Constants.CLIENT_ERROR_SDK_MESSAGE)
                return
            }
            // 사이즈가 큰 이미지를 처리하기 위해서
            try {
                getTempSharedPreferences(context).edit().putString(uuid + EXTRA_PREVIEW, preview)
                    .apply()
                getTempSharedPreferences(context).edit().putString(uuid + EXTRA_FEATURE, feature)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            intent.putExtra(EXTRA_UUID, uuid)
            // intent.putExtra(EXTRA_PREVIEW, preview)
            startActivity(context, intent, listener!!)
        }
    }
}