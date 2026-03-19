package com.example.projectgutenberg.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.data.local.ShelfCache
import com.example.projectgutenberg.data.repository.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.SocketTimeoutException

class LibraryViewModel(
    private val repository: BookRepository,
    private val shelfCache: ShelfCache
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
        private const val CACHE_MAX_AGE_MS = 30L * 60L * 1000L
    }

    private val _popularBooks = kotlinx.coroutines.flow.MutableStateFlow(shelfCache.getPopularBooks())
    val popularBooks: kotlinx.coroutines.flow.StateFlow<List<BookEntity>> get() = _popularBooks

    private val _newestBooks = kotlinx.coroutines.flow.MutableStateFlow(shelfCache.getNewestBooks())
    val newestBooks: kotlinx.coroutines.flow.StateFlow<List<BookEntity>> get() = _newestBooks

    private val _libraryBooks = kotlinx.coroutines.flow.MutableStateFlow<List<BookEntity>>(emptyList())
    val libraryBooks: kotlinx.coroutines.flow.StateFlow<List<BookEntity>> get() = _libraryBooks

    private val _isLoadingPopular = kotlinx.coroutines.flow.MutableStateFlow(_popularBooks.value.isEmpty())
    val isLoadingPopular: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _isLoadingPopular

    private val _isLoadingNewest = kotlinx.coroutines.flow.MutableStateFlow(_newestBooks.value.isEmpty())
    val isLoadingNewest: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _isLoadingNewest

    private val _popularError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val popularError: kotlinx.coroutines.flow.StateFlow<String?> get() = _popularError

    private val _newestError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val newestError: kotlinx.coroutines.flow.StateFlow<String?> get() = _newestError

    init {
        loadLibraryBooks()
        refreshPopularBooks()
        refreshNewestBooks()
    }

    fun loadLibraryBooks() {
        viewModelScope.launch {
            repository.getLibraryBooks().collect { list ->
                _libraryBooks.value = list.sortedBy { it.title }
            }
        }
    }

    private fun refreshPopularBooks() {
        viewModelScope.launch {
            if (shelfCache.hasPopularBooks() && shelfCache.isPopularCacheFresh(CACHE_MAX_AGE_MS)) {
                _isLoadingPopular.value = false
                _popularError.value = null
                return@launch
            }

            _isLoadingPopular.value = true
            _popularError.value = null
            try {
                val entities = repository.getOfficialPopularBooks(limit = 12)
                _popularBooks.value = entities
                shelfCache.savePopularBooks(entities)
                Log.d(TAG, "Loaded ${entities.size} popular books")
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "Failed to fetch popular books", e)
                _popularError.value = buildRemoteShelfError(
                    exception = e,
                    hasCache = shelfCache.hasPopularBooks(),
                    cachedMessage = "Showing saved most popular books."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch popular books", e)
                _popularError.value = buildRemoteShelfError(
                    exception = e,
                    hasCache = shelfCache.hasPopularBooks(),
                    cachedMessage = "Showing saved most popular books."
                )
            } finally {
                _isLoadingPopular.value = false
            }
        }
    }

    private fun refreshNewestBooks() {
        viewModelScope.launch {
            if (shelfCache.hasNewestBooks() && shelfCache.isNewestCacheFresh(CACHE_MAX_AGE_MS)) {
                _isLoadingNewest.value = false
                _newestError.value = null
                return@launch
            }

            _isLoadingNewest.value = true
            _newestError.value = null
            try {
                val entities = repository.getOfficialNewestBooks(limit = 12)
                _newestBooks.value = entities
                shelfCache.saveNewestBooks(entities)
                Log.d(TAG, "Loaded ${entities.size} newest books")
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "Failed to fetch newest books", e)
                _newestError.value = buildRemoteShelfError(
                    exception = e,
                    hasCache = shelfCache.hasNewestBooks(),
                    cachedMessage = "Showing saved newest releases."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch newest books", e)
                _newestError.value = buildRemoteShelfError(
                    exception = e,
                    hasCache = shelfCache.hasNewestBooks(),
                    cachedMessage = "Showing saved newest releases."
                )
            } finally {
                _isLoadingNewest.value = false
            }
        }
    }

    private fun buildRemoteShelfError(
        exception: Exception,
        hasCache: Boolean,
        cachedMessage: String
    ): String {
        if (hasCache) return cachedMessage

        return when (exception) {
            is HttpException -> {
                if (exception.code() == 503) {
                    "Gutendex is temporarily unavailable."
                } else {
                    "Gutendex request failed (${exception.code()})."
                }
            }
            is SocketTimeoutException -> "Gutendex took too long to respond."
            else -> exception.message ?: "Unable to load books from Gutendex."
        }
    }
}
