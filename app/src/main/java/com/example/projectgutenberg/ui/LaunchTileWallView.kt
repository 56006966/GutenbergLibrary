package com.example.projectgutenberg.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import coil.load
import com.example.projectgutenberg.R
import com.example.projectgutenberg.data.local.BookEntity
import kotlin.math.roundToInt

class LaunchTileWallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val PLACEHOLDER_BOOK_COUNT = 100
        private const val PLACEHOLDER_TITLE = "Project Gutenberg"
        private const val PLACEHOLDER_AUTHOR = "Classic literature"
        private const val SCROLL_DURATION_MS = 22_000L
    }

    private val viewport = FrameLayout(context)
    private val tiltedStage = FrameLayout(context)
    private val scrollStrip = LinearLayout(context)
    private var stripAnimator: ObjectAnimator? = null

    private val tileSize = 92.dp()
    private val tileGap = 14.dp()
    private val rowCount = 5
    private val tilesPerRow = 8
    private var books: List<BookEntity> = emptyList()

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

        tiltedStage.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        tiltedStage.clipChildren = false
        tiltedStage.clipToPadding = false
        tiltedStage.rotationX = 60f
        tiltedStage.rotation = -18f
        tiltedStage.translationX = (-150).dp().toFloat()
        tiltedStage.translationY = (-90).dp().toFloat()

        scrollStrip.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        scrollStrip.orientation = LinearLayout.VERTICAL
        scrollStrip.clipChildren = false
        scrollStrip.clipToPadding = false

        tiltedStage.addView(scrollStrip)
        viewport.addView(tiltedStage)
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

    fun pauseAnimation() {
        stripAnimator?.pause()
    }

    fun resumeAnimation() {
        stripAnimator?.let {
            if (it.isPaused) it.resume()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rebuildWall()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pauseAnimation()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resumeAnimation()
        }
        return true
    }

    private fun rebuildWall() {
        if (!isAttachedToWindow) return
        if (books.isEmpty()) return

        stopAnimation()
        scrollStrip.removeAllViews()

        val segment = createWallSegment()
        val duplicate = createWallSegment()
        scrollStrip.addView(segment)
        scrollStrip.addView(duplicate)

        scrollStrip.post {
            val segmentHeight = segment.height.toFloat()
            if (segmentHeight <= 0f) return@post

            scrollStrip.translationY = 0f
            stripAnimator = ObjectAnimator.ofFloat(scrollStrip, "translationY", 0f, -segmentHeight).apply {
                duration = SCROLL_DURATION_MS
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun createWallSegment(): LinearLayout {
        val segment = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        repeat(rowCount) { rowIndex ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = tileGap
                    leftMargin = if (rowIndex % 2 == 1) tileSize / 2 else 0
                }
            }

            val startIndex = (rowIndex * tilesPerRow) % books.size
            repeat(tilesPerRow) { tileIndex ->
                row.addView(createTileView(books[(startIndex + tileIndex) % books.size]))
            }
            segment.addView(row)
        }

        return segment
    }

    private fun createTileView(book: BookEntity): View {
        val card = CardView(context).apply {
            radius = 18.dp().toFloat()
            cardElevation = 10.dp().toFloat()
            setCardBackgroundColor(Color.parseColor("#1A1D24"))
            layoutParams = LinearLayout.LayoutParams(tileSize, tileSize).apply {
                rightMargin = tileGap
            }
        }

        val image = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#242833"))
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
        stripAnimator?.cancel()
        stripAnimator = null
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()
}
