package com.example.projectgutenberg.data.local

import android.content.Context

interface ShelfCacheStore {
    fun getPopularBooks(): List<BookEntity>
    fun savePopularBooks(books: List<BookEntity>)
    fun getNewestBooks(): List<BookEntity>
    fun saveNewestBooks(books: List<BookEntity>)
    fun getTopDownloadedBooks(): List<BookEntity>
    fun saveTopDownloadedBooks(books: List<BookEntity>)
    fun hasPopularBooks(): Boolean
    fun hasNewestBooks(): Boolean
    fun hasTopDownloadedBooks(): Boolean
    fun isPopularCacheFresh(maxAgeMs: Long): Boolean
    fun isNewestCacheFresh(maxAgeMs: Long): Boolean
    fun isTopDownloadedCacheFresh(maxAgeMs: Long): Boolean
    fun context(): Context
}
