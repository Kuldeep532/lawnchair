package app.lawnchair.books

import android.content.Context

/** Voice preferences used only by Books Reader and PDF Reader. */
object ReaderVoicePreferences {
    private const val PREFS = "nexus_reader_voice"
    private const val PROVIDER = "provider"

    const val LOCAL = "local"
    const val ELEVENLABS = "elevenlabs"

    fun provider(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PROVIDER, LOCAL)
            ?.takeIf { it == LOCAL || it == ELEVENLABS }
            ?: LOCAL

    fun setProvider(context: Context, provider: String) {
        if (provider == LOCAL || provider == ELEVENLABS) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PROVIDER, provider)
                .apply()
        }
    }
}
