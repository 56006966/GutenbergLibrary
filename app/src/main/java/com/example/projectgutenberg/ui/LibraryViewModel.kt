package com.example.projectgutenberg.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: BookRepository) : ViewModel() {

    private val _books = MutableStateFlow<List<BookEntity>>(emptyList())
    val books: StateFlow<List<BookEntity>> get() = _books

    init {
        loadBooks()
        fetchGutenbergBooks()
    }

    // Load books from Room
    fun loadBooks() {
        viewModelScope.launch {
            repository.getPopularBooks().collect { list ->
                _books.value = list
            }
        }
    }
    // In LibraryViewModel
    fun saveBookText(book: BookEntity, textUrl: String) = viewModelScope.launch {
        try {
            val text = repository.downloadBookText(textUrl)
            val updated = book.copy(text = text)
            repository.insertBook(updated)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fetch Gutenberg books from API and save as BookEntity
    fun fetchGutenbergBooks(topic: String = "") {
        viewModelScope.launch {
            try {
                val response = repository.getGutenbergBooks(topic)

                val entities = response.results.map { book ->
                    BookEntity(
                        id = book.id,
                        title = book.title,
                        author = book.authors.joinToString { it.name },
                        genre = book.subjects?.firstOrNull() ?: "Unknown",
                        downloads = book.download_count,
                        coverUrl = book.formats?.get("image/jpeg"),
                        text = null,          // plain text will be downloaded later
                        status = "To Read"    // default status
                    )
                }

                repository.insertBooks(entities)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Mark a book as Favorite
    fun markAsFavorite(book: BookEntity) = viewModelScope.launch {
        val updated = book.copy(status = "Favorites")
        repository.insertBook(updated)
    }

    // Mark a book as Read
    fun markAsRead(book: BookEntity) = viewModelScope.launch {
        val updated = book.copy(status = "Read")
        repository.insertBook(updated)
    }
    // Filter books by status
    fun getFavorites() = books.value.filter { it.status == "Favorites" }
    fun getToRead() = books.value.filter { it.status == "To Read" }
    fun getRead() = books.value.filter { it.status == "Read" }
}