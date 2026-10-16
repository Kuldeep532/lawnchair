package app.lawnchair.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Shared encrypted storage for Nexus Assistant session and preferences.
 *
 * If encrypted storage cannot be opened (for example after a device credential/key
 * becomes invalid), callers receive a clear error rather than silently writing tokens
 * in plaintext. Existing legacy preferences are read only for non-secret preferences.
 */
internal object AssistantPreferences {
    private const val FILE_NAME = "nexus_assistant_cloud"
    private const val LEGACY_FILE_NAME = "nexus_assistant_cloud"
    private const val TOKEN_KEY = "access_token"
    private const val REFRESH_KEY = "refresh_token"

    @Volatile
    private var encrypted: SharedPreferences? = null

    fun secure(context: Context): SharedPreferences {
        encrypted?.let { return it }
        return synchronized(this) {
            encrypted ?: createEncrypted(context.applicationContext).also { encrypted = it }
        }
    }

    private fun createEncrypted(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Migrate existing plaintext session values once, then delete the old secret copies.
     * If encryption setup fails, do not keep returning or writing plaintext secrets.
     */
    fun migrateLegacySession(context: Context) {
        val secure = secure(context)
        val legacy = context.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)
        val access = legacy.getString(TOKEN_KEY, null)
        val refresh = legacy.getString(REFRESH_KEY, null)
        if (!access.isNullOrBlank() && secure.getString(TOKEN_KEY, null).isNullOrBlank()) {
            secure.edit()
                .putString(TOKEN_KEY, access)
                .apply {
                    if (!refresh.isNullOrBlank()) putString(REFRESH_KEY, refresh)
                }
                .commit()
            // Remove legacy secret values only after the encrypted copy is confirmed.
            if (!secure.getString(TOKEN_KEY, null).isNullOrBlank()) {
                legacy.edit().remove(TOKEN_KEY).remove(REFRESH_KEY).apply()
            }
        }
    }

    fun isSecretKey(key: String): Boolean = key == TOKEN_KEY || key == REFRESH_KEY
}
