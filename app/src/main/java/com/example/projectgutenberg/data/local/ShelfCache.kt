package com.example.projectgutenberg.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ShelfCache(context: Context) {

    private val prefs = context.getSharedPreferences("shelf_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<BookEntity>>() {}.type

    fun getPopularBooks(): List<BookEntity> = getBooks(POPULAR_BOOKS_KEY)

    fun savePopularBooks(books: List<BookEntity>) {
        saveBooks(POPULAR_BOOKS_KEY, POPULAR_UPDATED_AT_KEY, books)
    }

    fun getNewestBooks(): List<BookEntity> = getBooks(NEWEST_BOOKS_KEY)

    fun saveNewestBooks(books: List<BookEntity>) {
        saveBooks(NEWEST_BOOKS_KEY, NEWEST_UPDATED_AT_KEY, books)
    }

    fun hasPopularBooks(): Boolean = getPopularBooks().isNotEmpty()

    fun hasNewestBooks(): Boolean = getNewestBooks().isNotEmpty()

    fun isPopularCacheFresh(maxAgeMs: Long): Boolean = isCacheFresh(POPULAR_UPDATED_AT_KEY, maxAgeMs)

    fun isNewestCacheFresh(maxAgeMs: Long): Boolean = isCacheFresh(NEWEST_UPDATED_AT_KEY, maxAgeMs)

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
        const val POPULAR_UPDATED_AT_KEY = "popular_books_updated_at"
        const val NEWEST_UPDATED_AT_KEY = "newest_books_updated_at"
    }
}
