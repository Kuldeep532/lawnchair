package app.lawnchair.widgets.custom

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.android.launcher3.R

class NexusAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            updateAll(context)
        }
    }

    companion object {
        const val PREFS = "nexus_widget_studio"
        const val KEY_PACKAGE = "selected_package"
        const val KEY_CLASS = "selected_class"
        const val KEY_LABEL = "selected_label"
        const val KEY_COLOR = "selected_color"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NexusAppWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val packageName = prefs.getString(KEY_PACKAGE, null)
            val className = prefs.getString(KEY_CLASS, null)
            val label = prefs.getString(KEY_LABEL, "Choose an app") ?: "Choose an app"
            val color = prefs.getInt(KEY_COLOR, Color.rgb(49, 93, 168))
            val views = RemoteViews(context.packageName, R.layout.nexus_app_widget)
            views.setTextViewText(R.id.nexus_app_widget_title, label)
            views.setTextViewText(R.id.nexus_app_widget_subtitle, "Tap to open")
            views.setInt(R.id.nexus_app_widget_root, "setBackgroundColor", color)

            val launchIntent = if (!packageName.isNullOrBlank() && !className.isNullOrBlank()) {
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(packageName, className))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                context.packageManager.getLaunchIntentForPackage(context.packageName)
            }
            if (launchIntent != null) {
                views.setOnClickPendingIntent(
                    R.id.nexus_app_widget_root,
                    PendingIntent.getActivity(
                        context,
                        widgetId,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            views.setContentDescription(R.id.nexus_app_widget_root, "$label app widget. Tap to open.")
            manager.updateAppWidget(widgetId, views)
        }
    }
}
