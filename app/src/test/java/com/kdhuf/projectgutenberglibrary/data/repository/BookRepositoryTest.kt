package com.kdhuf.projectgutenberglibrary.data.repository

import com.kdhuf.projectgutenberglibrary.data.local.BookDao
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.remote.AuthorDto
import com.kdhuf.projectgutenberglibrary.data.remote.BookDto
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergApi
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergResponse
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BookRepositoryTest {

    private lateinit var api: TrackingGutenbergApi
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        api = TrackingGutenbergApi()
        repository = BookRepository(
            dao = FakeBookDao(),
            api = api
        )
    }

    @Test
    fun `selectPreferredEpubUrl prefers illustrated epub when present`() {
        val formats = mapOf(
            "application/epub+zip; charset=utf-8" to "https://example.com/book.epub",
            "application/epub+zip; noimages" to "https://example.com/book-noimages.epub",
            "application/epub+zip; images" to "https://example.com/book-images.epub"
        )

        val result = repository.selectPreferredEpubUrl(formats)

        assertEquals("https://example.com/book-images.epub", result)
    }

    @Test
    fun `selectPreferredEpubUrl falls back to plain epub when no illustrated version exists`() {
        val formats = mapOf(
            "application/epub+zip; charset=utf-8" to "https://example.com/book.epub",
            "application/epub+zip; noimages" to "https://example.com/book-noimages.epub"
        )

        val result = repository.selectPreferredEpubUrl(formats)

        assertEquals("https://example.com/book.epub", result)
    }

    @Test
    fun `selectPreferredEpubUrl prefers older e-reader variant over plain epub`() {
        val formats = mapOf(
            "application/epub+zip" to "https://example.com/book.epub",
            "application/epub+zip; older E-readers" to "https://example.com/book-older.epub"
        )

        val result = repository.selectPreferredEpubUrl(formats)

        assertEquals("https://example.com/book-older.epub", result)
    }

    @Test
    fun `selectPreferredEpubUrl returns null when epub format is missing`() {
        val formats = mapOf(
            "text/plain; charset=utf-8" to "https://example.com/book.txt",
            "text/html" to "https://example.com/book.html"
        )

        val result = repository.selectPreferredEpubUrl(formats)

        assertNull(result)
    }

    @Test
    fun `selectPreferredEpubUrl returns null when formats are null`() {
        assertNull(repository.selectPreferredEpubUrl(null))
    }

    @Test
    fun `getPopularBooksPage without url requests popular sort from api`() = runTest {
        val response = repository.getPopularBooksPage()

        assertEquals("popular", api.lastSort)
        assertNull(api.lastPageUrl)
        assertEquals(api.booksResponse, response)
    }

    @Test
    fun `getPopularBooksPage with next url requests explicit page`() = runTest {
        val nextUrl = "https://gutendex.com/books?sort=popular&page=3"

        val response = repository.getPopularBooksPage(nextUrl)

        assertEquals(nextUrl, api.lastPageUrl)
        assertEquals(api.pageResponse, response)
    }

    @Test
    fun `mapRemoteBookToShelf uses metadata from dto when available`() {
        val dto = BookDto(
            id = 1342,
            title = "Pride and Prejudice",
            authors = listOf(AuthorDto("Jane Austen")),
            subjects = listOf("Courtship -- Fiction", "Domestic fiction"),
            formats = mapOf("image/jpeg" to "http://www.gutenberg.org/covers/1342.jpg"),
            download_count = 12345
        )

        val mapped = repository.mapRemoteBookToShelf(dto)

        assertEquals(1342, mapped.id)
        assertEquals("Pride and Prejudice", mapped.title)
        assertEquals("Jane Austen", mapped.author)
        assertEquals("Courtship -- Fiction", mapped.genre)
        assertEquals("https://www.gutenberg.org/covers/1342.jpg", mapped.coverUrl)
        assertEquals(12345, mapped.downloads)
        assertEquals(BookEntity.STATUS_TO_READ, mapped.status)
    }

    @Test
    fun `mapRemoteBookToShelf falls back to defaults when dto is sparse`() {
        val dto = BookDto(
            id = 84,
            title = "Frankenstein",
            authors = emptyList(),
            subjects = null,
            formats = emptyMap(),
            download_count = 99
        )

        val mapped = repository.mapRemoteBookToShelf(dto)

        assertEquals("Project Gutenberg", mapped.author)
        assertEquals("Classic literature", mapped.genre)
        assertEquals(
            "https://www.gutenberg.org/cache/epub/84/pg84.cover.medium.jpg",
            mapped.coverUrl
        )
    }

    private class FakeBookDao : BookDao {
        override suspend fun getBookById(id: Int): BookEntity? = null
        override fun getPopularBooks(): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun getNewestReleases(): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun getAllBooks(): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun getBooksByStatus(status: String): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun sortByTitle(): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun sortByDownloads(): Flow<List<BookEntity>> = flowOf(emptyList())
        override suspend fun updateStatus(id: Int, status: String) = Unit
        override suspend fun updateLastPageIndex(id: Int, pageIndex: Int) = Unit
        override suspend fun updateFavorite(id: Int, isFavorite: Boolean) = Unit
        override suspend fun deleteBookById(id: Int) = Unit
        override suspend fun getBookCount(): Int = 0
        override suspend fun insert(book: BookEntity) = Unit
        override suspend fun insertAll(books: List<BookEntity>) = Unit
        override fun getAllBooksFlow(): Flow<List<BookEntity>> = flowOf(emptyList())
    }

    private class TrackingGutenbergApi : GutenbergApi {
        val booksResponse = GutenbergResponse(results = listOf(BookDto(id = 1, title = "Popular page")))
        val pageResponse = GutenbergResponse(results = listOf(BookDto(id = 2, title = "Explicit page")))
        var lastSort: String? = null
        var lastPageUrl: String? = null

        override suspend fun getBooks(
            search: String?,
            topic: String?,
            sort: String?
        ): GutenbergResponse {
            lastSort = sort
            return booksResponse
        }

        override suspend fun getBooksPage(url: String): GutenbergResponse {
            lastPageUrl = url
            return pageResponse
        }

        override suspend fun getBookDetails(id: Int): BookDto = error("Not needed for this test")

        override suspend fun getBookText(textUrl: String): ResponseBody = error("Not needed for this test")
    }
}
