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
import com.kdhuf.projectgutenberglibrary.R
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import kotlin.math.roundToInt

class LaunchTileWallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val PLACEHOLDER_BOOK_COUNT = 100
        private const val PLACEHOLDER_TITLE = "Project Gutenberg"
        private const val PLACEHOLDER_AUTHOR = "Classic literature"
        private const val LOG_TAG = "LaunchTileWallView"
        private const val FRAME_LOG_INTERVAL_MS = 2_000L
        private const val SCROLL_SPEED_DP_PER_SECOND = 18f
    }

    private val viewport = FrameLayout(context)
    private val perspectiveStage = FrameLayout(context)
    private val scrollStrip = LinearLayout(context)
    private val choreographer by lazy { Choreographer.getInstance() }
    private var scrollFrameCallback: Choreographer.FrameCallback? = null

    private val baseTileSize = 92.dp()
    private val tileGap = 14.dp()
    private var viewportScale = 1f
    private var previewVisibleRows: Int? = null
    private var previewVisibleCols: Int? = null
    private var uniformPreviewMode = false
    private var books: List<BookEntity> = emptyList()
    private var touchPaused = false
    private var lastFrameNanos = 0L
    private var segmentHeightPx = 0f
    private var scrollOffsetPx = 0f
    private var lastFrameLogNanos = 0L

    private val startupCoverIds = listOf(
        1342, 84, 11, 98, 2701, 1661, 1952, 74, 64317, 174,
        1232, 4300, 1080, 2591, 5200, 76, 345, 46, 1400, 158,
        3207, 219, 8800, 205, 1497, 236, 55, 16, 1259, 2148,
        4217, 844, 514, 2852, 1399, 4085, 244, 135, 35, 25344,
        100, 215, 730, 829, 3090, 5740, 1998, 5000, 2814, 3600
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
        setPlaceholderTiles()
    }

    fun setBooks(items: List<BookEntity>) {
        books = items.ifEmpty { books }
        rebuildWall()
    }

    fun setPlaceholderTiles() {
        if (books.isEmpty()) {
            books = List(PLACEHOLDER_BOOK_COUNT) { index ->
                val bookId = startupCoverIds[index % startupCoverIds.size]
                BookEntity(
                    id = bookId,
                    title = PLACEHOLDER_TITLE,
                    author = PLACEHOLDER_AUTHOR,
                    genre = PLACEHOLDER_AUTHOR,
                    downloads = 0,
                    coverUrl = buildCoverUrl(bookId),
                    coverPath = null,
                    text = null,
                    epubPath = null,
                    status = BookEntity.STATUS_TO_READ
                )
            }
        }
        rebuildWall()
    }

    fun setViewportScale(scale: Float) {
        viewportScale = scale.coerceIn(0.5f, 1f)
        Log.d(LOG_TAG, "setViewportScale scale=$viewportScale")
        applyViewportBounds()
    }

    fun setPreviewWindow(visibleRows: Int, visibleCols: Int) {
        previewVisibleRows = visibleRows.coerceIn(2, 6)
        previewVisibleCols = visibleCols.coerceIn(2, 6)
        uniformPreviewMode = true
        perspectiveStage.rotationX = 0f
        perspectiveStage.rotation = 0f
        perspectiveStage.translationX = 0f
        perspectiveStage.translationY = 0f
        perspectiveStage.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        Log.d(
            LOG_TAG,
            "setPreviewWindow rows=$previewVisibleRows cols=$previewVisibleCols"
        )
        if (isAttachedToWindow && isActuallyVisible()) {
            rebuildWall()
        }
    }

    fun pauseAnimation() {
        touchPaused = true
        Log.d(LOG_TAG, "pauseAnimation")
        stopAnimation()
    }

    fun resumeAnimation() {
        touchPaused = false
        Log.d(
            LOG_TAG,
            "resumeAnimation visible=${isActuallyVisible()} segmentHeightPx=$segmentHeightPx childCount=${scrollStrip.childCount}"
        )
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
        Log.d(LOG_TAG, "onAttachedToWindow visible=${isActuallyVisible()}")
        if (isActuallyVisible()) {
            rebuildWall()
        }
    }

    override fun onDetachedFromWindow() {
        Log.d(LOG_TAG, "onDetachedFromWindow")
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d(LOG_TAG, "onSizeChanged w=$w h=$h oldw=$oldw oldh=$oldh")
        applyViewportBounds()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        Log.d(
            LOG_TAG,
            "onVisibilityAggregated isVisible=$isVisible attached=$isAttachedToWindow touchPaused=$touchPaused"
        )
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
            Log.d(LOG_TAG, "rebuildWall skipped: not attached")
            return
        }
        if (!isActuallyVisible()) {
            Log.d(LOG_TAG, "rebuildWall skipped: not visible")
            return
        }
        if (books.isEmpty()) {
            Log.d(LOG_TAG, "rebuildWall skipped: no books")
            return
        }

        stopAnimation()
        scrollStrip.removeAllViews()

        val visibleRows = computeVisibleRows()
        val visibleCols = computeVisibleCols()
        val tileSize = computeTileSizePx(visibleRows, visibleCols)
        val rowCount = computeSegmentRowCount(visibleRows)
        val tilesPerRow = computeSegmentTilesPerRow(visibleCols)
        Log.d(
            LOG_TAG,
            "rebuildWall viewport=${viewport.width}x${viewport.height} host=${width}x${height} visible=${visibleRows}x$visibleCols segmentRows=$rowCount segmentCols=$tilesPerRow tileSize=$tileSize books=${books.size}"
        )

        val segmentWidth = computeSegmentWidthPx(tilesPerRow, tileSize)
        val segmentHeight = computeSegmentHeightPx(rowCount, tileSize)

        scrollStrip.layoutParams = LayoutParams(
            segmentWidth,
            segmentHeight * 3
        )
        perspectiveStage.layoutParams = LayoutParams(
            segmentWidth,
            if (viewport.height > 0) viewport.height else LayoutParams.WRAP_CONTENT,
            if (uniformPreviewMode) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
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
                Log.d(
                    LOG_TAG,
                    "rebuildWall post skipped: measuredSegmentHeight=$measuredSegmentHeight computedSegmentHeight=$computedSegmentHeight"
                )
                return@post
            }

            segmentHeightPx = segmentHeight.toFloat()
            scrollOffsetPx = 0f
            scrollStrip.translationY = -segmentHeightPx
            Log.d(
                LOG_TAG,
                "rebuildWall ready segmentHeightPx=$segmentHeightPx measuredSegmentHeight=$measuredSegmentHeight computedSegmentHeight=$computedSegmentHeight"
            )
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
                    leftMargin = if (uniformPreviewMode) 0 else if (rowIndex % 2 == 1) tileSize / 2 else 0
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
            Log.d(LOG_TAG, "startAnimation skipped: touchPaused")
            return
        }
        if (!isActuallyVisible()) {
            Log.d(LOG_TAG, "startAnimation skipped: not visible")
            return
        }
        if (segmentHeightPx <= 0f) {
            Log.d(LOG_TAG, "startAnimation skipped: segmentHeightPx=$segmentHeightPx")
            return
        }
        if (scrollFrameCallback != null) {
            Log.d(LOG_TAG, "startAnimation skipped: already running")
            return
        }

        lastFrameNanos = 0L
        lastFrameLogNanos = 0L
        Log.d(LOG_TAG, "startAnimation segmentHeightPx=$segmentHeightPx")
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
            maybeLogFrame(frameTimeNanos, deltaSeconds)
        } else {
            scrollStrip.translationY = -segmentHeightPx
        }

        lastFrameNanos = frameTimeNanos
        choreographer.postFrameCallback(callback)
    }

    private fun createTileView(book: BookEntity, tileSize: Int): View {
        val card = CardView(context).apply {
            radius = 18.dp().toFloat()
            cardElevation = 10.dp().toFloat()
            setCardBackgroundColor(Color.parseColor("#E7D8BC"))
            layoutParams = LinearLayout.LayoutParams(tileSize, tileSize).apply {
                rightMargin = tileGap
            }
        }

        val image = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#D5C3A1"))
            setImageResource(R.drawable.icon)
            alpha = 0.96f
        }

        val url = book.coverPath ?: book.coverUrl
        if (!url.isNullOrBlank()) {
            image.load(url) {
                crossfade(true)
                placeholder(R.drawable.icon)
                error(R.drawable.icon)
            }
        }

        card.addView(image)
        return card
    }

    private fun buildCoverUrl(bookId: Int): String {
        return "https://www.gutenberg.org/cache/epub/$bookId/pg$bookId.cover.medium.jpg"
    }

    private fun stopAnimation() {
        if (scrollFrameCallback != null) {
            Log.d(LOG_TAG, "stopAnimation offset=$scrollOffsetPx")
        }
        scrollFrameCallback?.let(choreographer::removeFrameCallback)
        scrollFrameCallback = null
        lastFrameNanos = 0L
        lastFrameLogNanos = 0L
    }

    private fun applyViewportBounds() {
        val scaledWidth = if (width > 0) (width * viewportScale).roundToInt() else LayoutParams.MATCH_PARENT
        val scaledHeight = if (height > 0) (height * viewportScale).roundToInt() else LayoutParams.MATCH_PARENT
        viewport.layoutParams = LayoutParams(
            scaledWidth,
            scaledHeight,
            Gravity.CENTER
        )
        Log.d(
            LOG_TAG,
            "applyViewportBounds host=${width}x${height} viewport=${scaledWidth}x${scaledHeight} scale=$viewportScale"
        )
        viewport.requestLayout()
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
        previewVisibleRows?.let { return it }
        val viewportHeight = if (viewport.height > 0) viewport.height else height
        val tileSpan = (baseTileSize + tileGap).coerceAtLeast(1)
        return ((viewportHeight / tileSpan) + 1).coerceIn(3, 6)
    }

    private fun computeVisibleCols(): Int {
        previewVisibleCols?.let { return it }
        val viewportWidth = if (viewport.width > 0) viewport.width else width
        val tileSpan = (baseTileSize + tileGap).coerceAtLeast(1)
        return ((viewportWidth / tileSpan) + 1).coerceIn(3, 8)
    }

    private fun computeSegmentRowCount(visibleRows: Int): Int {
        return if (uniformPreviewMode) {
            (visibleRows * 4).coerceIn(8, 16)
        } else {
            (visibleRows + 2).coerceIn(4, 10)
        }
    }

    private fun computeSegmentTilesPerRow(visibleCols: Int): Int {
        return if (uniformPreviewMode) {
            (visibleCols + 2).coerceIn(5, 8)
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
        return minOf(widthBased, heightBased)
    }

    private fun computeSegmentHeightPx(rowCount: Int, tileSize: Int): Int {
        return (rowCount * tileSize) + ((rowCount - 1).coerceAtLeast(0) * tileGap)
    }

    private fun computeSegmentWidthPx(tilesPerRow: Int, tileSize: Int): Int {
        return (tilesPerRow * tileSize) + ((tilesPerRow - 1).coerceAtLeast(0) * tileGap)
    }

    private fun maybeLogFrame(frameTimeNanos: Long, deltaSeconds: Float) {
        if (lastFrameLogNanos == 0L ||
            frameTimeNanos - lastFrameLogNanos >= FRAME_LOG_INTERVAL_MS * 1_000_000L
        ) {
            Log.d(
                LOG_TAG,
                "frame delta=${"%.4f".format(deltaSeconds)} offset=${"%.1f".format(scrollOffsetPx)} translationY=${"%.1f".format(scrollStrip.translationY)}"
            )
            lastFrameLogNanos = frameTimeNanos
        }
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()
}
