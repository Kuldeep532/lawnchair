package app.lawnchair.widgets.custom

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle

/** Stores the current Widget Studio selection separately for each newly pinned widget. */
class NexusAppWidgetConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val prefs = getSharedPreferences(NexusAppWidgetProvider.PREFS, MODE_PRIVATE)
        val packageName = prefs.getString(NexusAppWidgetProvider.KEY_PACKAGE, null)
        val className = prefs.getString(NexusAppWidgetProvider.KEY_CLASS, null)
        val label = prefs.getString(NexusAppWidgetProvider.KEY_LABEL, null)
        if (packageName.isNullOrBlank() || className.isNullOrBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        prefs.edit()
            .putString("${NexusAppWidgetProvider.KEY_PACKAGE}_$widgetId", packageName)
            .putString("${NexusAppWidgetProvider.KEY_CLASS}_$widgetId", className)
            .putString("${NexusAppWidgetProvider.KEY_LABEL}_$widgetId", label)
            .putInt("${NexusAppWidgetProvider.KEY_COLOR}_$widgetId", prefs.getInt(NexusAppWidgetProvider.KEY_COLOR, 0xFF315DA8.toInt()))
            .apply()

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        NexusAppWidgetProvider.updateAll(this)
        finish()
    }
}
