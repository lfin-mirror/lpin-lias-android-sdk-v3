package io.lpin.android.sdk.face

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginTop
import io.lpin.android.sdk.face.core.ui.CameraFragment
import io.lpin.android.sdk.face.liveness.LivenessEyeBlink
import io.lpin.android.sdk.face.model.CameraFrameData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.Comparator
import kotlin.math.min
import kotlin.properties.Delegates

class FaceAuthenticator : AppCompatActivity(), LFaceCameraFragmentListener, LocationListener {
    private var state = STATE_AUTH
    private lateinit var faceRecognizer: FaceRecognizer
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceLiveness: LivenessEyeBlink
    private var dialog: AlertDialog? = null

    // 애니메이션 관리
    private var isPlayAnimation = false


    // 카메라
    private lateinit var cameraFragment: CameraFragment

    // 인증 횟수
    private var recognizeCount = 0

    // 인증 유사도 평균
    private var recognizeConfidenceAvg = 0.0f
    private var recognizeConfidenceSum = 0.0f

    // 등록 시 사용할 feature 목록
    private val features: ArrayList<FloatArray> = arrayListOf()

    // 리스너
    private lateinit var listener: Listener

    // 얼굴 데이터 저장
    private lateinit var faceSharedPreferences: FaceSharedPreferences

    // 얼굴 프로세스 처리 유무
    private var isComputingAvailable = false
    private var isComputing = false

    // Timeout Handler
    private val timeoutHandler = Handler()
    private var timeoutCallback: Runnable? = null

    // 얼굴 데이터 백그라운드 처리
    private var imageProcessHandlerThread: HandlerThread? = null
    private var imageProcessHandler: Handler? = null

    // 얼굴 라이브니스 백그라운드 처리
    private var livenessProcessHandlerThread: HandlerThread? = null
    private var livenessProcessHandler: Handler? = null

    private var containerMarginTop: Int = 0

    // 사용자 지정 데이터
    private lateinit var extraUUID: String
    private var extraType: Int = 0

    // 캡쳐
    private lateinit var captureButton: ImageButton
    private lateinit var captureFailureText: TextView

    // 위치데이터 백그라운드 업데이트
    private var locationManager: LocationManager? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {

        // 호출자 인텐트로부터 요청 메시지를 얻는다.
        val intent = intent
        listener = listeners?.pop() ?: return finish()
        if (listeners?.isEmpty() == true) {
            listeners = null
        }
        // 레이아웃 설정
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        this.supportActionBar?.hide()
        this.actionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)

        try {
            val providers =
                arrayOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            locationManager =
                applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            for (provider in providers) {
                if (locationManager?.isProviderEnabled(provider) == true) {
                    locationManager?.requestLocationUpdates(
                        provider,
                        100L,
                        0F,
                        this
                    )
                }
            }
        } catch (ignore: Exception) {
        }

        this.captureButton = findViewById(R.id.capture_button)
        this.captureButton.visibility = View.INVISIBLE
        this.captureFailureText = findViewById(R.id.capture_failure_text)
        this.captureFailureText.visibility = View.INVISIBLE

        // 카메라 초기화
        cameraFragment = CameraFragment(this)
        cameraFragment.setProgress(DEFAULT_MAX_REGISTER_COUNT.toFloat(), recognizeCount.toFloat())
        cameraFragment.setFacing(CameraFragment.CAMERA_FRONT)
        cameraFragment.setRadius(90)
        cameraFragment.setPreviewSize(CameraFragment.RATIO_1_1)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, cameraFragment)
            .commit()

        this.faceSharedPreferences = FaceSharedPreferences(applicationContext)
        this.faceRecognizer = FaceRecognizer.Builder(applicationContext).build()
        this.faceDetector = FaceDetector.Builder(applicationContext).build()
        this.faceLiveness = LivenessEyeBlink(this).apply { init() }
        // FaceAuthenticator 타입
        this.extraUUID = intent.getStringExtra(EXTRA_UUID) ?: UUID.randomUUID().toString()
        this.extraType = intent.getIntExtra(EXTRA_TYPE, STATE_AUTH)

        this.containerMarginTop = findViewById<FrameLayout>(R.id.container).marginTop

        // 1. 인증 타입
        //    - 유저가 있는지 확인한다.
        //    - 유저가 없으면 Exception 처리
        // 2. 등록 타입
        //    - 유저가 없으면 등록
        //    - 유저가 있으면 재등록
        // 1. 인증 타입
        //    - 유저가 있는지 확인한다.
        //    - 유저가 없으면 Exception 처리
        // 2. 등록 타입
        //    - 유저가 없으면 등록
        //    - 유저가 있으면 재등록
        val hasUser = checkHasUser()

        // 유저가 등록되어있는지 확인
        launch(extraType, hasUser)
    }

    private fun checkHasUser(): Boolean {
        return if (faceSharedPreferences.user != null) {
            faceRecognizer.isValidUser(faceSharedPreferences.user)
        } else {
            false
        }
    }

    private fun launch(extraType: Int, hasUser: Boolean) {
        when (extraType) {
            STATE_AUTH -> {
                if (hasUser) {
                    state = STATE_AUTH
                } else {
                    AlertDialog.Builder(this).apply {
                        setTitle("사용자 등록오류")
                        setMessage("현재 사용자가 등록되어있지 않습니다.")
                        setCancelable(false)
                        setPositiveButton("확인") { _, _: Int ->
                            state = STATE_NONE
                            finish()
                            // 현재 얼굴이 등록되어있지 않음
                            onFailure(
                                Constants.CLIENT_ERROR_NO_FACE_CODE,
                                Constants.CLIENT_ERROR_NO_FACE_MESSAGE
                            )
                        }
                        show().apply { dialog = this }
                    }
                }
            }
            STATE_AUTH_SINGLE -> {
                if (hasUser) {
                    state = STATE_AUTH_SINGLE
                } else {
                    AlertDialog.Builder(this).apply {
                        setTitle("사용자 등록오류")
                        setMessage("현재 사용자가 등록되어있지 않습니다.")
                        setCancelable(false)
                        setPositiveButton("확인") { _, _: Int ->
                            state = STATE_NONE
                            finish()
                            onFailure(
                                Constants.CLIENT_ERROR_NO_FACE_CODE,
                                Constants.CLIENT_ERROR_NO_FACE_MESSAGE
                            )
                        }
                        show().apply { dialog = this }
                    }
                }
            }
            STATE_REGISTER -> {
                state = STATE_REGISTER_READY
                if (hasUser) {
                    AlertDialog.Builder(this).apply {
                        setTitle("등록된 사용자 존재")
                        setMessage("이미 등록된 사용자가 있습니다.")
                        setCancelable(false)
                        setPositiveButton("확인") { _: DialogInterface?, _: Int ->
                            state = STATE_NONE
                            finish()
                            // 현재 얼굴이 등록되어있음
                            onFailure(
                                Constants.CLIENT_ERROR_ALREADY_REGISTERED_FACE_CODE,
                                Constants.CLIENT_ERROR_ALREADY_REGISTERED_FACE_MESSAGE
                            )
                        }
                        show().apply { dialog = this }
                    }
                } else {
                    this.captureButton.visibility = View.VISIBLE
                    this.captureButton.setOnClickListener {
                        try {
                            this.captureButton.visibility = View.INVISIBLE
                            this.state = STATE_REGISTER
                            this.startTimeoutHandler(intent)
                        } catch (ignore: Exception) {
                        }
                    }
                }
            }
            STATE_MODIFY -> {
                state = STATE_REGISTER_READY
                if (hasUser) {
                    AlertDialog.Builder(this).apply {
                        setTitle("등록된 사용자 존재")
                        setMessage("이미 등록된 사용자가 있습니다. 새로 등록하시려면 등록버튼을 눌러주세요.\n승인처리 이후 사용하실 수 있습니다.")
                        setCancelable(false)
                        setNegativeButton("취소") { _: DialogInterface?, _: Int ->
                            state = STATE_NONE
                            finish()
                        }
                        setPositiveButton("등록") { _: DialogInterface?, _: Int ->
                            state = STATE_MODIFY
                            // 타임아웃 시작
                            startTimeoutHandler(intent)
                        }
                        show().apply { dialog = this }
                    }
                } else {
                    this.captureButton.visibility = View.VISIBLE
                    this.captureButton.setOnClickListener {
                        try {
                            this.captureButton.visibility = View.INVISIBLE
                            this.state = STATE_MODIFY
                            this.startTimeoutHandler(intent)
                        } catch (ignore: Exception) {
                        }
                    }
                }
            }
        }
    }


    /**
     * 타임아웃 Handler 시작
     * https://lfincorp.atlassian.net/browse/NHIS-40
     *
     * @param intent Activity Intent
     */
    private fun startTimeoutHandler(intent: Intent) {
        timeoutCallback = Runnable {
            if (timeoutCallback != null)
                timeoutHandler.removeCallbacks(timeoutCallback!!)
            state = STATE_NONE
            val dialog = android.app.AlertDialog.Builder(this)
            dialog.setTitle("시간 초과")
            dialog.setMessage("인증시간이 초과하였습니다.")
            dialog.setCancelable(false)
            dialog.setPositiveButton("확인") { view: DialogInterface?, which: Int ->
                finish()
                // 현재 얼굴이 등록되어있지 않음
                onFailure("4001", "인증시간이 초과하였습니다.")
            }
            try {
                dialog.show()
            } catch (ignore: Exception) {
            }
        }
        if (timeoutCallback != null) {
            timeoutHandler.postDelayed(
                timeoutCallback!!,
                intent.getLongExtra(EXTRA_TIMEOUT, DEFAULT_TIMEOUT)
            )
        }
    }

    /**
     * 인증 실패 시 사용자에게 보여주는 Dialog
     */
    private fun onFailure(code: String, message: String) {
        if (timeoutCallback != null) {
            timeoutHandler.removeCallbacks(timeoutCallback!!)
        }
        try {
            cameraFragment.onPause()
            cameraFragment.onStop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
        Handler(Looper.getMainLooper()).post { listener.onFailure(code, message) }
    }

    /**
     * 인증 성공 시 사용자에게 보여주는 Dialog
     */
    private fun onSuccess(faceData: FaceData) {
        if (timeoutCallback != null) {
            timeoutHandler.removeCallbacks(timeoutCallback!!)
        }
        if(extraType <= STATE_LIVENESS) {
            try {
                cameraFragment.onPause()
                cameraFragment.onStop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        finish()
        Handler(Looper.getMainLooper()).post { listener.onSuccess(faceData) }
    }

    override fun onBackPressed() {
        // 이전 상태 복귀
        val stateBackup = state
        // 현재 상태 정지
        state = STATE_NONE
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("경고")
        dialog.setMessage("취소하시겠습니까?")
        dialog.setCancelable(false)
        dialog.setNegativeButton("취소") { view: DialogInterface, which: Int ->
            view.dismiss()
            // 이전 상태로 다시 돌려놓기
            state = stateBackup
        }
        dialog.setPositiveButton("확인") { view: DialogInterface, which: Int ->
            view.dismiss()
            // 에러 코드 전달.
            listener.onFailure(
                Constants.CLIENT_ERROR_CANCEL_FACE_CODE,
                Constants.CLIENT_ERROR_CANCEL_FACE_MESSAGE
            )
            super.onBackPressed()
            // 취소
            finish()
        }
        dialog.show().apply {
            this@FaceAuthenticator.dialog = this
        }
    }

    override fun onResume() {
        super.onResume()

        imageProcessHandlerThread = HandlerThread("inference")
        imageProcessHandlerThread!!.start()
        if (imageProcessHandlerThread != null)
            imageProcessHandler = Handler(imageProcessHandlerThread!!.looper)
        livenessProcessHandlerThread = HandlerThread("liveness")
        livenessProcessHandlerThread?.start()
        if (livenessProcessHandlerThread != null)
            livenessProcessHandler = Handler(livenessProcessHandlerThread!!.looper)

    }

    override fun onPause() {
        if (!isFinishing) {
            finish()
        }
        imageProcessHandlerThread?.quitSafely()
        try {
            imageProcessHandlerThread?.join()
            imageProcessHandlerThread = null
            imageProcessHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        livenessProcessHandlerThread?.quitSafely()
        try {
            livenessProcessHandlerThread?.join()
            livenessProcessHandlerThread = null
            livenessProcessHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        super.onPause()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    public override fun onDestroy() {
        state = STATE_NONE
        dialog?.dismiss()
        dialog = null

        try {
            locationManager?.removeUpdates(this)
        } catch (ignore: Exception) {
        }

        super.onDestroy()
    }

    private var isFaceLivenessProcess = false
    private var isDetectedFaceProcess = false

    private fun Bitmap.rotate(value: Float): Bitmap =
        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { setRotate(value) }, false)

    private fun Bitmap.resize(w: Int, h: Int): Bitmap = Bitmap.createScaledBitmap(this, w, h, false)
    private var cropBitmap: Bitmap? = null
    private var thumbnailBitmap: Bitmap? = null

    private fun isAuthState(): Boolean = state == STATE_AUTH || state == STATE_AUTH_SINGLE

    private fun Face.toThumbnailBitmap(): Bitmap {
        val size = Face.faceSize
        val bytes = getByteArray()
        val pixels = IntArray(size * size) { i ->
            val r = bytes[i * 3].toInt() and 0xFF
            val g = bytes[i * 3 + 1].toInt() and 0xFF
            val b = bytes[i * 3 + 2].toInt() and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    override fun processImage(frameData: CameraFrameData) {
        // val bitmap = frameData.getCroppedBitmap()
        Log.d(TAG, "orientation : ${frameData.orientation}")

        if (cropBitmap != null)
            if (cropBitmap?.isRecycled != false)
                cropBitmap?.recycle()

        cropBitmap = frameData.getScreenBitmap().rotate(frameData.orientation.toFloat()).let {
            val cropSize = min(it.width, it.height)
            Bitmap.createBitmap(it, 0, 0, cropSize, cropSize)
        }

        // val bitmap = frameData.getScreenBitmap()
        // Liveness
        if (isAuthState()) {
            if (!isFaceLivenessProcess) {
                isFaceLivenessProcess = true
                if (cropBitmap != null) {
                    try {
                        faceLiveness.detecting(frameData.getCroppedBitmap()!!) {
                            isFaceLivenessProcess = false
                        }
                    } catch (ignore: Exception) {
                    }
                }
            }
        }

        // 안면등록 시 얼굴이 있으면 버튼 활성화
        if (state == STATE_REGISTER_READY) {
            if (!isDetectedFaceProcess) {
                isDetectedFaceProcess = true
                CoroutineScope(Dispatchers.IO).launch {
                    val detections = faceDetector.detect(cropBitmap)
                    val isButtonEnable = detections.isNotEmpty()
                    runOnUiThread {
                        captureButton.isEnabled = isButtonEnable
                        captureFailureText.visibility =
                            if (isButtonEnable) View.INVISIBLE else View.VISIBLE
                    }
                    isDetectedFaceProcess = false
                }
            }
            return
        }

        // 안면인식 시작 지점
        if (state == STATE_NONE || state == STATE_REGISTER_READY)
            return

        if (isComputing)
            return

        isComputing = true

        imageProcessHandler?.post {
            // 얼굴 Detect
            val detections = faceDetector.detect(cropBitmap)

            // 현재 등록이 재등록일 때 이전 유저 데이터의 Similarity 값을 저장하고 있는다.
            var isCurrentUser = false
            var recognizeConfidence = 0.0f
            val recognitions: List<Pair<String, Float>> =
                faceRecognizer.recognize(detections) ?: emptyList()
            val currentUser: String = faceSharedPreferences.user ?: ""
            // 데이터 검사
            if (recognitions.isNotEmpty() && currentUser != "") {
                for (i in recognitions.indices) {
                    val user = recognitions[i]
                    // 유저가 일치했을 때
                    if (currentUser == user.first) {
                        isCurrentUser = true
                        // confidence
                        recognizeConfidence = user.second
                    }
                }
            }

            // 얼굴 데이터 존재 시
            if (detections.isNotEmpty()) {
                // 얼굴 위치를 확인
                // for (detection in detections) {
                // isComputingAvailable = detection.boundingBox.top in (100.0)..(500.0)
                // }
                isComputingAvailable = true
                // 얼굴이 가운데쪽에 있을 때 인증하도록 수정
                if (isComputingAvailable) {
                    when (state) {
                        STATE_AUTH -> {
                            if (isCurrentUser) {
                                if (thumbnailBitmap == null) {
                                    thumbnailBitmap = detections[0].toThumbnailBitmap()
                                }
                                recognizeConfidenceSum += recognizeConfidence
                                recognizeCount += 1
                                cameraFragment.setProgress(
                                    max = RECOGNIZE_MAX_COUNT.toFloat(),
                                    progress = recognizeCount.toFloat()
                                )
                                if (recognizeCount > RECOGNIZE_MAX_COUNT) {
                                    recognizeConfidenceAvg = recognizeConfidenceSum / recognizeCount
                                    Log.d(TAG, "recognize confidence avg : $recognizeConfidenceAvg")
                                    // 인증 유사도 평균을 구하여 threshold 이상일 때
                                    if (recognizeConfidenceAvg >= FACE_THRESHOLD) {
                                        if (faceLiveness.isLive()) {
                                            onSuccess(
                                                FaceData(
                                                    currentUser,
                                                    faceRecognizer.userFeatureHash(currentUser),
                                                    recognizeConfidenceAvg,
                                                    thumbnailBitmap
                                                )
                                            )
                                            state = STATE_NONE
                                        } else {
                                            // Liveness 처리
                                            runOnUiThread {
                                                if (!isPlayAnimation) {
                                                    isPlayAnimation = true
                                                    val anim: Animation = AlphaAnimation(0.3f, 1.0f)
                                                    anim.duration = 450
                                                    anim.startOffset = 20
                                                    anim.repeatMode = Animation.REVERSE
                                                    anim.repeatCount = Animation.INFINITE
                                                    this.captureFailureText.startAnimation(anim)
                                                }
                                                this.captureFailureText.text = "눈을 천천히 깜빡여 주세요"
                                                this.captureFailureText.visibility = View.VISIBLE
                                            }
                                        }
                                    } else {
                                        onFailure(
                                            Constants.CLIENT_ERROR_INVALID_FACE_CODE,
                                            Constants.CLIENT_ERROR_INVALID_FACE_MESSAGE
                                        )
                                        state = STATE_NONE
                                    }
                                }
                            }
                        }
                        STATE_AUTH_SINGLE -> {
                            val similarity = if (isCurrentUser) recognizeConfidence else null
                            val decision = SingleReferenceAuthDecider.decide(
                                hasRecognitions = recognitions.isNotEmpty(),
                                isCurrentUser = isCurrentUser,
                                similarity = similarity,
                                threshold = FACE_THRESHOLD.toFloat(),
                                isLive = faceLiveness.isLive()
                            )

                            if (isCurrentUser && thumbnailBitmap == null) {
                                thumbnailBitmap = detections[0].toThumbnailBitmap()
                            }

                            when (decision) {
                                SingleReferenceAuthDecider.Decision.CONTINUE -> {
                                }

                                SingleReferenceAuthDecider.Decision.FAIL -> {
                                    onFailure(
                                        Constants.CLIENT_ERROR_INVALID_FACE_CODE,
                                        Constants.CLIENT_ERROR_INVALID_FACE_MESSAGE
                                    )
                                    state = STATE_NONE
                                }

                                SingleReferenceAuthDecider.Decision.REQUIRE_LIVENESS -> {
                                    runOnUiThread {
                                        if (!isPlayAnimation) {
                                            isPlayAnimation = true
                                            val anim: Animation = AlphaAnimation(0.3f, 1.0f)
                                            anim.duration = 450
                                            anim.startOffset = 20
                                            anim.repeatMode = Animation.REVERSE
                                            anim.repeatCount = Animation.INFINITE
                                            this.captureFailureText.startAnimation(anim)
                                        }
                                        this.captureFailureText.text = "눈을 천천히 깜빡여 주세요"
                                        this.captureFailureText.visibility = View.VISIBLE
                                    }
                                }

                                SingleReferenceAuthDecider.Decision.SUCCESS -> {
                                    cameraFragment.setProgress(max = 1.0f, progress = 1.0f)
                                    onSuccess(
                                        FaceData(
                                            currentUser,
                                            faceRecognizer.userFeatureHash(currentUser),
                                            similarity ?: 0.0f,
                                            thumbnailBitmap
                                        )
                                    )
                                    state = STATE_NONE
                                }
                            }
                        }
                        STATE_MODIFY, STATE_REGISTER -> {
                            // 정렬
                            detections.sortWith(Comparator { f1: Face, f2: Face ->
                                val a1 = f1.boundingBox.width() * f1.boundingBox.height()
                                val a2 = f2.boundingBox.width() * f2.boundingBox.height()
                                when {
                                    a1 < a2 -> {
                                        1
                                    }
                                    a1 > a2 -> {
                                        -1
                                    }
                                    else -> {
                                        0
                                    }
                                }
                            })
                            val detection = detections[0]
                            // features 에 추가
                            features.add(faceRecognizer.getFeature(detection))
                            // 중간 프레임에서 썸네일 캡처
                            if (thumbnailBitmap == null && features.size >= DEFAULT_MAX_REGISTER_COUNT / 2) {
                                thumbnailBitmap = detection.toThumbnailBitmap()
                            }
                            // UI 변경
                            cameraFragment.setProgress(
                                max = DEFAULT_MAX_REGISTER_COUNT.toFloat(),
                                progress = features.size.toFloat()
                            )

                            if (features.size > DEFAULT_MAX_REGISTER_COUNT) {
                                faceRecognizer.insert(extraUUID, features)

                                // 상태 변경
                                if (state == STATE_MODIFY) {
                                    // 변경된 유저 ID 등록
                                    // faceSharedPreferences.userChange = extraUUID
                                    faceSharedPreferences.addModifyUser(extraUUID)
                                    onSuccess(
                                        FaceData(
                                            extraUUID,
                                            faceRecognizer.userFeatureHash(extraUUID),
                                            recognizeConfidence,
                                            thumbnailBitmap
                                        )
                                    )
                                } else if (state == STATE_REGISTER) {
                                    // 변경된 유저 ID 등록
                                    faceSharedPreferences.user = extraUUID
                                    onSuccess(
                                        FaceData(
                                            extraUUID,
                                            faceRecognizer.userFeatureHash(extraUUID),
                                            0.0F,
                                            thumbnailBitmap
                                        )
                                    )
                                }
                                state = STATE_NONE
                            }
                        }
                        else -> {
                        }
                    }
                }
            }
            isComputing = false
        }
    }

    interface Listener {
        fun onSuccess(faceData: FaceData?)
        fun onFailure(code: String?, message: String?)
    }

    /**
     * Builder class
     */
    class Builder(private val context: Context) {
        private var type: FaceAuthenticatorType? = FaceAuthenticatorType.AUTH
        private var listener: Listener? = null
        private var uuid = UUID.randomUUID().toString()
        private var timeoutMs = DEFAULT_TIMEOUT

        /**
         * 얼굴 인증이 되는 유사도를 지정한다.
         *
         * @param threshold 유사도 값 0.0 ~ 1.0 ( default : 0.85 )
         */
        // fun setThreshold(threshold: Double): Builder = apply { this.threshold = threshold }
        // fun setThreshold(threshold: String): Builder = apply { this.threshold = threshold.toDouble() }

        /**
         * 인증기 타입
         *
         * @param type
         * @return Builder
         * @see FaceAuthenticatorType
         */
        fun setType(type: FaceAuthenticatorType?): Builder {
            this.type = type
            return this
        }

        /**
         * 사용자 ID 설정
         *
         * @param uuid
         * @return
         */
        fun setUuid(uuid: String): Builder {
            this.uuid = uuid
            return this
        }

        /**
         * 리스너 설정
         *
         * @param callback
         * @return Builder
         * @see FaceAuthenticator.Listener
         */
        fun setListener(callback: Listener?): Builder {
            listener = callback
            return this
        }

        fun setTimeout(timeoutMs: Long): Builder {
            this.timeoutMs = timeoutMs
            return this
        }

        fun run() {
            requireNotNull(type) { "You must setType() on FaceAuthenticator.Builder" }
            requireNotNull(listener) { "You must setListener() on FaceAuthenticator.Builder" }
            // 라이센스 목록 받기
            val allowLicense =
                BuildConfig.ALLOW_LICENSE_PACKAGES.any { `package` -> `package` == context.applicationContext.packageName }
            if (!allowLicense) {
                // 라이센스가 없으면 라이브러리를 사용하지 못하도록 처리
                listener?.onFailure(
                    Constants.CLIENT_ERROR_SDK_INIT,
                    Constants.CLIENT_ERROR_SDK_MESSAGE
                )
                return
            }
            // dash 및 공백 제거
            uuid = uuid.replace("-", "").replace(" ", "")
            var extraType by Delegates.notNull<Int>()
            when (type) {
                FaceAuthenticatorType.AUTH -> {
                    extraType = STATE_AUTH
                }
                FaceAuthenticatorType.AUTH_SINGLE -> {
                    extraType = STATE_AUTH_SINGLE
                }
                FaceAuthenticatorType.REGISTER -> {
                    extraType = STATE_REGISTER
                }
                FaceAuthenticatorType.MODIFY -> {
                    extraType = STATE_MODIFY
                }
                FaceAuthenticatorType.FRR -> {
                    extraType = STATE_FFR
                }
                FaceAuthenticatorType.FAR -> {
                    extraType = STATE_FAR
                }
                else -> {}
            }

            // 기존 인증, 등록, 수정 모드시에는 activity 실행
            if (extraType == STATE_AUTH ||
                extraType == STATE_AUTH_SINGLE ||
                extraType == STATE_REGISTER ||
                extraType == STATE_MODIFY
            ) {
                val intent = Intent(context, FaceAuthenticator::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra(EXTRA_UUID, uuid)
                intent.putExtra(EXTRA_TYPE, extraType)
                intent.putExtra(EXTRA_TIMEOUT, timeoutMs)
                startActivity(context, intent, listener!!)
            }
        }
    }

    companion object {
        private val TAG = FaceAuthenticator::class.java.simpleName
        private const val EXTRA_TYPE = "EXTRA_TYPE"
        private const val EXTRA_TIMEOUT = "EXTRA_TIMEOUT"
        private const val EXTRA_UUID = "EXTRA_UUID"
        private const val STATE_NONE = 0x00
        private const val STATE_AUTH = 0x01
        private const val STATE_AUTH_SINGLE = 0x02
        private const val STATE_REGISTER_READY = 0x03
        private const val STATE_REGISTER = 0x04
        private const val STATE_MODIFY = 0x05
        private const val STATE_LIVENESS = 0x06
        private const val STATE_FFR = 0x07
        private const val STATE_FFR_BATCH = 0x08
        private const val STATE_FAR = 0x09
        private const val STATE_FAR_BATCH = 0x0A
        private const val CROP_SIZE = 224
        private const val MAINTAIN_ASPECT = true
        private const val TEXT_SIZE_DIP = 10f
        private const val DEFAULT_MAX_REGISTER_COUNT = 20
        private const val RECOGNIZE_MAX_COUNT = 5
        private var listeners: ArrayDeque<Listener>? = null
        var FACE_THRESHOLD: Double = 0.81
            private set

        fun setThreshold(threshold: Double) {
            FACE_THRESHOLD = threshold
        }

        fun setThreshold(threshold: String) {
            threshold.toDoubleOrNull()?.apply { FACE_THRESHOLD = this }
        }

        // Timeout
        private const val DEFAULT_TIMEOUT = 10 * 1000L
        fun startActivity(context: Context, intent: Intent?, listener: Listener) {
            if (listeners == null) {
                listeners = ArrayDeque()
            }
            listeners!!.push(listener)
            context.startActivity(intent)
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "위치데이터 업데이트")
        Log.d(TAG, location.toString())
    }

    override fun onProviderEnabled(provider: String) {
    }

    override fun onProviderDisabled(provider: String) {
    }
}
