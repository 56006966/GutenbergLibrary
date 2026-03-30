package com.kdhuf.projectgutenberglibrary.data.repository

import com.kdhuf.projectgutenberglibrary.data.catalog.CatalogDataSource
import com.kdhuf.projectgutenberglibrary.data.local.BookDao
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.remote.AuthorDto
import com.kdhuf.projectgutenberglibrary.data.remote.BookDto
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergMirror
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergResponse
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertNull

class BookRepositoryTest {

    private lateinit var catalogDataSource: TrackingCatalogDataSource
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        catalogDataSource = TrackingCatalogDataSource()
        repository = BookRepository(
            dao = FakeBookDao(),
            catalogDataSource = catalogDataSource
        )
    }

    @Test
    fun `getPopularBooksPage without url requests popular sort from api`() = runTest {
        val response = repository.getPopularBooksPage()

        assertEquals("popular", catalogDataSource.lastSort)
        assertNull(catalogDataSource.lastPageUrl)
        assertEquals(catalogDataSource.booksResponse, response)
    }

    @Test
    fun `getPopularBooksPage with next url requests explicit page`() = runTest {
        val nextUrl = "https://catalog.example.test/books?sort=popular&page=3"

        val response = repository.getPopularBooksPage(nextUrl)

        assertEquals(nextUrl, catalogDataSource.lastPageUrl)
        assertEquals(catalogDataSource.pageResponse, response)
    }

    @Test
    fun `getOfficialNewestBooks requests newest sort from api`() = runTest {
        val books = repository.getOfficialNewestBooks(limit = 1)

        assertEquals("newest", catalogDataSource.lastSort)
        assertEquals(1, books.size)
        assertEquals("Popular page", books.first().title)
    }

    @Test
    fun `mapRemoteBookToShelf uses metadata from dto when available`() {
        val dto = BookDto(
            id = 1342,
            title = "Pride and Prejudice",
            authors = listOf(AuthorDto("Jane Austen")),
            subjects = listOf("Courtship -- Fiction", "Domestic fiction"),
            formats = mapOf("image/jpeg" to "https://example.com/covers/1342.jpg"),
            download_count = 12345
        )

        val mapped = repository.mapRemoteBookToShelf(dto)

        assertEquals(1342, mapped.id)
        assertEquals("Pride and Prejudice", mapped.title)
        assertEquals("Jane Austen", mapped.author)
        assertEquals("Courtship -- Fiction", mapped.genre)
        assertEquals("https://example.com/covers/1342.jpg", mapped.coverUrl)
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
        assertEquals(GutenbergMirror.coverUrl(84), mapped.coverUrl)
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

    private class TrackingCatalogDataSource : CatalogDataSource {
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
    }
}
