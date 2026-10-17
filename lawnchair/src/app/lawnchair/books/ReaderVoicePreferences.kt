package app.lawnchair.books

import android.content.Context

/** Voice preferences used only by Books Reader and PDF Reader. */
object ReaderVoicePreferences {
    private const val PREFS = "nexus_reader_voice"
    private const val PROVIDER = "provider"

    const val LOCAL = "local"

    fun provider(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PROVIDER, LOCAL)
            ?.takeIf { it == LOCAL }
            ?: LOCAL

    fun setProvider(context: Context, provider: String) {
        if (provider == LOCAL) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PROVIDER, provider)
                .apply()
        }
    }
}
