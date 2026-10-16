package app.lawnchair.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for assistant credentials and preferences.
 *
 * The repository originally used a plaintext preferences file. This helper preserves that
 * file's name for migration, but never falls back to plaintext for new credential writes.
 */
internal object AssistantPreferences {
    private const val FILE_NAME = "nexus_assistant_secure"
    private const val LEGACY_FILE_NAME = "nexus_assistant_cloud"
    const val TOKEN_KEY = "access_token"
    const val REFRESH_KEY = "refresh_token"

    @Volatile
    private var encrypted: SharedPreferences? = null

    fun secure(context: Context): SharedPreferences {
        encrypted?.let { return it }
        return synchronized(this) {
            encrypted ?: createEncrypted(context.applicationContext).also { encrypted = it }
        }
    }

    fun legacy(context: Context): SharedPreferences =
        context.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)

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

    fun migrateLegacySession(context: Context) {
        val secure = secure(context)
        val old = legacy(context)
        val access = old.getString(TOKEN_KEY, null)
        val refresh = old.getString(REFRESH_KEY, null)
        if (!access.isNullOrBlank() && secure.getString(TOKEN_KEY, null).isNullOrBlank()) {
            val committed = secure.edit()
                .putString(TOKEN_KEY, access)
                .apply {
                    if (!refresh.isNullOrBlank()) putString(REFRESH_KEY, refresh)
                }
                .commit()
            if (committed && !secure.getString(TOKEN_KEY, null).isNullOrBlank()) {
                old.edit().remove(TOKEN_KEY).remove(REFRESH_KEY).apply()
            }
        } else if (access.isNullOrBlank() && refresh.isNullOrBlank()) {
            // No plaintext credentials remain to migrate.
            return
        }
    }
}
