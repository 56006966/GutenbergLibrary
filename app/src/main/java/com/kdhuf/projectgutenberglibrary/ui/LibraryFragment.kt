package com.kdhuf.projectgutenberglibrary.ui

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kdhuf.projectgutenberglibrary.R
import com.kdhuf.projectgutenberglibrary.data.local.BookDatabase
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.local.ShelfCache
import com.kdhuf.projectgutenberglibrary.data.repository.BookRepository
import com.kdhuf.projectgutenberglibrary.data.remote.RetrofitInstance
import com.kdhuf.projectgutenberglibrary.databinding.FragmentLibraryBinding
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LibraryViewModel
    private lateinit var uiPreferences: ReaderUiPreferences
    private val newestAdapter = BookAdapter()
    private val popularAdapter = BookAdapter()
    private val libraryAdapter = BookAdapter()
    private var newestSortMode = LibraryShelfSortMode.RECENT_DESC
    private var popularSortMode = LibraryShelfSortMode.POPULAR_DESC
    private var currentNewestBooks: List<BookEntity> = emptyList()
    private var currentPopularBooks: List<BookEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        uiPreferences = ReaderUiPreferences(requireContext())
        setupRecyclerViews()
        setupViewModel()
        applyLibraryTheme()
        setupSortButtons()
        binding.libraryThemeButton.setOnClickListener {
            uiPreferences.setDarkModeEnabled(!uiPreferences.isDarkModeEnabled())
            applyLibraryTheme()
        }
        collectBooks()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            applyLibraryTheme()
        }
    }

    private fun setupRecyclerViews() {
        setupShelf(binding.newestRecyclerView, newestAdapter)
        setupShelf(binding.popularRecyclerView, popularAdapter)
        setupShelf(binding.libraryRecyclerView, libraryAdapter)
        newestAdapter.setPresentationMode(BookAdapter.PresentationMode.HOME_SHELF)
        popularAdapter.setPresentationMode(BookAdapter.PresentationMode.HOME_SHELF)
        libraryAdapter.setPresentationMode(BookAdapter.PresentationMode.HOME_SHELF)

        val openBook: (BookEntity) -> Unit = { book ->
            val action = LibraryFragmentDirections
                .actionLibraryFragmentToBookWebViewFragment(
                    book.id,
                    BookMetadataFormatter.normalizeTitle(book.title)
                )
            findNavController().navigate(action)
        }

        newestAdapter.onBookClick = openBook
        popularAdapter.onBookClick = openBook
        libraryAdapter.onBookClick = openBook
    }

    private fun setupShelf(recyclerView: RecyclerView, adapter: BookAdapter) {
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = adapter
        recyclerView.clipToPadding = false
        recyclerView.clipChildren = false
        recyclerView.itemAnimator = null
        recyclerView.addItemDecoration(HorizontalSpaceItemDecoration(0))
    }

    private fun setupViewModel() {
        val dao = BookDatabase.getDatabase(requireContext()).bookDao()
        val repository = BookRepository(dao, RetrofitInstance.catalogDataSource)
        val shelfCache = ShelfCache(requireContext())

        viewModel = androidx.lifecycle.ViewModelProvider(
            requireActivity(),
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return LibraryViewModel(repository, shelfCache) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        )[LibraryViewModel::class.java]
    }

    private fun setupSortButtons() {
        binding.librarySortButton.setOnClickListener { anchor ->
            showSortMenu(anchor, SortMenuScope.LIBRARY) { mode -> viewModel.setLibrarySortMode(mode) }
        }
        binding.newestSortButton.setOnClickListener { anchor ->
            showSortMenu(anchor, SortMenuScope.NEWEST) { mode ->
                newestSortMode = mode
                binding.newestSortButton.text = labelForSortMode(mode)
                submitNewestBooks()
            }
        }
        binding.popularSortButton.setOnClickListener { anchor ->
            showSortMenu(anchor, SortMenuScope.POPULAR) { mode ->
                popularSortMode = mode
                binding.popularSortButton.text = labelForSortMode(mode)
                submitPopularBooks()
            }
        }
    }

    private fun showSortMenu(
        anchor: View,
        scope: SortMenuScope,
        onModeSelected: (LibraryShelfSortMode) -> Unit
    ) {
        val popupTheme = if (uiPreferences.isDarkModeEnabled()) {
            R.style.Theme_ProjectGutenberg_PopupOverlay_Dark
        } else {
            R.style.Theme_ProjectGutenberg_PopupOverlay
        }
        PopupMenu(ContextThemeWrapper(requireContext(), popupTheme), anchor).apply {
            inflate(R.menu.library_sort_menu)
            pruneMenuForScope(menu, scope)
            setOnMenuItemClickListener { item ->
                val mode = when (item.itemId) {
                    R.id.sort_title_asc -> LibraryShelfSortMode.TITLE_ASC
                    R.id.sort_title_desc -> LibraryShelfSortMode.TITLE_DESC
                    R.id.sort_author_asc -> LibraryShelfSortMode.AUTHOR_ASC
                    R.id.sort_author_desc -> LibraryShelfSortMode.AUTHOR_DESC
                    R.id.sort_recent_desc -> LibraryShelfSortMode.RECENT_DESC
                    R.id.sort_recent_asc -> LibraryShelfSortMode.RECENT_ASC
                    R.id.sort_popular_desc -> LibraryShelfSortMode.POPULAR_DESC
                    R.id.sort_popular_asc -> LibraryShelfSortMode.POPULAR_ASC
                    else -> return@setOnMenuItemClickListener false
                }
                onModeSelected(mode)
                true
            }
        }.show()
    }

    private fun pruneMenuForScope(menu: android.view.Menu, scope: SortMenuScope) {
        when (scope) {
            SortMenuScope.LIBRARY -> Unit
            SortMenuScope.NEWEST -> {
                menu.removeItem(R.id.sort_popular_desc)
                menu.removeItem(R.id.sort_popular_asc)
            }
            SortMenuScope.POPULAR -> {
                menu.removeItem(R.id.sort_recent_desc)
                menu.removeItem(R.id.sort_recent_asc)
            }
        }
    }

    private fun labelForSortMode(mode: LibraryShelfSortMode): String {
        return when (mode) {
            LibraryShelfSortMode.TITLE_ASC -> getString(R.string.library_sort_title_asc)
            LibraryShelfSortMode.TITLE_DESC -> getString(R.string.library_sort_title_desc)
            LibraryShelfSortMode.AUTHOR_ASC -> getString(R.string.library_sort_author_asc)
            LibraryShelfSortMode.AUTHOR_DESC -> getString(R.string.library_sort_author_desc)
            LibraryShelfSortMode.RECENT_DESC -> getString(R.string.library_sort_recent_desc)
            LibraryShelfSortMode.RECENT_ASC -> getString(R.string.library_sort_recent_asc)
            LibraryShelfSortMode.POPULAR_DESC -> getString(R.string.library_sort_popular_desc)
            LibraryShelfSortMode.POPULAR_ASC -> getString(R.string.library_sort_popular_asc)
        }
    }

    private fun submitNewestBooks() {
        newestAdapter.submitList(LibraryShelfSortLogic.sortBooks(currentNewestBooks, newestSortMode))
    }

    private fun submitPopularBooks() {
        popularAdapter.submitList(LibraryShelfSortLogic.sortBooks(currentPopularBooks, popularSortMode))
    }

    private fun applyLibraryTheme() {
        val darkModeEnabled = uiPreferences.isDarkModeEnabled()
        val text = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
        )
        val secondary = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SECONDARY_TEXT else ReaderUiPalette.LIGHT_SECONDARY_TEXT
        )
        val buttonBackground = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_BUTTON else ReaderUiPalette.LIGHT_BUTTON
        )

        binding.libraryRoot.setBackgroundResource(
            if (darkModeEnabled) R.drawable.wood_library_background_dark else R.drawable.wood_library_background
        )
        binding.libraryContent.setBackgroundColor(Color.TRANSPARENT)
        binding.libraryRecyclerView.setBackgroundResource(
            if (darkModeEnabled) R.drawable.shelf_row_background_dark else R.drawable.shelf_row_background
        )
        binding.newestRecyclerView.setBackgroundResource(
            if (darkModeEnabled) R.drawable.shelf_row_background_dark else R.drawable.shelf_row_background
        )
        binding.popularRecyclerView.setBackgroundResource(
            if (darkModeEnabled) R.drawable.shelf_row_background_dark else R.drawable.shelf_row_background
        )

        binding.libraryShelfTitle.setTextColor(text)
        binding.newestShelfTitle.setTextColor(text)
        binding.popularShelfTitle.setTextColor(text)
        binding.libraryEmptyText.setTextColor(secondary)
        binding.newestLoadingText.setTextColor(secondary)
        binding.newestEmptyText.setTextColor(secondary)
        binding.popularLoadingText.setTextColor(secondary)
        binding.popularEmptyText.setTextColor(secondary)

        newestAdapter.setDarkMode(darkModeEnabled)
        popularAdapter.setDarkMode(darkModeEnabled)
        libraryAdapter.setDarkMode(darkModeEnabled)
        listOf(binding.librarySortButton, binding.newestSortButton, binding.popularSortButton).forEach { button ->
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonBackground)
            button.strokeColor = android.content.res.ColorStateList.valueOf(secondary)
            button.setTextColor(text)
            button.iconTint = android.content.res.ColorStateList.valueOf(text)
        }
        binding.libraryThemeButton.apply {
            setImageResource(if (darkModeEnabled) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon)
            imageTintList = android.content.res.ColorStateList.valueOf(text)
        }
    }

    private fun collectBooks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.newestBooks.collect { books ->
                        currentNewestBooks = books
                        submitNewestBooks()
                        binding.newestEmptyText.visibility =
                            if (books.isEmpty() && !viewModel.isLoadingNewest.value) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.popularBooks.collect { books ->
                        currentPopularBooks = books
                        submitPopularBooks()
                        binding.popularEmptyText.visibility =
                            if (books.isEmpty() && !viewModel.isLoadingPopular.value) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.libraryBooks.collect { books ->
                        libraryAdapter.submitList(books)
                        binding.libraryEmptyText.visibility =
                            if (books.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.librarySortMode.collect { sortMode ->
                        binding.librarySortButton.text = labelForSortMode(sortMode)
                    }
                }
                launch {
                    viewModel.isLoadingNewest.collect { isLoading ->
                        binding.newestLoadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
                        if (!isLoading) {
                            binding.newestEmptyText.visibility =
                                if (viewModel.newestBooks.value.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
                launch {
                    viewModel.isLoadingPopular.collect { isLoading ->
                        binding.popularLoadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
                        if (!isLoading) {
                            binding.popularEmptyText.visibility =
                                if (viewModel.popularBooks.value.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
                launch {
                    viewModel.newestError.collect { error ->
                        binding.newestEmptyText.text =
                            error ?: getString(com.kdhuf.projectgutenberglibrary.R.string.remote_shelf_empty)
                    }
                }
                launch {
                    viewModel.popularError.collect { error ->
                        binding.popularEmptyText.text =
                            error ?: getString(com.kdhuf.projectgutenberglibrary.R.string.remote_shelf_empty)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private enum class SortMenuScope {
    LIBRARY,
    NEWEST,
    POPULAR
}

class HorizontalSpaceItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.right = space
    }
}
