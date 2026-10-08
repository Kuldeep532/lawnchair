package app.lawnchair.books

data class PiperVoice(
    val id: String,
    val language: String,
    val displayName: String,
    val quality: String,
)

object PiperVoiceCatalog {
    val voices = listOf(
        PiperVoice("hi_IN-pratham-medium", "Hindi", "Pratham", "Medium"),
        PiperVoice("hi_IN-priyamvada-medium", "Hindi", "Priyamvada", "Medium"),
        PiperVoice("en_US-amy-medium", "English (US)", "Amy", "Medium"),
        PiperVoice("en_US-daniela-high", "English (US)", "Daniela", "High"),
        PiperVoice("en_GB-alan-medium", "English (UK)", "Alan", "Medium"),
        PiperVoice("en_GB-cori-high", "English (UK)", "Cori", "High"),
        PiperVoice("de_DE-thorsten-high", "German", "Thorsten", "High"),
        PiperVoice("fr_FR-siwis-medium", "French", "Siwis", "Medium"),
        PiperVoice("es_ES-sharvard-medium", "Spanish", "Sharvard", "Medium"),
    )
}
