package com.example.projectgutenberg.data.repository

import android.text.Html
import android.util.Log
import com.example.projectgutenberg.data.local.BookDao
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.data.remote.BookDto
import com.example.projectgutenberg.data.remote.GutenbergApi
import com.example.projectgutenberg.data.remote.GutenbergResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class BookRepository(
    private val dao: BookDao,
    private val api: GutenbergApi
) {

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /* ----------------------------
       LOCAL DATABASE OPERATIONS
    ----------------------------- */

    fun getPopularBooks() = dao.getPopularBooks()

    fun getAllBooks() = dao.getAllBooks()

    fun getBooksByStatus(status: String) =
        dao.getBooksByStatus(status)

    fun sortByTitle() = dao.sortByTitle()

    fun sortByDownloads() = dao.sortByDownloads()

    suspend fun insertBook(book: BookEntity) =
        dao.insert(book)

    suspend fun insertBooks(books: List<BookEntity>) =
        dao.insertAll(books)

    suspend fun updateStatus(id: Int, status: String) =
        dao.updateStatus(id, status)

    suspend fun deleteBook(id: Int) =
        dao.deleteBookById(id)

    suspend fun getLocalBook(id: Int) =
        dao.getBookById(id)

    /* ----------------------------
       REMOTE API
    ----------------------------- */

    suspend fun getGutenbergBooks(topic: String): GutenbergResponse =
        api.getBooks(topic)

    suspend fun getBookDetails(id: Int): BookDto =
        api.getBookDetails(id)

    /* ----------------------------
       DOWNLOAD BOOK TEXT
    ----------------------------- */

    suspend fun downloadBookText(url: String): String =
        withContext(Dispatchers.IO) {
            try {
                val secureUrl = url.replace("http://", "https://")
                val request = Request.Builder().url(secureUrl).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful)
                    throw Exception("Failed: ${response.code}")

                val body = response.body?.string() ?: ""

                val cleanedText =
                    if (body.trimStart().startsWith("<")) {
                        Html.fromHtml(
                            body,
                            Html.FROM_HTML_MODE_LEGACY
                        ).toString()
                    } else body

                cleanedText

            } catch (e: Exception) {
                Log.e("BookDownload", "Failed", e)
                "Text not available: ${e.message}"
            }
        }

    /* ----------------------------
       DOWNLOAD + SAVE
    ----------------------------- */

    suspend fun downloadAndSaveBook(bookDto: BookDto): BookEntity {

        val possibleFormats = listOf(
            "text/plain; charset=utf-8",
            "text/plain; charset=us-ascii",
            "text/plain"
        )

        val textUrl = possibleFormats.firstNotNullOfOrNull { key ->
            bookDto.formats?.get(key)
        }?.replace("http://", "https://")
            ?: throw Exception("No plain text format available")

        val text = downloadBookText(textUrl)

        val entity = BookEntity(
            id = bookDto.id,
            title = bookDto.title,
            author = bookDto.authors.joinToString { it.name },
            genre = bookDto.subjects?.firstOrNull() ?: "Unknown",
            downloads = bookDto.download_count,
            coverUrl = bookDto.formats?.get("image/jpeg")
                ?.replace("http://", "https://"),
            text = text,
            status = BookEntity.STATUS_TO_READ
        )

        dao.insert(entity)

        return entity
    }
}