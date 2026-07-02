package com.migul.treningsprogram.ui.log

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * R6 — the Auros celebration burst: a brief (~0.9 s) radial spray of teal/cyan/gold/violet
 * sparks behind the XP count-up on the workout-complete surface. Purely decorative — the view is
 * non-interactive (touches pass through), draws nothing when idle, and runs a single
 * ValueAnimator, so it can never block the dialog's buttons or jank the layout.
 *
 * [play] scales with the moment: bigger + more particles for level-ups, PRs and Perfect Weeks.
 */
class AurosBurstView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private data class Spark(
        val angle: Double,
        val speed: Float,       // fraction of max radius this spark travels
        val size: Float,        // px radius at full strength
        val color: Int,
        val drift: Float        // slight upward drift, px at progress 1
    )

    private var sparks: List<Spark> = emptyList()
    private var progress = 1f
    private var animator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Vivid gamification palette (sanctioned): cyan, ice, gold, violet, green.
    private val palette = intArrayOf(
        0xFF7FE9E1.toInt(), 0xFFCBFFFC.toInt(), 0xFFFFD54A.toInt(),
        0xFFC77DFF.toInt(), 0xFF37D67A.toInt()
    )

    /** Fire the burst once. [intensity] 1.0 = ordinary session, up to ~1.5 for the big moments. */
    fun play(intensity: Float = 1f) {
        val rng = java.util.Random()
        val count = (22 * intensity).toInt().coerceIn(12, 40)
        sparks = List(count) {
            Spark(
                angle = rng.nextDouble() * 2 * Math.PI,
                speed = 0.45f + rng.nextFloat() * 0.55f,
                size = (3f + rng.nextFloat() * 5f) * intensity.coerceAtMost(1.3f),
                color = palette[rng.nextInt(palette.size)],
                drift = 10f + rng.nextFloat() * 22f
            )
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sparks.isEmpty() || progress >= 1f) return
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(width, height) * 0.48f
        val fade = (1f - progress)
        for (s in sparks) {
            val r = maxR * s.speed * progress
            val x = cx + (cos(s.angle) * r).toFloat()
            val y = cy + (sin(s.angle) * r).toFloat() - s.drift * progress
            paint.color = s.color
            paint.alpha = (255 * fade).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, s.size * (0.6f + 0.4f * fade), paint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
