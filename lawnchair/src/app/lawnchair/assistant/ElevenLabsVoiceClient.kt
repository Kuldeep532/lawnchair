package app.lawnchair.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ElevenLabs speech client. Requests are proxied through the authenticated Supabase gateway;
 * the ElevenLabs API key is never stored in the Android app.
 */
object ElevenLabsVoiceClient {
    private const val GATEWAY_URL =
        "https://cpbwiarqlvtlnwbkmpws.supabase.co/functions/v1/nexus-api-gateway/functions/elevenlabs-tts"
    private const val SUPABASE_URL = "https://cpbwiarqlvtlnwbkmpws.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_7uUT1Hx6rRj7Nv6N9qa7og_-dqxZ4j3"
    private const val PREFS = "nexus_assistant_cloud"
    private const val TOKEN_KEY = "access_token"
    private const val VOICE_KEY = "elevenlabs_voice_id"

    data class Voice(val id: String, val name: String)

    fun selectedVoiceId(context: Context): String? =
        preferences(context).getString(VOICE_KEY, null)?.takeIf { it.isNotBlank() }

    fun setSelectedVoiceId(context: Context, voiceId: String) {
        preferences(context).edit().putString(VOICE_KEY, voiceId).apply()
    }

    fun synthesize(context: Context, text: String): String {
        require(text.isNotBlank()) { "There is no text to read aloud." }
        val token = accessToken(context)
        val voiceId = selectedVoiceId(context) ?: listVoices(context).firstOrNull()?.id
            ?.also { setSelectedVoiceId(context, it) }
            ?: throw IllegalStateException("No ElevenLabs voices are available.")
        val payload = JSONObject()
            .put("action", "synthesize")
            .put("voiceId", voiceId)
            .put("text", text.take(10000))
            .put("modelId", "eleven_multilingual_v2")
        val result = request(GATEWAY_URL, token, payload)
        return result.optString("audioUrl").takeIf { it.startsWith("https://") }
            ?: throw IllegalStateException("ElevenLabs did not return an audio link.")
    }

    fun listVoices(context: Context): List<Voice> {
        val result = request(
            GATEWAY_URL,
            accessToken(context),
            JSONObject().put("action", "list-voices"),
        )
        val array = result.optJSONArray("voices") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim()
            if (id.isBlank()) null else Voice(id, item.optString("name", id))
        }
    }

    private fun preferences(context: Context): SharedPreferences {
        val secure = AssistantPreferences.secure(context)
        val legacy = AssistantPreferences.legacy(context)
        val oldVoiceId = legacy.getString(VOICE_KEY, null)
        if (!oldVoiceId.isNullOrBlank() && secure.getString(VOICE_KEY, null).isNullOrBlank()) {
            secure.edit().putString(VOICE_KEY, oldVoiceId).apply()
            legacy.edit().remove(VOICE_KEY).apply()
        }
        return secure
    }

    private fun accessToken(context: Context): String =
        preferences(context).getString(TOKEN_KEY, null)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in to your Nexus account to use ElevenLabs voice.")

    private fun request(url: String, token: String, payload: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val code = json.optString("error")
                throw IllegalStateException(
                    when (code) {
                        "AUTH_REQUIRED" -> "Sign in again to use ElevenLabs voice."
                        "PREMIUM_REQUIRED" -> "ElevenLabs voice requires an active premium plan."
                        "INSUFFICIENT_CREDITS" -> "You do not have enough AI credits for ElevenLabs voice."
                        "ELEVENLABS_NOT_CONFIGURED" -> "ElevenLabs is not configured on the Nexus server yet."
                        else -> "ElevenLabs voice is temporarily unavailable."
                    }
                )
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}
