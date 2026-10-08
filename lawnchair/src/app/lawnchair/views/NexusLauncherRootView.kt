package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.LauncherRootView

class NexusLauncherRootView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LauncherRootView(context, attrs) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
}
