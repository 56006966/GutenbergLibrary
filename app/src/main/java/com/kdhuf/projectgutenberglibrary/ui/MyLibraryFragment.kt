package com.kdhuf.projectgutenberglibrary.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.kdhuf.projectgutenberglibrary.R
import com.kdhuf.projectgutenberglibrary.data.local.BookDatabase
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.local.ShelfCache
import com.kdhuf.projectgutenberglibrary.data.remote.RetrofitInstance
import com.kdhuf.projectgutenberglibrary.data.repository.BookRepository
import com.kdhuf.projectgutenberglibrary.databinding.FragmentMyLibraryBinding
import kotlinx.coroutines.launch

class MyLibraryFragment : Fragment() {

    private sealed interface LibraryFilter {
        data object All : LibraryFilter
        data object Favorites : LibraryFilter
        data object DownloadedNewest : LibraryFilter
        data object DownloadedOldest : LibraryFilter
        data class Genre(val value: String) : LibraryFilter
    }

    private var _binding: FragmentMyLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LibraryViewModel
    private lateinit var uiPreferences: ReaderUiPreferences
    private val libraryAdapter = BookAdapter()
    private val carouselSnapHelper = PagerSnapHelper()
    private var selectedLibraryFilter: LibraryFilter = LibraryFilter.All
    private var filterOptions: List<Pair<LibraryFilter, String>> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        uiPreferences = ReaderUiPreferences(requireContext())
        setupViewModel()
        setupLibraryGrid()
        applyTheme()
        binding.myLibraryThemeButton.setOnClickListener {
            uiPreferences.setDarkModeEnabled(!uiPreferences.isDarkModeEnabled())
            applyTheme()
            updateFilterOptions(viewModel.libraryBooks.value)
        }
        collectBooks()
    }

    private fun setupViewModel() {
        val dao = BookDatabase.getDatabase(requireContext()).bookDao()
        val repository = BookRepository(dao, RetrofitInstance.api)
        val shelfCache = ShelfCache(requireContext())

        viewModel = androidx.lifecycle.ViewModelProvider(
            this,
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

    private fun setupLibraryGrid() {
        val recyclerView = binding.libraryManagerRecyclerView
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = libraryAdapter
        recyclerView.clipToPadding = false
        recyclerView.clipChildren = false
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        val horizontalPeek = (resources.displayMetrics.widthPixels * 0.19f).toInt()
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val verticalInset = (resources.displayMetrics.density * if (isLandscape) 18 else 8).toInt()
        recyclerView.setPadding(horizontalPeek, verticalInset, horizontalPeek, verticalInset)
        if (recyclerView.onFlingListener == null) {
            carouselSnapHelper.attachToRecyclerView(recyclerView)
        }
        recyclerView.addItemDecoration(HorizontalSpaceItemDecoration((resources.displayMetrics.density * 18).toInt()))
        recyclerView.addOnScrollListener(MyLibraryCarouselScrollListener())
        libraryAdapter.setLibraryActionsEnabled(true)
        libraryAdapter.setPresentationMode(BookAdapter.PresentationMode.CAROUSEL)
        libraryAdapter.setInfiniteCarouselEnabled(true)
        recyclerView.doOnLayout {
            snapToCenteredCover()
            applyCarouselTransforms()
        }
        libraryAdapter.onBookClick = { book ->
            val action = MyLibraryFragmentDirections.actionNavMyLibraryScreenToBookWebViewFragment(book.id, book.title)
            findNavController().navigate(action)
        }
        libraryAdapter.onFavoriteClick = { book -> viewModel.toggleFavorite(book) }
        libraryAdapter.onRemoveClick = { book -> viewModel.removeFromLibrary(requireContext(), book) }
    }

    private fun applyTheme() {
        val darkModeEnabled = uiPreferences.isDarkModeEnabled()
        val text = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
        )
        val secondary = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SECONDARY_TEXT else ReaderUiPalette.LIGHT_SECONDARY_TEXT
        )
        val surface = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
        )
        val button = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_BUTTON else ReaderUiPalette.LIGHT_BUTTON
        )

        binding.myLibraryRoot.setBackgroundColor(
            Color.parseColor(
                if (darkModeEnabled) ReaderUiPalette.DARK_BACKGROUND else ReaderUiPalette.LIGHT_BACKGROUND
            )
        )
        binding.myLibraryTitle.setTextColor(text)
        binding.myLibrarySubtitle.setTextColor(secondary)
        binding.myLibraryEmptyText.setTextColor(secondary)
        binding.myLibraryThemeButton.apply {
            setImageResource(if (darkModeEnabled) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon)
            imageTintList = android.content.res.ColorStateList.valueOf(text)
        }
        binding.libraryManagerFilterSpinner.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(surface)
            setStroke(2, button)
        }
        binding.libraryManagerFilterSpinner.setPopupBackgroundDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(surface)
            setStroke(2, button)
        })
        libraryAdapter.setDarkMode(darkModeEnabled)
    }

    private fun collectBooks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.libraryBooks.collect { books ->
                        updateFilterOptions(books)
                        submitFilteredBooks(books)
                    }
                }
            }
        }
    }

    private fun updateFilterOptions(books: List<BookEntity>) {
        filterOptions = buildList {
            add(LibraryFilter.All to getString(R.string.library_filter_all))
            add(LibraryFilter.Favorites to getString(R.string.library_filter_favorites))
            add(LibraryFilter.DownloadedNewest to getString(R.string.library_filter_downloaded_newest))
            add(LibraryFilter.DownloadedOldest to getString(R.string.library_filter_downloaded_oldest))
            books.mapNotNull { it.genre.takeIf(String::isNotBlank) }
                .distinct()
                .sorted()
                .forEach { genre ->
                    add(LibraryFilter.Genre(genre) to getString(R.string.library_filter_genre_prefix, genre))
                }
        }

        val darkModeEnabled = uiPreferences.isDarkModeEnabled()
        val textColor = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
        )
        val surfaceColor = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
        )
        binding.libraryManagerFilterSpinner.adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            filterOptions.map { it.second }
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(textColor)
                    setBackgroundColor(surfaceColor)
                    setPadding(24, paddingTop, 24, paddingBottom)
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(textColor)
                    setBackgroundColor(surfaceColor)
                }
            }
        }
        val selectedIndex = filterOptions.indexOfFirst { it.first == selectedLibraryFilter }.coerceAtLeast(0)
        binding.libraryManagerFilterSpinner.setSelection(selectedIndex, false)
        binding.libraryManagerFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLibraryFilter = filterOptions[position].first
                submitFilteredBooks(viewModel.libraryBooks.value)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun filterBooks(books: List<BookEntity>): List<BookEntity> {
        val filtered = when (val filter = selectedLibraryFilter) {
            LibraryFilter.All -> books.sortedBy { it.title }
            LibraryFilter.Favorites -> books.filter { it.isFavorite }.sortedBy { it.title }
            LibraryFilter.DownloadedNewest -> books.sortedByDescending { it.downloadedAt }
            LibraryFilter.DownloadedOldest -> books.sortedBy { it.downloadedAt.takeIf { value -> value > 0L } ?: Long.MAX_VALUE }
            is LibraryFilter.Genre -> books.filter { it.genre.equals(filter.value, ignoreCase = true) }.sortedBy { it.title }
        }
        return filtered
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun submitFilteredBooks(books: List<BookEntity>) {
        val filtered = filterBooks(books)
        binding.myLibraryEmptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        libraryAdapter.submitList(filtered) {
            if (filtered.isEmpty()) return@submitList
            scrollCarouselToAnchor(filtered.size)
            binding.libraryManagerRecyclerView.post {
                binding.libraryManagerRecyclerView.invalidateItemDecorations()
                snapToCenteredCover()
                applyCarouselTransforms()
            }
        }
    }

    private inner class MyLibraryCarouselScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            applyCarouselTransforms(recyclerView)
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                snapToCenteredCover(recyclerView)
                applyCarouselTransforms(recyclerView)
            }
        }
    }

    private fun scrollCarouselToAnchor(itemCount: Int) {
        val recyclerView = _binding?.libraryManagerRecyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val current = layoutManager.findFirstVisibleItemPosition()
        if (current != RecyclerView.NO_POSITION && current > 0) return

        val alignedAnchor = if (itemCount <= 1) 0 else libraryAdapter.getCarouselAnchorPosition()
        recyclerView.scrollToPosition(alignedAnchor)
    }

    private fun snapToCenteredCover(recyclerView: RecyclerView = binding.libraryManagerRecyclerView) {
        val layoutManager = recyclerView.layoutManager ?: return
        val snapView = carouselSnapHelper.findSnapView(layoutManager) ?: return
        val distance = carouselSnapHelper.calculateDistanceToFinalSnap(layoutManager, snapView) ?: return
        val dx = distance[0]
        val dy = distance[1]
        if (dx != 0 || dy != 0) {
            recyclerView.scrollBy(dx, dy)
        }
    }

    private fun applyCarouselTransforms(recyclerView: RecyclerView = binding.libraryManagerRecyclerView) {
        if (recyclerView.width == 0) return
        val centerX = recyclerView.width / 2f
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            val childCenterX = (child.left + child.right) / 2f
            val distanceFromCenter = kotlin.math.abs(centerX - childCenterX)
            val normalized = (distanceFromCenter / centerX).coerceIn(0f, 1f)
            val isLeft = childCenterX < centerX
            val centerBias = 1f - normalized
            val edgeBias = normalized * normalized
            child.pivotX = if (isLeft) child.width.toFloat() else 0f
            child.pivotY = child.height / 2f
            child.scaleX = 0.88f + (centerBias * 0.12f)
            child.scaleY = 0.8f + (centerBias * 0.2f)
            child.alpha = 0.58f + (centerBias * 0.42f)
            child.rotationY = if (isLeft) -(edgeBias * 36f) else (edgeBias * 36f)
            child.translationX = if (isLeft) edgeBias * 42f else -(edgeBias * 42f)
            child.translationZ = centerBias * 24f
            child.setTag(R.id.tag_carousel_center_bias, centerBias)

            child.findViewById<View>(R.id.bookCoverOverlay)?.alpha =
                (centerBias * 0.72f).coerceIn(0f, 0.72f)
            child.findViewById<View>(R.id.bookHoverInfo)?.alpha =
                (centerBias * 0.95f).coerceIn(0f, 0.95f)
        }
    }
}
