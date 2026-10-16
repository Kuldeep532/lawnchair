package app.lawnchair.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional online AI for Nexus Assistant.
 *
 * Only the Supabase project URL and publishable client key are shipped in the app.
 * Provider credentials stay in Supabase Edge Function secrets.
 */
object NexusCloudAssistant {
    private const val SUPABASE_URL = "https://cpbwiarqlvtlnwbkmpws.supabase.co"
    private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_7uUT1Hx6rRj7Nv6N9qa7og_-dqxZ4j3"

    private const val CHAT_FUNCTION = "nexus-ai-chat"
    private const val PREFERENCES = "nexus_assistant_cloud"
    private const val TOKEN_KEY = "access_token"
    private const val REFRESH_KEY = "refresh_token"
    private const val PROVIDER_KEY = "provider"

    fun saveSession(context: Context, accessToken: String, refreshToken: String? = null) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(TOKEN_KEY, accessToken)
            .apply {
                if (!refreshToken.isNullOrBlank()) putString(REFRESH_KEY, refreshToken)
            }
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .remove(TOKEN_KEY)
            .remove(REFRESH_KEY)
            .apply()
    }

    fun setProvider(context: Context, provider: String) {
        require(provider in setOf("gemini", "openai", "anthropic"))
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(PROVIDER_KEY, provider).apply()
    }

    fun provider(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(PROVIDER_KEY, "gemini") ?: "gemini"

    fun isSignedIn(context: Context): Boolean =
        !context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null).isNullOrBlank()

    /**
     * This operation performs network I/O. Call from a background thread.
     * The existing Nexus Supabase sign-in/session flow should call saveSession when it has
     * a verified user access token; no service-role or provider key is ever needed here.
     */
    fun reply(context: Context, message: String): String {
        require(message.isNotBlank()) { "Please enter a question." }
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val token = prefs.getString(TOKEN_KEY, null)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in to your Nexus account to use the online assistant.")
        val provider = prefs.getString(PROVIDER_KEY, "gemini") ?: "gemini"

        val payload = JSONObject()
            .put("provider", provider)
            .put("message", message.trim())
            .put("history", JSONArray())

        val connection = (URL("$SUPABASE_URL/functions/v1/$CHAT_FUNCTION").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val error = json.optString("error")
                throw IllegalStateException(
                    when (error) {
                        "AUTH_REQUIRED" -> "Sign in again to use the online assistant."
                        "PREMIUM_MODEL_REQUIRED_OPENAI", "PREMIUM_MODEL_REQUIRED_ANTHROPIC" ->
                            "This AI provider is available with an active premium plan. Select Gemini or check your plan."
                        "OPENAI_NOT_CONFIGURED", "ANTHROPIC_NOT_CONFIGURED", "GEMINI_NOT_CONFIGURED" ->
                            "This AI service is not configured yet. Please try another AI provider."
                        else -> "The online assistant is temporarily unavailable. Please try again."
                    }
                )
            }
            return json.optString("text").trim().takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The online assistant returned an empty response.")
        } finally {
            connection.disconnect()
        }
    }
}
