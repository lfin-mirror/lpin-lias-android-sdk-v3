package io.lpin.android.sdk.face.v1.legacy.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class FocusView : View {
    private lateinit var transparent: Paint
    private lateinit var black: Paint
    private val path = Path()

    constructor(context: Context?) : super(context) {
        initPaints()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initPaints()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initPaints()
    }

    private fun initPaints() {
        transparent = Paint()
        transparent.color = Color.TRANSPARENT
        transparent.strokeWidth = 10f
        black = Paint()
        black.color = Color.TRANSPARENT
        black.strokeWidth = 10f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2.toFloat()
        val y = height / 2.toFloat()
        val radius = y / 1.8f


        path.reset()
        path.addCircle(x, y, radius, Path.Direction.CW)
        path.fillType = Path.FillType.INVERSE_EVEN_ODD

        canvas.drawCircle(x, y, radius, transparent)
        canvas.drawPath(path, black)
        canvas.clipPath(path)
        canvas.drawColor(Color.parseColor("#FFFFFFFF"))
    }
}