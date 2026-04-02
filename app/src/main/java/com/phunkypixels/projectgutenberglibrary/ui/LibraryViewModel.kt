package com.phunkypixels.projectgutenberglibrary.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phunkypixels.projectgutenberglibrary.data.local.BookEntity
import com.phunkypixels.projectgutenberglibrary.data.local.ShelfCacheStore
import com.phunkypixels.projectgutenberglibrary.data.repository.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LibraryViewModel(
    private val repository: BookRepository,
    private val shelfCache: ShelfCacheStore,
    private val networkChecker: () -> Boolean = {
        com.phunkypixels.projectgutenberglibrary.util.isNetworkAvailable(shelfCache.context())
    }
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

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            repository.updateFavorite(book.id, !book.isFavorite)
        }
    }

    fun removeFromLibrary(book: BookEntity) {
        viewModelScope.launch {
            repository.removeLocalBook(book)
        }
    }

    private fun refreshPopularBooks() {
        viewModelScope.launch {
            if (shelfCache.hasPopularBooks() && shelfCache.isPopularCacheFresh(CACHE_MAX_AGE_MS)) {
                _isLoadingPopular.value = false
                _popularError.value = null
                return@launch
            }
            if (!networkChecker()) {
                _isLoadingPopular.value = false
                _popularError.value = if (shelfCache.hasPopularBooks()) {
                    "Offline. Showing saved most popular books."
                } else {
                    "Offline. Connect to the internet to load most popular books."
                }
                return@launch
            }

            _isLoadingPopular.value = true
            _popularError.value = null
            try {
                val entities = repository.getOfficialPopularBooks(limit = 12)
                _popularBooks.value = entities
                shelfCache.savePopularBooks(entities)
                debugLog(TAG) { "Loaded ${entities.size} popular books" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Popular books unavailable while offline", e)
                _popularError.value = buildRemoteShelfError(
                    exception = e,
                    hasCache = shelfCache.hasPopularBooks(),
                    cachedMessage = "Offline. Showing saved most popular books."
                )
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
            if (!networkChecker()) {
                _isLoadingNewest.value = false
                _newestError.value = if (shelfCache.hasNewestBooks()) {
                    "Offline. Showing saved newest releases."
                } else {
                    "Offline. Connect to the internet to load newest releases."
                }
                return@launch
            }

            _isLoadingNewest.value = true
            _newestError.value = null
            try {
                val entities = repository.getOfficialNewestBooks(limit = 12)
                _newestBooks.value = entities
                shelfCache.saveNewestBooks(entities)
                debugLog(TAG) { "Loaded ${entities.size} newest books" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Newest books unavailable while offline", e)
                _newestError.value = buildRemoteShelfError(
                    exception = e,
                    hasCache = shelfCache.hasNewestBooks(),
                    cachedMessage = "Offline. Showing saved newest releases."
                )
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
                    "The catalog service is temporarily unavailable."
                } else {
                    "Catalog request failed (${exception.code()})."
                }
            }
            is SocketTimeoutException -> "The catalog service took too long to respond."
            is UnknownHostException -> "Offline. Connect to the internet to load books."
            else -> exception.message ?: "Unable to load books from the catalog service."
        }
    }
}
