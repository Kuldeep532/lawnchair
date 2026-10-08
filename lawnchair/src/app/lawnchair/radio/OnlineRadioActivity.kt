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

class OnlineRadioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContent { LawnchairTheme { OnlineRadioScreen(::finish) } }
    }
}

private enum class RadioPage { HOME, BROWSE }

@Composable
private fun OnlineRadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { OnlineRadioStore.getInstance(context) }
    var page by remember { mutableStateOf(RadioPage.HOME) }
    var current by remember { mutableStateOf(store.current()) }
    var playing by remember { mutableStateOf(store.isPlaying()) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var query by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun play(station: RadioStation) {
        store.setCurrent(station, true)
        current = station
        playing = true
        OnlineRadioService.command(context, OnlineRadioService.ACTION_PLAY)
    }

    fun pause() {
        current?.let { store.setCurrent(it, false) }
        playing = false
        OnlineRadioService.command(context, OnlineRadioService.ACTION_PAUSE)
    }

    fun toggleFavorite() {
        current?.let {
            store.setFavorite(it, !store.isFavorite(it.id))
            favorites = store.favorites()
        }
    }

    suspend fun loadStations(search: String) {
        withContext(Dispatchers.IO) {
            val endpoint = buildString {
                append("https://de1.api.radio-browser.info/json/stations/search?limit=100&hidebroken=true&order=name&reverse=false")
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
        if (page == RadioPage.BROWSE) {
            loading = true
            message = null
            runCatching { loadStations(query) }
                .onFailure { message = "Stations could not be loaded. Please try again." }
            loading = false
        }
    }

    fun back() {
        if (page == RadioPage.BROWSE) page = RadioPage.HOME else onBack()
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
                onBrowse = { page = RadioPage.BROWSE },
                onPlay = { current?.let(::play) },
                onPause = ::pause,
                onFavorite = ::toggleFavorite,
            )
        } else {
            BrowseScreen(
                padding = padding,
                query = query,
                onQueryChange = { query = it },
                stations = stations,
                favorites = favorites,
                loading = loading,
                message = message,
                onPlay = ::play,
                onFavorite = {
                    store.setFavorite(it, !store.isFavorite(it.id))
                    favorites = store.favorites()
                },
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
    onBrowse: () -> Unit,
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
        Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
            Text("Browse Station")
        }
    }
}

@Composable
private fun BrowseScreen(
    padding: PaddingValues,
    query: String,
    onQueryChange: (String) -> Unit,
    stations: List<RadioStation>,
    favorites: List<RadioStation>,
    loading: Boolean,
    message: String?,
    onPlay: (RadioStation) -> Unit,
    onFavorite: (RadioStation) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().padding(padding),
    ) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Browse Station") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Favorite Station") },
            )
        }

        if (tab == 0) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
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
                stations = favorites,
                favorites = favorites,
                loading = false,
                message = null,
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
    onPlay: (RadioStation) -> Unit,
    onFavorite: (RadioStation) -> Unit,
) {
    when {
        loading -> Text("Loading stations…", Modifier.padding(horizontal = 20.dp))
        message != null -> Text(message, Modifier.padding(horizontal = 20.dp))
        stations.isEmpty() -> Text(
            "No stations found.",
            Modifier.padding(horizontal = 20.dp),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
