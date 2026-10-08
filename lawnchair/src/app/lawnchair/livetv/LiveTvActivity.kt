package app.lawnchair.livetv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class LiveTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { LiveTvScreen(::finish) } }
    }
}

private enum class LiveTvPage { LIST, PLAYER }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LiveTvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(LiveTvPage.LIST) }
    var selected by remember { mutableStateOf<LiveTvChannel?>(null) }
    var channels by remember { mutableStateOf(emptyList<LiveTvChannel>()) }

    LaunchedEffect(Unit) {
        channels = LiveTvPlaylist.load(context)
    }

    fun open(channel: LiveTvChannel) {
        selected = channel
        page = LiveTvPage.PLAYER
        LiveTvController.play(context, channel)
    }

    fun closePlayer() {
        LiveTvController.stop(context)
        selected = null
        page = LiveTvPage.LIST
    }

    BackHandler {
        if (page == LiveTvPage.PLAYER) closePlayer() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (page == LiveTvPage.PLAYER) selected?.name ?: "Live TV" else "Live TV") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (page == LiveTvPage.PLAYER) closePlayer() else onBack()
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (page == LiveTvPage.PLAYER && selected != null) {
            LivePlayerScreen(
                padding = padding,
                channel = selected!!,
                onClose = ::closePlayer,
            )
        } else {
            LiveChannelList(
                padding = padding,
                channels = channels,
                onOpen = ::open,
            )
        }
    }
}

@Composable
private fun LiveChannelList(
    padding: PaddingValues,
    channels: List<LiveTvChannel>,
    onOpen: (LiveTvChannel) -> Unit,
) {
    if (channels.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("No live channels are available.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(channels, key = { it.id + it.streamUrl }) { channel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(channel) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                Column(Modifier.weight(1f)) {
                    Text(channel.name)
                    if (channel.group.isNotBlank()) Text(channel.group)
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LivePlayerScreen(
    padding: PaddingValues,
    channel: LiveTvChannel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var future by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }

    DisposableEffect(channel.streamUrl) {
        val token = SessionToken(context, ComponentName(context, LiveTvPlaybackService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener(
            {
                if (pending.isDone) controller = runCatching { pending.get() }.getOrNull()
            },
            context.mainExecutor,
        )
        onDispose {
            controller?.release()
            future?.let { MediaController.releaseFuture(it) }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { PlayerView(it).apply { useController = true } },
            update = { view ->
                view.player = controller
                view.contentDescription = channel.name
            },
        )
        Text("Live • " + channel.name, Modifier.padding(horizontal = 16.dp))
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text("Close Live TV")
        }
    }
}

private object LiveTvController {
    fun play(context: Context, channel: LiveTvChannel) {
        val intent = Intent(context, LiveTvPlaybackService::class.java)
            .setAction(ACTION_PLAY)
            .putExtra(EXTRA_URL, channel.streamUrl)
            .putExtra(EXTRA_NAME, channel.name)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, LiveTvPlaybackService::class.java))
    }

    const val ACTION_PLAY = "app.lawnchair.livetv.PLAY"
    const val EXTRA_URL = "url"
    const val EXTRA_NAME = "name"
}
