package com.migul.treningsprogram.ui.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.migul.treningsprogram.domain.VolumeHeatmap
import java.util.Calendar

/**
 * F5 — weekly per-muscle volume heatmap. Rows = muscle groups (highest volume
 * first), columns = the last N Monday-based weeks, cell brightness = working
 * sets that week on the Auros teal→cyan ramp (empty cells stay reef-dim).
 * Pure rendering: data comes in via [setGrid]; all math lives in [VolumeHeatmap].
 */
class VolumeHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var grid: VolumeHeatmap.Grid? = null

    /**
     * Stage-3 item 11: invoked when a NON-EMPTY cell is tapped, with the cell's muscle row and
     * week-start column. Empty cells are ignored here (no navigation, no crash).
     */
    var onCellTap: ((muscle: String, weekStartMs: Long) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val cellHeight = 22f * density
    private val cellGap = 3f * density
    private val labelWidth = 76f * density
    private val footerHeight = 18f * density
    private val corner = 3f * density

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBC7C6")   // auros_fog
        textSize = 10f * density
    }
    private val weekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7E908E")   // auros_fog_dim
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setGrid(g: VolumeHeatmap.Grid) {
        grid = g
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = grid?.muscles?.size ?: 0
        val h = (rows * (cellHeight + cellGap) + footerHeight).toInt()
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val g = grid ?: return
        if (g.muscles.isEmpty()) return
        val cols = g.weekStarts.size
        val gridWidth = width - labelWidth
        val cellWidth = (gridWidth - cellGap * (cols - 1)) / cols

        g.muscles.forEachIndexed { row, muscle ->
            val top = row * (cellHeight + cellGap)
            // row label, ellipsized by hand to the label gutter
            var label = muscle
            while (labelPaint.measureText(label) > labelWidth - 8f * density && label.length > 3) {
                label = label.dropLast(1)
            }
            canvas.drawText(
                label, 0f,
                top + cellHeight / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint,
            )
            for (col in 0 until cols) {
                val left = labelWidth + col * (cellWidth + cellGap)
                rect.set(left, top, left + cellWidth, top + cellHeight)
                cellPaint.color = colorFor(g.sets[row][col], g.maxSets)
                canvas.drawRoundRect(rect, corner, corner, cellPaint)
            }
        }

        // Footer: week-start day/month under first, middle and last columns.
        val footerY = g.muscles.size * (cellHeight + cellGap) + footerHeight - 4f * density
        for (col in listOf(0, cols / 2, cols - 1).distinct()) {
            val cx = labelWidth + col * (cellWidth + cellGap) + cellWidth / 2f
            canvas.drawText(weekLabel(g.weekStarts[col]), cx, footerY, weekPaint)
        }
    }

    // ── Stage-3 item 11: tap → cell mapping (mirrors the onDraw geometry exactly) ────────────

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (onCellTap == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> return true
            android.view.MotionEvent.ACTION_UP -> {
                val cell = cellAt(event.x, event.y)
                if (cell != null) {
                    val g = grid ?: return true
                    val (row, col) = cell
                    if (g.sets[row][col] > 0) {
                        performClick()
                        onCellTap?.invoke(g.muscles[row], g.weekStarts[col])
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** The (row, col) under point (x, y), or null when outside the drawn cell area. */
    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val g = grid ?: return null
        if (g.muscles.isEmpty() || g.weekStarts.isEmpty()) return null
        val cols = g.weekStarts.size
        val gridWidth = width - labelWidth
        if (gridWidth <= 0f) return null
        val cellWidth = (gridWidth - cellGap * (cols - 1)) / cols

        val row = (y / (cellHeight + cellGap)).toInt()
        if (row < 0 || row >= g.muscles.size) return null
        if (y - row * (cellHeight + cellGap) > cellHeight + cellGap / 2f) return null

        if (x < labelWidth) return null
        val col = ((x - labelWidth) / (cellWidth + cellGap)).toInt()
        if (col < 0 || col >= cols) return null
        return row to col
    }

    private fun weekLabel(ms: Long): String = Calendar.getInstance().run {
        timeInMillis = ms
        "${get(Calendar.DAY_OF_MONTH)}/${get(Calendar.MONTH) + 1}"
    }

    /** 0 sets → reef-dim; then a teal→bright-cyan ramp scaled to the busiest cell. */
    private fun colorFor(sets: Int, max: Int): Int {
        if (sets <= 0 || max <= 0) return Color.parseColor("#0C2E2C") // auros_reef_dim
        val t = sets.toFloat() / max
        val from = floatArrayOf(0x00.toFloat(), 0x82.toFloat(), 0x7C.toFloat())   // #00827C
        val to = floatArrayOf(0xCB.toFloat(), 0xFF.toFloat(), 0xFC.toFloat())     // #CBFFFC
        val r = (from[0] + (to[0] - from[0]) * t).toInt()
        val gg = (from[1] + (to[1] - from[1]) * t).toInt()
        val b = (from[2] + (to[2] - from[2]) * t).toInt()
        return Color.rgb(r, gg, b)
    }
}
