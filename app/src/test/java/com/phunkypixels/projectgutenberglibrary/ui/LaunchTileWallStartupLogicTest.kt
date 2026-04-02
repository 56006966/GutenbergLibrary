package com.phunkypixels.projectgutenberglibrary.ui

import com.phunkypixels.projectgutenberglibrary.data.local.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchTileWallStartupLogicTest {

    @Test
    fun `defers wall books while overlay is visible`() {
        val books = listOf(sampleBook(1))

        val update = LaunchTileWallStartupLogic.updateBooks(
            overlayVisible = true,
            incomingBooks = books
        )

        assertNull(update.visibleBooks)
        assertEquals(books, update.pendingBooks)
    }

    @Test
    fun `applies wall books immediately when overlay is hidden`() {
        val books = listOf(sampleBook(2))

        val update = LaunchTileWallStartupLogic.updateBooks(
            overlayVisible = false,
            incomingBooks = books
        )

        assertEquals(books, update.visibleBooks)
        assertNull(update.pendingBooks)
    }

    private fun sampleBook(id: Int): BookEntity {
        return BookEntity(
            id = id,
            title = "Title $id",
            author = "Author $id",
            genre = "Genre",
            downloads = 0,
            coverUrl = null,
            coverPath = null,
            text = null,
            epubPath = null,
            status = BookEntity.STATUS_TO_READ
        )
    }
}
