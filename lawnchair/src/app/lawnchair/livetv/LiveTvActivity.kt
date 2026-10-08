package app.lawnchair.livetv

import android.content.Intent
import android.os.Bundle
import androidx.activity.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import androidx.media3.ui.PlayerView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class LiveTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { LiveTvScreen(::finish) } }
    }
}

private enum class LiveTvPage { LIST, PLAYER }

@Composable
private fun LiveTvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(LiveTvPage.LIST) }
    var selected by remember { mutableStateOf<LiveTvChannel?>(null) }
    val channels = remember { mutableStateOf(emptyList<LiveTvChannel>()) }

    LaunchedEffect(Unit) {
        channels.value = LiveTvPlaylist.load(context)
    }

    fun open(channel: LiveTvChannel) {
        selected.value?.let {}
        selected = channel
        page = LiveTvPage.PLAYER
        LiveTvController.play(context, channel)
    }

    fun closePlayer() {
        selected = null
        page = LiveTvPage.LIST
        LiveTvController.stop(context)
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
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LivePlayerSurface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    channel = selected!!,
                )
                Text(
                    "Playing live",
                    Modifier.padding(horizontal = 16.dp),
                )
                IconButton(
                    onClick = ::closePlayer,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Close channel")
                }
            }
        } else {
            if (channels.value.isEmpty()) {
                Text("No live channels are available.", Modifier.padding(20.dp))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                ) {
                    items(channels.value, key = { it.id + it.streamUrl }) { channel ->
                        RowPlaceholder(channel, ::open)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowPlaceholder(channel: LiveTvChannel, onOpen: (LiveTvChannel) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(channel) }
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
        Column(Modifier.weight(1f)) {
            Text(channel.name)
            if (channel.group.isNotBlank()) Text(channel.group)
        }
    }
}

@Composable
private fun LivePlayerSurface(
    modifier: Modifier,
    channel: LiveTvChannel,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(context).apply {
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                val token = SessionToken(context, android.content.ComponentName(context, LiveTvPlaybackService::class.java))
                val controllerFuture = MediaController.Builder(context, token).buildAsync()
                controllerFuture.addListener(
                    { player = controllerFuture.get() },
                    context.mainExecutor,
                )
            }
        },
        update = { view ->
            view.contentDescription = channel.name
        },
    )
}

private object LiveTvController {
    fun play(context: android.content.Context, channel: LiveTvChannel) {
        context.startService(
            Intent(context, LiveTvPlaybackService::class.java).setAction(ACTION_PLAY)
                .putExtra(EXTRA_URL, channel.streamUrl)
                .putExtra(EXTRA_NAME, channel.name),
        )
    }

    fun stop(context: android.content.Context) {
        context.stopService(Intent(context, LiveTvPlaybackService::class.java))
    }

    const val ACTION_PLAY = "app.lawnchair.livetv.PLAY"
    const val EXTRA_URL = "url"
    const val EXTRA_NAME = "name"
}

private var MediaControllerBuilderHack: MediaController?
    get() = null
    set(value) {}
