package com.kdhuf.projectgutenberglibrary.ui

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import coil.load
import com.kdhuf.projectgutenberglibrary.R
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergMirror
import com.kdhuf.projectgutenberglibrary.databinding.ItemBookBinding
import com.kdhuf.projectgutenberglibrary.util.isNetworkAvailable

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
        private const val HOME_SHELF_MAX_HEIGHT_DP = 188
    }

    enum class PresentationMode {
        STANDARD,
        CAROUSEL,
        HOME_SHELF
    }

    var onBookClick: ((BookEntity) -> Unit)? = null
    var onFavoriteClick: ((BookEntity) -> Unit)? = null
    var onRemoveClick: ((BookEntity) -> Unit)? = null
    private var darkModeEnabled = false
    private var libraryActionsEnabled = false
    private var presentationMode = PresentationMode.STANDARD
    private var infiniteCarouselEnabled = false
    private val homeShelfSpineColors = mutableMapOf<Int, Int>()
    private val pendingPaletteBookIds = mutableSetOf<Int>()
    private var homeShelfState = HomeShelfInteractionState()

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
            binding.bookTitle.text = book.title
            binding.bookOverlayTitle.text = book.title
            binding.bookSpineTitle.text = book.title
            applyPresentationLayout(book)
            applyCardTheme(book)
            loadCover(book)
            bindActions(book)
            resetInteractiveOverlay()
            bindHomeShelfInteraction(book)

            binding.root.setOnClickListener {
                if (presentationMode == PresentationMode.HOME_SHELF) {
                    handleHomeShelfTap(book)
                } else {
                    onBookClick?.invoke(book)
                }
            }
        }

        private fun applyPresentationLayout(book: BookEntity) {
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
            val cardMarginLayoutParams =
                cardLayoutParams as? ViewGroup.MarginLayoutParams
                    ?: ViewGroup.MarginLayoutParams(cardLayoutParams)
            val coverLayoutParams = binding.bookCover.layoutParams
            val titleLayoutParams = binding.bookTitle.layoutParams
            val coverContainerLayoutParams = binding.bookCoverContainer.layoutParams
            val expanded = homeShelfState.expandedBookId == book.id

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

                cardMarginLayoutParams.width = resolvedCarouselWidth
                cardMarginLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                cardMarginLayoutParams.topMargin = 0
                cardMarginLayoutParams.bottomMargin = 0
                coverLayoutParams.width = resolvedCoverWidth
                coverLayoutParams.height = (coverLayoutParams.width * 1.56f).toInt()
                titleLayoutParams.width = coverLayoutParams.width
                coverContainerLayoutParams.width = coverLayoutParams.width
                coverContainerLayoutParams.height = coverLayoutParams.height
                binding.bookTitle.visibility = View.GONE
                binding.bookSpine.visibility = View.GONE
                binding.root.radius = 22f * density
                binding.root.cardElevation = 10f * density
                binding.bookCover.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                binding.bookCover.setBackgroundColor(
                    Color.parseColor(if (darkModeEnabled) "#161616" else "#F2EEE7")
                )
            } else if (presentationMode == PresentationMode.HOME_SHELF) {
                val spineMetrics = homeShelfMetrics(bookTitle = book.title, density = density)
                val spineWidth = spineMetrics.widthPx
                val coverWidth = (118 * density).toInt()
                val coverHeight = spineMetrics.heightPx
                val maxHeightPx = (HOME_SHELF_MAX_HEIGHT_DP * density).toInt()

                cardMarginLayoutParams.width = spineWidth
                cardMarginLayoutParams.height = coverHeight
                cardMarginLayoutParams.topMargin = (maxHeightPx - coverHeight).coerceAtLeast(0)
                cardMarginLayoutParams.bottomMargin = 0
                coverLayoutParams.width = coverWidth
                coverLayoutParams.height = coverHeight
                titleLayoutParams.width = spineWidth
                coverContainerLayoutParams.width = spineWidth
                coverContainerLayoutParams.height = coverHeight
                updateLayoutSize(binding.bookSpine, spineWidth, coverHeight)
                updateLayoutSize(
                    binding.bookSpineTitle,
                    (spineWidth - (8 * density).toInt()).coerceAtLeast((40 * density).toInt()),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                binding.bookTitle.visibility = View.GONE
                binding.bookSpine.visibility = View.VISIBLE
                binding.root.radius = 0f
                binding.root.cardElevation = 0f
                binding.root.strokeWidth = 0
                binding.root.setCardBackgroundColor(Color.TRANSPARENT)
                binding.bookCover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.bookCover.setBackgroundResource(R.drawable.book_placeholder)
                binding.root.translationZ = if (expanded) 24f * density else 0f
            } else {
                cardMarginLayoutParams.width = (132 * density).toInt()
                cardMarginLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                cardMarginLayoutParams.topMargin = 0
                cardMarginLayoutParams.bottomMargin = 0
                coverLayoutParams.width = (120 * density).toInt()
                coverLayoutParams.height = (180 * density).toInt()
                titleLayoutParams.width = (120 * density).toInt()
                coverContainerLayoutParams.width = coverLayoutParams.width
                coverContainerLayoutParams.height = coverLayoutParams.height
                binding.bookTitle.visibility = View.VISIBLE
                binding.bookSpine.visibility = View.GONE
                binding.bookTitle.textSize = 12f
                binding.root.radius = 16f * density
                binding.root.cardElevation = 6f * density
                binding.root.strokeWidth = 0
                binding.bookCover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.bookCover.setBackgroundResource(R.drawable.book_placeholder)
            }

            updateLayoutParams(binding.root, cardMarginLayoutParams)
            updateLayoutParams(binding.bookCover, coverLayoutParams)
            updateLayoutParams(binding.bookCoverContainer, coverContainerLayoutParams)
            updateLayoutParams(binding.bookTitle, titleLayoutParams)
        }

        private fun applyCardTheme(book: BookEntity) {
            val cardBackground = Color.parseColor(
                if (darkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
            )
            val titleColor = Color.parseColor(
                if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
            )
            val coverActionColor = Color.WHITE
            val spineColor = homeShelfSpineColors[book.id]
                ?: defaultHomeShelfSpineColor(book)

            if (presentationMode != PresentationMode.HOME_SHELF) {
                binding.root.setCardBackgroundColor(cardBackground)
            }
            binding.bookTitle.setTextColor(titleColor)
            binding.bookSpineTitle.setTextColor(bestTextColorForBackground(spineColor))
            (binding.bookSpine.background.mutate() as? GradientDrawable)?.apply {
                colors = intArrayOf(lightenColor(spineColor, 0.18f), spineColor, darkenColor(spineColor, 0.18f))
            }
            binding.favoriteButton.setColorFilter(coverActionColor)
            binding.removeButton.setColorFilter(coverActionColor)
        }

        private fun bindActions(book: BookEntity) {
            binding.bookActionsRow.visibility =
                if (libraryActionsEnabled) android.view.View.VISIBLE else android.view.View.GONE
            binding.favoriteButton.setImageResource(
                if (book.isFavorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            binding.favoriteButton.setColorFilter(
                Color.parseColor(
                    if (book.isFavorite) "#E3B341"
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
                if (!libraryActionsEnabled && book.isFavorite) android.view.View.VISIBLE else android.view.View.GONE
        }

        private fun resetInteractiveOverlay() {
            if (presentationMode == PresentationMode.CAROUSEL) return
            binding.bookCoverOverlay.alpha = 0f
            binding.bookHoverInfo.alpha = 0f
        }

        private fun bindHomeShelfInteraction(book: BookEntity) {
            if (presentationMode != PresentationMode.HOME_SHELF) {
                binding.root.setOnHoverListener(null)
                binding.root.setOnFocusChangeListener(null)
                binding.root.setOnTouchListener(null)
                binding.root.scaleX = 1f
                binding.root.scaleY = 1f
                binding.root.translationY = 0f
                binding.root.translationX = 0f
                binding.root.rotation = 0f
                binding.root.rotationX = 0f
                binding.root.rotationY = 0f
                binding.bookCover.alpha = 1f
                binding.bookCover.translationX = 0f
                binding.bookSpine.alpha = 0f
                binding.bookSpine.translationX = 0f
                binding.bookPageEdge.alpha = 0f
                binding.bookBackCover.alpha = 0f
                resetHomeShelfPages()
                return
            }

            applyHomeShelfState(
                expanded = homeShelfState.expandedBookId == book.id,
                highlight = false,
                animate = false
            )

            binding.root.setOnHoverListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER -> applyHomeShelfState(
                        expanded = homeShelfState.expandedBookId == book.id,
                        highlight = true,
                        animate = true
                    )
                    MotionEvent.ACTION_HOVER_EXIT -> applyHomeShelfState(
                        expanded = homeShelfState.expandedBookId == book.id,
                        highlight = false,
                        animate = true
                    )
                }
                false
            }
            binding.root.setOnFocusChangeListener { _, hasFocus ->
                applyHomeShelfState(
                    expanded = homeShelfState.expandedBookId == book.id,
                    highlight = hasFocus,
                    animate = true
                )
            }
            binding.root.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> applyHomeShelfState(
                        expanded = homeShelfState.expandedBookId == book.id,
                        highlight = true,
                        animate = true
                    )
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> applyHomeShelfState(
                        expanded = homeShelfState.expandedBookId == book.id,
                        highlight = false,
                        animate = true
                    )
                }
                false
            }
        }

        private fun applyHomeShelfState(expanded: Boolean, highlight: Boolean, animate: Boolean) {
            val duration = if (animate) 220L else 0L
            val density = binding.root.resources.displayMetrics.density
            val lift = when {
                expanded -> -(10f * density)
                highlight -> -(2f * density)
                else -> 0f
            }
            val scale = when {
                expanded -> 1.14f
                highlight -> 1.015f
                else -> 1f
            }
            val coverShift = if (expanded) expandedCoverCenterShift() else 0f

            binding.root.animate().cancel()
            binding.bookCover.animate().cancel()
            binding.bookSpine.animate().cancel()
            binding.bookPageEdge.animate().cancel()
            binding.bookBackCover.animate().cancel()
            homeShelfPageViews().forEach { it.animate().cancel() }

            binding.root.animate()
                .translationY(lift)
                .translationX(0f)
                .scaleX(scale)
                .scaleY(scale)
                .rotation(0f)
                .setDuration(duration)
                .start()
            binding.bookCover.animate()
                .alpha(if (expanded) 1f else 0f)
                .translationX(coverShift)
                .translationY(0f)
                .setDuration(duration)
                .start()
            binding.bookSpine.animate()
                .alpha(1f)
                .translationX(0f)
                .rotation(0f)
                .translationZ(0f)
                .setDuration(duration)
                .start()
            binding.bookPageEdge.animate()
                .alpha(if (expanded) 1f else 0f)
                .translationX(coverShift + (3f * density))
                .translationY(0f)
                .setDuration(duration)
                .start()
            binding.bookBackCover.animate()
                .alpha(if (expanded) 1f else 0f)
                .translationX(coverShift)
                .translationY(0f)
                .setDuration(duration)
                .start()
            applyHomeShelfPageSpread(expanded = expanded, coverShift = coverShift, animate = animate)
        }

        private fun handleHomeShelfTap(book: BookEntity) {
            val result = HomeShelfInteractionLogic.onBookTapped(homeShelfState, book.id)
            val previousExpandedBookId = result.previousExpandedBookId
            homeShelfState = result.state
            previousExpandedBookId
                ?.takeIf { it != book.id }
                ?.let { previousId ->
                    val previousIndex = currentList.indexOfFirst { it.id == previousId }
                    if (previousIndex >= 0) notifyItemChanged(previousIndex)
                }

            applyHomeShelfState(expanded = true, highlight = false, animate = false)

            playHomeShelfOpeningAnimation(book)
        }

        private fun playHomeShelfOpeningAnimation(book: BookEntity) {
            val density = binding.root.resources.displayMetrics.density
            val coverShift = expandedCoverCenterShift()
            val animatedPages = listOf(binding.bookPage1, binding.bookPage3, binding.bookPage5)

            setTransientHardwareLayer(binding.bookCover, true)
            setTransientHardwareLayer(binding.bookBackCover, true)
            animatedPages.forEach { setTransientHardwareLayer(it, true) }

            binding.bookCover.pivotX = 0f
            binding.bookCover.pivotY = binding.bookCover.height / 2f
            binding.bookBackCover.pivotX = 0f
            binding.bookBackCover.pivotY = binding.bookBackCover.height / 2f
            animatedPages.forEach {
                it.pivotX = 0f
                it.pivotY = it.height / 2f
            }

            binding.bookCover.animate().cancel()
            binding.bookBackCover.animate().cancel()
            animatedPages.forEach { it.animate().cancel() }

            binding.bookCover.animate()
                .rotationY(-150f)
                .translationX(coverShift)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(360L)
                .withEndAction { setTransientHardwareLayer(binding.bookCover, false) }
                .start()
            binding.bookBackCover.animate()
                .rotationY(-18f)
                .translationX(coverShift)
                .setDuration(360L)
                .withEndAction { setTransientHardwareLayer(binding.bookBackCover, false) }
                .start()

            animatedPages.forEachIndexed { index, page ->
                val rotation = when (index) {
                    0 -> -135f
                    1 -> -85f
                    else -> -40f
                }
                page.animate()
                    .alpha(1f)
                    .translationX(coverShift + ((index + 1) * 4f * density))
                    .rotationY(rotation)
                    .setStartDelay((index * 24).toLong())
                    .setDuration(320L)
                    .withEndAction { setTransientHardwareLayer(page, false) }
                    .start()
            }

            binding.root.postDelayed({
                homeShelfState = HomeShelfInteractionLogic.onOpeningAnimationFinished(homeShelfState)
                onBookClick?.invoke(book)
            }, 280L)
        }

        private fun loadCover(book: BookEntity) {
            val localCover = book.coverPath
            val primaryCover = localCover ?: book.coverUrl
            val fallbackCover = GutenbergMirror.coverUrl(book.id)
            val networkAvailable = isNetworkAvailable(binding.root.context)

            if (!shouldLoadRemoteCover(localCover, primaryCover, networkAvailable)) {
                binding.bookCover.load(android.R.drawable.ic_menu_report_image) {
                    crossfade(false)
                    placeholder(android.R.drawable.ic_menu_report_image)
                    error(android.R.drawable.ic_menu_report_image)
                }
                updateHomeShelfSpineColor(book.id, defaultHomeShelfSpineColor(book))
                return
            }

            if (primaryCover.isNullOrBlank()) {
                binding.bookCover.load(fallbackCover) {
                    crossfade(false)
                    placeholder(android.R.drawable.ic_menu_report_image)
                    error(android.R.drawable.ic_menu_report_image)
                    listener(
                        onSuccess = { _, result ->
                            updatePaletteFromDrawable(book, result.drawable)
                        }
                    )
                }
                return
            }

            binding.bookCover.load(primaryCover) {
                crossfade(false)
                placeholder(android.R.drawable.ic_menu_report_image)
                error(android.R.drawable.ic_menu_report_image)
                listener(
                    onSuccess = { _, result ->
                        updatePaletteFromDrawable(book, result.drawable)
                    },
                    onError = { _, result ->
                        Log.w("BookAdapter", "Primary cover failed for ${book.id}: ${result.throwable.message}")
                        if (localCover == null && primaryCover != fallbackCover) {
                            binding.bookCover.load(fallbackCover) {
                                crossfade(false)
                                placeholder(android.R.drawable.ic_menu_report_image)
                                error(android.R.drawable.ic_menu_report_image)
                                listener(
                                    onSuccess = { _, fallbackResult ->
                                        updatePaletteFromDrawable(book, fallbackResult.drawable)
                                    }
                                )
                            }
                        } else {
                            updateHomeShelfSpineColor(book.id, defaultHomeShelfSpineColor(book))
                        }
                    }
                )
            }
        }

        private fun homeShelfMetrics(bookTitle: String, density: Float): HomeShelfLayoutMetrics {
            val metrics = HomeShelfInteractionLogic.metricsForTitle(bookTitle)
            return HomeShelfLayoutMetrics(
                widthDp = metrics.widthDp,
                heightDp = metrics.heightDp,
                widthPx = (metrics.widthDp * density).toInt(),
                heightPx = (metrics.heightDp * density).toInt()
            )
        }

        private fun updatePaletteFromDrawable(book: BookEntity, drawable: android.graphics.drawable.Drawable) {
            if (presentationMode != PresentationMode.HOME_SHELF) return
            if (homeShelfSpineColors.containsKey(book.id) || !pendingPaletteBookIds.add(book.id)) return
            runCatching {
                val bitmap = drawable.toBitmap(width = 48, height = 72, config = android.graphics.Bitmap.Config.ARGB_8888)
                Palette.from(bitmap).clearFilters().generate { palette ->
                    bitmap.recycle()
                    val color = palette
                        ?.getDominantColor(defaultHomeShelfSpineColor(book))
                        ?: defaultHomeShelfSpineColor(book)
                    updateHomeShelfSpineColor(book.id, darkenColor(color, 0.15f))
                }
            }.onFailure {
                pendingPaletteBookIds.remove(book.id)
                updateHomeShelfSpineColor(book.id, defaultHomeShelfSpineColor(book))
            }
        }

        private fun updateHomeShelfSpineColor(bookId: Int, color: Int) {
            if (homeShelfSpineColors[bookId] == color) return
            homeShelfSpineColors[bookId] = color
            pendingPaletteBookIds.remove(bookId)
            if (presentationMode == PresentationMode.HOME_SHELF && bindingAdapterPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                applyCardTheme(getBookForPosition(bindingAdapterPosition))
            }
        }

        private fun defaultHomeShelfSpineColor(book: BookEntity): Int {
            val fallback = listOf("#7A3324", "#254E70", "#5B3A29", "#314F38", "#6C2940", "#4E3F69")
            return fallback[book.id.mod(fallback.size)].toColorInt()
        }

        private fun bestTextColorForBackground(backgroundColor: Int): Int {
            val darkness = (0.299 * Color.red(backgroundColor) +
                0.587 * Color.green(backgroundColor) +
                0.114 * Color.blue(backgroundColor)) / 255
            return if (darkness > 0.62f) "#1E130C".toColorInt() else "#F6EBDD".toColorInt()
        }

        private fun lightenColor(color: Int, amount: Float): Int {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            return Color.rgb(
                (r + ((255 - r) * amount)).toInt().coerceIn(0, 255),
                (g + ((255 - g) * amount)).toInt().coerceIn(0, 255),
                (b + ((255 - b) * amount)).toInt().coerceIn(0, 255)
            )
        }

        private fun darkenColor(color: Int, amount: Float): Int {
            return Color.rgb(
                (Color.red(color) * (1f - amount)).toInt().coerceIn(0, 255),
                (Color.green(color) * (1f - amount)).toInt().coerceIn(0, 255),
                (Color.blue(color) * (1f - amount)).toInt().coerceIn(0, 255)
            )
        }

        private fun updateLayoutSize(view: View, width: Int, height: Int) {
            val params = view.layoutParams
            if (params.width == width && params.height == height) return
            params.width = width
            params.height = height
            view.layoutParams = params
        }

        private fun updateLayoutParams(view: View, newParams: ViewGroup.LayoutParams) {
            val current = view.layoutParams
            if (current == null) {
                view.layoutParams = newParams
                return
            }
            if (current.width == newParams.width && current.height == newParams.height) {
                if (current is ViewGroup.MarginLayoutParams && newParams is ViewGroup.MarginLayoutParams) {
                    if (current.leftMargin == newParams.leftMargin &&
                        current.topMargin == newParams.topMargin &&
                        current.rightMargin == newParams.rightMargin &&
                        current.bottomMargin == newParams.bottomMargin
                    ) {
                        return
                    }
                } else {
                    return
                }
            }
            view.layoutParams = newParams
        }

        private fun applyHomeShelfPageSpread(expanded: Boolean, coverShift: Float, animate: Boolean) {
            val duration = if (animate) 220L else 0L
            val alpha = if (expanded) 1f else 0f
            homeShelfPageViews().forEachIndexed { index, pageView ->
                pageView.pivotX = 0f
                pageView.pivotY = pageView.height / 2f
                pageView.animate()
                    .alpha(alpha)
                    .translationX(if (expanded) coverShift + ((index + 1) * 2f) else 0f)
                    .translationY(0f)
                    .rotationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration)
                    .start()
            }
            binding.bookCover.rotationY = 0f
            binding.bookCover.scaleX = 1f
            binding.bookCover.scaleY = 1f
            binding.bookBackCover.rotationY = 0f
        }

        private fun resetHomeShelfPages() {
            binding.bookBackCover.translationX = 0f
            binding.bookBackCover.translationY = 0f
            binding.bookBackCover.rotationY = 0f
            binding.bookBackCover.scaleX = 1f
            binding.bookBackCover.scaleY = 1f
            binding.bookCover.translationY = 0f
            binding.bookCover.rotationY = 0f
            binding.bookCover.scaleX = 1f
            binding.bookCover.scaleY = 1f
            binding.bookPageEdge.translationY = 0f
            homeShelfPageViews().forEach { pageView ->
                pageView.alpha = 0f
                pageView.translationX = 0f
                pageView.translationY = 0f
                pageView.rotationY = 0f
                pageView.scaleX = 1f
                pageView.scaleY = 1f
            }
        }

        private fun homeShelfPageViews(): List<View> = listOf(
            binding.bookPage1,
            binding.bookPage2,
            binding.bookPage3,
            binding.bookPage4,
            binding.bookPage5,
            binding.bookPage6
        )

        private fun expandedCoverCenterShift(): Float {
            val recyclerView = binding.root.parent as? View ?: return (binding.bookSpine.width * 0.7f)
            val itemCenter = binding.root.x + (binding.root.width / 2f)
            val targetCenter = recyclerView.width / 2f
            return (targetCenter - itemCenter) + (binding.bookSpine.width * 0.65f)
        }

        private fun setTransientHardwareLayer(view: View, enabled: Boolean) {
            view.setLayerType(if (enabled) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, null)
        }
    }
}

private data class HomeShelfLayoutMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val widthPx: Int,
    val heightPx: Int
)

private class BookDiffCallback : DiffUtil.ItemCallback<BookEntity>() {
    override fun areItemsTheSame(oldItem: BookEntity, newItem: BookEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: BookEntity, newItem: BookEntity): Boolean {
        return oldItem == newItem
    }
}
