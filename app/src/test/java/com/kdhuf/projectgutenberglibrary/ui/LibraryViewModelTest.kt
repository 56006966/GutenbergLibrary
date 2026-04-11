package com.kdhuf.projectgutenberglibrary.ui

import android.content.Context
import com.kdhuf.projectgutenberglibrary.data.catalog.CatalogDataSource
import com.kdhuf.projectgutenberglibrary.data.local.BookDao
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.local.ShelfCacheStore
import com.kdhuf.projectgutenberglibrary.data.remote.BookDto
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergResponse
import com.kdhuf.projectgutenberglibrary.data.repository.BookRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `offline without cache surfaces newest and popular offline messages`() = runTest {
        val repository = TrackingBookRepository()
        val shelfCache = FakeShelfCacheStore()

        val viewModel = LibraryViewModel(
            repository = repository,
            shelfCache = shelfCache,
            networkChecker = { false }
        )
        advanceUntilIdle()

        assertEquals(
            "Offline. Connect to the internet to load most popular books.",
            viewModel.popularError.value
        )
        assertEquals(
            "Offline. Connect to the internet to load newest releases.",
            viewModel.newestError.value
        )
        assertFalse(repository.popularRequested)
        assertFalse(repository.newestRequested)
    }

    @Test
    fun `offline with cache keeps cached shelf messaging and skips repository calls`() = runTest {
        val repository = TrackingBookRepository()
        val cachedBook = sampleBook(1342)
        val shelfCache = FakeShelfCacheStore(
            popular = listOf(cachedBook),
            newest = listOf(cachedBook)
        )

        val viewModel = LibraryViewModel(
            repository = repository,
            shelfCache = shelfCache,
            networkChecker = { false }
        )
        advanceUntilIdle()

        assertEquals("Offline. Showing saved most popular books.", viewModel.popularError.value)
        assertEquals("Offline. Showing saved newest releases.", viewModel.newestError.value)
        assertFalse(repository.popularRequested)
        assertFalse(repository.newestRequested)
    }

    @Test
    fun `fresh cache skips refresh and clears loading states`() = runTest {
        val cachedBook = sampleBook(84)
        val repository = TrackingBookRepository()
        val shelfCache = FakeShelfCacheStore(
            popular = listOf(cachedBook),
            newest = listOf(cachedBook),
            popularFresh = true,
            newestFresh = true
        )

        val viewModel = LibraryViewModel(
            repository = repository,
            shelfCache = shelfCache,
            networkChecker = { true }
        )
        advanceUntilIdle()

        assertEquals(listOf(cachedBook), viewModel.popularBooks.value)
        assertEquals(listOf(cachedBook), viewModel.newestBooks.value)
        assertFalse(repository.popularRequested)
        assertFalse(repository.newestRequested)
        assertFalse(viewModel.isLoadingPopular.value)
        assertFalse(viewModel.isLoadingNewest.value)
        assertNull(viewModel.popularError.value)
        assertNull(viewModel.newestError.value)
    }

    @Test
    fun `cached books are exposed immediately on initialization`() = runTest {
        val cachedPopular = listOf(sampleBook(1, title = "Cached Popular"))
        val cachedNewest = listOf(sampleBook(2, title = "Cached Newest"))

        val viewModel = LibraryViewModel(
            repository = TrackingBookRepository(),
            shelfCache = FakeShelfCacheStore(
                popular = cachedPopular,
                newest = cachedNewest
            ),
            networkChecker = { false }
        )
        advanceUntilIdle()

        assertEquals(cachedPopular, viewModel.popularBooks.value)
        assertEquals(cachedNewest, viewModel.newestBooks.value)
    }

    @Test
    fun `loadLibraryBooks sorts titles alphabetically`() = runTest {
        val repository = TrackingBookRepository(
            libraryBooks = listOf(
                sampleBook(2, title = "Zulu"),
                sampleBook(1, title = "Alpha")
            )
        )

        val viewModel = LibraryViewModel(
            repository = repository,
            shelfCache = FakeShelfCacheStore(),
            networkChecker = { false }
        )
        advanceUntilIdle()

        assertEquals(listOf("Alpha", "Zulu"), viewModel.libraryBooks.value.map { it.title })
    }

    @Test
    fun `toggleFavorite flips current favorite value`() = runTest {
        val repository = TrackingBookRepository()
        val book = sampleBook(55, favorite = true)

        val viewModel = LibraryViewModel(
            repository = repository,
            shelfCache = FakeShelfCacheStore(),
            networkChecker = { false }
        )
        viewModel.toggleFavorite(book)
        advanceUntilIdle()

        assertEquals(55, repository.lastFavoriteUpdate?.first)
        assertEquals(false, repository.lastFavoriteUpdate?.second)
    }

    @Test
    fun `toggleSaveForLater marks library book as to read`() = runTest {
        val repository = TrackingBookRepository()
        val book = sampleBook(56, status = BookEntity.STATUS_LIBRARY)

        val viewModel = LibraryViewModel(
            repository = repository,
            shelfCache = FakeShelfCacheStore(),
            networkChecker = { false }
        )
        viewModel.toggleSaveForLater(book)
        advanceUntilIdle()

        assertEquals(56, repository.lastStatusUpdate?.first)
        assertEquals(BookEntity.STATUS_TO_READ, repository.lastStatusUpdate?.second)
    }

    @Test
    fun `toggleSaveForLater restores saved book to library`() = runTest {
        val repository = TrackingBookRepository()
        val book = sampleBook(57, status = BookEntity.STATUS_TO_READ)

        val viewModel = LibraryViewModel(
            repository = repository,
            shelfCache = FakeShelfCacheStore(),
            networkChecker = { false }
        )
        viewModel.toggleSaveForLater(book)
        advanceUntilIdle()

        assertEquals(57, repository.lastStatusUpdate?.first)
        assertEquals(BookEntity.STATUS_LIBRARY, repository.lastStatusUpdate?.second)
    }

    private fun sampleBook(
        id: Int,
        title: String = "Sample",
        favorite: Boolean = false,
        status: String = BookEntity.STATUS_LIBRARY
    ) = BookEntity(
        id = id,
        title = title,
        author = "Author",
        genre = "Classic",
        downloads = 1,
        coverUrl = null,
        text = null,
        isFavorite = favorite,
        status = status
    )

    private class TrackingBookRepository(
        private val libraryBooks: List<BookEntity> = emptyList()
    ) : BookRepository(
        dao = FakeBookDao(),
        catalogDataSource = FakeCatalogDataSource()
    ) {
        var popularRequested = false
        var newestRequested = false
        var lastFavoriteUpdate: Pair<Int, Boolean>? = null
        var lastStatusUpdate: Pair<Int, String>? = null

        override suspend fun getOfficialPopularBooks(limit: Int): List<BookEntity> {
            popularRequested = true
            return emptyList()
        }

        override suspend fun getOfficialNewestBooks(limit: Int): List<BookEntity> {
            newestRequested = true
            return emptyList()
        }

        override fun getLibraryBooks(): Flow<List<BookEntity>> = flowOf(libraryBooks)

        override suspend fun updateFavorite(id: Int, isFavorite: Boolean) {
            lastFavoriteUpdate = id to isFavorite
        }

        override suspend fun updateStatus(id: Int, status: String) {
            lastStatusUpdate = id to status
        }
    }

    private class FakeShelfCacheStore(
        private val popular: List<BookEntity> = emptyList(),
        private val newest: List<BookEntity> = emptyList(),
        private val popularFresh: Boolean = false,
        private val newestFresh: Boolean = false
    ) : ShelfCacheStore {
        var savedPopular: List<BookEntity> = emptyList()
        var savedNewest: List<BookEntity> = emptyList()

        override fun getPopularBooks(): List<BookEntity> = popular
        override fun savePopularBooks(books: List<BookEntity>) {
            savedPopular = books
        }

        override fun getNewestBooks(): List<BookEntity> = newest
        override fun saveNewestBooks(books: List<BookEntity>) {
            savedNewest = books
        }

        override fun getTopDownloadedBooks(): List<BookEntity> = emptyList()
        override fun saveTopDownloadedBooks(books: List<BookEntity>) = Unit

        override fun hasPopularBooks(): Boolean = popular.isNotEmpty()
        override fun hasNewestBooks(): Boolean = newest.isNotEmpty()
        override fun hasTopDownloadedBooks(): Boolean = false
        override fun isPopularCacheFresh(maxAgeMs: Long): Boolean = popularFresh
        override fun isNewestCacheFresh(maxAgeMs: Long): Boolean = newestFresh
        override fun isTopDownloadedCacheFresh(maxAgeMs: Long): Boolean = false
        override fun context(): Context {
            throw UnsupportedOperationException("Context should not be used in these tests")
        }
    }

    private class FakeBookDao : BookDao {
        override suspend fun getBookById(id: Int): BookEntity? = null
        override fun getPopularBooks(): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun getNewestReleases(): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun getAllBooks(): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun getBooksByStatus(status: String): Flow<List<BookEntity>> = flowOf(emptyList())
        override fun getBooksByStatuses(statuses: List<String>): Flow<List<BookEntity>> = flowOf(emptyList())
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

    private class FakeCatalogDataSource : CatalogDataSource {
        override suspend fun getBooks(
            search: String?,
            topic: String?,
            sort: String?
        ): GutenbergResponse = error("Not needed for this test")

        override suspend fun getBooksPage(url: String): GutenbergResponse = error("Not needed for this test")

        override suspend fun getBookDetails(id: Int): BookDto = error("Not needed for this test")

    }
}
