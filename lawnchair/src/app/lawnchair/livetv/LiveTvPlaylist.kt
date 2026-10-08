package app.lawnchair.livetv

import android.content.Context

object LiveTvPlaylist {
    private const val ASSET_NAME = "live_tv_in.m3u"

    fun load(context: Context): List<LiveTvChannel> =
        context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
            val result = mutableListOf<LiveTvChannel>()
            var pendingName: String? = null
            var pendingId: String? = null
            var pendingGroup: String = ""

            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("#EXTINF:") -> {
                        pendingName = line.substringAfterLast(",").trim().ifBlank { "Live TV" }
                        pendingId = Regex("""tvg-id="([^"]*)"""").find(line)?.groupValues?.get(1)
                            ?.ifBlank { null }
                        pendingGroup = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1).orEmpty()
                    }
                    line.isNotEmpty() && !line.startsWith("#") && pendingName != null -> {
                        result += LiveTvChannel(
                            id = pendingId ?: "$pendingName|$line",
                            name = pendingName!!,
                            streamUrl = line,
                            group = pendingGroup,
                        )
                        pendingName = null
                        pendingId = null
                        pendingGroup = ""
                    }
                }
            }
            result.distinctBy { it.id + "|" + it.streamUrl }
        }
}
