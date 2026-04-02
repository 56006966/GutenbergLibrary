package com.phunkypixels.projectgutenberglibrary.ui

import com.phunkypixels.projectgutenberglibrary.data.local.BookEntity

data class LaunchTileWallBooksUpdate(
    val visibleBooks: List<BookEntity>?,
    val pendingBooks: List<BookEntity>?
)

object LaunchTileWallStartupLogic {

    fun updateBooks(
        overlayVisible: Boolean,
        incomingBooks: List<BookEntity>
    ): LaunchTileWallBooksUpdate {
        return if (overlayVisible) {
            LaunchTileWallBooksUpdate(
                visibleBooks = null,
                pendingBooks = incomingBooks
            )
        } else {
            LaunchTileWallBooksUpdate(
                visibleBooks = incomingBooks,
                pendingBooks = null
            )
        }
    }
}
