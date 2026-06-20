package com.migul.treningsprogram.ui.history

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class StrengthChartView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    data class Entry(val dateMs: Long, val value: Float)

    private var entries: List<Entry> = emptyList()
    private var yLabel = "kg"

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C67F5")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C67F5")
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A7C67F5")
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A3C")
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8888A8")
        textSize = 30f
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8888A8")
        textSize = 36f
    }

    fun setData(data: List<Entry>, label: String = "kg") {
        entries = data
        yLabel = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.size < 2) {
            emptyPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Not enough data yet", width / 2f, height / 2f, emptyPaint)
            return
        }

        val pl = 80f; val pr = 24f; val pt = 24f; val pb = 48f
        val cw = width - pl - pr
        val ch = height - pt - pb

        val minV = entries.minOf { it.value }
        val maxV = entries.maxOf { it.value }
        val minD = entries.minOf { it.dateMs }.toFloat()
        val maxD = entries.maxOf { it.dateMs }.toFloat()
        val rv = if (maxV - minV > 0f) maxV - minV else 1f
        val rd = if (maxD - minD > 0f) maxD - minD else 1f

        fun x(d: Long) = pl + (d - minD) / rd * cw
        fun y(v: Float) = pt + ch - (v - minV) / rv * ch

        // grid lines
        for (i in 0..3) {
            val gy = pt + i * ch / 3f
            canvas.drawLine(pl, gy, pl + cw, gy, gridPaint)
        }

        // fill under line
        val fillPath = Path().apply {
            moveTo(x(entries.first().dateMs), pt + ch)
            lineTo(x(entries.first().dateMs), y(entries.first().value))
            for (e in entries.drop(1)) lineTo(x(e.dateMs), y(e.value))
            lineTo(x(entries.last().dateMs), pt + ch)
            close()
        }
        canvas.drawPath(fillPath, fillPaint)

        // line
        val linePath = Path().apply {
            moveTo(x(entries.first().dateMs), y(entries.first().value))
            for (e in entries.drop(1)) lineTo(x(e.dateMs), y(e.value))
        }
        canvas.drawPath(linePath, linePaint)

        // dots
        for (e in entries) canvas.drawCircle(x(e.dateMs), y(e.value), 7f, dotPaint)

        // axis labels
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${maxV.toInt()}$yLabel", 4f, pt + 10f, textPaint)
        canvas.drawText("${minV.toInt()}$yLabel", 4f, pt + ch + 4f, textPaint)
    }
}
