package app.lawnchair.widgets.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.widget.RemoteViews
import app.lawnchair.R

class NexusMusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> controlSession(context) { controller ->
                val playing = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                if (playing) controller.transportControls.pause() else controller.transportControls.play()
            }
            ACTION_NEXT -> controlSession(context) { it.transportControls.skipToNext() }
            ACTION_PREVIOUS -> controlSession(context) { it.transportControls.skipToPrevious() }
            ACTION_OPEN_ACCESS -> openNotificationAccessSettings(context)
        }
        updateAllWidgets(context)
    }

    private fun controlSession(context: Context, action: (MediaController) -> Unit) {
        val controller = activeSessions(context).firstOrNull()
        if (controller == null) {
            openNotificationAccessSettings(context)
            return
        }
        runCatching { action(controller) }.onFailure {
            Log.w(TAG, "Could not control active media session", it)
        }
    }

    private fun activeSessions(context: Context): List<MediaController> = runCatching {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager.getActiveSessions(
            ComponentName(context, NexusMusicNotificationListenerService::class.java),
        )
    }.getOrElse {
        Log.d(TAG, "Notification access is not enabled yet", it)
        emptyList()
    }

    companion object {
        private const val TAG = "NexusMusicWidget"
        private const val ACTION_PLAY_PAUSE = "app.lawnchair.musicwidget.PLAY_PAUSE"
        private const val ACTION_NEXT = "app.lawnchair.musicwidget.NEXT"
        private const val ACTION_PREVIOUS = "app.lawnchair.musicwidget.PREVIOUS"
        private const val ACTION_OPEN_ACCESS = "app.lawnchair.musicwidget.OPEN_ACCESS"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NexusMusicWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.nexus_music_widget)
            val controller = runCatching { 
                val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                sessionManager.getActiveSessions(
                    ComponentName(context, NexusMusicNotificationListenerService::class.java),
                ).firstOrNull()
            }.getOrNull()

            val metadata = controller?.metadata
            val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: "Nothing playing"
            val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: if (controller == null) "Enable media access to control playback" else controller.packageName

            views.setTextViewText(R.id.nexus_music_title, title)
            views.setTextViewText(R.id.nexus_music_artist, artist)
            val isPlaying = controller?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            views.setTextViewText(R.id.nexus_music_play_pause, if (isPlaying) "Ⅱ" else "▶")
            views.setContentDescription(R.id.nexus_music_play_pause, if (isPlaying) "Pause playback" else "Play playback")

            views.setOnClickPendingIntent(R.id.nexus_music_previous, actionIntent(context, ACTION_PREVIOUS, widgetId))
            views.setOnClickPendingIntent(R.id.nexus_music_play_pause, actionIntent(context, ACTION_PLAY_PAUSE, widgetId))
            views.setOnClickPendingIntent(R.id.nexus_music_next, actionIntent(context, ACTION_NEXT, widgetId))
            views.setOnClickPendingIntent(R.id.nexus_music_access, actionIntent(context, ACTION_OPEN_ACCESS, widgetId))
            manager.updateAppWidget(widgetId, views)
        }

        private fun actionIntent(context: Context, action: String, widgetId: Int): PendingIntent {
            val intent = Intent(context, NexusMusicWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                action.hashCode() + widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openNotificationAccessSettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

/** Required by Android's active-media-session API; the user must explicitly enable access. */
class NexusMusicNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NexusMusicWidgetProvider.updateAllWidgets(this)
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        NexusMusicWidgetProvider.updateAllWidgets(this)
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        NexusMusicWidgetProvider.updateAllWidgets(this)
    }
}
