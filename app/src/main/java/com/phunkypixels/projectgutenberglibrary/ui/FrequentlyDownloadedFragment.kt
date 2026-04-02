package com.phunkypixels.projectgutenberglibrary.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.phunkypixels.projectgutenberglibrary.R
import com.phunkypixels.projectgutenberglibrary.data.local.BookDatabase
import com.phunkypixels.projectgutenberglibrary.data.local.BookEntity
import com.phunkypixels.projectgutenberglibrary.data.local.ShelfCachePolicy
import com.phunkypixels.projectgutenberglibrary.data.local.ShelfCache
import com.phunkypixels.projectgutenberglibrary.data.remote.RetrofitInstance
import com.phunkypixels.projectgutenberglibrary.data.repository.BookRepository
import com.phunkypixels.projectgutenberglibrary.databinding.FragmentFrequentlyDownloadedBinding
import kotlinx.coroutines.launch

class FrequentlyDownloadedFragment : Fragment() {

    private var _binding: FragmentFrequentlyDownloadedBinding? = null
    private val binding get() = _binding!!

    private val booksAdapter = BookAdapter()
    private lateinit var repository: BookRepository
    private lateinit var shelfCache: ShelfCache
    private lateinit var uiPreferences: ReaderUiPreferences
    private lateinit var viewModel: FrequentlyDownloadedViewModel
    private lateinit var layoutManager: GridLayoutManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFrequentlyDownloadedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        uiPreferences = ReaderUiPreferences(requireContext())
        viewModel = ViewModelProvider(this)[FrequentlyDownloadedViewModel::class.java]
        shelfCache = ShelfCache(requireContext())
        repository = BookRepository(
            BookDatabase.getDatabase(requireContext()).bookDao(),
            RetrofitInstance.catalogDataSource
        )
        setupRecyclerView()
        applyTheme()
        if (viewModel.isInitialLoadComplete && viewModel.loadedBooks.isNotEmpty()) {
            showLoadedState()
        } else if (shelfCache.hasTopDownloadedBooks()) {
            viewModel.loadedBooks.clear()
            viewModel.loadedBooks += shelfCache.getTopDownloadedBooks()
            viewModel.nextPopularPageUrl = null
            viewModel.isInitialLoadComplete = true
            showLoadedState()
            if (!shelfCache.isTopDownloadedCacheFresh(ShelfCachePolicy.TOP_DOWNLOADED_MAX_AGE_MS)) {
                loadTopDownloads()
            }
        } else {
            loadTopDownloads()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            applyTheme()
        }
    }

    private fun setupRecyclerView() {
        layoutManager = GridLayoutManager(requireContext(), 3)
        binding.frequentlyDownloadedRecyclerView.layoutManager = layoutManager
        binding.frequentlyDownloadedRecyclerView.adapter = booksAdapter
        binding.frequentlyDownloadedRecyclerView.addItemDecoration(GridSpaceItemDecoration(12))
        binding.frequentlyDownloadedRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (FrequentlyDownloadedPaging.shouldLoadMore(
                        dy = dy,
                        isLoadingMore = viewModel.isLoadingMore,
                        lastVisible = lastVisible,
                        itemCount = booksAdapter.itemCount
                    )
                ) {
                    loadMoreDownloads()
                }
            }
        })
        booksAdapter.onBookClick = { book ->
            val action = FrequentlyDownloadedFragmentDirections
                .actionNavFrequentlyDownloadedScreenToBookWebViewFragment(book.id, book.title)
            findNavController().navigate(action)
        }
    }

    private fun loadTopDownloads() {
        binding.frequentlyDownloadedLoadingText.visibility = View.VISIBLE
        binding.frequentlyDownloadedEmptyText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.getPopularBooksPage() }
                .onSuccess { books ->
                    val shelfBooks = books.results
                        .map(repository::mapRemoteBookToShelf)
                        .take(FrequentlyDownloadedPaging.InitialTopDownloadLimit)
                    Log.d("FrequentlyDownloaded", "Loaded ${shelfBooks.size} books for frequently downloaded")
                    binding.frequentlyDownloadedLoadingText.visibility = View.GONE
                    viewModel.loadedBooks.clear()
                    viewModel.loadedBooks += shelfBooks
                    shelfCache.saveTopDownloadedBooks(shelfBooks)
                    viewModel.nextPopularPageUrl = books.next
                    viewModel.isInitialLoadComplete = true
                    showLoadedState()
                }
                .onFailure { error ->
                    Log.e("FrequentlyDownloaded", "Failed to load frequently downloaded books", error)
                    binding.frequentlyDownloadedLoadingText.visibility = View.GONE
                    binding.frequentlyDownloadedEmptyText.visibility = View.VISIBLE
                    binding.frequentlyDownloadedEmptyText.text =
                        getString(R.string.frequently_downloaded_error)
                }
        }
    }

    private fun loadMoreDownloads() {
        val pageUrl = viewModel.nextPopularPageUrl ?: return
        viewModel.isLoadingMore = true
        updatePagingLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repository.getPopularBooksPage(pageUrl) }
                .onSuccess { response ->
                    val newBooks = response.results
                        .map(repository::mapRemoteBookToShelf)
                        .filterNot { incoming -> viewModel.loadedBooks.any { existing -> existing.id == incoming.id } }
                    val shouldAdvanceAgain = newBooks.isEmpty() && !response.next.isNullOrBlank()

                    if (newBooks.isNotEmpty()) {
                        viewModel.loadedBooks += newBooks
                        booksAdapter.submitList(viewModel.loadedBooks.toList())
                        Log.d(
                            "FrequentlyDownloaded",
                            "Appended ${newBooks.size} books, total now ${viewModel.loadedBooks.size}"
                        )
                    } else {
                        Log.d("FrequentlyDownloaded", "Popular page produced only duplicates; advancing to next page")
                    }

                    viewModel.nextPopularPageUrl = response.next
                    viewModel.isLoadingMore = false
                    updatePagingLoading(false)
                    if (shouldAdvanceAgain) loadMoreDownloads()
                }
                .onFailure { error ->
                    Log.e("FrequentlyDownloaded", "Failed to load more frequently downloaded books", error)
                    viewModel.isLoadingMore = false
                    updatePagingLoading(false)
                }
        }
    }

    private fun showLoadedState() {
        binding.frequentlyDownloadedLoadingText.visibility = View.GONE
        binding.frequentlyDownloadedEmptyText.visibility =
            if (viewModel.loadedBooks.isEmpty()) View.VISIBLE else View.GONE
        booksAdapter.submitList(viewModel.loadedBooks.toList()) {
            restoreScrollPosition()
        }
        updatePagingLoading(viewModel.isLoadingMore)
    }

    private fun updatePagingLoading(isVisible: Boolean) {
        binding.frequentlyDownloadedPagingLoading.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun restoreScrollPosition() {
        if (viewModel.scrollPosition <= 0 && viewModel.scrollOffset == 0) return
        layoutManager.scrollToPositionWithOffset(viewModel.scrollPosition, viewModel.scrollOffset)
    }

    private fun applyTheme() {
        val darkModeEnabled = uiPreferences.isDarkModeEnabled()
        val background = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_BACKGROUND else ReaderUiPalette.LIGHT_BACKGROUND
        )
        val text = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
        )
        val secondary = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SECONDARY_TEXT else ReaderUiPalette.LIGHT_SECONDARY_TEXT
        )

        binding.frequentlyDownloadedRoot.setBackgroundColor(background)
        binding.frequentlyDownloadedTitle.setTextColor(text)
        binding.frequentlyDownloadedSubtitle.setTextColor(secondary)
        binding.frequentlyDownloadedLoadingText.setTextColor(secondary)
        binding.frequentlyDownloadedEmptyText.setTextColor(secondary)
        binding.frequentlyDownloadedPagingText.setTextColor(secondary)
        booksAdapter.setDarkMode(darkModeEnabled)
    }

    override fun onPause() {
        super.onPause()
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        if (firstVisible != RecyclerView.NO_POSITION) {
            viewModel.scrollPosition = firstVisible
            viewModel.scrollOffset = layoutManager.findViewByPosition(firstVisible)?.top ?: 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class GridSpaceItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.right = space
        outRect.bottom = space
    }
}
