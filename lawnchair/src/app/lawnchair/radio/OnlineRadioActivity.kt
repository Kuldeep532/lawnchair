package app.lawnchair.radio

import android.os.Bundle
import androidx.activity.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

private enum class RadioPage {
    HOME,
    DISCOVERY,
}

private enum class DiscoveryTab {
    STATIONS,
    RECENT,
}

class OnlineRadioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { OnlineRadioScreen(::finish) } }
    }
}

@Composable
private fun OnlineRadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { OnlineRadioStore.getInstance(context) }
    var page by remember { mutableStateOf(RadioPage.HOME) }
    var discoveryTab by remember { mutableStateOf(DiscoveryTab.STATIONS) }
    var current by remember { mutableStateOf(store.current()) }
    var playing by remember { mutableStateOf(store.isPlaying()) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var recentlyPlayed by remember { mutableStateOf(store.recentlyPlayed()) }
    var query by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refreshLocalState() {
        current = store.current()
        playing = store.isPlaying()
        favorites = store.favorites()
        recentlyPlayed = store.recentlyPlayed()
    }

    fun play(station: RadioStation) {
        store.setCurrent(station, true)
        store.addRecentlyPlayed(station)
        refreshLocalState()
        OnlineRadioService.command(context, OnlineRadioService.ACTION_PLAY)
    }

    fun pause() {
        current?.let { store.setCurrent(it, false) }
        refreshLocalState()
        OnlineRadioService.command(context, OnlineRadioService.ACTION_PAUSE)
    }

    fun toggleFavorite(station: RadioStation) {
        store.setFavorite(station, !store.isFavorite(station.id))
        refreshLocalState()
    }

    fun back() {
        when (page) {
            RadioPage.DISCOVERY -> {
                page = RadioPage.HOME
                query = ""
                message = null
            }
            RadioPage.HOME -> onBack()
        }
    }

    suspend fun loadStations(search: String) {
        withContext(Dispatchers.IO) {
            val endpoint = buildString {
                append(
                    "https://de1.api.radio-browser.info/json/stations/search" +
                        "?limit=100&hidebroken=true&order=name&reverse=false"
                )
                if (search.isNotBlank()) {
                    append("&name=")
                    append(URLEncoder.encode(search, "UTF-8"))
                }
            }
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            try {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(text)
                stations = buildList {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val url = o.optString("url_resolved").ifBlank { o.optString("url") }
                        val name = o.optString("name").trim()
                        if (name.isNotBlank() && url.isNotBlank()) {
                            add(
                                RadioStation(
                                    id = o.optString("stationuuid").ifBlank { url },
                                    name = name,
                                    url = url,
                                    country = o.optString("country"),
                                    language = o.optString("language"),
                                    tags = o.optString("tags"),
                                )
                            )
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    LaunchedEffect(page, query) {
        if (page == RadioPage.DISCOVERY && discoveryTab == DiscoveryTab.STATIONS) {
            loading = true
            message = null
            runCatching { loadStations(query) }
                .onFailure { message = "Stations could not be loaded. Please try again." }
            loading = false
        }
    }

    LaunchedEffect(page, discoveryTab) {
        if (page == RadioPage.DISCOVERY && discoveryTab == DiscoveryTab.RECENT) {
            refreshLocalState()
        }
    }

    BackHandler(onBack = ::back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Online Radio") },
                navigationIcon = {
                    IconButton(onClick = ::back) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (page == RadioPage.HOME) {
            HomeScreen(
                padding = padding,
                current = current,
                playing = playing,
                favorite = current?.let(store::isFavorite) == true,
                favoriteCount = favorites.size,
                onDiscovery = { page = RadioPage.DISCOVERY },
                onFavorites = { page = RadioPage.DISCOVERY },
                onPlay = { current?.let(::play) },
                onPause = ::pause,
                onFavorite = { current?.let(::toggleFavorite) },
            )
        } else {
            DiscoveryScreen(
                padding = padding,
                tab = discoveryTab,
                query = query,
                onTabChange = { discoveryTab = it },
                onQueryChange = { query = it },
                stations = stations,
                favorites = favorites,
                recentlyPlayed = recentlyPlayed,
                loading = loading,
                message = message,
                onPlay = ::play,
                onFavorite = ::toggleFavorite,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    current: RadioStation?,
    playing: Boolean,
    favorite: Boolean,
    favoriteCount: Int,
    onDiscovery: () -> Unit,
    onFavorites: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onFavorite: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Now Playing: " + (current?.name ?: "None"))

        if (current != null) {
            val details = listOf(current.country, current.language)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            if (details.isNotBlank()) Text(details)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = if (playing) onPause else onPlay,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                    )
                    Text(if (playing) "Pause" else "Play")
                }
                OutlinedButton(
                    onClick = onFavorite,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favorite) "Remove from Favorite" else "Add to Favorite",
                    )
                    Text(if (favorite) "Favorite" else "Add to Favorite")
                }
            }
        }

        Button(onClick = onDiscovery, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Text("Station Discovery")
        }

        OutlinedButton(onClick = onFavorites, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Favorite, contentDescription = null)
            Text("Favorite Stations ($favoriteCount)")
        }
    }
}

@Composable
private fun DiscoveryScreen(
    padding: PaddingValues,
    tab: DiscoveryTab,
    query: String,
    onTabChange: (DiscoveryTab) -> Unit,
    onQueryChange: (String) -> Unit,
    stations: List<RadioStation>,
    favorites: List<RadioStation>,
    recentlyPlayed: List<RadioStation>,
    loading: Boolean,
    message: String?,
    onPlay: (RadioStation) -> Unit,
    onFavorite: (RadioStation) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding),
    ) {
        TabRow(selectedTabIndex = tab.ordinal) {
            Tab(
                selected = tab == DiscoveryTab.STATIONS,
                onClick = { onTabChange(DiscoveryTab.STATIONS) },
                text = { Text("Station Discovery") },
            )
            Tab(
                selected = tab == DiscoveryTab.RECENT,
                onClick = { onTabChange(DiscoveryTab.RECENT) },
                text = { Text("Recently Played") },
            )
        }

        if (tab == DiscoveryTab.STATIONS) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Search stations") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                },
                singleLine = true,
            )
            StationList(
                stations = stations,
                favorites = favorites,
                loading = loading,
                message = message,
                onPlay = onPlay,
                onFavorite = onFavorite,
            )
        } else {
            StationList(
                stations = recentlyPlayed,
                favorites = favorites,
                loading = false,
                message = null,
                emptyMessage = "No recently played stations.",
                onPlay = onPlay,
                onFavorite = onFavorite,
            )
        }
    }
}

@Composable
private fun StationList(
    stations: List<RadioStation>,
    favorites: List<RadioStation>,
    loading: Boolean,
    message: String?,
    emptyMessage: String = "No stations found.",
    onPlay: (RadioStation) -> Unit,
    onFavorite: (RadioStation) -> Unit,
) {
    when {
        loading -> Text("Loading stations…", Modifier.padding(16.dp))
        message != null -> Text(message, Modifier.padding(16.dp))
        stations.isEmpty() -> Text(emptyMessage, Modifier.padding(16.dp))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(stations, key = { it.id }) { station ->
                val favorite = favorites.any { it.id == station.id }
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(station.name)
                    val details = listOf(station.country, station.language)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    if (details.isNotBlank()) Text(details)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onPlay(station) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                            Text("Play")
                        }
                        IconButton(onClick = { onFavorite(station) }) {
                            Icon(
                                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (favorite) "Remove from Favorite" else "Add to Favorite",
                            )
                        }
                    }
                }
            }
        }
    }
}
