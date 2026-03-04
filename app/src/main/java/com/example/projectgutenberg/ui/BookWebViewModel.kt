package com.example.projectgutenberg.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// UI state for the Book WebView
sealed class BookUiState {
    object Loading : BookUiState()
    data class Success(val book: BookEntity) : BookUiState()
    data class Error(val message: String) : BookUiState()
}

class BookWebViewViewModel(private val repository: BookRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<BookUiState>(BookUiState.Loading)
    val uiState: StateFlow<BookUiState> get() = _uiState

    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            _uiState.value = BookUiState.Loading
            try {
                // Fetch book details from API
                val book = repository.getBookDetails(bookId)

                // Convert API book to BookEntity for UI
                val bookEntity = BookEntity(
                    id = book.id,
                    title = book.title,
                    author = book.authors.joinToString { it.name },
                    genre = book.subjects?.firstOrNull() ?: "Unknown",
                    downloads = book.download_count,
                    coverUrl = book.formats?.get("image/jpeg"),
                    text = null, // text can be loaded later
                    status = "To Read"
                )

                _uiState.value = BookUiState.Success(bookEntity)

            } catch (e: Exception) {
                _uiState.value = BookUiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }
}