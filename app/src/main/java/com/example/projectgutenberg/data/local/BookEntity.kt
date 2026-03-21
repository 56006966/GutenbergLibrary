package com.example.projectgutenberg.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val author: String,
    val genre: String,
    val downloads: Int,
    val coverUrl: String?,
    val coverPath: String? = null,
    val text: String?,
    val epubPath: String? = null,
    val lastPageIndex: Int = 0,
    val downloadedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val status: String,
    val isLoading: Boolean = false
) {
    companion object {
        const val STATUS_LIBRARY = "Library"
        const val STATUS_TO_READ = "To Read"
        const val STATUS_READ = "Read"
        const val STATUS_FAVORITES = "Favorites"
    }
}
