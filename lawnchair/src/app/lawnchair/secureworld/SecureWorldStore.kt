package app.lawnchair.secureworld

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

data class SecureNote(val id: String, val title: String, val body: String, val category: String = "General")
data class SecureDocument(
    val id: String,
    val category: String,
    val number: String,
    val name: String,
    val details: String,
    val uri: String,
    val ocrText: String = "",
)
data class SecurePassword(val id: String, val title: String, val username: String, val password: String, val category: String = "General")

class SecureWorldStore private constructor(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "secure_world",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun notes(): List<SecureNote> = decode("notes") { p ->
        if (p.size >= 4) SecureNote(p[0], p[1], p[2], p.drop(3).joinToString(SEP)) else if (p.size >= 3) SecureNote(p[0], p[1], p[2], "General") else null
    }

    fun saveNote(title: String, body: String, category: String = "General"): SecureNote {
        val item = SecureNote(UUID.randomUUID().toString(), title.trim(), body, category.trim().ifBlank { "General" })
        saveNotes(notes() + item)
        return item
    }

    fun deleteNote(id: String) = saveNotes(notes().filterNot { it.id == id })

    fun documents(): List<SecureDocument> = decode("documents") { p ->
        if (p.size >= 7) {
            SecureDocument(p[0], p[1], p[2], p[3], p[4], p[5], p[6])
        } else if (p.size >= 6) {
            SecureDocument(p[0], p[1], p[2], p[3], p[4], p[5], "")
        } else if (p.size >= 3) {
            SecureDocument(p[0], "Other", "", p[1], "", p.drop(2).joinToString(SEP))
        } else {
            null
        }
    }

    fun saveDocument(category: String, number: String, name: String, details: String, uri: String, ocrText: String = ""): SecureDocument {
        val item = SecureDocument(
            UUID.randomUUID().toString(),
            category.trim(),
            number.trim(),
            name.trim(),
            details.trim(),
            uri,
            ocrText,
        )
        saveDocuments(documents() + item)
        return item
    }

    fun deleteDocument(id: String) = saveDocuments(documents().filterNot { it.id == id })

    fun passwords(): List<SecurePassword> = decode("passwords") { p ->
        if (p.size >= 5) SecurePassword(p[0], p[1], p[2], p[3], p.drop(4).joinToString(SEP)) else if (p.size >= 4) SecurePassword(p[0], p[1], p[2], p[3], "General") else null
    }

    fun savePassword(title: String, username: String, password: String, category: String = "General"): SecurePassword {
        val item = SecurePassword(UUID.randomUUID().toString(), title.trim(), username.trim(), password, category.trim().ifBlank { "General" })
        savePasswords(passwords() + item)
        return item
    }

    fun deletePassword(id: String) = savePasswords(passwords().filterNot { it.id == id })

    private fun saveNotes(items: List<SecureNote>) {
        val encoded = items.joinToString(RECORD) {
            listOf(it.id, it.title, it.body, it.category).joinToString(SEP, transform = ::escape)
        }
        preferences.edit().putString("notes", encoded).apply()
    }

    private fun saveDocuments(items: List<SecureDocument>) {
        val encoded = items.joinToString(RECORD) {
            listOf(it.id, it.category, it.number, it.name, it.details, it.uri, it.ocrText)
                .joinToString(SEP, transform = ::escape)
        }
        preferences.edit().putString("documents", encoded).apply()
    }

    private fun savePasswords(items: List<SecurePassword>) {
        val encoded = items.joinToString(RECORD) {
            listOf(it.id, it.title, it.username, it.password, it.category).joinToString(SEP, transform = ::escape)
        }
        preferences.edit().putString("passwords", encoded).apply()
    }

    private fun <T> decode(key: String, mapper: (List<String>) -> T?): List<T> {
        val encoded = preferences.getString(key, null).orEmpty()
        if (encoded.isEmpty()) return emptyList()
        return encoded.split(RECORD).mapNotNull { mapper(it.split(SEP).map(::unescape)) }
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(RECORD, "\\e").replace(SEP, "\\u")

    private fun unescape(value: String): String =
        value.replace("\\u", SEP).replace("\\e", RECORD).replace("\\\\", "\\")

    companion object {
        private const val RECORD = "\u001e"
        private const val SEP = "\u001f"

        @Volatile private var instance: SecureWorldStore? = null

        fun getInstance(context: Context): SecureWorldStore =
            instance ?: synchronized(this) {
                instance ?: SecureWorldStore(context.applicationContext).also { instance = it }
            }
    }
}
