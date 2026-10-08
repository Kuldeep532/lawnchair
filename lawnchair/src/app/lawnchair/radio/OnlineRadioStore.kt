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

    fun current(): RadioStation? {
        val value = preferences.getString("current", null) ?: return null
        return decodeStation(value)
    }

    fun isPlaying(): Boolean = preferences.getBoolean("playing", false)

    fun setCurrent(station: RadioStation?, playing: Boolean) {
        preferences.edit()
            .putString("current", station?.let(::encodeStation))
            .putBoolean("playing", playing)
            .apply()
    }

    fun favorites(): List<RadioStation> =
        preferences.getStringSet("favorites", emptySet()).orEmpty()
            .mapNotNull(::decodeStation)
            .sortedBy { it.name.lowercase() }

    fun isFavorite(id: String): Boolean =
        preferences.getStringSet("favorites", emptySet()).orEmpty().contains(id)

    fun setFavorite(station: RadioStation, favorite: Boolean) {
        val current = preferences.getStringSet("favorites", emptySet()).orEmpty().toMutableSet()
        if (favorite) current.removeAll { decodeStation(it)?.id == station.id }
        if (favorite) current.add(encodeStation(station))
        else current.removeAll { decodeStation(it)?.id == station.id }
        preferences.edit().putStringSet("favorites", current).apply()
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

    companion object {
        @Volatile private var instance: OnlineRadioStore? = null

        fun getInstance(context: Context): OnlineRadioStore =
            instance ?: synchronized(this) {
                instance ?: OnlineRadioStore(context).also { instance = it }
            }
    }
}
