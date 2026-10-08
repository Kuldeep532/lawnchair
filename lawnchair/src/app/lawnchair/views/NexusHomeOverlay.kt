package app.lawnchair.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NexusHomeOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val clock = TextView(context)
    private val date = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(22, 14, 22, 14)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        isClickable = false
        isFocusable = false
        clipToOutline = true

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(96, 255, 255, 255), Color.argb(40, 255, 255, 255)),
        ).apply {
            cornerRadius = 42f
            setStroke(1, Color.argb(72, 255, 255, 255))
        }

        clock.apply {
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            letterSpacing = 0.035f
            contentDescription = "Current time"
        }
        addView(clock, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        date.apply {
            textSize = 12f
            setTextColor(Color.argb(218, 255, 255, 255))
            gravity = Gravity.CENTER
            contentDescription = "Current date"
        }
        addView(date, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = 2
        })

        val brand = TextView(context).apply {
            text = "NEXUS • YOUR SPACE"
            textSize = 9f
            setTextColor(Color.argb(170, 255, 255, 255))
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            contentDescription = "Nexus launcher"
        }
        addView(brand, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = 6
        })

        refresh()
        postDelayed(object : Runnable {
            override fun run() {
                refresh()
                postDelayed(this, 60_000L)
            }
        }, 60_000L)
    }

    private fun refresh() {
        val now = Date()
        clock.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
    }
}
