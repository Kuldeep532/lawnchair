package app.lawnchair.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

class NexusDockSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        foregroundGravity = Gravity.CENTER

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(110, 255, 255, 255), Color.argb(58, 255, 255, 255)),
        ).apply {
            cornerRadius = 44f
            setStroke(1, Color.argb(85, 255, 255, 255))
        }

        val hint = TextView(context).apply {
            text = "Nexus Dock"
            textSize = 12f
            setTextColor(Color.argb(190, 255, 255, 255))
            gravity = Gravity.CENTER
            contentDescription = "Nexus dock"
            isFocusable = true
        }
        addView(
            hint,
            LayoutParams(LayoutParams.MATCH_PARENT, 44).apply {
                gravity = Gravity.CENTER
            },
        )
    }
}
