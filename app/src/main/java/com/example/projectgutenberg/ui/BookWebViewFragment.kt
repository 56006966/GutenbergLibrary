package com.example.projectgutenberg.ui

import android.content.res.ColorStateList
import android.util.Base64
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import coil.load
import com.example.projectgutenberg.R
import com.example.projectgutenberg.data.local.BookDatabase
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.data.remote.BookDto
import com.example.projectgutenberg.data.remote.RetrofitInstance
import com.example.projectgutenberg.data.repository.BookRepository
import com.example.projectgutenberg.databinding.FragmentBookWebviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileInputStream

class BookWebViewFragment : Fragment() {

    private var _binding: FragmentBookWebviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: BookRepository
    private val args: BookWebViewFragmentArgs by navArgs()
    private var pageBodies: List<String> = emptyList()
    private var currentPageIndex = 0
    private var currentTextZoom = 115
    private var inkModeEnabled = false
    private var fallbackText: String? = null
    private var pendingPageIndex = 0
    private lateinit var uiPreferences: ReaderUiPreferences
    private var isPageFlipInProgress = false
    private var pendingRevealForward: Boolean? = null
    private var hasPlayedOpeningAnimation = false
    private var restoredFromInstanceState = false
    private var lastNavigationEventAt = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookWebviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = BookDatabase.getDatabase(requireContext()).bookDao()
        repository = BookRepository(dao, RetrofitInstance.api)
        uiPreferences = ReaderUiPreferences(requireContext())
        restoredFromInstanceState = savedInstanceState != null

        restoreReaderState(savedInstanceState)
        requireActivity().title = args.bookTitle
        binding.bookTitleText.text = args.bookTitle

        setupWebView()
        setupChapterControls()
        setupKeyboardShortcuts()
        applyReaderTheme()
        animateEntrance()
        loadBook()
    }

    private fun setupWebView() {
        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean = true

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    pendingRevealForward?.let { forward ->
                        startRevealAnimation(forward)
                    }
                }
            }
            setBackgroundColor(Color.parseColor(ReaderUiPalette.LIGHT_BACKGROUND))
            settings.apply {
                javaScriptEnabled = false
                domStorageEnabled = true
                builtInZoomControls = true
                displayZoomControls = false
                textZoom = currentTextZoom
            }
        }
    }

    private fun setupChapterControls() {
        binding.textSizeButton.setOnClickListener {
            showTextSizeMenu(it)
        }
        binding.inkModeButton.setOnClickListener {
            inkModeEnabled = !inkModeEnabled
            uiPreferences.setDarkModeEnabled(inkModeEnabled)
            binding.inkModeButton.text = getString(
                if (inkModeEnabled) R.string.ink_mode_on else R.string.ink_mode_off
            )
            applyReaderTheme()
            rerenderCurrentContent()
        }
        binding.pageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && pageBodies.isNotEmpty()) {
                    binding.chapterPositionText.text = getString(
                        R.string.chapter_position,
                        progress + 1,
                        pageBodies.size
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val targetIndex = seekBar?.progress ?: return
                if (targetIndex != currentPageIndex) {
                    animateToPage(targetIndex, forward = targetIndex < currentPageIndex)
                } else {
                    renderCurrentPage()
                }
            }
        })
    }

    private fun setupKeyboardShortcuts() {
        val keyHandler = View.OnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            if (event.repeatCount > 0) return@OnKeyListener true

            val now = SystemClock.elapsedRealtime()
            if (now - lastNavigationEventAt < KEY_NAVIGATION_THROTTLE_MS) {
                return@OnKeyListener true
            }

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    lastNavigationEventAt = now
                    goToPreviousPage()
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    lastNavigationEventAt = now
                    goToNextPage()
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    lastNavigationEventAt = now
                    binding.webView.pageUp(false)
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    lastNavigationEventAt = now
                    binding.webView.pageDown(false)
                    true
                }
                else -> false
            }
        }

        binding.readerRoot.setOnKeyListener(keyHandler)
        binding.webView.setOnKeyListener(keyHandler)
        binding.readerRoot.requestFocus()
    }

    private fun animateEntrance() {
        binding.readerCard.alpha = 0f
        binding.readerCard.translationY = 48f
        binding.readerCard.scaleX = 0.96f
        binding.readerCard.scaleY = 0.96f
        binding.readerCard.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260L)
            .start()
    }

    private fun loadBook() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.book_opening_status)

        lifecycleScope.launch {
            try {
                val localBook = withContext(Dispatchers.IO) { repository.getLocalBook(args.bookId) }
                if (!restoredFromInstanceState) {
                    pendingPageIndex = localBook?.lastPageIndex ?: 0
                }
                prepareOpeningCover(localBook?.coverPath ?: localBook?.coverUrl)
                val savedEpub = localBook?.epubPath?.let(::File)?.takeIf { it.exists() && it.length() > 10000 }
                if (savedEpub != null) {
                    Log.d("BookWebViewFragment", "Opening saved EPUB for ${args.bookId} from ${savedEpub.absolutePath}")
                    showEpub(savedEpub)
                    return@launch
                }

                binding.statusText.text = getString(R.string.book_downloading_status)
                val epubFile = withContext(Dispatchers.IO) {
                    repository.downloadEpub(
                        requireContext(),
                        buildFallbackEpubUrl(args.bookId),
                        args.bookId
                    )
                }

                saveToLibrary(epubFile)
                showEpub(epubFile)
            } catch (directEpubError: Exception) {
                try {
                    val remoteBook = withContext(Dispatchers.IO) {
                        repository.getBookDetails(args.bookId)
                    }
                    val epubUrl = remoteBook.formats
                        ?.entries
                        ?.firstOrNull { it.key.startsWith("application/epub+zip") }
                        ?.value

                    if (epubUrl != null) {
                        prepareOpeningCover(
                            remoteBook.formats?.get("image/jpeg")?.replace("http://", "https://")
                                ?: buildFallbackCoverUrl(args.bookId)
                        )
                        val epubFile = withContext(Dispatchers.IO) {
                            repository.downloadEpub(requireContext(), epubUrl, args.bookId)
                        }
                        saveToLibrary(epubFile, remoteBook)
                        showEpub(epubFile)
                    } else {
                        showTextFallback(remoteBook)
                    }
                } catch (e: Exception) {
                    binding.progressBar.visibility = View.GONE
                    binding.errorText.visibility = View.VISIBLE
                    binding.statusText.visibility = View.VISIBLE
                    binding.errorText.text =
                        getString(R.string.failed_load_book, e.message ?: directEpubError.message ?: "Unknown error")
                    binding.statusText.text = getString(R.string.book_failed_status)
                }
            }
        }
    }

    private suspend fun showEpub(epubFile: File) {
        fallbackText = null
        pageBodies = withContext(Dispatchers.IO) {
            loadCachedOrExtractPages(epubFile)
        }.ifEmpty {
            listOf("<p>No readable pages were found in this EPUB.</p>")
        }

        currentPageIndex = pendingPageIndex.coerceIn(0, pageBodies.lastIndex)
        binding.progressBar.visibility = View.GONE
        binding.statusText.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.chapterControls.visibility = if (pageBodies.size > 1) View.VISIBLE else View.GONE
        binding.pageSeekBar.visibility = if (pageBodies.size > 1) View.VISIBLE else View.GONE
        renderCurrentPage()
        playOpeningCoverAnimationIfNeeded()
    }

    private fun extractEpubPages(epubFile: File): List<String> {
        val epubBook = FileInputStream(epubFile).use { input ->
            EpubReader().readEpub(input)
        }

        val pages = mutableListOf<String>()

        epubBook.spine.spineReferences.forEach { spineReference ->
            val raw = decodeResource(spineReference.resource.data)
            val body = BODY_REGEX.find(raw)?.groupValues?.get(1) ?: raw
            if (isRenderablePage(body)) {
                pages += splitIntoPages(body)
            }
        }

        return pages
    }

    private fun loadCachedOrExtractPages(epubFile: File): List<String> {
        readPageCache(epubFile)?.takeIf { it.isNotEmpty() }?.let { return it }

        val extractedPages = extractEpubPages(epubFile)
        writePageCache(epubFile, extractedPages)
        return extractedPages
    }

    private fun readPageCache(epubFile: File): List<String>? {
        val cacheFile = pageCacheFileFor(epubFile)
        if (!cacheFile.exists() || cacheFile.lastModified() < epubFile.lastModified()) return null

        return runCatching {
            cacheFile.readLines()
                .filter { it.isNotBlank() }
                .map { encoded ->
                    String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                }
        }.getOrNull()
    }

    private fun writePageCache(epubFile: File, pages: List<String>) {
        if (pages.isEmpty()) return

        val cacheFile = pageCacheFileFor(epubFile)
        runCatching {
            val encoded = pages.joinToString("\n") { page ->
                Base64.encodeToString(page.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
            cacheFile.writeText(encoded)
        }
    }

    private fun pageCacheFileFor(epubFile: File): File {
        return File(epubFile.parentFile ?: requireContext().cacheDir, "${epubFile.nameWithoutExtension}.pages")
    }

    private fun isRenderablePage(body: String): Boolean {
        val hasImage = IMG_REGEX.containsMatchIn(body)
        val visibleText = TAG_REGEX.replace(body, " ")
            .replace(ENTITY_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

        return hasImage || visibleText.length >= 120
    }

    private fun splitIntoPages(body: String): List<String> {
        val contentBlocks = BLOCK_REGEX.findAll(body)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (contentBlocks.isEmpty()) {
            return body.chunked(PAGE_CHAR_LIMIT)
                .filter { it.isNotBlank() }
                .map { it.trim() }
        }

        val pages = mutableListOf<String>()
        val builder = StringBuilder()

        contentBlocks.forEach { block ->
            val isIllustrationBlock = IMG_REGEX.containsMatchIn(block)
            if (isIllustrationBlock) {
                if (builder.isNotEmpty()) {
                    pages += builder.toString()
                    builder.clear()
                }
                pages += wrapIllustrationBlock(block)
                return@forEach
            }

            if (builder.isNotEmpty() && builder.length + block.length > PAGE_CHAR_LIMIT) {
                pages += builder.toString()
                builder.clear()
            }
            builder.append(block)
        }

        if (builder.isNotEmpty()) {
            pages += builder.toString()
        }

        return pages
    }

    private fun wrapIllustrationBlock(block: String): String {
        return """
            <div style="display:flex; min-height:100%; align-items:center; justify-content:center; padding:20px 0;">
                <div style="width:100%;">$block</div>
            </div>
        """.trimIndent()
    }

    private fun buildPageHtml(pageBody: String): String {
        val background = if (inkModeEnabled) "#111111" else "#faf7f0"
        val textColor = if (inkModeEnabled) "#d8d1bf" else "#111111"
        return """
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <style>
                    html {
                        background: $background;
                    }
                    body {
                        font-family: serif;
                        line-height: 1.6;
                        padding: 28px 24px 32px 24px;
                        margin: 0;
                        min-height: 100vh;
                        box-sizing: border-box;
                        color: $textColor;
                        background: $background;
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                    }
                    a {
                        color: inherit;
                        text-decoration: none;
                        pointer-events: none;
                    }
                </style>
            </head>
            <body>$pageBody</body>
            </html>
        """.trimIndent()
    }

    private fun renderCurrentPage() {
        if (pageBodies.isEmpty()) return

        binding.webView.loadDataWithBaseURL(
            "https://www.gutenberg.org/",
            buildPageHtml(pageBodies[currentPageIndex]),
            "text/html",
            "UTF-8",
            null
        )
        binding.chapterPositionText.text = getString(
            R.string.chapter_position,
            currentPageIndex + 1,
            pageBodies.size
        )
        binding.pageSeekBar.max = (pageBodies.size - 1).coerceAtLeast(0)
        if (!binding.pageSeekBar.isPressed) {
            binding.pageSeekBar.progress = currentPageIndex
        }
        persistReadingPosition()
    }

    private fun goToPreviousPage() {
        if (currentPageIndex > 0) {
            animateToPage(currentPageIndex - 1, forward = true)
        }
    }

    private fun goToNextPage() {
        if (currentPageIndex < pageBodies.lastIndex) {
            animateToPage(currentPageIndex + 1, forward = false)
        }
    }

    private fun animateToPage(targetIndex: Int, forward: Boolean) {
        if (targetIndex !in pageBodies.indices || isPageFlipInProgress) return
        lastNavigationEventAt = SystemClock.elapsedRealtime()

        val snapshot = captureCurrentPageBitmap()
        if (snapshot == null) {
            currentPageIndex = targetIndex
            renderCurrentPage()
            return
        }

        isPageFlipInProgress = true
        val width = binding.pageSurface.width.takeIf { it > 0 }?.toFloat() ?: 240f
        val travel = width * 0.06f
        val turn = if (forward) -88f else 88f
        val overlayOffset = if (forward) -travel else travel
        val lift = binding.pageSurface.height * 0.035f

        binding.pageFlipOverlay.setImageBitmap(snapshot)
        binding.pageFlipOverlay.visibility = View.VISIBLE
        binding.pageFlipShadow.visibility = View.VISIBLE
        binding.pageFlipShadow.alpha = 0f
        binding.pageFlipOverlay.alpha = 1f
        binding.pageFlipOverlay.translationX = 0f
        binding.pageFlipOverlay.rotationY = 0f
        binding.pageFlipOverlay.scaleX = 1f
        binding.pageFlipOverlay.scaleY = 1f
        binding.pageFlipOverlay.rotationX = 0f
        binding.pageFlipOverlay.cameraDistance = width * 24f
        binding.pageFlipOverlay.pivotY = 0f
        binding.pageFlipOverlay.pivotX =
            if (forward) binding.pageFlipOverlay.width.toFloat() else 0f

        binding.webView.animate().cancel()
        binding.pageFlipOverlay.animate().cancel()
        binding.pageFlipShadow.animate().cancel()
        binding.webView.alpha = 0f

        binding.pageFlipOverlay.animate()
            .rotationY(turn)
            .rotationX(8f)
            .translationX(overlayOffset)
            .translationY(-lift)
            .scaleX(0.96f)
            .setDuration(220L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                currentPageIndex = targetIndex
                pendingRevealForward = forward
                renderCurrentPage()
                binding.pageSurface.postDelayed({
                    pendingRevealForward?.let { revealForward ->
                        startRevealAnimation(revealForward)
                    }
                }, 120L)
            }
            .start()

        binding.pageFlipShadow.animate()
            .alpha(0.24f)
            .setDuration(180L)
            .start()
    }

    private fun startRevealAnimation(forward: Boolean) {
        if (!isAdded || _binding == null || pendingRevealForward == null) return

        pendingRevealForward = null
        val width = binding.pageSurface.width.takeIf { it > 0 }?.toFloat() ?: 240f
        val travel = width * 0.06f
        val incomingTurn = if (forward) 88f else -88f
        val incomingOffset = if (forward) travel else -travel
        val lift = binding.pageSurface.height * 0.035f

        binding.webView.cameraDistance = width * 24f
        binding.webView.pivotY = 0f
        binding.webView.pivotX = if (forward) 0f else binding.webView.width.toFloat()
        binding.webView.rotationY = incomingTurn
        binding.webView.rotationX = 8f
        binding.webView.translationX = incomingOffset
        binding.webView.translationY = -lift
        binding.webView.scaleX = 0.96f
        binding.webView.alpha = 0.78f

        binding.webView.animate()
            .rotationY(0f)
            .rotationX(0f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .alpha(1f)
            .setDuration(240L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.pageFlipOverlay.setImageDrawable(null)
                binding.pageFlipOverlay.visibility = View.GONE
                binding.pageFlipShadow.visibility = View.GONE
                binding.pageFlipOverlay.rotationY = 0f
                binding.pageFlipOverlay.rotationX = 0f
                binding.pageFlipOverlay.translationX = 0f
                binding.pageFlipOverlay.translationY = 0f
                binding.pageFlipShadow.alpha = 0f
                isPageFlipInProgress = false
            }
            .start()

        binding.pageFlipShadow.animate()
            .alpha(0f)
            .setDuration(240L)
            .start()
    }

    private fun captureCurrentPageBitmap(): Bitmap? {
        val pageWidth = binding.pageSurface.width
        val pageHeight = binding.pageSurface.height
        val width = binding.webView.width
        val height = binding.webView.height
        if (pageWidth <= 0 || pageHeight <= 0 || width <= 0 || height <= 0 || binding.webView.visibility != View.VISIBLE) {
            return null
        }

        return runCatching {
            val contentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                binding.webView.draw(canvas)
            }

            Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val density = resources.displayMetrics.density
                val horizontalInset = (18f * density).toInt()
                val verticalInset = (12f * density).toInt()
                val pageRect = RectF(
                    horizontalInset.toFloat(),
                    verticalInset.toFloat(),
                    (pageWidth - horizontalInset).toFloat(),
                    (pageHeight - verticalInset).toFloat()
                )
                val contentRect = Rect(
                    horizontalInset,
                    verticalInset,
                    pageWidth - horizontalInset,
                    pageHeight - verticalInset
                )
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (inkModeEnabled) Color.parseColor("#33000000") else Color.parseColor("#22000000")
                }
                val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(
                        if (inkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
                    )
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(
                        if (inkModeEnabled) "#2f2f2f" else "#d8cfb7"
                    )
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f * density
                }

                canvas.drawRoundRect(
                    RectF(
                        pageRect.left + (4f * density),
                        pageRect.top + (4f * density),
                        pageRect.right + (4f * density),
                        pageRect.bottom + (4f * density)
                    ),
                    10f * density,
                    10f * density,
                    shadowPaint
                )
                canvas.drawRoundRect(pageRect, 10f * density, 10f * density, pagePaint)
                canvas.drawBitmap(contentBitmap, null, contentRect, null)
                canvas.drawRoundRect(pageRect, 10f * density, 10f * density, borderPaint)
            }
        }.getOrNull()
    }

    private fun updateTextZoom(delta: Int) {
        currentTextZoom = (currentTextZoom + delta).coerceIn(80, 220)
        binding.webView.settings.textZoom = currentTextZoom
    }

    private fun setTextZoom(zoom: Int) {
        currentTextZoom = zoom
        binding.webView.settings.textZoom = currentTextZoom
    }

    private fun showTextSizeMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 90, 0, getString(R.string.text_size_small))
            menu.add(0, 115, 1, getString(R.string.text_size_medium))
            menu.add(0, 140, 2, getString(R.string.text_size_large))
            menu.add(0, 170, 3, getString(R.string.text_size_extra_large))
            setOnMenuItemClickListener { item ->
                setTextZoom(item.itemId)
                true
            }
        }.show()
    }

    private fun rerenderCurrentContent() {
        binding.webView.setBackgroundColor(
            Color.parseColor(
                if (inkModeEnabled) ReaderUiPalette.DARK_BACKGROUND else ReaderUiPalette.LIGHT_BACKGROUND
            )
        )
        fallbackText?.let {
            renderFallbackText(it)
            return
        }
        if (pageBodies.isNotEmpty()) {
            renderCurrentPage()
        }
    }

    private fun decodeResource(bytes: ByteArray): String {
        return runCatching { bytes.toString(Charsets.UTF_8) }
            .getOrElse { String(bytes) }
    }

    private suspend fun showTextFallback(remoteBook: BookDto) {
        val textUrl = remoteBook.formats
            ?.entries
            ?.firstOrNull { entry ->
                entry.key.startsWith("text/html") || entry.key.startsWith("text/plain")
            }
            ?.value
            ?: throw IllegalStateException("No EPUB or text format available")

        binding.statusText.visibility = View.GONE

        val text = withContext(Dispatchers.IO) {
            repository.downloadBookText(textUrl)
        }
        fallbackText = text

        val libraryBook = remoteBook.toLibraryEntity().copy(text = text)
        withContext(Dispatchers.IO) {
            repository.insertBook(libraryBook)
        }

        binding.progressBar.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.chapterControls.visibility = View.GONE
        renderFallbackText(text)
    }

    private fun renderFallbackText(text: String) {
        val safeText = text.replace("<", "&lt;").replace(">", "&gt;")
        val body = "<pre style='white-space: pre-wrap; margin: 0;'>$safeText</pre>"
        binding.webView.loadDataWithBaseURL(null, buildPageHtml(body), "text/html", "UTF-8", null)
    }

    private suspend fun saveToLibrary(epubFile: File, remoteBook: BookDto? = null) {
        withContext(Dispatchers.IO) {
            val existing = repository.getLocalBook(args.bookId)
            val coverUrl = existing?.coverUrl
                ?: remoteBook?.formats?.get("image/jpeg")?.replace("http://", "https://")
                ?: buildFallbackCoverUrl(args.bookId)
            val coverPath = existing?.coverPath
                ?: repository.downloadCover(requireContext(), coverUrl, args.bookId)?.absolutePath

            val baseBook = remoteBook?.toLibraryEntity() ?: BookEntity(
                id = args.bookId,
                title = args.bookTitle,
                author = existing?.author ?: "Project Gutenberg",
                genre = existing?.genre ?: "Classic literature",
                downloads = existing?.downloads ?: 0,
                coverUrl = coverUrl,
                coverPath = coverPath,
                text = epubFile.absolutePath,
                epubPath = epubFile.absolutePath,
                status = BookEntity.STATUS_LIBRARY
            )
            repository.insertBook(
                (existing ?: baseBook).copy(
                    status = BookEntity.STATUS_LIBRARY,
                    text = epubFile.absolutePath,
                    epubPath = epubFile.absolutePath,
                    coverUrl = coverUrl,
                    coverPath = coverPath
                )
            )
        }
    }

    private fun buildFallbackEpubUrl(bookId: Int): String {
        return "https://www.gutenberg.org/cache/epub/$bookId/pg$bookId.epub"
    }

    private fun buildFallbackCoverUrl(bookId: Int): String {
        return "https://www.gutenberg.org/cache/epub/$bookId/pg$bookId.cover.medium.jpg"
    }

    private fun BookDto.toLibraryEntity(): BookEntity {
        return BookEntity(
            id = id,
            title = title,
            author = authors.joinToString { it.name },
            genre = subjects?.firstOrNull() ?: "Unknown",
            downloads = download_count,
            coverUrl = formats?.get("image/jpeg")?.replace("http://", "https://")
                ?: buildFallbackCoverUrl(id),
            coverPath = null,
            text = null,
            epubPath = null,
            status = BookEntity.STATUS_LIBRARY
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INK_MODE, inkModeEnabled)
        outState.putInt(STATE_TEXT_ZOOM, currentTextZoom)
        outState.putInt(STATE_PAGE_INDEX, currentPageIndex)
    }

    override fun onPause() {
        persistReadingPosition()
        super.onPause()
    }

    private fun restoreReaderState(savedInstanceState: Bundle?) {
        inkModeEnabled = savedInstanceState?.getBoolean(STATE_INK_MODE)
            ?: uiPreferences.isDarkModeEnabled()
        currentTextZoom = savedInstanceState?.getInt(STATE_TEXT_ZOOM) ?: 115
        pendingPageIndex = savedInstanceState?.getInt(STATE_PAGE_INDEX) ?: 0
    }

    private fun applyReaderTheme() {
        val background = Color.parseColor(
            if (inkModeEnabled) ReaderUiPalette.DARK_BACKGROUND else ReaderUiPalette.LIGHT_BACKGROUND
        )
        val surface = Color.parseColor(
            if (inkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
        )
        val text = Color.parseColor(
            if (inkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
        )
        val secondaryText = Color.parseColor(
            if (inkModeEnabled) ReaderUiPalette.DARK_SECONDARY_TEXT else ReaderUiPalette.LIGHT_SECONDARY_TEXT
        )
        val buttonBackground = Color.parseColor(
            if (inkModeEnabled) ReaderUiPalette.DARK_BUTTON else ReaderUiPalette.LIGHT_BUTTON
        )

        binding.readerRoot.setBackgroundColor(background)
        binding.readerCard.setCardBackgroundColor(surface)
        binding.webView.setBackgroundColor(background)

        binding.bookTitleText.setTextColor(text)
        binding.statusText.setTextColor(secondaryText)
        binding.errorText.setTextColor(Color.parseColor(ReaderUiPalette.ERROR_TEXT))
        binding.chapterPositionText.setTextColor(secondaryText)
        binding.pageSeekBar.progressTintList = ColorStateList.valueOf(text)
        binding.pageSeekBar.thumbTintList = ColorStateList.valueOf(text)

        styleActionButton(binding.textSizeButton, buttonBackground, text)
        styleActionButton(binding.inkModeButton, buttonBackground, text)

        binding.inkModeButton.text = getString(
            if (inkModeEnabled) R.string.ink_mode_on else R.string.ink_mode_off
        )
    }

    private fun styleActionButton(button: android.widget.Button, background: Int, text: Int) {
        button.backgroundTintList = ColorStateList.valueOf(background)
        button.setTextColor(text)
    }

    private fun persistReadingPosition() {
        if (!this::repository.isInitialized) return
        lifecycleScope.launch(Dispatchers.IO) {
            repository.updateLastPageIndex(args.bookId, currentPageIndex)
        }
    }

    private fun prepareOpeningCover(coverSource: String?) {
        if (hasPlayedOpeningAnimation || coverSource.isNullOrBlank()) return
        binding.openingCoverImage.load(coverSource) {
            crossfade(false)
            placeholder(android.R.drawable.ic_menu_report_image)
            error(android.R.drawable.ic_menu_report_image)
        }
        binding.openingCoverImage.visibility = View.VISIBLE
        binding.openingCoverImage.alpha = 0f
        binding.openingCoverImage.scaleX = 0.76f
        binding.openingCoverImage.scaleY = 0.76f
    }

    private fun playOpeningCoverAnimationIfNeeded() {
        if (hasPlayedOpeningAnimation || binding.openingCoverImage.drawable == null) {
            binding.webView.alpha = 1f
            return
        }

        hasPlayedOpeningAnimation = true
        binding.webView.alpha = 0f
        binding.openingCoverImage.visibility = View.VISIBLE
        binding.openingCoverImage.pivotX = binding.openingCoverImage.width.toFloat().coerceAtLeast(1f)
        binding.openingCoverImage.pivotY = binding.openingCoverImage.height / 2f
        binding.openingCoverImage.cameraDistance =
            binding.pageSurface.width.coerceAtLeast(1) * 24f

        binding.openingCoverImage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.openingCoverImage.animate()
                    .rotationY(-96f)
                    .alpha(0.4f)
                    .setDuration(280L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        binding.openingCoverImage.visibility = View.GONE
                        binding.openingCoverImage.rotationY = 0f
                        binding.openingCoverImage.alpha = 1f
                        binding.webView.animate().alpha(1f).setDuration(140L).start()
                    }
                    .start()
            }
            .start()
    }

    private companion object {
        const val PAGE_CHAR_LIMIT = 2200
        const val KEY_NAVIGATION_THROTTLE_MS = 180L
        const val STATE_INK_MODE = "state_ink_mode"
        const val STATE_TEXT_ZOOM = "state_text_zoom"
        const val STATE_PAGE_INDEX = "state_page_index"
        val BODY_REGEX = Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val PARAGRAPH_REGEX = Regex("<p\\b[^>]*>.*?</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val BLOCK_REGEX = Regex(
            "<(figure|div|p|img|h[1-6]|blockquote|pre)\\b[^>]*>.*?</\\1>|<img\\b[^>]*?/?>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val TAG_REGEX = Regex("<[^>]+>")
        val ENTITY_REGEX = Regex("&[^;]+;")
        val WHITESPACE_REGEX = Regex("\\s+")
        val IMG_REGEX = Regex("<img\\b", RegexOption.IGNORE_CASE)
    }
}
