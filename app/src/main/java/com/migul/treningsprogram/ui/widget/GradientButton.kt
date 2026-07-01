package com.migul.treningsprogram.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/**
 * Auros primary CTA — the luminous teal→cyan "current" gradient with dark ink.
 *
 * [MaterialButton] paints its own [com.google.android.material.shape.MaterialShapeDrawable]
 * background and cannot render a gradient declaratively, so we install a gradient
 * [RippleDrawable] ourselves once, at construction. Because this is still a
 * MaterialButton, ViewBinding types and every Material feature (icon, insets,
 * ripple) keep working — callers need no changes, they just use this tag.
 *
 * The gradient is rationed to the single primary action on a screen; secondary
 * actions stay on the solid/ghost/tonal button styles.
 */
class GradientButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr) {

    init {
        val radiusPx = CORNER_RADIUS_DP * resources.displayMetrics.density

        val fill = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFF00827C.toInt(), 0xFF34C6BC.toInt(), 0xFF7FE9E1.toInt()),
        ).apply { cornerRadius = radiusPx }

        val rippleMask = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.WHITE, Color.WHITE),
        ).apply { cornerRadius = radiusPx }

        // Dark, low-alpha ripple reads against the bright gradient.
        background = RippleDrawable(
            ColorStateList.valueOf(0x33011D1C),
            fill,
            rippleMask,
        )
    }

    private companion object {
        const val CORNER_RADIUS_DP = 12f
    }
}
