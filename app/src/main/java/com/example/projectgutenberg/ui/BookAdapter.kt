package com.example.projectgutenberg.ui

import android.content.res.Configuration
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import coil.load
import com.example.projectgutenberg.R
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.databinding.ItemBookBinding
import com.example.projectgutenberg.util.isNetworkAvailable

internal fun shouldLoadRemoteCover(
    localCover: String?,
    primaryCover: String?,
    networkAvailable: Boolean
): Boolean {
    if (primaryCover.isNullOrBlank()) return networkAvailable
    if (localCover == null && !networkAvailable) return false
    return true
}

class BookAdapter : ListAdapter<BookEntity, BookAdapter.BookViewHolder>(BookDiffCallback()) {

    companion object {
        private const val VIRTUAL_CAROUSEL_LOOPS = 10_000
    }

    enum class PresentationMode {
        STANDARD,
        CAROUSEL
    }

    var onBookClick: ((BookEntity) -> Unit)? = null
    var onFavoriteClick: ((BookEntity) -> Unit)? = null
    var onRemoveClick: ((BookEntity) -> Unit)? = null
    private var darkModeEnabled = false
    private var libraryActionsEnabled = false
    private var presentationMode = PresentationMode.STANDARD
    private var infiniteCarouselEnabled = false

    init {
        setHasStableIds(true)
    }

    fun setDarkMode(enabled: Boolean) {
        if (darkModeEnabled == enabled) return
        darkModeEnabled = enabled
        notifyDataSetChanged()
    }

    fun setLibraryActionsEnabled(enabled: Boolean) {
        if (libraryActionsEnabled == enabled) return
        libraryActionsEnabled = enabled
        notifyDataSetChanged()
    }

    fun setPresentationMode(mode: PresentationMode) {
        if (presentationMode == mode) return
        presentationMode = mode
        notifyDataSetChanged()
    }

    fun setInfiniteCarouselEnabled(enabled: Boolean) {
        if (infiniteCarouselEnabled == enabled) return
        infiniteCarouselEnabled = enabled
        notifyDataSetChanged()
    }

    fun getCarouselAnchorPosition(): Int {
        val size = currentList.size
        if (size <= 1) return 0
        val midpointLoop = VIRTUAL_CAROUSEL_LOOPS / 2
        return midpointLoop * size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(getBookForPosition(position))
    }

    override fun getItemCount(): Int {
        val size = currentList.size
        if (size == 0) return 0
        return if (presentationMode == PresentationMode.CAROUSEL && infiniteCarouselEnabled) {
            if (size == 1) 1 else size * VIRTUAL_CAROUSEL_LOOPS
        } else {
            size
        }
    }

    override fun getItemId(position: Int): Long {
        return getBookForPosition(position).id.toLong()
    }

    private fun getBookForPosition(position: Int): BookEntity {
        val size = currentList.size
        require(size > 0) { "Cannot bind carousel item with empty list" }
        return currentList[position % size]
    }

    inner class BookViewHolder(
        private val binding: ItemBookBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(book: BookEntity) {
            applyPresentationLayout()
            binding.bookTitle.text = book.title
            binding.bookOverlayTitle.text = book.title
            applyCardTheme()
            loadCover(book)
            bindActions(book)
            resetCarouselOverlay()

            binding.root.setOnClickListener {
                onBookClick?.invoke(book)
            }
        }

        private fun applyPresentationLayout() {
            val density = binding.root.resources.displayMetrics.density
            val parentView = binding.root.parent as? View
            val parentWidth = parentView?.width ?: 0
            val parentHeight = parentView?.height ?: 0
            val isLandscape =
                binding.root.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthFactor = if (isLandscape) 0.5f else 0.62f
            val baseCarouselWidth =
                if (parentWidth > 0) parentWidth.toFloat() * widthFactor else 220f * density

            val cardLayoutParams = binding.root.layoutParams ?: ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val coverLayoutParams = binding.bookCover.layoutParams
            val titleLayoutParams = binding.bookTitle.layoutParams

            if (presentationMode == PresentationMode.CAROUSEL) {
                val maxCoverHeight = when {
                    parentHeight > 0 && isLandscape -> parentHeight.toFloat() * 0.58f
                    parentHeight > 0 -> parentHeight.toFloat() * 0.86f
                    else -> Float.MAX_VALUE
                }
                val targetCoverWidth = baseCarouselWidth * 0.9f
                val heightBoundCoverWidth = maxCoverHeight / 1.56f
                val resolvedCoverWidth = minOf(targetCoverWidth, heightBoundCoverWidth).toInt()
                    .coerceAtLeast((150 * density).toInt())
                val resolvedCarouselWidth = (resolvedCoverWidth / 0.9f).toInt()

                cardLayoutParams.width = resolvedCarouselWidth
                coverLayoutParams.width = resolvedCoverWidth
                coverLayoutParams.height = (coverLayoutParams.width * 1.56f).toInt()
                titleLayoutParams.width = coverLayoutParams.width
                binding.bookCoverContainer.layoutParams = binding.bookCoverContainer.layoutParams.apply {
                    width = coverLayoutParams.width
                    height = coverLayoutParams.height
                }
                binding.bookTitle.visibility = View.GONE
                binding.root.radius = 22f * density
                binding.root.cardElevation = 10f * density
                binding.bookCover.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.bookCover.setBackgroundColor(
                    Color.parseColor(if (darkModeEnabled) "#161616" else "#F2EEE7")
                )
            } else {
                cardLayoutParams.width = (132 * density).toInt()
                coverLayoutParams.width = (120 * density).toInt()
                coverLayoutParams.height = (180 * density).toInt()
                titleLayoutParams.width = (120 * density).toInt()
                binding.bookCoverContainer.layoutParams = binding.bookCoverContainer.layoutParams.apply {
                    width = coverLayoutParams.width
                    height = coverLayoutParams.height
                }
                binding.bookTitle.visibility = View.VISIBLE
                binding.bookTitle.textSize = 12f
                binding.root.radius = 16f * density
                binding.root.cardElevation = 6f * density
                binding.bookCover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.bookCover.setBackgroundResource(R.drawable.book_placeholder)
            }

            binding.root.layoutParams = cardLayoutParams
            binding.bookCover.layoutParams = coverLayoutParams
            binding.bookTitle.layoutParams = titleLayoutParams
        }

        private fun applyCardTheme() {
            val cardBackground = Color.parseColor(
                if (darkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
            )
            val titleColor = Color.parseColor(
                if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
            )
            val coverActionColor = Color.WHITE

            binding.root.setCardBackgroundColor(cardBackground)
            binding.bookTitle.setTextColor(titleColor)
            binding.favoriteButton.setColorFilter(coverActionColor)
            binding.removeButton.setColorFilter(coverActionColor)
        }

        private fun bindActions(book: BookEntity) {
            binding.bookActionsRow.visibility =
                if (libraryActionsEnabled) android.view.View.VISIBLE else android.view.View.GONE
            binding.favoriteButton.setImageResource(
                if (book.isFavorite) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )
            binding.favoriteButton.setColorFilter(
                Color.parseColor(
                    if (book.isFavorite) "#C63A4A"
                    else if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
                )
            )
            binding.favoriteButton.setOnClickListener {
                onFavoriteClick?.invoke(book)
            }
            binding.removeButton.setOnClickListener {
                onRemoveClick?.invoke(book)
            }
            binding.favoriteBadge.visibility =
                if (libraryActionsEnabled && book.isFavorite) android.view.View.VISIBLE else android.view.View.GONE
        }

        private fun resetCarouselOverlay() {
            if (presentationMode == PresentationMode.CAROUSEL) return
            binding.bookCoverOverlay.alpha = 0f
            binding.bookHoverInfo.alpha = 0f
        }

        private fun loadCover(book: BookEntity) {
            val localCover = book.coverPath
            val primaryCover = localCover ?: book.coverUrl
            val fallbackCover = "https://www.gutenberg.org/cache/epub/${book.id}/pg${book.id}.cover.medium.jpg"
            val networkAvailable = isNetworkAvailable(binding.root.context)

            if (!shouldLoadRemoteCover(localCover, primaryCover, networkAvailable)) {
                binding.bookCover.load(android.R.drawable.ic_menu_report_image) {
                    crossfade(false)
                    placeholder(android.R.drawable.ic_menu_report_image)
                    error(android.R.drawable.ic_menu_report_image)
                }
                return
            }

            if (primaryCover.isNullOrBlank()) {
                binding.bookCover.load(fallbackCover) {
                    crossfade(false)
                    placeholder(android.R.drawable.ic_menu_report_image)
                    error(android.R.drawable.ic_menu_report_image)
                }
                return
            }

            binding.bookCover.load(primaryCover) {
                crossfade(false)
                placeholder(android.R.drawable.ic_menu_report_image)
                error(android.R.drawable.ic_menu_report_image)
                listener(
                    onError = { _, result ->
                        Log.w("BookAdapter", "Primary cover failed for ${book.id}: ${result.throwable.message}")
                        if (localCover == null && primaryCover != fallbackCover) {
                            binding.bookCover.load(fallbackCover) {
                                crossfade(false)
                                placeholder(android.R.drawable.ic_menu_report_image)
                                error(android.R.drawable.ic_menu_report_image)
                            }
                        }
                    }
                )
            }
        }
    }
}

private class BookDiffCallback : DiffUtil.ItemCallback<BookEntity>() {
    override fun areItemsTheSame(oldItem: BookEntity, newItem: BookEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: BookEntity, newItem: BookEntity): Boolean {
        return oldItem == newItem
    }
}
