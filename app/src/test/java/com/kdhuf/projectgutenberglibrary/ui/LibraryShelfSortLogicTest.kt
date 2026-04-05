package com.kdhuf.projectgutenberglibrary.ui

import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryShelfSortLogicTest {

    @Test
    fun `title sort orders books alphabetically`() {
        val sorted = LibraryShelfSortLogic.sortBooks(sampleBooks(), LibraryShelfSortMode.TITLE_ASC)

        assertEquals(listOf("Alpha", "Middle", "Zulu"), sorted.map { it.title })
    }

    @Test
    fun `author sort orders by author then title`() {
        val sorted = LibraryShelfSortLogic.sortBooks(sampleBooks(), LibraryShelfSortMode.AUTHOR_ASC)

        assertEquals(listOf("Author A", "Author B", "Author B"), sorted.map { it.author })
        assertEquals(listOf("Middle", "Alpha", "Zulu"), sorted.map { it.title })
    }

    @Test
    fun `recent sort orders newest downloads first`() {
        val sorted = LibraryShelfSortLogic.sortBooks(sampleBooks(), LibraryShelfSortMode.RECENT_DESC)

        assertEquals(listOf("Zulu", "Middle", "Alpha"), sorted.map { it.title })
    }

    @Test
    fun `recent sort preserves input order when books have no download timestamps`() {
        val sorted = LibraryShelfSortLogic.sortBooks(
            listOf(
                book(id = 1, title = "Zulu", author = "Author B", downloadedAt = 0L),
                book(id = 2, title = "Alpha", author = "Author A", downloadedAt = 0L),
                book(id = 3, title = "Middle", author = "Author C", downloadedAt = 0L)
            ),
            LibraryShelfSortMode.RECENT_DESC
        )

        assertEquals(listOf("Zulu", "Alpha", "Middle"), sorted.map { it.title })
    }

    @Test
    fun `popular sort orders books by downloads descending`() {
        val sorted = LibraryShelfSortLogic.sortBooks(
            listOf(
                book(id = 1, title = "Zulu", author = "Author B", downloadedAt = 0L, downloads = 200),
                book(id = 2, title = "Alpha", author = "Author A", downloadedAt = 0L, downloads = 500),
                book(id = 3, title = "Middle", author = "Author C", downloadedAt = 0L, downloads = 300)
            ),
            LibraryShelfSortMode.POPULAR_DESC
        )

        assertEquals(listOf("Alpha", "Middle", "Zulu"), sorted.map { it.title })
    }

    @Test
    fun `title descending sorts reverse alphabetically`() {
        val sorted = LibraryShelfSortLogic.sortBooks(sampleBooks(), LibraryShelfSortMode.TITLE_DESC)

        assertEquals(listOf("Zulu", "Middle", "Alpha"), sorted.map { it.title })
    }

    @Test
    fun `popular ascending sorts lowest downloads first`() {
        val sorted = LibraryShelfSortLogic.sortBooks(
            listOf(
                book(id = 1, title = "Zulu", author = "Author B", downloadedAt = 0L, downloads = 200),
                book(id = 2, title = "Alpha", author = "Author A", downloadedAt = 0L, downloads = 500),
                book(id = 3, title = "Middle", author = "Author C", downloadedAt = 0L, downloads = 300)
            ),
            LibraryShelfSortMode.POPULAR_ASC
        )

        assertEquals(listOf("Zulu", "Middle", "Alpha"), sorted.map { it.title })
    }

    private fun sampleBooks(): List<BookEntity> {
        return listOf(
            book(id = 1, title = "Zulu", author = "Author B", downloadedAt = 40L),
            book(id = 2, title = "Alpha", author = "Author B", downloadedAt = 10L),
            book(id = 3, title = "Middle", author = "Author A", downloadedAt = 20L)
        )
    }

    private fun book(
        id: Int,
        title: String,
        author: String,
        downloadedAt: Long,
        downloads: Int = 0
    ): BookEntity {
        return BookEntity(
            id = id,
            title = title,
            author = author,
            genre = "Fiction",
            downloads = downloads,
            coverUrl = null,
            coverPath = null,
            text = null,
            epubPath = null,
            downloadedAt = downloadedAt,
            status = BookEntity.STATUS_LIBRARY
        )
    }
}
