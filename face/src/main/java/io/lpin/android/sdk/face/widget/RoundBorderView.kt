package io.lpin.android.sdk.face.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

class RoundBorderView : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val rect = RectF()
    private val startAngle = -90f
    private val maxAngle = 360f
    private var diameter = 0f
    private var angle = 0f

    private var progress = 100f


    override fun onDraw(canvas: Canvas) {
        drawCircle(angle, canvas, paint)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        diameter = Math.min(width, height).toFloat()
        updateRect()
    }

    private fun updateRect() {
        val padding = (paint.strokeWidth / 2.5F)
        rect.set(
            padding,
            padding,
            width.toFloat() - padding, height.toFloat() - padding
        )
    }

    private fun drawCircle(drawAngle: Float, canvas: Canvas, paint: Paint) {
        canvas.drawArc(rect, startAngle, drawAngle, false, paint)
    }

    private fun calculateAngle(max: Float, progress: Float) =
        maxAngle / max * progress

    fun setProgress(max: Float, progress: Float) {
        this.progress = progress
        angle = calculateAngle(max, progress)
        try {
            invalidate()
        } catch (ignore: Exception) {
        }
    }

    fun setProgressShader(shader: IntArray) {
        paint.shader = SweepGradient(
            width.toFloat() / 2, height.toFloat() / 2, shader, null
        )
        invalidate()
    }

    fun getProgress(): Float {
        return progress
    }

    fun setProgressWidth(width: Float) {
        paint.strokeWidth = width
        updateRect()
        invalidate()
    }
}