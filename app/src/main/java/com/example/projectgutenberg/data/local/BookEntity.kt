package com.example.projectgutenberg.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val author: String,
    val genre: String,
    val downloads: Int,
    val coverUrl: String? = null,
    val text: String?,
    val status: String = STATUS_TO_READ
) {
    companion object {
        const val STATUS_TO_READ = "To Read"
        const val STATUS_READ = "Read"
        const val STATUS_FAVORITES = "Favorites"
    }
}