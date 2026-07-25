package com.migul.treningsprogram.ui.history

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.migul.treningsprogram.domain.ChartAxis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * R3 — dependency-free body-weight-over-time chart for Stats → Progress, modeled directly on
 * [StrengthChartView] (same Auros palette, grid, axis/callout treatment, ChartAxis date labels).
 *
 * Differences that matter for weigh-in data:
 *   • the Y axis zooms to the DATA range with padding and a minimum span (never from 0) so weekly
 *     ±0.5 kg movements are visible without a flat 0–80 kg line, and a near-flat series doesn't
 *     explode into fake drama;
 *   • raw weigh-ins render as a muted thin line + dots, with a SMOOTHED trend line drawn bold on
 *     top (the glow fill hangs from the trend, not the noise).
 */
class BodyWeightChartView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    data class Entry(val dateMs: Long, val value: Float)

    private var raw: List<Entry> = emptyList()
    private var smoothed: List<Entry> = emptyList()

    /** Minimum Y span in kg so a flat series keeps an honest, calm scale. */
    private val minRangeKg = 2f

    private val rawLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#337FE9E1")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FE9E1")
        style = Paint.Style.FILL
    }
    private val trendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FE9E1")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0C2E2C")
        strokeWidth = 1f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7E908E")
        textSize = 28f
    }
    private val calloutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBC7C6")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7E908E")
        textSize = 36f
    }

    // ── QoL item 09: touch-to-read (scrub) — snaps to the nearest RAW weigh-in (the actual
    // logged value, NOT the smoothed trend — user-confirmed in the brief).
    private val scrub = ChartScrub(this) { raw.map { it.dateMs } }
    private val scrubDateFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val scrubLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#997FE9E1")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val scrubRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6F2F1")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val scrubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6F2F1")
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
    }

    /** @param rawData weigh-ins in chronological order; @param trendData smoothed overlay series. */
    fun setData(rawData: List<Entry>, trendData: List<Entry>) {
        raw = rawData
        smoothed = trendData
        invalidate()
    }

    private fun fmt(v: Float): String {
        val num = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)
        return "${num}kg"
    }

    // QoL item 09: same plot geometry as onDraw — ChartScrub needs it to snap touches.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pl = 84f; val pr = 28f
        return scrub.onTouchEvent(event, pl, width - pl - pr) || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (raw.size < 2) {
            emptyPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Not enough data yet", width / 2f, height / 2f, emptyPaint)
            return
        }

        val pl = 84f; val pr = 28f; val pt = 40f; val pb = 64f
        val cw = width - pl - pr
        val ch = height - pt - pb

        // Y range: zoom to the data with 15% padding and a floor of [minRangeKg] — never from 0.
        val dataMin = raw.minOf { it.value }
        val dataMax = raw.maxOf { it.value }
        val pad = ((dataMax - dataMin) * 0.15f).coerceAtLeast(minRangeKg / 2f)
        val minV = dataMin - pad
        val maxV = dataMax + pad
        val rv = maxV - minV
        val minD = raw.minOf { it.dateMs }.toFloat()
        val maxD = raw.maxOf { it.dateMs }.toFloat()
        val rd = if (maxD - minD > 0f) maxD - minD else 1f

        fun x(d: Long) = pl + (d - minD) / rd * cw
        fun y(v: Float) = pt + ch - (v - minV) / rv * ch

        for (i in 0..3) {
            val gy = pt + i * ch / 3f
            canvas.drawLine(pl, gy, pl + cw, gy, gridPaint)
        }

        // Glow fill hangs from the SMOOTHED trend (the signal), not the raw noise.
        val trend = if (smoothed.size >= 2) smoothed else raw
        fillPaint.shader = LinearGradient(
            0f, pt, 0f, pt + ch,
            0x4D7FE9E1, 0x007FE9E1.toInt(),
            Shader.TileMode.CLAMP,
        )
        val fillPath = Path().apply {
            moveTo(x(trend.first().dateMs), pt + ch)
            lineTo(x(trend.first().dateMs), y(trend.first().value))
            for (e in trend.drop(1)) lineTo(x(e.dateMs), y(e.value))
            lineTo(x(trend.last().dateMs), pt + ch)
            close()
        }
        canvas.drawPath(fillPath, fillPaint)

        // Raw weigh-ins: muted line + dots.
        val rawPath = Path().apply {
            moveTo(x(raw.first().dateMs), y(raw.first().value))
            for (e in raw.drop(1)) lineTo(x(e.dateMs), y(e.value))
        }
        canvas.drawPath(rawPath, rawLinePaint)
        for (e in raw) canvas.drawCircle(x(e.dateMs), y(e.value), 6f, dotPaint)

        // Smoothed trend on top.
        val trendPath = Path().apply {
            moveTo(x(trend.first().dateMs), y(trend.first().value))
            for (e in trend.drop(1)) lineTo(x(e.dateMs), y(e.value))
        }
        canvas.drawPath(trendPath, trendPaint)

        // Y-axis labels: padded max top, padded min bottom.
        axisPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(fmt(maxV), 4f, pt + 8f, axisPaint)
        canvas.drawText(fmt(minV), 4f, pt + ch + 4f, axisPaint)

        // Callouts: min / max / latest raw weigh-ins (same selection rules as StrengthChartView).
        val maxEntry = raw.maxByOrNull { it.value }
        val minEntry = raw.minByOrNull { it.value }
        val latest = raw.last()
        val labelled = LinkedHashSet<Entry>()
        if (maxEntry != null) labelled.add(maxEntry)
        if (minEntry != null && dataMin != dataMax) labelled.add(minEntry)
        labelled.add(latest)
        for (e in labelled) {
            val px = x(e.dateMs)
            val py = y(e.value)
            val above = py > pt + ch * 0.6f
            calloutPaint.textAlign = when {
                px < pl + cw * 0.15f -> Paint.Align.LEFT
                px > pl + cw * 0.85f -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val ty = if (above) py - 14f else py + 32f
            canvas.drawText(fmt(e.value), px, ty, calloutPaint)
        }

        // X-axis date labels (start + end, mid when wide enough).
        val labels = ChartAxis.dateLabels(raw.map { it.dateMs })
        val baseY = pt + ch + 40f
        if (labels.size >= 1) {
            axisPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(labels.first(), pl, baseY, axisPaint)
        }
        if (labels.size >= 2) {
            axisPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(labels.last(), pl + cw, baseY, axisPaint)
        }
        if (labels.size >= 3 && cw > 360f) {
            axisPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(labels[1], pl + cw / 2f, baseY, axisPaint)
        }

        // ── QoL item 09: touch-to-read marker — vertical line + ring on the RAW weigh-in dot,
        // with the weigh-in's date · exact weight in the top padding band (right-aligned so the
        // readout never sits under the finger). Drawn last, above trend/callouts.
        val sel = scrub.selectedIndex
        if (sel in raw.indices) {
            val e = raw[sel]
            val px = x(e.dateMs)
            canvas.drawLine(px, pt, px, pt + ch, scrubLinePaint)
            canvas.drawCircle(px, y(e.value), 11f, scrubRingPaint)
            val readout = scrubDateFmt.format(Date(e.dateMs)) + "  ·  " + fmt(e.value)
            scrubTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(readout, pl + cw, 30f, scrubTextPaint)
        }
    }
}
