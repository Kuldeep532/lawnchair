package app.lawnchair.account

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase Auth for optional Nexus account features.
 * Launcher basics remain available without signing in.
 */
object NexusAccountStore {
    private const val SUPABASE_URL = "https://cpbwiarqlvtlnwbkmpws.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_7uUT1Hx6rRj7Nv6N9qa7og_-dqxZ4j3"
    private const val FILE = "nexus_account_secure"
    private const val ACCESS = "access_token"
    private const val REFRESH = "refresh_token"
    private const val EMAIL = "email"
    private const val NAME = "name"
    private const val LEGAL_ACCEPTED = "legal_accepted_v1"

    private fun secure(context: Context): SharedPreferences {
        val master = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext, FILE, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    data class Account(
        val accessToken: String,
        val refreshToken: String?,
        val email: String,
        val name: String,
    )

    fun hasAcceptedLegal(context: Context): Boolean =
        secure(context).getBoolean(LEGAL_ACCEPTED, false)

    fun setLegalAccepted(context: Context, accepted: Boolean) {
        secure(context).edit().putBoolean(LEGAL_ACCEPTED, accepted).apply()
    }

    fun isSignedIn(context: Context): Boolean =
        !secure(context).getString(ACCESS, null).isNullOrBlank()

    fun account(context: Context): Account? {
        val p = secure(context)
        val access = p.getString(ACCESS, null)?.takeIf(String::isNotBlank) ?: return null
        return Account(
            accessToken = access,
            refreshToken = p.getString(REFRESH, null),
            email = p.getString(EMAIL, "") ?: "",
            name = p.getString(NAME, "") ?: "",
        )
    }

    /** Run from a worker/background dispatcher. Returns a user-facing Supabase message on failure. */
    fun signUp(context: Context, name: String, email: String, password: String): String {
        checkLegal(context)
        require(name.trim().length >= 2) { "Enter your name." }
        require(email.trim().contains("@")) { "Enter a valid email address." }
        require(password.length >= 8) { "Use a password with at least 8 characters." }
        val result = request(
            endpoint = "/auth/v1/signup",
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("data", JSONObject().put("full_name", name.trim())),
        )
        val access = result.optString("access_token")
        if (access.isNotBlank()) save(context, result, name.trim(), email.trim())
        val user = result.optJSONObject("user")
        return if (access.isBlank() && user != null) {
            "Account created. Check your email to confirm the account, then sign in."
        } else if (access.isBlank()) {
            "Account request sent. Check your email if confirmation is enabled."
        } else {
            "Your Nexus account is ready."
        }
    }

    fun signIn(context: Context, email: String, password: String): String {
        checkLegal(context)
        require(email.trim().contains("@")) { "Enter a valid email address." }
        require(password.isNotEmpty()) { "Enter your password." }
        val result = request(
            endpoint = "/auth/v1/token?grant_type=password",
            body = JSONObject().put("email", email.trim()).put("password", password),
        )
        if (result.optString("access_token").isBlank()) {
            throw IllegalStateException("The account needs email confirmation. Check your inbox and try again.")
        }
        val user = result.optJSONObject("user")
        val metadata = user?.optJSONObject("user_metadata")
        val name = metadata?.optString("full_name").orEmpty().ifBlank { email.substringBefore("@") }
        save(context, result, name, email.trim())
        return "Signed in to Nexus as $name."
    }

    fun signOut(context: Context) {
        val token = secure(context).getString(ACCESS, null)
        if (!token.isNullOrBlank()) {
            runCatching {
                request("/auth/v1/logout", JSONObject(), token)
            }
        }
        secure(context).edit()
            .remove(ACCESS).remove(REFRESH).remove(EMAIL).remove(NAME)
            .apply()
    }

    private fun checkLegal(context: Context) {
        check(hasAcceptedLegal(context)) {
            "Accept the Privacy Policy and Terms & Conditions before creating or signing in to a Nexus account."
        }
    }

    private fun save(context: Context, result: JSONObject, fallbackName: String, fallbackEmail: String) {
        val user = result.optJSONObject("user")
        val meta = user?.optJSONObject("user_metadata")
        val email = user?.optString("email").orEmpty().ifBlank { fallbackEmail }
        val name = meta?.optString("full_name").orEmpty().ifBlank {
            meta?.optString("name").orEmpty().ifBlank { fallbackName }
        }
        secure(context).edit()
            .putString(ACCESS, result.optString("access_token"))
            .putString(REFRESH, result.optString("refresh_token"))
            .putString(EMAIL, email)
            .putString(NAME, name)
            .apply()
        // Keep the existing Nexus Assistant online AI session in sync with this verified Supabase session.
        runCatching {
            app.lawnchair.assistant.NexusCloudAssistant.saveSession(
                context, result.optString("access_token"), result.optString("refresh_token"),
            )
        }
    }

    private fun request(endpoint: String, body: JSONObject, bearer: String? = null): JSONObject {
        val connection = (URL("$SUPABASE_URL$endpoint").openConnection() as HttpURLConnection).apply {
            requestMethod = if (endpoint.endsWith("/logout")) "POST" else "POST"
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(response) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val message = json.optString("msg").ifBlank {
                    json.optString("message").ifBlank { json.optString("error_description").ifBlank { json.optString("error") } }
                }
                throw IllegalStateException(message.ifBlank { "Supabase could not complete the request. Please try again." })
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}
