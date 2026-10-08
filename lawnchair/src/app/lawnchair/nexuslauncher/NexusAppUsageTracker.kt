package app.lawnchair.nexuslauncher

import android.content.ComponentName
import android.content.Context

object NexusAppUsageTracker {
    private const val PREFS_NAME = "nexus_app_preferences"
    private const val KEY_PREFIX = "launch:"
    private const val KEY_LAST_PREFIX = "last:"

    fun recordLaunch(context: Context, componentName: ComponentName) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = componentName.flattenToString()
        prefs.edit()
            .putLong(KEY_PREFIX + key, prefs.getLong(KEY_PREFIX + key, 0L) + 1L)
            .putLong(KEY_LAST_PREFIX + key, System.currentTimeMillis())
            .apply()
    }
}