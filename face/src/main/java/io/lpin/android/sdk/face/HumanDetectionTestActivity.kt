package io.lpin.android.sdk.face

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class HumanDetectionTestActivity : AppCompatActivity() {

    private lateinit var faceDetector: FaceDetector
    private lateinit var imageView: ImageView
    private lateinit var resultLabel: TextView
    private lateinit var detailLabel: TextView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { runDetection(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_human_detection_test)

        faceDetector = FaceDetector.Builder(this).build()

        imageView = findViewById(R.id.ivPreview)
        resultLabel = findViewById(R.id.tvResult)
        detailLabel = findViewById(R.id.tvDetail)

        findViewById<Button>(R.id.btnSelectImage).setOnClickListener {
            pickImage.launch("image/*")
        }
    }

    private fun runDetection(uri: Uri) {
        @Suppress("DEPRECATION")
        val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        imageView.setImageBitmap(bitmap)
        resultLabel.text = "분석 중..."
        detailLabel.text = ""

        val detection = faceDetector.detectHuman(bitmap)

        resultLabel.text = if (detection.isHuman) "✓ 사람 감지됨" else "✗ 사람 없음"
        detailLabel.text = buildString {
            append("감지된 얼굴 수: ${detection.faceCount}")
            append("\n판정 사유: ${detection.reason}")
            if (detection.labels.isNotEmpty()) {
                append("\n라벨: ")
                append(detection.labels.entries.joinToString { (label, confidence) ->
                    "$label=${String.format("%.2f", confidence)}"
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceDetector.finalize()
    }
}
