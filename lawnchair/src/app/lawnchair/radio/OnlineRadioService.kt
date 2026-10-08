package app.lawnchair.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import app.lawnchair.R

class OnlineRadioService : Service() {
    private var player: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val store by lazy { OnlineRadioStore.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    private fun play() {
        val station = store.current() ?: return
        if (player?.isPlaying == true) return
        requestAudioFocus()
        val existing = player
        if (existing != null) {
            existing.start()
            store.setCurrent(station, true)
            startForeground(NOTIFICATION_ID, notification(station, true))
            return
        }

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(station.url)
            setOnPreparedListener {
                it.start()
                store.setCurrent(station, true)
                promoteToForeground(station, true)
            }
            setOnCompletionListener {
                store.setCurrent(station, false)
                updateNotification()
            }
            setOnErrorListener { _, _, _ ->
                store.setCurrent(station, false)
                releasePlayer()
                stopForeground(STOP_FOREGROUND_DETACH)
                true
            }
            prepareAsync()
        }
        promoteToForeground(station, false)
    }

    private fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        store.current()?.let { store.setCurrent(it, false) }
        updateNotification()
    }

    private fun stopPlayback() {
        store.setCurrent(null, false)
        releasePlayer()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        player?.runCatching { reset(); release() }
        player = null
    }

    private fun requestAudioFocus() {
        val listener = AudioManager.OnAudioFocusChangeListener { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
        }
    }

    private fun updateNotification() {
        store.current()?.let { promoteToForeground(it, store.isPlaying()) }
    }

    private fun promoteToForeground(station: RadioStation, playing: Boolean) {
        val notification = notification(station, playing)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(station: RadioStation, playing: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_home)
            .setContentTitle(station.name)
            .setContentText(if (playing) "Online Radio is playing" else "Online Radio is paused")
            .setOngoing(playing)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    100,
                    Intent(this, OnlineRadioActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                servicePendingIntent(if (playing) ACTION_PAUSE else ACTION_PLAY)
            )
            .build()

    private fun servicePendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, OnlineRadioService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Online Radio",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    override fun onDestroy() {
        releasePlayer()
        abandonAudioFocus()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PLAY = "app.lawnchair.radio.PLAY"
        const val ACTION_PAUSE = "app.lawnchair.radio.PAUSE"
        const val ACTION_STOP = "app.lawnchair.radio.STOP"
        private const val CHANNEL_ID = "online_radio"
        private const val NOTIFICATION_ID = 2607

        fun command(context: android.content.Context, action: String) {
            val intent = Intent(context, OnlineRadioService::class.java).setAction(action)
            if (action == ACTION_PLAY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
