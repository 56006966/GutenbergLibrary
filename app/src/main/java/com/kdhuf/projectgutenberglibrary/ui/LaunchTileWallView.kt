package com.kdhuf.projectgutenberglibrary.ui

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.util.AttributeSet
import android.view.Gravity
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.annotation.VisibleForTesting
import coil.load
import coil.request.CachePolicy
import coil.size.Scale
import coil.size.Size
import com.kdhuf.projectgutenberglibrary.R
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.remote.GutenbergMirror
import kotlin.math.roundToInt
import kotlin.random.Random

class LaunchTileWallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val PLACEHOLDER_BOOK_COUNT = 18
        private const val MAX_LAUNCH_WALL_BOOKS = 18
        private const val PLACEHOLDER_TITLE = "Project Gutenberg"
        private const val PLACEHOLDER_AUTHOR = "Classic literature"
        private const val LOG_TAG = "LaunchTileWallView"
        private const val SCROLL_SPEED_DP_PER_SECOND = 18f
        private const val VERBOSE_LOGGING = false
        private const val MIRROR_COVER_BASE_URL = "https://books.phunkypixels.com"
    }

    private val viewport = FrameLayout(context)
    private val perspectiveStage = FrameLayout(context)
    private val scrollStrip = LinearLayout(context)
    private val choreographer by lazy { Choreographer.getInstance() }
    private var scrollFrameCallback: Choreographer.FrameCallback? = null

    private val baseTileSize = 92.dp()
    private val tileGap = 14.dp()
    private var viewportScale = 1f
    private var fixedVisibleRows: Int? = null
    private var fixedVisibleCols: Int? = null
    private var useUniformGrid = false
    private var books: List<BookEntity> = emptyList()
    private var touchPaused = false
    private var lastFrameNanos = 0L
    private var segmentHeightPx = 0f
    private var scrollOffsetPx = 0f
    private val wallShuffleSeed = Random.nextInt()

    private val startupCoverIds = listOf(
        1, 10, 100, 1000, 1001, 1002,
        10000, 10001, 10002, 10003, 10004, 10005,
        10006, 10007, 10008, 10009, 10010, 10011,
        10012, 10013, 10014, 10015, 10016, 10017,
        10018, 10019
    )

    init {
        clipChildren = false
        clipToPadding = false

        viewport.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        viewport.clipChildren = true
        viewport.clipToPadding = true

        perspectiveStage.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        perspectiveStage.clipChildren = false
        perspectiveStage.clipToPadding = false
        perspectiveStage.rotationX = 62f
        perspectiveStage.rotation = 0f
        perspectiveStage.translationX = 0f
        perspectiveStage.translationY = (-78).dp().toFloat()

        scrollStrip.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        scrollStrip.orientation = LinearLayout.VERTICAL
        scrollStrip.clipChildren = false
        scrollStrip.clipToPadding = false

        perspectiveStage.addView(scrollStrip)
        viewport.addView(perspectiveStage)
        addView(viewport)
        books = normalizeBooksForWall(buildPlaceholderBooks())
    }

    fun setBooks(items: List<BookEntity>) {
        books = normalizeBooksForWall(items.ifEmpty { books })
        rebuildWall()
    }

    fun setPlaceholderTiles() {
        books = normalizeBooksForWall(books.ifEmpty { buildPlaceholderBooks() })
        rebuildWall()
    }

    fun setViewportScale(scale: Float) {
        val newScale = scale.coerceIn(0.5f, 1f)
        if (viewportScale == newScale) return
        viewportScale = newScale
        debugLog { "setViewportScale scale=$viewportScale" }
        applyViewportBounds()
    }

    fun setVisibleWindow(visibleRows: Int, visibleCols: Int) {
        val newRows = visibleRows.coerceIn(2, 6)
        val newCols = visibleCols.coerceIn(2, 6)
        val changed = fixedVisibleRows != newRows || fixedVisibleCols != newCols || !useUniformGrid
        val expectedGravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        fixedVisibleRows = newRows
        fixedVisibleCols = newCols
        useUniformGrid = true
        perspectiveStage.rotationX = 0f
        perspectiveStage.rotation = 0f
        perspectiveStage.translationX = 0f
        perspectiveStage.translationY = 0f
        val currentParams = perspectiveStage.layoutParams as? LayoutParams
        val needsLayoutParamsUpdate = currentParams == null ||
            currentParams.width != LayoutParams.WRAP_CONTENT ||
            currentParams.height != LayoutParams.WRAP_CONTENT ||
            currentParams.gravity != expectedGravity
        if (needsLayoutParamsUpdate) {
            perspectiveStage.layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                expectedGravity
            )
        }
        debugLog { "setVisibleWindow rows=$fixedVisibleRows cols=$fixedVisibleCols" }
        if (changed && isAttachedToWindow && isActuallyVisible()) {
            rebuildWall()
        }
    }

    fun setPreviewWindow(visibleRows: Int, visibleCols: Int) {
        setVisibleWindow(visibleRows, visibleCols)
    }

    fun pauseAnimation() {
        touchPaused = true
        debugLog { "pauseAnimation" }
        stopAnimation()
    }

    fun resumeAnimation() {
        touchPaused = false
        debugLog {
            "resumeAnimation visible=${isActuallyVisible()} segmentHeightPx=$segmentHeightPx childCount=${scrollStrip.childCount}"
        }
        if (isActuallyVisible()) {
            if (segmentHeightPx > 0f && scrollStrip.childCount > 0) {
                startAnimation()
            } else {
                rebuildWall()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        debugLog { "onAttachedToWindow visible=${isActuallyVisible()}" }
        if (isActuallyVisible()) {
            rebuildWall()
        }
    }

    override fun onDetachedFromWindow() {
        debugLog { "onDetachedFromWindow" }
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        debugLog { "onSizeChanged w=$w h=$h oldw=$oldw oldh=$oldh" }
        applyViewportBounds()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        debugLog {
            "onVisibilityAggregated isVisible=$isVisible attached=$isAttachedToWindow touchPaused=$touchPaused"
        }
        if (!isAttachedToWindow) return
        if (isVisible) {
            if (!touchPaused) {
                resumeAnimation()
            }
        } else {
            stopAnimation()
        }
    }

    private fun rebuildWall() {
        if (!isAttachedToWindow) {
            debugLog { "rebuildWall skipped: not attached" }
            return
        }
        if (!isActuallyVisible()) {
            debugLog { "rebuildWall skipped: not visible" }
            return
        }
        if (books.isEmpty()) {
            debugLog { "rebuildWall skipped: no books" }
            return
        }

        stopAnimation()
        scrollStrip.removeAllViews()

        val visibleRows = computeVisibleRows()
        val visibleCols = computeVisibleCols()
        val tileSize = computeTileSizePx(visibleRows, visibleCols)
        val rowCount = computeSegmentRowCount(visibleRows)
        val tilesPerRow = computeSegmentTilesPerRow(visibleCols)
        debugLog {
            "rebuildWall viewport=${viewport.width}x${viewport.height} host=${width}x${height} visible=${visibleRows}x$visibleCols segmentRows=$rowCount segmentCols=$tilesPerRow tileSize=$tileSize books=${books.size}"
        }

        val segmentWidth = computeSegmentWidthPx(tilesPerRow, tileSize)
        val segmentHeight = computeSegmentHeightPx(rowCount, tileSize)

        scrollStrip.layoutParams = LayoutParams(
            segmentWidth,
            segmentHeight * 3
        )
        perspectiveStage.layoutParams = LayoutParams(
            segmentWidth,
            if (viewport.height > 0) viewport.height else LayoutParams.WRAP_CONTENT,
            if (useUniformGrid) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
        )

        val leadingSegment = createWallSegment(rowCount, tilesPerRow, tileSize, segmentWidth, segmentHeight)
        val centerSegment = createWallSegment(rowCount, tilesPerRow, tileSize, segmentWidth, segmentHeight)
        val trailingSegment = createWallSegment(rowCount, tilesPerRow, tileSize, segmentWidth, segmentHeight)
        scrollStrip.addView(leadingSegment)
        scrollStrip.addView(centerSegment)
        scrollStrip.addView(trailingSegment)

        scrollStrip.post {
            val measuredSegmentHeight = centerSegment.height
            val computedSegmentHeight = segmentHeight
            val segmentHeight = measuredSegmentHeight.takeIf { it > 0 } ?: computedSegmentHeight
            if (segmentHeight <= 0) {
                debugLog {
                    "rebuildWall post skipped: measuredSegmentHeight=$measuredSegmentHeight computedSegmentHeight=$computedSegmentHeight"
                }
                return@post
            }

            segmentHeightPx = segmentHeight.toFloat()
            scrollOffsetPx = 0f
            scrollStrip.translationY = -segmentHeightPx
            debugLog {
                "rebuildWall ready segmentHeightPx=$segmentHeightPx measuredSegmentHeight=$measuredSegmentHeight computedSegmentHeight=$computedSegmentHeight"
            }
            startAnimation()
            invalidate()
        }
    }

    private fun createWallSegment(
        rowCount: Int,
        tilesPerRow: Int,
        tileSize: Int,
        segmentWidth: Int,
        segmentHeight: Int
    ): LinearLayout {
        val segment = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(
                segmentWidth,
                segmentHeight
            )
        }

        repeat(rowCount) { rowIndex ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    tileSize
                ).apply {
                    if (rowIndex < rowCount - 1) {
                        bottomMargin = tileGap
                    }
                    leftMargin = if (useUniformGrid) 0 else if (rowIndex % 2 == 1) tileSize / 2 else 0
                }
            }

            val startIndex = (rowIndex * tilesPerRow) % books.size
            repeat(tilesPerRow) { tileIndex ->
                row.addView(createTileView(books[(startIndex + tileIndex) % books.size], tileSize))
            }
            segment.addView(row)
        }

        return segment
    }

    private fun startAnimation() {
        if (touchPaused) {
            debugLog { "startAnimation skipped: touchPaused" }
            return
        }
        if (!isActuallyVisible()) {
            debugLog { "startAnimation skipped: not visible" }
            return
        }
        if (segmentHeightPx <= 0f) {
            debugLog { "startAnimation skipped: segmentHeightPx=$segmentHeightPx" }
            return
        }
        if (scrollFrameCallback != null) {
            debugLog { "startAnimation skipped: already running" }
            return
        }

        lastFrameNanos = 0L
        debugLog { "startAnimation segmentHeightPx=$segmentHeightPx" }
        scrollFrameCallback = Choreographer.FrameCallback { frameTimeNanos ->
            stepAnimation(frameTimeNanos)
        }
        choreographer.postFrameCallback(scrollFrameCallback)
    }

    private fun stepAnimation(frameTimeNanos: Long) {
        val callback = scrollFrameCallback ?: return
        if (touchPaused || !isActuallyVisible() || segmentHeightPx <= 0f) {
            stopAnimation()
            return
        }

        if (lastFrameNanos != 0L) {
            val deltaSeconds = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            val pixelsPerSecond = SCROLL_SPEED_DP_PER_SECOND * resources.displayMetrics.density
            scrollOffsetPx = (scrollOffsetPx + (pixelsPerSecond * deltaSeconds)) % segmentHeightPx
            scrollStrip.translationY = -segmentHeightPx - scrollOffsetPx
        } else {
            scrollStrip.translationY = -segmentHeightPx
        }

        lastFrameNanos = frameTimeNanos
        choreographer.postFrameCallback(callback)
    }

    private fun createTileView(book: BookEntity, tileSize: Int): View {
        val card = CardView(context).apply {
            radius = 18.dp().toFloat()
            cardElevation = 4.dp().toFloat()
            setCardBackgroundColor(Color.parseColor("#E7D8BC"))
            layoutParams = LinearLayout.LayoutParams(tileSize, tileSize).apply {
                rightMargin = tileGap
            }
        }

        val image = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#D5C3A1"))
            setImageResource(R.drawable.icon)
            alpha = 0.96f
            val inset = 6.dp()
            setPadding(inset, inset, inset, inset)
        }

        val url = preferredLaunchWallCover(book)
        val fallbackUrl = GutenbergMirror.coverUrl(book.id)
        if (!url.isNullOrBlank()) {
            image.load(url) {
                crossfade(false)
                placeholder(R.drawable.icon)
                error(R.drawable.icon)
                size(Size(tileSize, tileSize))
                scale(Scale.FILL)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                networkCachePolicy(CachePolicy.ENABLED)
                listener(
                    onError = { _, _ ->
                        if (url != fallbackUrl) {
                            image.load(fallbackUrl) {
                                crossfade(false)
                                placeholder(R.drawable.icon)
                                error(R.drawable.icon)
                                size(Size(tileSize, tileSize))
                                scale(Scale.FILL)
                                memoryCachePolicy(CachePolicy.ENABLED)
                                diskCachePolicy(CachePolicy.ENABLED)
                                networkCachePolicy(CachePolicy.ENABLED)
                            }
                        }
                    }
                )
            }
        }

        card.addView(image)
        return card
    }

    private fun stopAnimation() {
        if (scrollFrameCallback != null) {
            debugLog { "stopAnimation offset=$scrollOffsetPx" }
        }
        scrollFrameCallback?.let(choreographer::removeFrameCallback)
        scrollFrameCallback = null
        lastFrameNanos = 0L
    }

    private fun applyViewportBounds() {
        val scaledWidth = if (width > 0) (width * viewportScale).roundToInt() else LayoutParams.MATCH_PARENT
        val scaledHeight = if (height > 0) (height * viewportScale).roundToInt() else LayoutParams.MATCH_PARENT
        val currentParams = viewport.layoutParams as? LayoutParams
        val needsUpdate = currentParams == null ||
            currentParams.width != scaledWidth ||
            currentParams.height != scaledHeight ||
            currentParams.gravity != Gravity.CENTER
        if (!needsUpdate) return

        viewport.layoutParams = LayoutParams(
            scaledWidth,
            scaledHeight,
            Gravity.CENTER
        )
        debugLog {
            "applyViewportBounds host=${width}x${height} viewport=${scaledWidth}x${scaledHeight} scale=$viewportScale"
        }
    }

    private fun computeRowCount(): Int {
        val visibleRows = computeVisibleRows()
        return computeSegmentRowCount(visibleRows)
    }

    private fun computeTilesPerRow(): Int {
        val visibleCols = computeVisibleCols()
        return computeSegmentTilesPerRow(visibleCols)
    }

    private fun computeVisibleRows(): Int {
        fixedVisibleRows?.let { return it }
        val viewportHeight = if (viewport.height > 0) viewport.height else height
        val tileSpan = (baseTileSize + tileGap).coerceAtLeast(1)
        return ((viewportHeight / tileSpan) + 1).coerceIn(3, 6)
    }

    private fun computeVisibleCols(): Int {
        fixedVisibleCols?.let { return it }
        val viewportWidth = if (viewport.width > 0) viewport.width else width
        val tileSpan = (baseTileSize + tileGap).coerceAtLeast(1)
        return ((viewportWidth / tileSpan) + 1).coerceIn(3, 8)
    }

    private fun computeSegmentRowCount(visibleRows: Int): Int {
        return if (useUniformGrid) {
            (visibleRows * 3).coerceIn(6, 10)
        } else {
            (visibleRows + 2).coerceIn(4, 10)
        }
    }

    private fun computeSegmentTilesPerRow(visibleCols: Int): Int {
        return if (useUniformGrid) {
            (visibleCols + 1).coerceIn(4, 7)
        } else {
            (visibleCols + 2).coerceIn(5, 12)
        }
    }

    private fun computeTileSizePx(visibleRows: Int, visibleCols: Int): Int {
        val viewportWidth = if (viewport.width > 0) viewport.width else width
        val viewportHeight = if (viewport.height > 0) viewport.height else height
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return baseTileSize
        }

        val widthBased = ((viewportWidth - (tileGap * (visibleCols - 1))) / visibleCols).coerceAtLeast(baseTileSize)
        val heightBased = ((viewportHeight - (tileGap * (visibleRows - 1))) / visibleRows).coerceAtLeast(baseTileSize)
        val uncappedTileSize = minOf(widthBased, heightBased)
        if (!useUniformGrid) return uncappedTileSize

        val window = LaunchTileWallWindow(visibleRows = visibleRows, visibleCols = visibleCols)
        val maxTileSize = LaunchTileWallLayoutLogic.maxTileSizePx(
            window = window,
            density = resources.displayMetrics.density
        )
        return minOf(uncappedTileSize, maxTileSize)
    }

    private fun computeSegmentHeightPx(rowCount: Int, tileSize: Int): Int {
        return (rowCount * tileSize) + ((rowCount - 1).coerceAtLeast(0) * tileGap)
    }

    private fun computeSegmentWidthPx(tilesPerRow: Int, tileSize: Int): Int {
        return (tilesPerRow * tileSize) + ((tilesPerRow - 1).coerceAtLeast(0) * tileGap)
    }

    @VisibleForTesting
    internal fun debugViewportSize(): Pair<Int, Int> = viewport.width to viewport.height

    @VisibleForTesting
    internal fun debugGridSize(): Pair<Int, Int> = computeRowCount() to computeTilesPerRow()

    @VisibleForTesting
    internal fun debugIsAnimating(): Boolean = scrollFrameCallback != null

    @VisibleForTesting
    internal fun debugSegmentHeightPx(): Float = segmentHeightPx

    private fun isActuallyVisible(): Boolean {
        return visibility == View.VISIBLE && windowVisibility == View.VISIBLE && isShown
    }

    private fun buildPlaceholderBooks(): List<BookEntity> {
        return List(PLACEHOLDER_BOOK_COUNT) { index ->
            val bookId = startupCoverIds[index % startupCoverIds.size]
            BookEntity(
                id = bookId,
                title = PLACEHOLDER_TITLE,
                author = PLACEHOLDER_AUTHOR,
                genre = PLACEHOLDER_AUTHOR,
                downloads = 0,
                coverUrl = GutenbergMirror.coverUrl(bookId),
                coverPath = null,
                text = null,
                epubPath = null,
                status = BookEntity.STATUS_TO_READ
            )
        }
    }

    private fun normalizeBooksForWall(items: List<BookEntity>): List<BookEntity> {
        return items
            .map { book ->
                book.copy(coverUrl = resolvedMirrorCoverUrl(book))
            }
            .distinctBy { it.id }
            .sortedBy { book -> book.id xor wallShuffleSeed }
            .take(MAX_LAUNCH_WALL_BOOKS)
            .ifEmpty { buildPlaceholderBooks() }
    }

    private fun preferredLaunchWallCover(book: BookEntity): String? {
        return book.coverPath?.takeIf { it.isNotBlank() }
            ?: book.coverUrl?.takeIf { it.isNotBlank() }
            ?: resolvedMirrorCoverUrl(book)
    }

    private fun resolvedMirrorCoverUrl(book: BookEntity): String {
        val existingUrl = book.coverUrl?.takeIf { it.isNotBlank() }
        return GutenbergMirror.resolve(existingUrl)
            ?: "$MIRROR_COVER_BASE_URL/${book.id}/pg${book.id}.cover.medium.jpg"
    }

    private inline fun debugLog(message: () -> String) {
        if (VERBOSE_LOGGING) {
            Log.d(LOG_TAG, message())
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()
}
