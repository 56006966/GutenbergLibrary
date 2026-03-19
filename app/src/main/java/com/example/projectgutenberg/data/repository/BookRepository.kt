package com.example.projectgutenberg.data.repository

import android.content.Context
import android.util.Log
import com.example.projectgutenberg.data.local.BookDao
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.data.remote.BookDto
import com.example.projectgutenberg.data.remote.GutenbergApi
import com.example.projectgutenberg.data.remote.GutenbergResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File

class BookRepository(
    private val dao: BookDao,
    private val api: GutenbergApi
) {

    private val okHttpClient = OkHttpClient()

    // ---------------- ROOM ----------------

    fun getPopularBooks(): Flow<List<BookEntity>> = dao.getPopularBooks()

    fun getLibraryBooks(): Flow<List<BookEntity>> = dao.getBooksByStatus(BookEntity.STATUS_LIBRARY)

    suspend fun insertBook(book: BookEntity) = dao.insert(book)

    suspend fun insertBooks(books: List<BookEntity>) = dao.insertAll(books)

    suspend fun getLocalBook(id: Int) = dao.getBookById(id)

    suspend fun updateLastPageIndex(id: Int, pageIndex: Int) = dao.updateLastPageIndex(id, pageIndex)

    // ---------------- API ----------------

    suspend fun getGutenbergBooks(topic: String? = null, sort: String? = null): GutenbergResponse {
        return api.getBooks(topic, sort)
    }

    suspend fun getBookDetails(id: Int): BookDto {
        return api.getBookDetails(id)
    }

    suspend fun getOfficialNewestBooks(limit: Int = 12): List<BookEntity> = withContext(Dispatchers.IO) {
        val rssXml = fetchUrl("https://www.gutenberg.org/cache/epub/feeds/today.rss")
        parseRssBooks(rssXml, limit)
    }

    suspend fun getOfficialPopularBooks(limit: Int = 12): List<BookEntity> = withContext(Dispatchers.IO) {
        val opdsXml = fetchUrl("https://www.gutenberg.org/ebooks/search.opds/?sort_order=downloads")
        parseOpdsBooks(opdsXml, limit)
    }

    // ---------------- TEXT DOWNLOAD ----------------

    suspend fun downloadBookText(url: String): String =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url.replace("http://", "https://"))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                response.body?.string() ?: ""
            } catch (e: Exception) {
                Log.e("Repository", "Text download failed", e)
                ""
            }
        }

    // ---------------- EPUB DOWNLOAD ----------------

    suspend fun downloadEpub(
        context: Context,
        url: String,
        bookId: Int
    ): File = withContext(Dispatchers.IO) {

        val file = File(context.filesDir, "book_${bookId}.epub")

        if (file.exists() && file.length() > 10000) {
            return@withContext file
        }

        val request = Request.Builder()
            .url(url.replace("http://", "https://"))
            .addHeader("User-Agent", "ProjectGutenbergApp")
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

        file
    }

    suspend fun downloadCover(
        context: Context,
        url: String,
        bookId: Int
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, "book_${bookId}_cover.jpg")

            if (file.exists() && file.length() > 2048) {
                return@withContext file
            }

            val request = Request.Builder()
                .url(url.replace("http://", "https://"))
                .addHeader("User-Agent", "ProjectGutenbergApp")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use

                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            file.takeIf { it.exists() && it.length() > 2048 }
        }.getOrNull()
    }

    suspend fun updateStatus(id: Int, status: String) {
        dao.updateStatus(id, status)
    }
    suspend fun getOrDownloadEpub(
        context: Context,
        book: BookDto
    ): File = withContext(Dispatchers.IO) {

        val epubUrl = book.formats?.get("application/epub+zip")
            ?: throw Exception("EPUB not available")

        val file = File(context.filesDir, "book_${book.id}.epub")

        if (file.exists() && file.length() > 10000) {
            return@withContext file
        }

        return@withContext downloadEpub(context, epubUrl, book.id)
    }

    private fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url.replace("http://", "https://"))
            .addHeader("User-Agent", "ProjectGutenbergApp/1.0 (+https://example.com/contact)")
            .addHeader("Accept", "application/atom+xml, application/rss+xml, application/xml, text/xml")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Feed request failed: ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseRssBooks(xml: String, limit: Int): List<BookEntity> {
        val parser = newParser(xml)
        val books = mutableListOf<BookEntity>()
        var insideItem = false
        var title: String? = null
        var link: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT && books.size < limit) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        insideItem = true
                        title = null
                        link = null
                    }
                    "title" -> if (insideItem) title = parser.nextText()
                    "link" -> if (insideItem) link = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" && insideItem) {
                    val id = extractBookId(link)
                    if (id != null && !title.isNullOrBlank()) {
                        books += buildShelfBook(
                            id = id,
                            rawTitle = title!!,
                            downloads = limit - books.size
                        )
                    }
                    insideItem = false
                }
            }
            parser.next()
        }

        return books
    }

    private fun parseOpdsBooks(xml: String, limit: Int): List<BookEntity> {
        val parser = newParser(xml)
        val books = mutableListOf<BookEntity>()
        var insideEntry = false
        var id: Int? = null
        var title: String? = null
        val authors = mutableListOf<String>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT && books.size < limit) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        insideEntry = true
                        id = null
                        title = null
                        authors.clear()
                    }
                    "id" -> if (insideEntry) id = extractBookId(parser.nextText())
                    "title" -> if (insideEntry) title = parser.nextText()
                    "name" -> if (insideEntry) authors += parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry" && insideEntry) {
                    if (id != null && !title.isNullOrBlank()) {
                        books += BookEntity(
                            id = id!!,
                            title = title!!.trim(),
                            author = authors.joinToString().ifBlank { "Project Gutenberg" },
                            genre = "Classic literature",
                            downloads = limit - books.size,
                            coverUrl = buildFallbackCoverUrl(id!!),
                            coverPath = null,
                            text = null,
                            epubPath = null,
                            lastPageIndex = 0,
                            status = BookEntity.STATUS_TO_READ
                        )
                    }
                    insideEntry = false
                }
            }
            parser.next()
        }

        return books
    }

    private fun buildShelfBook(id: Int, rawTitle: String, downloads: Int): BookEntity {
        val parts = rawTitle.split(" by ", limit = 2)
        val parsedTitle = parts.firstOrNull().orEmpty().trim().ifBlank { "Untitled" }
        val parsedAuthor = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "Project Gutenberg" }
        return BookEntity(
            id = id,
            title = parsedTitle,
            author = parsedAuthor,
            genre = "Classic literature",
            downloads = downloads,
            coverUrl = buildFallbackCoverUrl(id),
            coverPath = null,
            text = null,
            epubPath = null,
            lastPageIndex = 0,
            status = BookEntity.STATUS_TO_READ
        )
    }

    private fun buildFallbackCoverUrl(bookId: Int): String {
        return "https://www.gutenberg.org/cache/epub/$bookId/pg$bookId.cover.medium.jpg"
    }

    private fun extractBookId(value: String?): Int? {
        val text = value ?: return null
        return Regex("/ebooks/(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d+)$").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun newParser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply {
            setInput(ByteArrayInputStream(xml.toByteArray()), "UTF-8")
        }
    }
}
