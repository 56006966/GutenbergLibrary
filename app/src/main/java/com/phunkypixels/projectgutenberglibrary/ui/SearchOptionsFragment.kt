package com.phunkypixels.projectgutenberglibrary.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.phunkypixels.projectgutenberglibrary.R
import com.phunkypixels.projectgutenberglibrary.data.local.BookDatabase
import com.phunkypixels.projectgutenberglibrary.data.local.BookEntity
import com.phunkypixels.projectgutenberglibrary.data.remote.BookDto
import com.phunkypixels.projectgutenberglibrary.data.remote.GutenbergMirror
import com.phunkypixels.projectgutenberglibrary.data.remote.RetrofitInstance
import com.phunkypixels.projectgutenberglibrary.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchOptionsFragment : Fragment(R.layout.fragment_search_options) {

    private val searchAdapter = BookAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val queryInput = view.findViewById<EditText>(R.id.queryInput)
        val authorInput = view.findViewById<EditText>(R.id.authorInput)
        val titleInput = view.findViewById<EditText>(R.id.titleInput)
        val subjectInput = view.findViewById<EditText>(R.id.subjectInput)
        val searchButton = view.findViewById<Button>(R.id.searchButton)
        val progress = view.findViewById<ProgressBar>(R.id.searchProgress)
        val statusText = view.findViewById<TextView>(R.id.searchStatusText)
        val titleText = view.findViewById<TextView>(R.id.searchTitleText)
        val subtitleText = view.findViewById<TextView>(R.id.searchSubtitleText)
        val themeButton = view.findViewById<ImageButton>(R.id.searchThemeButton)
        val scrollView = view.findViewById<ScrollView>(R.id.searchScrollView)
        val resultsView = view.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)
        val searchCard = view.findViewById<MaterialCardView>(R.id.searchCard)

        resultsView.layoutManager = GridLayoutManager(requireContext(), calculateAdaptiveSpanCount())
        resultsView.adapter = searchAdapter
        searchAdapter.onBookClick = { book ->
            findNavController().navigate(
                R.id.bookWebViewFragment,
                bundleOf("bookId" to book.id, "bookTitle" to book.title)
            )
        }

        queryInput.setText(arguments?.getString(ARG_INITIAL_QUERY).orEmpty())
        applyTheme(
            queryInput,
            authorInput,
            titleInput,
            subjectInput,
            searchButton,
            statusText,
            titleText,
            subtitleText,
            scrollView,
            searchCard
        )
        themeButton.setOnClickListener {
            val prefs = ReaderUiPreferences(requireContext())
            prefs.setDarkModeEnabled(!prefs.isDarkModeEnabled())
            applyTheme(
                queryInput,
                authorInput,
                titleInput,
                subjectInput,
                searchButton,
                statusText,
                titleText,
                subtitleText,
                scrollView,
                searchCard
            )
        }

        searchButton.setOnClickListener {
            val query = queryInput.text?.toString().orEmpty()
            val author = authorInput.text?.toString().orEmpty()
            val title = titleInput.text?.toString().orEmpty()
            val subject = subjectInput.text?.toString().orEmpty()

            dismissKeyboard(view)
            view.clearFocus()

            progress.visibility = View.VISIBLE
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.search_loading_results)
            resultsView.visibility = View.GONE

            lifecycleScope.launch {
                val repository = BookRepository(
                    BookDatabase.getDatabase(requireContext()).bookDao(),
                    RetrofitInstance.catalogDataSource
                )
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        repository.getGutenbergBooks(
                            search = listOf(query, author, title).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null },
                            topic = subject.ifBlank { null }
                        ).results
                    }
                }

                progress.visibility = View.GONE
                statusText.visibility = View.VISIBLE

                result.onSuccess { books ->
                    val filteredBooks = books.filter { dto ->
                        dto.matchesAuthor(author) &&
                            dto.matchesTitle(title) &&
                            dto.matchesSubject(subject)
                    }.map { dto -> dto.toSearchEntity() }

                    searchAdapter.submitList(filteredBooks)
                    resultsView.visibility = if (filteredBooks.isEmpty()) View.GONE else View.VISIBLE
                    statusText.text = if (filteredBooks.isEmpty()) {
                        getString(R.string.search_empty_results)
                    } else {
                        "${filteredBooks.size} result(s)"
                    }
                    if (filteredBooks.isNotEmpty()) {
                        scrollView.post {
                            scrollView.smoothScrollTo(0, resultsView.top)
                        }
                    }
                }.onFailure { error ->
                    resultsView.visibility = View.GONE
                    statusText.text = error.message ?: getString(R.string.remote_shelf_error)
                }
            }
        }
    }

    private fun BookDto.matchesAuthor(author: String): Boolean {
        if (author.isBlank()) return true
        return authors.any { it.name.contains(author, ignoreCase = true) }
    }

    private fun BookDto.matchesTitle(title: String): Boolean {
        if (title.isBlank()) return true
        return this.title.contains(title, ignoreCase = true)
    }

    private fun BookDto.matchesSubject(subject: String): Boolean {
        if (subject.isBlank()) return true
        return subjects?.any { it.contains(subject, ignoreCase = true) } == true
    }

    private fun dismissKeyboard(view: View) {
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun applyTheme(
        queryInput: EditText,
        authorInput: EditText,
        titleInput: EditText,
        subjectInput: EditText,
        searchButton: Button,
        statusText: TextView,
        titleText: TextView,
        subtitleText: TextView,
        scrollView: ScrollView,
        searchCard: MaterialCardView
    ) {
        val darkModeEnabled = ReaderUiPreferences(requireContext()).isDarkModeEnabled()
        val background = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_BACKGROUND else ReaderUiPalette.LIGHT_BACKGROUND
        )
        val surface = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
        )
        val text = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
        )
        val secondary = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SECONDARY_TEXT else ReaderUiPalette.LIGHT_SECONDARY_TEXT
        )
        val button = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_BUTTON else ReaderUiPalette.LIGHT_BUTTON
        )

        scrollView.setBackgroundColor(background)
        searchCard.setCardBackgroundColor(surface)
        statusText.setTextColor(secondary)
        titleText.setTextColor(text)
        subtitleText.setTextColor(secondary)
        searchAdapter.setDarkMode(darkModeEnabled)

        listOf(queryInput, authorInput, titleInput, subjectInput).forEach { input ->
            input.setTextColor(text)
            input.setHintTextColor(secondary)
            input.backgroundTintList = ColorStateList.valueOf(button)
        }
        searchButton.backgroundTintList = ColorStateList.valueOf(button)
        searchButton.setTextColor(text)
        view?.findViewById<ImageButton>(R.id.searchThemeButton)?.apply {
            setImageResource(if (darkModeEnabled) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon)
            imageTintList = ColorStateList.valueOf(text)
        }
    }

    private fun calculateAdaptiveSpanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return when {
            widthDp >= 960 -> 4
            widthDp >= 600 -> 3
            else -> 2
        }
    }

    private fun BookDto.toSearchEntity(): BookEntity {
        return BookEntity(
            id = id,
            title = title,
            author = authors.joinToString { it.name },
            genre = subjects?.firstOrNull() ?: "Unknown",
            downloads = download_count,
            coverUrl = GutenbergMirror.resolve(formats?.get("image/jpeg")) ?: GutenbergMirror.coverUrl(id),
            coverPath = null,
            text = null,
            epubPath = null,
            lastPageIndex = 0,
            downloadedAt = 0L,
            isFavorite = false,
            status = BookEntity.STATUS_TO_READ
        )
    }

    companion object {
        private const val ARG_INITIAL_QUERY = "initial_query"

        fun args(initialQuery: String) = bundleOf(ARG_INITIAL_QUERY to initialQuery)
    }
}
