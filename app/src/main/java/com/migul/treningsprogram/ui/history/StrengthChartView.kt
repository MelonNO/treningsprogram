package com.migul.treningsprogram.ui.history

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.migul.treningsprogram.domain.ChartAxis

/**
 * Dependency-free line chart for the Stats area (Recap / Progress / History).
 *
 * UX1 readability pass: the chart now draws
 *   • X-axis date labels (start + end at minimum, plus a mid label when there is room),
 *   • value callouts on the min / max / latest points (so the most useful numbers are readable
 *     without an interactive tooltip),
 *   • a unit suffix shown on every value label.
 *
 * Pure label/format math lives in [ChartAxis] (JVM-unit-tested); this view only renders.
 */
class StrengthChartView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    data class Entry(val dateMs: Long, val value: Float)

    private var entries: List<Entry> = emptyList()
    private var unit = "kg"
    /** True → values are whole counts (e.g. sets/sessions): label as integers without a space. */
    private var integerValues = false
    private var highlightDateMs: Long? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FE9E1")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FE9E1")
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A7FE9E1")
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0C2E2C")
        strokeWidth = 1f
    }
    /** Axis tick labels (y units, x dates) — muted. */
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7E908E")
        textSize = 28f
    }
    /** Point value callouts — brighter so the key numbers stand out. */
    private val calloutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBC7C6")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7E908E")
        textSize = 36f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD54A")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    /**
     * @param data points to plot
     * @param label the unit suffix shown on value labels (e.g. "kg", "sets", "sessions"); empty
     *   for a bare number
     * @param highlightDateMs optional date to ring (nearest point)
     * @param integerValues true when values are whole counts so labels render as integers
     */
    fun setData(
        data: List<Entry>,
        label: String = "kg",
        highlightDateMs: Long? = null,
        integerValues: Boolean = false
    ) {
        entries = data
        unit = label
        this.highlightDateMs = highlightDateMs
        this.integerValues = integerValues
        invalidate()
    }

    private fun fmtValue(v: Float): String {
        val num = if (integerValues || v == v.toInt().toFloat()) v.toInt().toString()
                  else "%.1f".format(v)
        return when {
            unit.isBlank() -> num
            // "70kg" reads tighter than "70 kg" on a cramped axis; "12 sets" needs the space.
            unit.length <= 2 -> "$num$unit"
            else -> "$num $unit"
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.size < 2) {
            emptyPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Not enough data yet", width / 2f, height / 2f, emptyPaint)
            return
        }

        // Padding: extra bottom room for the date row; left room for the y unit labels.
        val pl = 84f; val pr = 28f; val pt = 40f; val pb = 64f
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

        // highlight the session's data point (the one nearest the requested date)
        highlightDateMs?.let { hd ->
            val target = entries.minByOrNull { kotlin.math.abs(it.dateMs - hd) }
            if (target != null) canvas.drawCircle(x(target.dateMs), y(target.value), 13f, highlightPaint)
        }

        // ── Y-axis unit labels (max at top, min at bottom) ───────────────────────────────
        axisPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(fmtValue(maxV), 4f, pt + 8f, axisPaint)
        if (minV != maxV) canvas.drawText(fmtValue(minV), 4f, pt + ch + 4f, axisPaint)

        // ── Value callouts on the most useful points (min / max / latest) ────────────────
        // Skip when the whole series is flat (one label is enough) or points coincide.
        val maxEntry = entries.maxByOrNull { it.value }
        val minEntry = entries.minByOrNull { it.value }
        val latest = entries.last()
        val labelled = LinkedHashSet<Entry>()
        if (maxEntry != null) labelled.add(maxEntry)
        if (minEntry != null && minV != maxV) labelled.add(minEntry)
        labelled.add(latest)
        for (e in labelled) {
            val px = x(e.dateMs)
            val py = y(e.value)
            // Bottom-most point reads better with the label above the dot; others below.
            val above = py > pt + ch * 0.6f
            calloutPaint.textAlign = when {
                px < pl + cw * 0.15f -> Paint.Align.LEFT
                px > pl + cw * 0.85f -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val ty = if (above) py - 14f else py + 32f
            canvas.drawText(fmtValue(e.value), px, ty, calloutPaint)
        }

        // ── X-axis date labels (start + end, plus a mid label when wide enough) ──────────
        val labels = ChartAxis.dateLabels(entries.map { it.dateMs })
        val baseY = pt + ch + 40f
        if (labels.size >= 1) {
            axisPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(labels.first(), pl, baseY, axisPaint)
        }
        if (labels.size >= 2) {
            axisPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(labels.last(), pl + cw, baseY, axisPaint)
        }
        // Mid label only if the chart is wide enough to avoid overlap.
        if (labels.size >= 3 && cw > 360f) {
            axisPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(labels[1], pl + cw / 2f, baseY, axisPaint)
        }
    }
}
