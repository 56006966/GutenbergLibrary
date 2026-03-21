package com.example.projectgutenberg.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ShelfCache(context: Context) : ShelfCacheStore {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("shelf_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<BookEntity>>() {}.type

    override fun getPopularBooks(): List<BookEntity> = getBooks(POPULAR_BOOKS_KEY)

    override fun savePopularBooks(books: List<BookEntity>) {
        saveBooks(POPULAR_BOOKS_KEY, POPULAR_UPDATED_AT_KEY, books)
    }

    override fun getNewestBooks(): List<BookEntity> = getBooks(NEWEST_BOOKS_KEY)

    override fun saveNewestBooks(books: List<BookEntity>) {
        saveBooks(NEWEST_BOOKS_KEY, NEWEST_UPDATED_AT_KEY, books)
    }

    override fun getTopDownloadedBooks(): List<BookEntity> = getBooks(TOP_DOWNLOADED_BOOKS_KEY)

    override fun saveTopDownloadedBooks(books: List<BookEntity>) {
        saveBooks(TOP_DOWNLOADED_BOOKS_KEY, TOP_DOWNLOADED_UPDATED_AT_KEY, books)
    }

    override fun hasPopularBooks(): Boolean = getPopularBooks().isNotEmpty()

    override fun hasNewestBooks(): Boolean = getNewestBooks().isNotEmpty()

    override fun hasTopDownloadedBooks(): Boolean = getTopDownloadedBooks().isNotEmpty()

    override fun isPopularCacheFresh(maxAgeMs: Long): Boolean = isCacheFresh(POPULAR_UPDATED_AT_KEY, maxAgeMs)

    override fun isNewestCacheFresh(maxAgeMs: Long): Boolean = isCacheFresh(NEWEST_UPDATED_AT_KEY, maxAgeMs)

    override fun isTopDownloadedCacheFresh(maxAgeMs: Long): Boolean =
        isCacheFresh(TOP_DOWNLOADED_UPDATED_AT_KEY, maxAgeMs)

    override fun context(): Context = appContext

    private fun getBooks(key: String): List<BookEntity> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<BookEntity>>(json, listType) }
            .getOrDefault(emptyList())
            .map { book ->
                book.copy(
                    coverUrl = book.coverUrl?.replace("http://", "https://")
                        ?: "https://www.gutenberg.org/cache/epub/${book.id}/pg${book.id}.cover.medium.jpg"
                )
            }
    }

    private fun saveBooks(dataKey: String, timestampKey: String, books: List<BookEntity>) {
        prefs.edit()
            .putString(dataKey, gson.toJson(books, listType))
            .putLong(timestampKey, System.currentTimeMillis())
            .apply()
    }

    private fun isCacheFresh(timestampKey: String, maxAgeMs: Long): Boolean {
        val lastUpdated = prefs.getLong(timestampKey, 0L)
        if (lastUpdated == 0L) return false
        return System.currentTimeMillis() - lastUpdated <= maxAgeMs
    }

    private companion object {
        const val POPULAR_BOOKS_KEY = "popular_books"
        const val NEWEST_BOOKS_KEY = "newest_books"
        const val TOP_DOWNLOADED_BOOKS_KEY = "top_downloaded_books"
        const val POPULAR_UPDATED_AT_KEY = "popular_books_updated_at"
        const val NEWEST_UPDATED_AT_KEY = "newest_books_updated_at"
        const val TOP_DOWNLOADED_UPDATED_AT_KEY = "top_downloaded_books_updated_at"
    }
}
