package app.lawnchair.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding

class NexusHomeOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(20, 16, 20, 8)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(92, 255, 255, 255), Color.argb(42, 255, 255, 255)),
        ).apply {
            cornerRadius = 42f
            setStroke(1, Color.argb(70, 255, 255, 255))
        }

        val title = TextView(context).apply {
            text = "NEXUS"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            contentDescription = "Nexus launcher home"
            isFocusable = true
        }
        addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        val subtitle = TextView(context).apply {
            text = "Your space. Your control."
            textSize = 12f
            setTextColor(Color.argb(215, 255, 255, 255))
            gravity = Gravity.CENTER
            contentDescription = "Your space. Your control."
        }
        addView(subtitle, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = 2
        })
    }
}
