package app.lawnchair.livetv

data class LiveTvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val group: String = "",
)
