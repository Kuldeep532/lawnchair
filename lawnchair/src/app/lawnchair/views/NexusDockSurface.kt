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

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(118, 255, 255, 255), Color.argb(46, 255, 255, 255)),
        ).apply {
            cornerRadius = 46f
            setStroke(1, Color.argb(82, 255, 255, 255))
        }

        alpha = 0.98f
    }
}
