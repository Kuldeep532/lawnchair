package app.lawnchair.views

import android.content.Context
import android.graphics.Color
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
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(20)
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        val title = TextView(context).apply {
            text = "Nexus"
            textSize = 20f
            setTextColor(Color.WHITE)
            contentDescription = "Nexus launcher"
            isFocusable = true
        }
        addView(title, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }
}
