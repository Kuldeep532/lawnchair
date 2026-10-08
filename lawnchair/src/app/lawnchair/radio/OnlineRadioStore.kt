package app.lawnchair.radio

import android.content.Context
import org.json.JSONObject

data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val country: String = "",
    val language: String = "",
    val tags: String = "",
)

class OnlineRadioStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "online_radio",
        Context.MODE_PRIVATE,
    )

    fun current(): RadioStation? =
        preferences.getString(KEY_CURRENT, null)?.let(::decodeStation)

    fun isPlaying(): Boolean = preferences.getBoolean(KEY_PLAYING, false)

    fun setCurrent(station: RadioStation?, playing: Boolean) {
        preferences.edit()
            .putString(KEY_CURRENT, station?.let(::encodeStation))
            .putBoolean(KEY_PLAYING, playing)
            .apply()
    }

    fun favorites(): List<RadioStation> =
        preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
            .mapNotNull(::decodeStation)
            .sortedBy { it.name.lowercase() }

    fun isFavorite(id: String): Boolean = favorites().any { it.id == id }

    fun setFavorite(station: RadioStation, favorite: Boolean) {
        val next = favorites().filterNot { it.id == station.id }.toMutableList()
        if (favorite) next.add(station)
        preferences.edit().putStringSet(KEY_FAVORITES, next.map(::encodeStation).toSet()).apply()
    }

    fun recentlyPlayed(): List<RadioStation> =
        preferences.getStringSet(KEY_RECENT, emptySet()).orEmpty()
            .mapNotNull(::decodeRecent)
            .sortedByDescending { it.second }
            .map { it.first }

    fun addRecentlyPlayed(station: RadioStation) {
        val next = preferences.getStringSet(KEY_RECENT, emptySet()).orEmpty()
            .mapNotNull(::decodeRecent)
            .filterNot { it.first.id == station.id }
            .toMutableList()
        next.add(station to System.currentTimeMillis())
        val encoded = next.sortedByDescending { it.second }
            .take(MAX_RECENT)
            .map { (item, time) -> time.toString() + "::" + encodeStation(item) }
            .toSet()
        preferences.edit().putStringSet(KEY_RECENT, encoded).apply()
    }

    private fun encodeStation(station: RadioStation): String =
        JSONObject().apply {
            put("id", station.id)
            put("name", station.name)
            put("url", station.url)
            put("country", station.country)
            put("language", station.language)
            put("tags", station.tags)
        }.toString()

    private fun decodeStation(value: String): RadioStation? = runCatching {
        val o = JSONObject(value)
        RadioStation(
            id = o.optString("id"),
            name = o.optString("name"),
            url = o.optString("url"),
            country = o.optString("country"),
            language = o.optString("language"),
            tags = o.optString("tags"),
        )
    }.getOrNull()?.takeIf { it.id.isNotBlank() && it.url.isNotBlank() }

    private fun decodeRecent(value: String): Pair<RadioStation, Long>? = runCatching {
        val separator = value.indexOf("::")
        require(separator > 0)
        val timestamp = value.substring(0, separator).toLong()
        val station = decodeStation(value.substring(separator + 2)) ?: return@runCatching null
        station to timestamp
    }.getOrNull()

    companion object {
        private const val KEY_CURRENT = "current"
        private const val KEY_PLAYING = "playing"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENT = "recently_played"
        private const val MAX_RECENT = 20

        @Volatile private var instance: OnlineRadioStore? = null

        fun getInstance(context: Context): OnlineRadioStore =
            instance ?: synchronized(this) {
                instance ?: OnlineRadioStore(context).also { instance = it }
            }
    }
}
