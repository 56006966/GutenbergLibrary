package com.phunkypixels.projectgutenberglibrary.ui.transform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phunkypixels.projectgutenberglibrary.data.local.BookEntity
import com.phunkypixels.projectgutenberglibrary.data.repository.BookRepository
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
