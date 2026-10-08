package app.lawnchair.secureworld

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Local encrypted storage used by Secure World.
 *
 * The store is intentionally small: individual Secure World screens decide how their
 * encrypted values are structured.
 */
class SecureWorldStore private constructor(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "secure_world",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getString(key: String): String? = preferences.getString(key, null)

    fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    companion object {
        @Volatile
        private var instance: SecureWorldStore? = null

        fun getInstance(context: Context): SecureWorldStore =
            instance ?: synchronized(this) {
                instance ?: SecureWorldStore(context.applicationContext).also { instance = it }
            }
    }
}
