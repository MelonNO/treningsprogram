package com.migul.treningsprogram.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/**
 * Auros ghost/secondary CTA — transparent body with the 1dp Aurora border
 * (cyan → white → lavender), the spec's signature secondary action.
 *
 * MaterialButton's stroke is a flat ColorStateList and cannot take a gradient,
 * so we suppress it and draw the gradient rounded-rect stroke ourselves on top.
 * Everything else (ripple, icon, insets, ViewBinding) stays MaterialButton.
 * Companion style: [R.style.Widget_Auros_Button_Ghost] — this class overrides
 * whatever strokeColor that style sets.
 */
class AuroraGhostButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonOutlinedStyle,
) : MaterialButton(context, attrs, defStyleAttr) {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BORDER_WIDTH_DP * resources.displayMetrics.density
    }
    private val borderRect = RectF()

    init {
        // The gradient border replaces the style's flat outline.
        strokeColor = ColorStateList.valueOf(0x00000000)
        strokeWidth = 0
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        borderPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(0xE6CBFFFC.toInt(), 0xE6EDFFFE.toInt(), 0xE6FFFDFA.toInt(), 0xE6FAD1FF.toInt()),
            floatArrayOf(0f, 0.26f, 0.48f, 0.89f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = borderPaint.strokeWidth / 2f
        // MaterialButton reserves insetTop/Bottom inside the view bounds.
        borderRect.set(
            half,
            insetTop + half,
            width - half,
            height - insetBottom - half,
        )
        val radius = CORNER_RADIUS_DP * resources.displayMetrics.density
        canvas.drawRoundRect(borderRect, radius, radius, borderPaint)
    }

    private companion object {
        const val BORDER_WIDTH_DP = 1.2f
        const val CORNER_RADIUS_DP = 10f
    }
}
