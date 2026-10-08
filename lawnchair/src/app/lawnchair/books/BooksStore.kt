package app.lawnchair.books

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

data class BookItem(
    val id: String,
    val title: String,
    val uri: String,
    val mimeType: String,
    val addedAt: Long,
    val lastPosition: Int = 0,
)

class BooksStore private constructor(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "books_library",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun books(): List<BookItem> =
        preferences.getString(KEY_BOOKS, null).orEmpty()
            .split(RECORD)
            .filter { it.isNotBlank() }
            .mapNotNull { decode(it) }
            .sortedByDescending { it.lastPosition == 0 }
            .sortedByDescending { it.addedAt }

    fun addBook(title: String, uri: String, mimeType: String): BookItem {
        books().firstOrNull { it.uri == uri }?.let { return it }
        val item = BookItem(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Book" }.trim(),
            uri = uri,
            mimeType = mimeType,
            addedAt = System.currentTimeMillis(),
        )
        save(books() + item)
        return item
    }

    fun removeBook(id: String) = save(books().filterNot { it.id == id })

    fun updatePosition(id: String, position: Int) {
        save(books().map {
            if (it.id == id) it.copy(lastPosition = position.coerceAtLeast(0)) else it
        })
    }

    private fun save(items: List<BookItem>) {
        preferences.edit()
            .putString(KEY_BOOKS, items.joinToString(RECORD) { encode(it) })
            .apply()
    }

    private fun encode(item: BookItem): String =
        listOf(item.id, item.title, item.uri, item.mimeType, item.addedAt.toString(), item.lastPosition.toString())
            .joinToString(SEP) { escape(it) }

    private fun decode(value: String): BookItem? {
        val p = value.split(SEP).map(::unescape)
        if (p.size < 6) return null
        return p.getOrNull(4)?.toLongOrNull()?.let { added ->
            BookItem(
                id = p[0],
                title = p[1],
                uri = p[2],
                mimeType = p[3],
                addedAt = added,
                lastPosition = p[5].toIntOrNull() ?: 0,
            )
        }
    }

    private fun escape(value: String) =
        value.replace("\", "\\").replace(RECORD, "\e").replace(SEP, "\u")

    private fun unescape(value: String) =
        value.replace("\u", SEP).replace("\e", RECORD).replace("\\", "\")

    companion object {
        private const val KEY_BOOKS = "books"
        private const val RECORD = ""
        private const val SEP = ""

        @Volatile private var instance: BooksStore? = null

        fun getInstance(context: Context): BooksStore =
            instance ?: synchronized(this) {
                instance ?: BooksStore(context.applicationContext).also { instance = it }
            }
    }
}