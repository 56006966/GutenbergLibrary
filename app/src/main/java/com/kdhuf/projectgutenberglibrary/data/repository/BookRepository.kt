package com.kdhuf.projectgutenberglibrary.data.repository

import android.content.Context
import android.util.Log
import com.kdhuf.projectgutenberglibrary.data.local.BookDao
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.remote.BookDto
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergApi
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File

open class BookRepository(
    private val dao: BookDao,
    private val api: GutenbergApi
) {

    companion object {
        private const val TAG = "BookRepository"
        private const val PROJECT_GUTENBERG_AUTHOR = "Project Gutenberg"
        private const val CLASSIC_LITERATURE_GENRE = "Classic literature"
        private const val GUTENBERG_CONTACT_USER_AGENT = "ProjectGutenbergApp/1.0 (+https://example.com/contact)"
        private const val EPUB_DOWNLOAD_USER_AGENT = "ProjectGutenbergApp"
        private const val TOP_DOWNLOADS_URL = "https://www.gutenberg.org/browse/scores/top"
        private const val POPULAR_OPDS_URL = "https://www.gutenberg.org/ebooks/search.opds/?sort_order=downloads"
        private const val NEWEST_RSS_URL = "https://www.gutenberg.org/cache/epub/feeds/today.rss"
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

    open suspend fun removeLocalBook(context: Context, book: BookEntity) = withContext(Dispatchers.IO) {
        listOfNotNull(book.epubPath, book.coverPath, book.text)
            .map(::File)
            .filter { it.exists() }
            .forEach { file ->
                runCatching { file.delete() }
                val pageCache = File(file.parentFile ?: context.cacheDir, "${file.nameWithoutExtension}.v3.pages")
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
        return api.getBooks(search, topic, sort)
    }

    open suspend fun getPopularBooksPage(nextPageUrl: String? = null): GutenbergResponse {
        return if (nextPageUrl.isNullOrBlank()) {
            api.getBooks(sort = "popular")
        } else {
            api.getBooksPage(nextPageUrl)
        }
    }

    open fun mapRemoteBookToShelf(dto: BookDto): BookEntity {
        return BookEntity(
            id = dto.id,
            title = dto.title,
            author = dto.authors.joinToString { it.name }.ifBlank { PROJECT_GUTENBERG_AUTHOR },
            genre = dto.subjects?.firstOrNull() ?: CLASSIC_LITERATURE_GENRE,
            downloads = dto.download_count,
            coverUrl = dto.formats?.get("image/jpeg")?.replace("http://", "https://")
                ?: buildFallbackCoverUrl(dto.id),
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
        return api.getBookDetails(id)
    }

    open suspend fun getOfficialNewestBooks(limit: Int = 12): List<BookEntity> = withContext(Dispatchers.IO) {
        val rssXml = fetchUrl(NEWEST_RSS_URL)
        parseRssBooks(rssXml, limit)
    }

    open suspend fun getOfficialPopularBooks(limit: Int = 12): List<BookEntity> = withContext(Dispatchers.IO) {
        val opdsXml = fetchUrl(POPULAR_OPDS_URL)
        parseOpdsBooks(opdsXml, limit)
    }

    open suspend fun getTopDownloadedBooks(limit: Int = 100): List<BookEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val html = fetchHtmlUrl(TOP_DOWNLOADS_URL)
            parseTopDownloadedBooks(html, limit)
        }.fold(
            onSuccess = { books ->
                if (books.isNotEmpty()) {
                    books
                } else {
                    Log.w(TAG, "Top 100 scores page returned no parsable books; falling back to OPDS")
                    getOfficialPopularBooks(limit)
                }
            },
            onFailure = { error ->
                Log.w(TAG, "Top 100 scores page failed; falling back to OPDS", error)
                getOfficialPopularBooks(limit)
            }
        )
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

        val file = epubFileFor(context, bookId, url)

        if (file.exists() && file.length() > 10000) {
            return@withContext file
        }

        val request = Request.Builder()
            .url(url.replace("http://", "https://"))
            .addHeader("User-Agent", EPUB_DOWNLOAD_USER_AGENT)
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
                .addHeader("User-Agent", EPUB_DOWNLOAD_USER_AGENT)
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

        val epubUrl = selectPreferredEpubUrl(book.formats)
            ?: throw Exception("EPUB not available")

        val file = epubFileFor(context, book.id, epubUrl)

        if (file.exists() && file.length() > 10000) {
            return@withContext file
        }

        return@withContext downloadEpub(context, epubUrl, book.id)
    }

    private fun fetchUrl(url: String): String {
        val request = Request.Builder()
            .url(url.replace("http://", "https://"))
            .addHeader("User-Agent", GUTENBERG_CONTACT_USER_AGENT)
            .addHeader("Accept", "application/atom+xml, application/rss+xml, application/xml, text/xml")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Feed request failed: ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun fetchHtmlUrl(url: String): String {
        val request = Request.Builder()
            .url(url.replace("http://", "https://"))
            .addHeader("User-Agent", GUTENBERG_CONTACT_USER_AGENT)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Top 100 request failed: ${response.code}")
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
                            author = authors.joinToString().ifBlank { PROJECT_GUTENBERG_AUTHOR },
                            genre = CLASSIC_LITERATURE_GENRE,
                            downloads = limit - books.size,
                            coverUrl = buildFallbackCoverUrl(id!!),
                            coverPath = null,
                            text = null,
                            epubPath = null,
                            downloadedAt = 0L,
                            isFavorite = false,
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
        val parsedAuthor = parts.getOrNull(1)?.trim().orEmpty().ifBlank { PROJECT_GUTENBERG_AUTHOR }
        return BookEntity(
            id = id,
            title = parsedTitle,
            author = parsedAuthor,
            genre = CLASSIC_LITERATURE_GENRE,
            downloads = downloads,
            coverUrl = buildFallbackCoverUrl(id),
            coverPath = null,
            text = null,
            epubPath = null,
            downloadedAt = 0L,
            isFavorite = false,
            lastPageIndex = 0,
            status = BookEntity.STATUS_TO_READ
        )
    }

    private fun buildFallbackCoverUrl(bookId: Int): String {
        return "https://www.gutenberg.org/cache/epub/$bookId/pg$bookId.cover.medium.jpg"
    }

    private fun parseTopDownloadedBooks(html: String, limit: Int): List<BookEntity> {
        val sectionMatch = Regex(
            """Top\s*100\s*EBooks\s*yesterday.*?<ol[^>]*>(.*?)</ol>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)

        val orderedListBlock = sectionMatch?.groupValues?.getOrNull(1).orEmpty()
        if (orderedListBlock.isBlank()) return emptyList()

        val itemPattern = Regex(
            """<li[^>]*>\s*<a[^>]+href="/ebooks/(\d+)"[^>]*>(.*?)</a>\s*\(([\d,]+)\)\s*</li>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        return itemPattern.findAll(orderedListBlock)
            .take(limit)
            .mapIndexedNotNull { index, match ->
                val id = match.groupValues[1].toIntOrNull() ?: return@mapIndexedNotNull null
                val rawTitle = decodeHtml(match.groupValues[2]).replace(Regex("\\s+"), " ").trim()
                val downloads = match.groupValues[3].replace(",", "").toIntOrNull() ?: (limit - index)
                buildShelfBook(id = id, rawTitle = rawTitle, downloads = downloads)
            }
            .toList()
    }

    private fun decodeHtml(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    fun selectPreferredEpubUrl(formats: Map<String, String>?): String? {
        return formats
            ?.entries
            ?.filter { entry -> entry.key.startsWith("application/epub+zip") }
            ?.maxByOrNull { entry -> scoreEpubCandidate(entry.key, entry.value) }
            ?.value
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
