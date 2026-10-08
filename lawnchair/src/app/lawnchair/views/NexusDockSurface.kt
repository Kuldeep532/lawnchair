package app.lawnchair.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

class NexusDockSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        clipToOutline = true
        elevation = 10f

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(154, 255, 255, 255),
                Color.argb(88, 255, 255, 255),
                Color.argb(54, 255, 255, 255),
            ),
        ).apply {
            cornerRadius = 52f
            setStroke(1, Color.argb(104, 255, 255, 255))
        }

        alpha = 0.98f

        // Keep the real launcher Hotseat above this surface, while adding a
        // subtle inner edge so the dock reads as a floating, tactile surface.
        foreground = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(34, 255, 255, 255), Color.TRANSPARENT),
        ).apply {
            cornerRadius = 52f
        }
    }
}
