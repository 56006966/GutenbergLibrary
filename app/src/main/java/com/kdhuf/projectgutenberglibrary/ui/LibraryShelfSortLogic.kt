package com.kdhuf.projectgutenberglibrary.ui

import com.kdhuf.projectgutenberglibrary.data.local.BookEntity

enum class LibraryShelfSortMode {
    TITLE_ASC,
    TITLE_DESC,
    AUTHOR_ASC,
    AUTHOR_DESC,
    RECENT_DESC,
    RECENT_ASC,
    POPULAR_DESC,
    POPULAR_ASC
}

object LibraryShelfSortLogic {

    fun sortBooks(books: List<BookEntity>, mode: LibraryShelfSortMode): List<BookEntity> {
        return when (mode) {
            LibraryShelfSortMode.TITLE_ASC -> books.sortedBy { it.title.lowercase() }
            LibraryShelfSortMode.TITLE_DESC -> books.sortedByDescending { it.title.lowercase() }
            LibraryShelfSortMode.AUTHOR_ASC -> books.sortedWith(
                compareBy<BookEntity> { it.author.lowercase() }
                    .thenBy { it.title.lowercase() }
            )
            LibraryShelfSortMode.AUTHOR_DESC -> books.sortedWith(
                compareByDescending<BookEntity> { it.author.lowercase() }
                    .thenByDescending { it.title.lowercase() }
            )
            LibraryShelfSortMode.RECENT_DESC -> {
                if (books.any { it.downloadedAt > 0L }) {
                    books.sortedWith(
                        compareByDescending<BookEntity> { it.downloadedAt }
                            .thenBy { it.title.lowercase() }
                    )
                } else {
                    books.toList()
                }
            }
            LibraryShelfSortMode.RECENT_ASC -> {
                if (books.any { it.downloadedAt > 0L }) {
                    books.sortedWith(
                        compareBy<BookEntity> { it.downloadedAt }
                            .thenBy { it.title.lowercase() }
                    )
                } else {
                    books.toList()
                }
            }
            LibraryShelfSortMode.POPULAR_DESC -> books.sortedWith(
                compareByDescending<BookEntity> { it.downloads }
                    .thenBy { it.title.lowercase() }
            )
            LibraryShelfSortMode.POPULAR_ASC -> books.sortedWith(
                compareBy<BookEntity> { it.downloads }
                    .thenBy { it.title.lowercase() }
            )
        }
    }
}
