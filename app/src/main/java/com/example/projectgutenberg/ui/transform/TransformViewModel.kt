package com.example.projectgutenberg.ui.transform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.data.repository.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TransformViewModel(
    private val repository: BookRepository
) : ViewModel() {

    // Expose books as a StateFlow
    val books: StateFlow<List<BookEntity>> = repository.getPopularBooks()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )
}
