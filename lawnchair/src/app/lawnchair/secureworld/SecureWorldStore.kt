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

data class SecureMedia(
    val id: String,
    val name: String,
    val uri: String,
    val mimeType: String,
    val category: String = "Private Media",
)

data class SecureHiddenFile(
    val id: String,
    val name: String,
    val localPath: String,
    val mimeType: String,
    val category: String = "Hidden Files",
)

data class SecureVaultCategory(val id: String, val name: String, val isBuiltIn: Boolean = true)

class SecureWorldStore private constructor(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "secure_world",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun media(): List<SecureMedia> = decode("media") { p ->
        if (p.size >= 4) SecureMedia(p[0], p[1], p[2], p[3], p.getOrNull(4).orEmpty().ifBlank { "Private Media" }) else null
    }

    fun saveMedia(name: String, uri: String, mimeType: String, category: String = "Private Media"): SecureMedia {
        val item = SecureMedia(UUID.randomUUID().toString(), name.trim(), uri, mimeType, category.trim().ifBlank { "Private Media" })
        val encoded = media() + item
        val value = encoded.joinToString(RECORD) { listOf(it.id, it.name, it.uri, it.mimeType, it.category).joinToString(SEP, transform = ::escape) }
        preferences.edit().putString("media", value).apply()
        return item
    }

    fun deleteMedia(id: String) {
        val value = media().filterNot { it.id == id }
            .joinToString(RECORD) { listOf(it.id, it.name, it.uri, it.mimeType, it.category).joinToString(SEP, transform = ::escape) }
        preferences.edit().putString("media", value).apply()
    }

    fun categories(): List<SecureVaultCategory> {
        val custom = preferences.getString("categories", null).orEmpty()
            .split(RECORD)
            .mapNotNull { record ->
                if (record.isBlank()) null else record.split(SEP).let { parts ->
                    if (parts.size >= 2) SecureVaultCategory(unescape(parts[0]), unescape(parts.drop(1).joinToString(SEP)), false) else null
                }
            }
        val customNames = custom.map { it.name.lowercase() }.toSet()
        return defaultCategories.map { SecureVaultCategory(it.lowercase().replace(' ', '_'), it, true) }
            .filterNot { it.name.lowercase() in customNames } + custom
    }

    fun addCategory(name: String): SecureVaultCategory? {
        val clean = name.trim()
        if (clean.isBlank() || categories().any { it.name.equals(clean, true) }) return null
        val item = SecureVaultCategory(UUID.randomUUID().toString(), clean, false)
        val current = preferences.getString("categories", null).orEmpty()
        val next = if (current.isBlank()) {
            escape(item.id) + SEP + escape(item.name)
        } else {
            current + RECORD + escape(item.id) + SEP + escape(item.name)
        }
        preferences.edit().putString("categories", next).apply()
        return item
    }

    fun deleteCategory(name: String) {
        if (defaultCategories.any { it.equals(name, true) }) return
        val next = categories()
            .filterNot { it.name.equals(name, true) }
            .joinToString(RECORD) { escape(it.id) + SEP + escape(it.name) }
        preferences.edit().putString("categories", next).apply()
    }

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

        private val defaultCategories = listOf(
            "General",
            "Personal",
            "Finance",
            "Bank Passwords",
            "Internet Banking Passwords",
            "My Google Account",
            "My Instagram",
            "My Facebook",
            "Email Accounts",
            "Social Media",
            "Work",
            "Travel",
            "Identity Documents",
            "Insurance",
            "Education",
            "Health",
            "Shopping",
            "Bills",
            "Wi-Fi & Network",
            "Other",
        )

        @Volatile private var instance: SecureWorldStore? = null

        fun getInstance(context: Context): SecureWorldStore =
            instance ?: synchronized(this) {
                instance ?: SecureWorldStore(context.applicationContext).also { instance = it }
            }
    }
}
