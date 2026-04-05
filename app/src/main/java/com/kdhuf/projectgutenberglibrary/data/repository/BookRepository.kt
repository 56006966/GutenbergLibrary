package com.kdhuf.projectgutenberglibrary.data.repository

import android.content.Context
import com.kdhuf.projectgutenberglibrary.data.catalog.CatalogDataSource
import com.kdhuf.projectgutenberglibrary.data.local.BookDao
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.remote.BookDto
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergMirror
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

open class BookRepository(
    private val dao: BookDao,
    private val catalogDataSource: CatalogDataSource
) {

    companion object {
        private const val PROJECT_GUTENBERG_AUTHOR = "Project Gutenberg"
        private const val CLASSIC_LITERATURE_GENRE = "Classic literature"
        private const val MIRROR_DOWNLOAD_USER_AGENT = "ProjectGutenbergLibrary/1.0 (+https://example.com/catalog-backend)"
    }

    private val okHttpClient = OkHttpClient()

    // ---------------- ROOM ----------------

    open fun getPopularBooks(): Flow<List<BookEntity>> = dao.getPopularBooks()

    open fun getLibraryBooks(): Flow<List<BookEntity>> = dao.getBooksByStatus(BookEntity.STATUS_LIBRARY)

    open suspend fun insertBook(book: BookEntity) = dao.insert(book)

    open suspend fun insertBooks(books: List<BookEntity>) = dao.insertAll(books)

    open suspend fun getLocalBook(id: Int) = dao.getBookById(id)

    open suspend fun updateLastPageIndex(id: Int, pageIndex: Int) = dao.updateLastPageIndex(id, pageIndex)

    open suspend fun updateFavorite(id: Int, isFavorite: Boolean) = dao.updateFavorite(id, isFavorite)

    open suspend fun deleteBookById(id: Int) = dao.deleteBookById(id)

    open suspend fun removeLocalBook(book: BookEntity) = withContext(Dispatchers.IO) {
        listOfNotNull(book.epubPath, book.coverPath, book.text)
            .map(::File)
            .filter { it.exists() }
            .forEach { file ->
                runCatching { file.delete() }
                val parent = file.parentFile ?: return@forEach
                val pageCache = File(parent, "${file.nameWithoutExtension}.v10.pages")
                if (pageCache.exists()) {
                    runCatching { pageCache.delete() }
                }
            }
        dao.deleteBookById(book.id)
    }

    // ---------------- API ----------------

    open suspend fun getGutenbergBooks(
        search: String? = null,
        topic: String? = null,
        sort: String? = null
    ): GutenbergResponse {
        return catalogDataSource.getBooks(search, topic, sort)
    }

    open suspend fun getPopularBooksPage(nextPageUrl: String? = null): GutenbergResponse {
        return if (nextPageUrl.isNullOrBlank()) {
            catalogDataSource.getBooks(sort = "popular")
        } else {
            catalogDataSource.getBooksPage(nextPageUrl)
        }
    }

    open fun mapRemoteBookToShelf(dto: BookDto): BookEntity {
        return BookEntity(
            id = dto.id,
            title = dto.title,
            author = dto.authors.joinToString { it.name }.ifBlank { PROJECT_GUTENBERG_AUTHOR },
            genre = dto.subjects?.firstOrNull() ?: CLASSIC_LITERATURE_GENRE,
            downloads = dto.download_count,
            coverUrl = GutenbergMirror.resolve(dto.formats?.get("image/jpeg")) ?: GutenbergMirror.coverUrl(dto.id),
            coverPath = null,
            text = null,
            epubPath = null,
            downloadedAt = 0L,
            isFavorite = false,
            lastPageIndex = 0,
            status = BookEntity.STATUS_TO_READ
        )
    }

    open suspend fun getBookDetails(id: Int): BookDto {
        return catalogDataSource.getBookDetails(id)
    }

    open suspend fun getOfficialNewestBooks(limit: Int = 50): List<BookEntity> = withContext(Dispatchers.IO) {
        catalogDataSource.getBooks(sort = "newest")
            .results
            .take(limit)
            .map(::mapRemoteBookToShelf)
    }

    open suspend fun getOfficialPopularBooks(limit: Int = 50): List<BookEntity> = withContext(Dispatchers.IO) {
        catalogDataSource.getBooks(sort = "popular")
            .results
            .take(limit)
            .map(::mapRemoteBookToShelf)
    }

    open suspend fun getTopDownloadedBooks(limit: Int = 100): List<BookEntity> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<BookEntity>()
        var nextPageUrl: String? = null

        while (collected.size < limit) {
            val response = if (nextPageUrl.isNullOrBlank()) {
                catalogDataSource.getBooks(sort = "popular")
            } else {
                catalogDataSource.getBooksPage(nextPageUrl)
            }

            val newBooks = response.results
                .map(::mapRemoteBookToShelf)
                .filterNot { incoming -> collected.any { existing -> existing.id == incoming.id } }

            collected += newBooks
            if (response.next.isNullOrBlank()) break
            nextPageUrl = response.next
        }

        collected.take(limit)
    }

    suspend fun updateStatus(id: Int, status: String) {
        dao.updateStatus(id, status)
    }

    suspend fun downloadBookText(url: String): String =
        withContext(Dispatchers.IO) {
            val candidates = GutenbergMirror.resolveCandidates(url)
            var lastError: Exception? = null

            for (candidate in candidates) {
                try {
                    val request = Request.Builder()
                        .url(candidate)
                        .addHeader("User-Agent", MIRROR_DOWNLOAD_USER_AGENT)
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Text failed: ${response.code}")
                        }
                        return@withContext response.body?.string().orEmpty()
                    }
                } catch (error: Exception) {
                    lastError = error
                }
            }

            throw lastError ?: Exception("Text download failed")
        }

    suspend fun downloadEpub(
        context: Context,
        url: String,
        bookId: Int
    ): File = withContext(Dispatchers.IO) {
        val resolvedUrls = GutenbergMirror.resolveCandidates(url)
        val file = epubFileFor(context, bookId, resolvedUrls.first())

        if (file.exists() && file.length() > 10_000) {
            return@withContext file
        }

        var lastError: Exception? = null
        for (resolvedUrl in resolvedUrls) {
            try {
                val request = Request.Builder()
                    .url(resolvedUrl)
                    .addHeader("User-Agent", MIRROR_DOWNLOAD_USER_AGENT)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("EPUB failed: ${response.code}")
                    }

                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (file.exists() && file.length() > 10_000) {
                    return@withContext file
                }
            } catch (error: Exception) {
                lastError = error
            }
        }

        throw lastError ?: Exception("EPUB download failed")
    }

    suspend fun downloadCover(
        context: Context,
        url: String,
        bookId: Int
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, "book_${bookId}_cover.jpg")

            if (file.exists() && file.length() > 2_048) {
                return@withContext file
            }

            for (candidate in GutenbergMirror.resolveCandidates(url)) {
                try {
                    val request = Request.Builder()
                        .url(candidate)
                        .addHeader("User-Agent", MIRROR_DOWNLOAD_USER_AGENT)
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use

                        response.body?.byteStream()?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    if (file.exists() && file.length() > 2_048) {
                        return@withContext file
                    }
                } catch (_: Exception) {
                    // Try the next official mirror candidate.
                }
            }

            file.takeIf { it.exists() && it.length() > 2_048 }
        }.getOrNull()
    }

    fun selectPreferredEpubUrl(formats: Map<String, String>?): String? {
        return formats
            ?.entries
            ?.filter { entry -> entry.key.startsWith("application/epub+zip") }
            ?.maxByOrNull { entry -> scoreEpubCandidate(entry.key, entry.value) }
            ?.value
            ?.let(GutenbergMirror::resolve)
    }

    private fun scoreEpubCandidate(formatKey: String, formatUrl: String): Int {
        val key = formatKey.lowercase()
        val url = formatUrl.lowercase()
        var score = 0

        val noImages = "noimages" in key || "noimages" in url
        val withImages = ("images" in key || "images" in url) && !noImages

        if (noImages) score -= 200
        if ("older e-readers" in key || "older ereaders" in key) score += 80
        if (withImages) score += 200
        if ("charset" in key) score -= 5
        if (url.contains(".epub")) score += 10

        return score
    }

    private fun epubFileFor(context: Context, bookId: Int, url: String): File {
        val normalizedUrl = url.lowercase()
        val suffix = when {
            "noimages" in normalizedUrl -> "_noimages"
            "images" in normalizedUrl -> "_images"
            else -> ""
        }
        return File(context.filesDir, "book_${bookId}${suffix}.epub")
    }
}
