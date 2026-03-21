package com.example.projectgutenberg.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.roundToInt

class BookWebViewFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentBookWebviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: BookRepository
    private val args: BookWebViewFragmentArgs by navArgs()
    private var pageBodies: List<String> = emptyList()
    private var currentPageIndex = 0
    private var currentTextZoom = 115
    private var inkModeEnabled = false
    private var fallbackText: String? = null
    private var readerCoverSource: String? = null
    private var pendingPageIndex = 0
    private lateinit var uiPreferences: ReaderUiPreferences
    private var isPageFlipInProgress = false
    private var pendingRevealForward: Boolean? = null
    private var hasPlayedOpeningAnimation = false
    private var restoredFromInstanceState = false
    private var lastNavigationEventAt = 0L
    private var currentScrollRatio = 0f
    private var pendingScrollRatio = 0f
    private var isReaderChromeVisible = true
    private var frontMatterPageCount = 0
    private lateinit var gestureDetector: GestureDetectorCompat
    private var webViewScrollListener: ViewTreeObserver.OnScrollChangedListener? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var isReadingAloud = false
    private var isReadAloudPaused = false
    private var ttsUnavailable = false
    private var availableVoices: List<android.speech.tts.Voice> = emptyList()
    private var pendingTtsAction: PendingTtsAction? = null
    private var currentPageSpeakableContent = ""
    private var currentPageWordBoundaries: List<TtsWordBoundary> = emptyList()
    private var currentTtsWordIndex = 0
    private var pendingTtsWordIndex = 0
    private var currentTtsUtteranceStartWordIndex = 0
    private var pendingResumeReadAloudAfterPageLoad = false

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
        setupHeaderActions()
        setupChapterControls()
        setupTtsPositionControls()
        setupKeyboardShortcuts()
        setupTouchNavigation()
        applyReaderTheme()
        enterReaderImmersiveMode()
        animateEntrance()
        loadBook()
    }

    private fun setupHeaderActions() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.readAloudButton.setOnClickListener {
            toggleReadAloud()
        }
        binding.readAloudButton.setOnLongClickListener {
            showVoicePicker()
            true
        }
        updateReadAloudUi()
    }

    private fun setupWebView() {
        binding.webView.apply {
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.deny()
                    Log.d("BookWebViewFragment", "Denied WebView permission request: ${request?.resources?.joinToString()}")
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean = true

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    restorePendingScrollPosition()
                    prepareTtsWebViewState()
                    pendingRevealForward?.let { forward ->
                        startRevealAnimation(forward)
                    }
                    if (pendingResumeReadAloudAfterPageLoad && isReadingAloud && !isReadAloudPaused) {
                        pendingResumeReadAloudAfterPageLoad = false
                        binding.webView.postDelayed(
                            { if (isReadingAloud && !isReadAloudPaused) speakCurrentPageAloud() },
                            180L
                        )
                    }
                }
            }
            setBackgroundColor(Color.parseColor(ReaderUiPalette.LIGHT_BACKGROUND))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                builtInZoomControls = true
                displayZoomControls = false
                textZoom = currentTextZoom
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = true
            }
        }
        webViewScrollListener = ViewTreeObserver.OnScrollChangedListener {
            currentScrollRatio = captureCurrentScrollRatio()
        }
        binding.webView.viewTreeObserver.addOnScrollChangedListener(webViewScrollListener)
    }

    private fun setupChapterControls() {
        binding.textSizeButton.setOnClickListener {
            showTextSizeMenu(it)
        }
        binding.inkModeButton.setOnClickListener {
            inkModeEnabled = !inkModeEnabled
            uiPreferences.setDarkModeEnabled(inkModeEnabled)
            applyReaderTheme()
            rerenderCurrentContent()
        }
        binding.pageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && pageBodies.isNotEmpty()) {
                    binding.chapterPositionText.text = getString(
                        R.string.chapter_position,
                        formatDisplayPageNumber(progress),
                        formatDisplayPageCount(progress)
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

    private fun setupTtsPositionControls() {
        binding.ttsPositionSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || currentPageWordBoundaries.isEmpty()) return
                currentTtsWordIndex = ReaderTtsLogic.clampWordIndex(progress, currentPageWordBoundaries.size)
                updateTtsPositionUi()
                highlightActiveTtsWord(currentTtsWordIndex)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (currentPageWordBoundaries.isEmpty()) return
                currentTtsWordIndex = ReaderTtsLogic.clampWordIndex(
                    seekBar?.progress ?: 0,
                    currentPageWordBoundaries.size
                )
                updateTtsPositionUi()
                highlightActiveTtsWord(currentTtsWordIndex)
                if (isReadingAloud) {
                    speakCurrentPageAloud()
                } else {
                    isReadAloudPaused = true
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = getString(R.string.tts_position_ready)
                }
            }
        })
    }

    private fun setupTouchNavigation() {
        gestureDetector = GestureDetectorCompat(requireContext(), ReaderGestureListener())
        val listener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
        binding.pageSurface.setOnTouchListener(listener)
        binding.webView.setOnTouchListener(listener)
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
        binding.statusText.text = getString(
            if (restoredFromInstanceState) R.string.book_reorienting_status
            else R.string.book_opening_status
        )

        lifecycleScope.launch {
            val localBook = withContext(Dispatchers.IO) { repository.getLocalBook(args.bookId) }
            val savedEpub = localBook?.epubPath?.let(::File)?.takeIf { it.exists() && it.length() > 10000 }
            Log.d(
                "BookWebViewFragment",
                "Initial load decision for ${args.bookId}: hasLocalBook=${localBook != null}, hasSavedEpub=${savedEpub != null}, restored=$restoredFromInstanceState"
            )
            try {
                if (!restoredFromInstanceState) {
                    pendingPageIndex = localBook?.lastPageIndex ?: 0
                }
                prepareOpeningCover(localBook?.coverPath ?: localBook?.coverUrl)
                if (shouldOpenSavedEpub(savedEpub)) {
                    val localEpub = checkNotNull(savedEpub)
                    binding.statusText.text = getString(R.string.book_opening_saved_status)
                    Log.d("BookWebViewFragment", "Opening saved EPUB for ${args.bookId} from ${localEpub.absolutePath}")
                    showEpub(localEpub)
                    return@launch
                }

                binding.statusText.text = getString(R.string.book_downloading_status)
                Log.d("BookWebViewFragment", "No saved EPUB for ${args.bookId}; downloading fallback EPUB")
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
                if (savedEpub != null) {
                    Log.d("BookWebViewFragment", "Falling back to saved EPUB for ${args.bookId} from ${savedEpub.absolutePath}")
                    showEpub(savedEpub)
                    return@launch
                }
                try {
                    Log.d("BookWebViewFragment", "Fallback EPUB failed for ${args.bookId}; requesting remote book details")
                    val remoteBook = withContext(Dispatchers.IO) {
                        repository.getBookDetails(args.bookId)
                    }
                    val epubUrl = repository.selectPreferredEpubUrl(remoteBook.formats)

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
        val prependedCover = prependSyntheticCoverPageIfNeeded()
        if (prependedCover && pendingPageIndex > 0) {
            pendingPageIndex += 1
        }
        frontMatterPageCount = detectFrontMatterPageCount(pageBodies)

        currentPageIndex = pendingPageIndex.coerceIn(0, pageBodies.lastIndex)
        binding.progressBar.visibility = View.GONE
        binding.statusText.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.chapterControls.visibility = if (pageBodies.size > 1) View.VISIBLE else View.GONE
        binding.pageSeekBar.visibility = if (pageBodies.size > 1) View.VISIBLE else View.GONE
        renderCurrentPage()
        setReaderChromeVisible(false, animate = false)
        playOpeningCoverAnimationIfNeeded()
    }

    private fun extractEpubPages(epubFile: File): List<String> {
        val epubBook = FileInputStream(epubFile).use { input ->
            EpubReader().readEpub(input)
        }
        val resourceLookup = buildResourceLookup(epubBook)
        val inlineImageCache = mutableMapOf<String, String>()

        val pages = mutableListOf<String>()

        epubBook.spine.spineReferences.forEach { spineReference ->
            val documentResource = spineReference.resource
            val raw = decodeResource(documentResource.data)
            val body = BODY_REGEX.find(raw)?.groupValues?.get(1) ?: raw
            val hydratedBody = replaceImageSources(
                body = body,
                documentHref = documentResource.href.orEmpty(),
                resourceLookup = resourceLookup,
                inlineImageCache = inlineImageCache
            )
            val preparedBody = cleanupBodyMarkup(hydratedBody)
            if (isRenderablePage(preparedBody) || IMG_TAG_REGEX.containsMatchIn(preparedBody)) {
                pages += splitIntoPages(preparedBody)
            }
        }

        return pages.filter(ReaderPageLogic::hasMeaningfulPageContent)
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
        return File(epubFile.parentFile ?: requireContext().cacheDir, "${epubFile.nameWithoutExtension}.v10.pages")
    }

    private fun isRenderablePage(body: String): Boolean {
        val visibleText = extractVisibleText(body)
        return visibleText.length >= 40
    }

    private fun splitIntoPages(body: String): List<String> {
        val rawBlocks = BLOCK_REGEX.findAll(body)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val contentBlocks = ReaderPageLogic.mergeDecorativeInitialBlocks(rawBlocks)
            .map(::formatContentBlockForReader)
            .toList()

        if (contentBlocks.isEmpty()) {
            return body.chunked(PAGE_CHAR_LIMIT)
                .filter { it.isNotBlank() }
                .map { it.trim() }
        }

        val pages = mutableListOf<String>()
        val builder = StringBuilder()

        contentBlocks.forEach { block ->
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

    private fun cleanupBodyMarkup(body: String): String {
        return body
            .replace(EMPTY_FIGURE_REGEX, " ")
            .replace(EMPTY_DIV_REGEX, " ")
    }

    private fun prependSyntheticCoverPageIfNeeded(): Boolean {
        val coverSource = readerCoverSource ?: return false
        if (pageBodies.isEmpty()) return false
        if (pageBodies.first().contains(coverSource, ignoreCase = true)) return false

        val coverPage = """
            <section class="reader-illustration reader-illustration--hero">
                <img class="reader-image reader-image--content" src="$coverSource" alt="Book cover" />
            </section>
        """.trimIndent()
        pageBodies = listOf(coverPage) + pageBodies
        return true
    }

    private fun mergeDecorativeInitialBlocks(blocks: List<String>): List<String> {
        if (blocks.isEmpty()) return blocks

        val mergedBlocks = mutableListOf<String>()
        var index = 0
        while (index < blocks.size) {
            val currentBlock = blocks[index]
            val initialInnerMarkup = extractDecorativeInitialMarkup(currentBlock)
            if (initialInnerMarkup != null && index + 1 < blocks.size) {
                mergedBlocks += prependDecorativeInitialMarkup(initialInnerMarkup, blocks[index + 1])
                index += 2
                continue
            }

            mergedBlocks += currentBlock
            index += 1
        }
        return mergedBlocks
    }

    private fun extractDecorativeInitialMarkup(block: String): String? {
        if (!isLikelyDecorativeInitialBlock(block)) return null
        return INITIAL_BLOCK_INNER_REGEX.find(block)?.groupValues?.get(1)?.trim()
    }

    private fun prependDecorativeInitialMarkup(initialMarkup: String, block: String): String {
        val openingTag = BLOCK_OPENING_TAG_REGEX.find(block) ?: return "$initialMarkup$block"
        return block.replaceRange(openingTag.range, "${openingTag.value}$initialMarkup")
    }

    private fun isLikelyDecorativeInitialBlock(block: String): Boolean {
        if (extractVisibleText(block).isNotBlank()) return false
        val imageTags = IMG_TAG_REGEX.findAll(block).map { it.value }.toList()
        if (imageTags.size != 1) return false
        return isDecorativeImageTag(imageTags.first())
    }

    private fun hasMeaningfulPageContent(page: String): Boolean {
        val visibleText = extractVisibleText(page)
        if (visibleText.isNotBlank()) return true

        val imageTags = IMG_TAG_REGEX.findAll(page).map { it.value }.toList()
        if (imageTags.isEmpty()) return false
        return imageTags.any { !isDecorativeImageTag(it) }
    }

    private fun formatContentBlockForReader(block: String): String {
        if (!IMG_TAG_REGEX.containsMatchIn(block)) return block

        val decoratedBlock = decorateImagesForReader(block)
        if (isDecorativeInitialBlock(decoratedBlock)) {
            return decoratedBlock
        }

        val imageCount = IMG_TAG_REGEX.findAll(decoratedBlock).count()
        val visibleTextLength = extractVisibleText(decoratedBlock).length

        return when {
            imageCount > 1 ->
                """<section class="reader-illustration reader-illustration--gallery">$decoratedBlock</section>"""
            visibleTextLength <= STANDALONE_ILLUSTRATION_TEXT_LIMIT ->
                """<section class="reader-illustration reader-illustration--hero">$decoratedBlock</section>"""
            visibleTextLength <= CAPTIONED_ILLUSTRATION_TEXT_LIMIT ->
                """<section class="reader-illustration reader-illustration--captioned">$decoratedBlock</section>"""
            else ->
                """<section class="reader-illustration reader-illustration--inline">$decoratedBlock</section>"""
        }
    }

    private fun decorateImagesForReader(block: String): String {
        return IMG_TAG_REGEX.replace(block) { match ->
            val tag = match.value
            val variant = if (isDecorativeImageTag(tag)) "reader-image--decorative" else "reader-image--content"
            addClassToTag(tag, "reader-image $variant")
        }
    }

    private fun addClassToTag(tag: String, classesToAdd: String): String {
        val classMatch = CLASS_ATTR_REGEX.find(tag)
        if (classMatch == null) {
            return tag.replaceFirst("<img", "<img class=\"$classesToAdd\"")
        }

        val existingClasses = classMatch.groupValues[2]
        val mergedClasses = (existingClasses.split(WHITESPACE_REGEX) + classesToAdd.split(' '))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")

        return tag.replaceRange(
            classMatch.range,
            "class=\"$mergedClasses\""
        )
    }

    private fun isDecorativeInitialBlock(block: String): Boolean {
        return INITIAL_ONLY_BLOCK_REGEX.containsMatchIn(block)
    }

    private fun isDecorativeImageTag(tag: String): Boolean {
        val normalized = tag.lowercase()
        val width = SIZE_ATTR_REGEX.find(normalized.substringAfter("width", ""))?.groupValues?.getOrNull(1)?.toIntOrNull()
        val height = SIZE_ATTR_REGEX.find(normalized.substringAfter("height", ""))?.groupValues?.getOrNull(1)?.toIntOrNull()
        return "align=\"left\"" in normalized ||
            "float:left" in normalized ||
            "class=\"drop" in normalized ||
            "class='drop" in normalized ||
            "class=\"initial" in normalized ||
            "class='initial" in normalized ||
            "src=\"drop" in normalized ||
            "src='drop" in normalized ||
            LETTER_IMAGE_NAME_REGEX.containsMatchIn(normalized) ||
            (width != null && width <= DECORATIVE_INITIAL_MAX_WIDTH_PX) ||
            (height != null && height <= DECORATIVE_INITIAL_MAX_HEIGHT_PX)
    }

    private fun extractVisibleText(body: String): String {
        return TAG_REGEX.replace(body, " ")
            .replace(ENTITY_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun buildPageHtml(pageBody: String): String {
        val background = if (inkModeEnabled) "#111111" else "#faf7f0"
        val textColor = if (inkModeEnabled) "#d8d1bf" else "#111111"
        val figureSurface = if (inkModeEnabled) "#171717" else "#f1ead8"
        val figureBorder = if (inkModeEnabled) "#353535" else "#d7ccb2"
        val figureShadow = if (inkModeEnabled) "rgba(0,0,0,0.28)" else "rgba(58,40,13,0.10)"
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
                        margin: 0 auto;
                        min-height: 100vh;
                        box-sizing: border-box;
                        color: $textColor;
                        background: $background;
                        max-width: 980px;
                    }
                    .reader-image {
                        max-width: 100%;
                        height: auto;
                    }
                    .reader-image--content {
                        display: block;
                        margin: 0.5rem auto 0.85rem auto;
                        border-radius: 14px;
                    }
                    .reader-image--decorative {
                        display: inline-block;
                        vertical-align: text-bottom;
                        margin: 0;
                    }
                    .reader-illustration {
                        margin: 0 0 1.35rem 0;
                        padding: 0.9rem;
                        border-radius: 20px;
                        background: $figureSurface;
                        border: 1px solid $figureBorder;
                        box-shadow: 0 14px 34px $figureShadow;
                        overflow: hidden;
                    }
                    .reader-illustration figure,
                    .reader-illustration div,
                    .reader-illustration p {
                        margin: 0;
                    }
                    .reader-illustration .reader-image--content {
                        float: none !important;
                        clear: both !important;
                    }
                    .reader-illustration--hero {
                        min-height: calc(100vh - 110px);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 1.2rem;
                        text-align: center;
                    }
                    .reader-illustration--hero .reader-image--content {
                        max-width: min(96%, 720px);
                        max-height: 72vh;
                        object-fit: contain;
                        margin-bottom: 0;
                    }
                    .reader-illustration--captioned {
                        padding: 1rem 1rem 0.85rem 1rem;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        text-align: center;
                    }
                    .reader-illustration--captioned .reader-image--content {
                        width: auto;
                        max-width: min(100%, 760px);
                        max-height: 55vh;
                        object-fit: contain;
                    }
                    .reader-illustration--captioned figcaption,
                    .reader-illustration--captioned small,
                    .reader-illustration--captioned em {
                        display: block;
                        margin-top: 0.75rem;
                        opacity: 0.82;
                    }
                    .reader-illustration--inline {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: flex-start;
                        gap: 0.85rem;
                    }
                    .reader-illustration--inline > * {
                        width: 100%;
                        max-width: 860px;
                    }
                    .reader-illustration--inline .reader-image--content {
                        width: auto;
                        max-width: min(100%, 760px);
                        max-height: 58vh;
                        object-fit: contain;
                        margin-left: auto;
                        margin-right: auto;
                    }
                    .reader-illustration--gallery {
                        display: grid;
                        gap: 0.85rem;
                        justify-items: center;
                    }
                    .reader-illustration--gallery .reader-image--content {
                        max-height: 40vh;
                        width: auto;
                        max-width: min(100%, 760px);
                        object-fit: contain;
                    }
                    img[align="left"],
                    img[align="right"],
                    img[style*="float:left"],
                    img[style*="float: left"],
                    img[style*="float:right"],
                    img[style*="float: right"],
                    img[class*="drop"],
                    img[class*="initial"],
                    img[src*="drop"],
                    .dropcap,
                    .dropcap img,
                    .figleft,
                    .figleft img,
                    .figright,
                    .figright img,
                    .initial,
                    .initial img {
                        float: none !important;
                        clear: none !important;
                        display: inline-block !important;
                        vertical-align: text-bottom !important;
                        margin: 0 0.12em 0 0 !important;
                        max-height: 1.7em !important;
                        max-width: none !important;
                        width: auto !important;
                    }
                    .decorative-initial-wrapper {
                        display: inline;
                        width: auto !important;
                    }
                    .decorative-initial-wrapper img {
                        margin-right: 0.12em !important;
                    }
                    p {
                        margin: 0 0 1em 0;
                    }
                    .tts-word--active {
                        color: #b53a24 !important;
                        background: rgba(225, 152, 107, 0.28);
                        border-radius: 0.18em;
                        box-shadow: 0 0 0 0.08em rgba(225, 152, 107, 0.18);
                    }
                    @media (max-width: 720px) {
                        body {
                            padding: 20px 16px 24px 16px;
                            max-width: none;
                        }
                        .reader-illustration {
                            border-radius: 16px;
                            padding: 0.7rem;
                        }
                        .reader-illustration--hero {
                            min-height: auto;
                            padding: 0.85rem;
                        }
                        .reader-illustration--hero .reader-image--content {
                            max-height: 56vh;
                        }
                        .reader-illustration--inline .reader-image--content,
                        .reader-illustration--captioned .reader-image--content,
                        .reader-illustration--gallery .reader-image--content {
                            max-height: 48vh;
                        }
                    }
                    a {
                        color: inherit;
                        text-decoration: none;
                        pointer-events: none;
                    }
                </style>
            </head>
            <body>$pageBody</body>
            <script>
                (function() {
                    if (window.readerTts) {
                        return;
                    }

                    function wrapTextNode(node, state) {
                        var text = node.nodeValue;
                        if (!text || !text.trim()) {
                            return;
                        }

                        var fragment = document.createDocumentFragment();
                        var regex = /\\S+/g;
                        var lastIndex = 0;
                        var match;

                        while ((match = regex.exec(text)) !== null) {
                            if (match.index > lastIndex) {
                                fragment.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
                            }

                            var span = document.createElement('span');
                            span.className = 'tts-word';
                            span.setAttribute('data-tts-word-index', String(state.wordCount));
                            span.textContent = match[0];
                            fragment.appendChild(span);
                            state.wordCount += 1;
                            lastIndex = match.index + match[0].length;
                        }

                        if (lastIndex < text.length) {
                            fragment.appendChild(document.createTextNode(text.slice(lastIndex)));
                        }

                        node.parentNode.replaceChild(fragment, node);
                    }

                    function walk(node, state) {
                        if (!node) {
                            return;
                        }
                        if (node.nodeType === Node.TEXT_NODE) {
                            wrapTextNode(node, state);
                            return;
                        }
                        if (node.nodeType !== Node.ELEMENT_NODE) {
                            return;
                        }
                        if (node.tagName === 'SCRIPT' || node.tagName === 'STYLE') {
                            return;
                        }

                        var children = Array.prototype.slice.call(node.childNodes);
                        for (var i = 0; i < children.length; i += 1) {
                            walk(children[i], state);
                        }
                    }

                    window.readerTts = {
                        prepared: false,
                        wordCount: 0,
                        activeIndex: -1,
                        prepare: function() {
                            if (this.prepared) {
                                return this.wordCount;
                            }
                            var state = { wordCount: 0 };
                            walk(document.body, state);
                            this.wordCount = state.wordCount;
                            this.prepared = true;
                            return this.wordCount;
                        },
                        clearActiveWord: function() {
                            if (this.activeIndex < 0) {
                                return;
                            }
                            var previous = document.querySelector('[data-tts-word-index="' + this.activeIndex + '"]');
                            if (previous) {
                                previous.classList.remove('tts-word--active');
                            }
                            this.activeIndex = -1;
                        },
                        setActiveWord: function(index) {
                            this.prepare();
                            this.clearActiveWord();
                            var next = document.querySelector('[data-tts-word-index="' + index + '"]');
                            if (!next) {
                                return;
                            }
                            next.classList.add('tts-word--active');
                            this.activeIndex = index;
                            next.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'auto' });
                        }
                    };

                    window.readerTts.prepare();
                })();
            </script>
            </html>
        """.trimIndent()
    }

    private fun updateCurrentPageTtsModel() {
        currentPageSpeakableContent = currentPageSpeakableText()
        currentPageWordBoundaries = ReaderTtsLogic.buildWordBoundaries(currentPageSpeakableContent)
        currentTtsWordIndex = if (pendingTtsWordIndex > 0) {
            ReaderTtsLogic.clampWordIndex(pendingTtsWordIndex, currentPageWordBoundaries.size)
        } else {
            ReaderTtsLogic.clampWordIndex(currentTtsWordIndex, currentPageWordBoundaries.size)
        }
        pendingTtsWordIndex = 0
    }

    private fun prepareTtsWebViewState() {
        binding.webView.evaluateJavascript(
            "(window.readerTts && window.readerTts.prepare()) || 0;"
        ) { result ->
            val jsWordCount = result.trim('"').toIntOrNull() ?: 0
            if (jsWordCount != currentPageWordBoundaries.size) {
                currentTtsWordIndex = ReaderTtsLogic.clampWordIndex(currentTtsWordIndex, jsWordCount)
            }
            if (currentPageWordBoundaries.isNotEmpty() && (isReadingAloud || currentTtsWordIndex > 0)) {
                highlightActiveTtsWord(currentTtsWordIndex)
            } else {
                clearActiveTtsWordHighlight()
            }
        }
    }

    private fun highlightActiveTtsWord(wordIndex: Int) {
        if (_binding == null) return
        if (currentPageWordBoundaries.isEmpty()) {
            clearActiveTtsWordHighlight()
            return
        }

        val clampedWordIndex = ReaderTtsLogic.clampWordIndex(wordIndex, currentPageWordBoundaries.size)
        binding.webView.evaluateJavascript(
            "window.readerTts && window.readerTts.setActiveWord($clampedWordIndex);",
            null
        )
    }

    private fun clearActiveTtsWordHighlight() {
        if (_binding == null) return
        binding.webView.evaluateJavascript(
            "window.readerTts && window.readerTts.clearActiveWord();",
            null
        )
    }

    private fun updateTtsPositionUi() {
        if (_binding == null) return

        val hasWords = currentPageWordBoundaries.isNotEmpty()
        binding.ttsPositionSeekBar.visibility = if (hasWords) View.VISIBLE else View.GONE
        binding.ttsPositionText.visibility = if (hasWords) View.VISIBLE else View.GONE

        if (!hasWords) {
            binding.ttsPositionSeekBar.max = 0
            binding.ttsPositionSeekBar.progress = 0
            return
        }

        val clampedWordIndex = ReaderTtsLogic.clampWordIndex(currentTtsWordIndex, currentPageWordBoundaries.size)
        if (binding.ttsPositionSeekBar.max != currentPageWordBoundaries.lastIndex.coerceAtLeast(0)) {
            binding.ttsPositionSeekBar.max = currentPageWordBoundaries.lastIndex.coerceAtLeast(0)
        }
        if (!binding.ttsPositionSeekBar.isPressed) {
            binding.ttsPositionSeekBar.progress = clampedWordIndex
        }
        binding.ttsPositionText.text = getString(
            R.string.tts_position_label,
            clampedWordIndex + 1,
            currentPageWordBoundaries.size
        )
    }

    private fun renderCurrentPage() {
        if (pageBodies.isEmpty()) return

        val preservedScrollRatio = pendingScrollRatio
        updateCurrentPageTtsModel()
        binding.webView.loadDataWithBaseURL(
            "https://www.gutenberg.org/",
            buildPageHtml(pageBodies[currentPageIndex]),
            "text/html",
            "UTF-8",
            null
        )
        binding.chapterPositionText.text = getString(
            R.string.chapter_position,
            formatDisplayPageNumber(currentPageIndex),
            formatDisplayPageCount(currentPageIndex)
        )
        binding.pageSeekBar.max = (pageBodies.size - 1).coerceAtLeast(0)
        if (!binding.pageSeekBar.isPressed) {
            binding.pageSeekBar.progress = currentPageIndex
        }
        currentScrollRatio = preservedScrollRatio.coerceIn(0f, 1f)
        updateTtsPositionUi()
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
        if (isReadingAloud && !isReadAloudPaused) {
            textToSpeech?.stop()
            pendingResumeReadAloudAfterPageLoad = true
        }

        val snapshot = captureCurrentPageBitmap()
        if (snapshot == null) {
            currentPageIndex = targetIndex
            currentScrollRatio = 0f
            pendingScrollRatio = 0f
            currentTtsWordIndex = 0
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
                currentScrollRatio = 0f
                pendingScrollRatio = 0f
                currentTtsWordIndex = 0
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
        uiPreferences.setTextZoom(currentTextZoom)
    }

    private fun setTextZoom(zoom: Int) {
        currentTextZoom = zoom
        binding.webView.settings.textZoom = currentTextZoom
        uiPreferences.setTextZoom(currentTextZoom)
    }

    private fun showTextSizeMenu(anchor: View) {
        val options = listOf(
            90 to getString(R.string.text_size_small),
            115 to getString(R.string.text_size_medium),
            140 to getString(R.string.text_size_large),
            170 to getString(R.string.text_size_extra_large)
        )
        val selectedIndex = options.indexOfFirst { it.first == currentTextZoom }.coerceAtLeast(0)

        AlertDialog.Builder(anchor.context)
            .setTitle(R.string.choose_text_size)
            .setSingleChoiceItems(
                options.map { it.second }.toTypedArray(),
                selectedIndex
            ) { dialog, which ->
                setTextZoom(options[which].first)
                dialog.dismiss()
            }
            .show()
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
        setReaderChromeVisible(false, animate = false)
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
                downloadedAt = System.currentTimeMillis(),
                isFavorite = existing?.isFavorite ?: false,
                status = BookEntity.STATUS_LIBRARY
            )
            repository.insertBook(
                (existing ?: baseBook).copy(
                    status = BookEntity.STATUS_LIBRARY,
                    text = epubFile.absolutePath,
                    epubPath = epubFile.absolutePath,
                    coverUrl = coverUrl,
                    coverPath = coverPath,
                    downloadedAt = existing?.downloadedAt?.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    isFavorite = existing?.isFavorite ?: false
                )
            )
        }
    }

    private fun buildFallbackEpubUrl(bookId: Int): String {
        return "https://www.gutenberg.org/cache/epub/$bookId/pg$bookId-images.epub"
    }

    private fun buildFallbackCoverUrl(bookId: Int): String {
        return "https://www.gutenberg.org/cache/epub/$bookId/pg$bookId.cover.medium.jpg"
    }

    private fun isLegacyGenericEpub(epubFile: File): Boolean {
        val name = epubFile.name.lowercase()
        return name.matches(Regex("""book_\d+\.epub"""))
    }

    private fun isReusableSavedEpub(epubFile: File): Boolean {
        val name = epubFile.name.lowercase()
        if ("_noimages" in name || "noimages" in name) return true
        if (isLegacyGenericEpub(epubFile)) return false
        return true
    }

    private fun formatPageNumber(pageNumber: Int): String {
        if (pageNumber <= 0) return pageNumber.toString()
        if (pageNumber > 3999) return pageNumber.toString()

        val numerals = listOf(
            1000 to "M",
            900 to "CM",
            500 to "D",
            400 to "CD",
            100 to "C",
            90 to "XC",
            50 to "L",
            40 to "XL",
            10 to "X",
            9 to "IX",
            5 to "V",
            4 to "IV",
            1 to "I"
        )

        var remaining = pageNumber
        val builder = StringBuilder()
        numerals.forEach { (value, numeral) ->
            while (remaining >= value) {
                builder.append(numeral)
                remaining -= value
            }
        }
        return builder.toString()
    }

    private fun detectFrontMatterPageCount(pages: List<String>): Int {
        val firstMainPageIndex = pages.indexOfFirst { page ->
            val normalized = extractVisibleText(page).lowercase()
            normalized.contains("chapter i") ||
                normalized.contains("chapter 1") ||
                normalized.contains("chapter one") ||
                normalized.contains("book i") ||
                normalized.contains("book 1") ||
                normalized.contains("book one")
        }
        return if (firstMainPageIndex <= 0) 0 else firstMainPageIndex
    }

    private fun formatDisplayPageNumber(pageIndex: Int): String {
        return if (frontMatterPageCount > 0 && pageIndex < frontMatterPageCount) {
            formatPageNumber(pageIndex + 1).lowercase()
        } else {
            (pageIndex - frontMatterPageCount + 1).coerceAtLeast(1).toString()
        }
    }

    private fun formatDisplayPageCount(pageIndex: Int): String {
        return if (frontMatterPageCount > 0 && pageIndex < frontMatterPageCount) {
            formatPageNumber(frontMatterPageCount).lowercase()
        } else {
            (pageBodies.size - frontMatterPageCount).coerceAtLeast(1).toString()
        }
    }

    private fun enterReaderImmersiveMode() {
        val window = activity?.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun exitReaderImmersiveMode() {
        val window = activity?.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())
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
            downloadedAt = 0L,
            isFavorite = false,
            status = BookEntity.STATUS_LIBRARY
        )
    }

    override fun onDestroyView() {
        exitReaderImmersiveMode()
        webViewScrollListener?.let { listener ->
            _binding?.webView?.viewTreeObserver
                ?.takeIf { it.isAlive }
                ?.removeOnScrollChangedListener(listener)
        }
        _binding?.topChrome?.animate()?.cancel()
        _binding?.bottomChrome?.animate()?.cancel()
        _binding?.pageFlipOverlay?.animate()?.cancel()
        _binding?.pageFlipShadow?.animate()?.cancel()
        _binding?.openingCoverImage?.animate()?.cancel()
        _binding?.webView?.apply {
            stopLoading()
            onPause()
            webViewClient = WebViewClient()
            setOnTouchListener(null)
            setOnKeyListener(null)
        }
        webViewScrollListener = null
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INK_MODE, inkModeEnabled)
        outState.putInt(STATE_TEXT_ZOOM, currentTextZoom)
        outState.putInt(STATE_PAGE_INDEX, currentPageIndex)
        outState.putFloat(STATE_SCROLL_RATIO, captureCurrentScrollRatio())
        outState.putInt(STATE_TTS_WORD_INDEX, currentTtsWordIndex)
    }

    override fun onPause() {
        persistReadingPosition()
        super.onPause()
    }

    private fun restoreReaderState(savedInstanceState: Bundle?) {
        inkModeEnabled = savedInstanceState?.getBoolean(STATE_INK_MODE)
            ?: uiPreferences.isDarkModeEnabled()
        currentTextZoom = savedInstanceState?.getInt(STATE_TEXT_ZOOM)
            ?: uiPreferences.getTextZoom()
        pendingPageIndex = savedInstanceState?.getInt(STATE_PAGE_INDEX)
            ?: uiPreferences.getLastPageIndex(args.bookId)
        currentScrollRatio = savedInstanceState?.getFloat(STATE_SCROLL_RATIO)
            ?: uiPreferences.getLastScrollRatio(args.bookId)
        pendingScrollRatio = currentScrollRatio
        pendingTtsWordIndex = savedInstanceState?.getInt(STATE_TTS_WORD_INDEX) ?: 0
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
        binding.topChrome.setBackgroundColor(surface)
        binding.bottomChrome.setBackgroundColor(surface)

        binding.bookTitleText.setTextColor(text)
        binding.statusText.setTextColor(secondaryText)
        binding.errorText.setTextColor(Color.parseColor(ReaderUiPalette.ERROR_TEXT))
        binding.chapterPositionText.setTextColor(secondaryText)
        binding.pageSeekBar.progressTintList = ColorStateList.valueOf(text)
        binding.pageSeekBar.thumbTintList = ColorStateList.valueOf(text)
        binding.ttsPositionText.setTextColor(secondaryText)
        binding.ttsPositionSeekBar.progressTintList = ColorStateList.valueOf(text)
        binding.ttsPositionSeekBar.thumbTintList = ColorStateList.valueOf(text)
        binding.backButton.imageTintList = ColorStateList.valueOf(text)

        styleActionButton(binding.textSizeButton, buttonBackground, text)
        binding.inkModeButton.backgroundTintList = ColorStateList.valueOf(buttonBackground)
        binding.inkModeButton.imageTintList = ColorStateList.valueOf(text)
        binding.readAloudButton.backgroundTintList = ColorStateList.valueOf(buttonBackground)
        binding.readAloudButton.imageTintList = ColorStateList.valueOf(text)
        binding.inkModeButton.setImageResource(
            if (inkModeEnabled) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon
        )
        updateReadAloudUi()
    }

    private fun styleActionButton(button: android.widget.Button, background: Int, text: Int) {
        button.backgroundTintList = ColorStateList.valueOf(background)
        button.setTextColor(text)
    }

    private fun setReaderChromeVisible(visible: Boolean, animate: Boolean = true) {
        if (!_bindingAvailable()) return
        isReaderChromeVisible = visible

        val topTarget = if (visible) 0f else -binding.topChrome.height.toFloat().coerceAtLeast(0f)
        val bottomTarget = if (visible) 0f else binding.bottomChrome.height.toFloat().coerceAtLeast(0f)

        if (animate) {
            binding.topChrome.animate()
                .translationY(topTarget)
                .alpha(if (visible) 1f else 0f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            binding.bottomChrome.animate()
                .translationY(bottomTarget)
                .alpha(if (visible) 1f else 0f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            binding.topChrome.translationY = topTarget
            binding.topChrome.alpha = if (visible) 1f else 0f
            binding.bottomChrome.translationY = bottomTarget
            binding.bottomChrome.alpha = if (visible) 1f else 0f
        }
    }

    private fun _bindingAvailable(): Boolean = _binding != null

    private fun persistReadingPosition() {
        if (!this::repository.isInitialized) return
        currentScrollRatio = captureCurrentScrollRatio()
        uiPreferences.setLastPageIndex(args.bookId, currentPageIndex)
        uiPreferences.setLastScrollRatio(args.bookId, currentScrollRatio)
        lifecycleScope.launch(Dispatchers.IO) {
            repository.updateLastPageIndex(args.bookId, currentPageIndex)
        }
    }

    private fun captureCurrentScrollRatio(): Float {
        val maxScroll = currentMaxScrollY()
        if (maxScroll <= 0) return currentScrollRatio.coerceIn(0f, 1f)
        val webView = _binding?.webView ?: return currentScrollRatio.coerceIn(0f, 1f)
        return (webView.scrollY.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
    }

    private fun restorePendingScrollPosition(attempt: Int = 0) {
        if (!isAdded || _binding == null) return

        val maxScroll = currentMaxScrollY()
        if (maxScroll <= 0) {
            if (attempt < MAX_SCROLL_RESTORE_ATTEMPTS) {
                binding.webView.postDelayed(
                    { restorePendingScrollPosition(attempt + 1) },
                    SCROLL_RESTORE_RETRY_MS
                )
            }
            return
        }

        val normalizedRatio = pendingScrollRatio.coerceIn(0f, 1f)
        val targetScroll = (normalizedRatio * maxScroll).roundToInt()
        binding.webView.post {
            binding.webView.scrollTo(0, targetScroll)
            currentScrollRatio = normalizedRatio
        }
    }

    private fun currentMaxScrollY(): Int {
        val webView = _binding?.webView ?: return 0
        val contentHeightPx = (webView.contentHeight * webView.scale).roundToInt()
        return (contentHeightPx - webView.height).coerceAtLeast(0)
    }

    private fun prepareOpeningCover(coverSource: String?) {
        readerCoverSource = coverSource
        if (hasPlayedOpeningAnimation || coverSource.isNullOrBlank()) return
        binding.openingCoverImage.load(coverSource) {
            crossfade(false)
            placeholder(android.R.drawable.ic_menu_report_image)
            error(android.R.drawable.ic_menu_report_image)
        }
        binding.openingCoverImage.visibility = View.VISIBLE
        binding.openingCoverImage.alpha = 0f
        binding.openingCoverImage.scaleX = 0.94f
        binding.openingCoverImage.scaleY = 0.94f
    }

    private fun playOpeningCoverAnimationIfNeeded() {
        if (hasPlayedOpeningAnimation || binding.openingCoverImage.drawable == null) {
            binding.webView.alpha = 1f
            return
        }

        hasPlayedOpeningAnimation = true
        binding.webView.alpha = 0f
        binding.openingCoverImage.visibility = View.VISIBLE
        binding.openingCoverImage.pivotX = 0f
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
                    .rotationY(96f)
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

    private fun replaceImageSources(
        body: String,
        documentHref: String,
        resourceLookup: Map<String, Resource>,
        inlineImageCache: MutableMap<String, String>
    ): String {
        if (resourceLookup.isEmpty()) return body

        return IMG_SRC_REGEX.replace(body) { match ->
            val originalSrc = match.groupValues[2]
            val resolvedSrc = resolveRelativeHref(documentHref, originalSrc)
            val resource = resourceLookup[resolvedSrc]
                ?: resourceLookup[originalSrc.substringAfterLast('/')]
                ?: resourceLookup[resolvedSrc.substringAfterLast('/')]
                ?: return@replace match.value
            val embeddedSrc = inlineImageCache.getOrPut(resolvedSrc) {
                buildInlineImageSource(resource, resolvedSrc) ?: return@getOrPut originalSrc
            }

            "${match.groupValues[1]}$embeddedSrc${match.groupValues[3]}"
        }
    }

    private fun buildResourceLookup(epubBook: Book): Map<String, Resource> {
        return epubBook.resources.all
            .mapNotNull { resource ->
                val href = resource.href?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                listOf(
                    href to resource,
                    href.substringAfterLast('/') to resource,
                    href.removePrefix("../") to resource,
                    href.removePrefix("./") to resource
                ).distinctBy { it.first }
            }
            .flatten()
            .toMap()
    }

    private fun buildInlineImageSource(resource: Resource, href: String): String? {
        val mimeType = guessImageMimeType(href) ?: resource.mediaType?.name?.lowercase()
        val imageBytes = when {
            mimeType == null -> return null
            !mimeType.startsWith("image/") -> return null
            mimeType == "image/jpeg" || mimeType == "image/png" || mimeType == "image/webp" ->
                shrinkRasterImage(resource.data, mimeType)
            else -> resource.data
        }
        if (imageBytes.isEmpty()) return null
        if (imageBytes.size > MAX_INLINE_IMAGE_BYTES) return null
        val encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$encoded"
    }

    private fun shrinkRasterImage(source: ByteArray, mimeType: String): ByteArray {
        if (source.size <= TARGET_INLINE_IMAGE_BYTES) return source

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching byteArrayOf()
            if (bounds.outWidth >= HARD_SKIP_IMAGE_DIMENSION || bounds.outHeight >= HARD_SKIP_IMAGE_DIMENSION) {
                return@runCatching byteArrayOf()
            }
            if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_INLINE_IMAGE_PIXELS) {
                return@runCatching byteArrayOf()
            }

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > MAX_INLINE_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_INLINE_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }
            while (
                (bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight.toLong() / sampleSize) >
                TARGET_INLINE_IMAGE_PIXELS
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, decodeOptions)
                ?: return@runCatching byteArrayOf()

            ByteArrayOutputStream().use { output ->
                val format = Bitmap.CompressFormat.JPEG
                var quality = INITIAL_INLINE_IMAGE_QUALITY
                bitmap.compress(format, quality, output)
                while (output.size() > TARGET_INLINE_IMAGE_BYTES && quality > MIN_INLINE_IMAGE_QUALITY) {
                    output.reset()
                    quality -= INLINE_IMAGE_QUALITY_STEP
                    bitmap.compress(format, quality, output)
                }
                bitmap.recycle()
                output.toByteArray().takeIf { it.size <= MAX_INLINE_IMAGE_BYTES } ?: byteArrayOf()
            }
        }.getOrElse { byteArrayOf() }
    }

    private fun resolveRelativeHref(documentHref: String, imageHref: String): String {
        if (imageHref.startsWith("data:", ignoreCase = true)) return imageHref
        if (imageHref.startsWith("/")) return imageHref.removePrefix("/")

        val baseSegments = documentHref.substringBeforeLast('/', "").split('/')
            .filter { it.isNotBlank() }
            .toMutableList()
        imageHref.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (baseSegments.isNotEmpty()) baseSegments.removeAt(baseSegments.lastIndex)
                else -> baseSegments += segment
            }
        }
        return baseSegments.joinToString("/")
    }

    private fun guessImageMimeType(href: String): String? {
        return when (href.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            else -> null
        }
    }

    private inner class ReaderGestureListener : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val width = binding.pageSurface.width.takeIf { it > 0 } ?: return false
            val leftZone = width * 0.33f
            val rightZone = width * 0.67f
            return when {
                e.x <= leftZone -> {
                    goToPreviousPage()
                    true
                }
                e.x >= rightZone -> {
                    goToNextPage()
                    true
                }
                else -> {
                    setReaderChromeVisible(!isReaderChromeVisible)
                    true
                }
            }
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val start = e1 ?: return false
            val deltaX = e2.x - start.x
            val deltaY = e2.y - start.y
            if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.2f &&
                kotlin.math.abs(velocityY) >= SWIPE_VELOCITY_THRESHOLD
            ) {
                if (deltaY > 0) {
                    setReaderChromeVisible(true)
                } else {
                    setReaderChromeVisible(false)
                }
                return true
            }
            if (kotlin.math.abs(deltaX) < SWIPE_DISTANCE_THRESHOLD) return false
            if (kotlin.math.abs(deltaX) <= kotlin.math.abs(deltaY) * 1.35f) return false
            if (kotlin.math.abs(velocityX) < SWIPE_VELOCITY_THRESHOLD) return false

            if (deltaX > 0) {
                goToPreviousPage()
            } else {
                goToNextPage()
            }
            return true
        }
    }

    private companion object {
        const val PAGE_CHAR_LIMIT = 2200
        const val KEY_NAVIGATION_THROTTLE_MS = 180L
        const val SWIPE_DISTANCE_THRESHOLD = 96
        const val SWIPE_VELOCITY_THRESHOLD = 320
        const val TARGET_INLINE_IMAGE_BYTES = 220_000
        const val MAX_INLINE_IMAGE_BYTES = 450_000
        const val MAX_INLINE_IMAGE_DIMENSION = 960
        const val TARGET_INLINE_IMAGE_PIXELS = 960L * 960L
        const val MAX_INLINE_IMAGE_PIXELS = 2_000_000L
        const val HARD_SKIP_IMAGE_DIMENSION = 5000
        const val INITIAL_INLINE_IMAGE_QUALITY = 72
        const val MIN_INLINE_IMAGE_QUALITY = 38
        const val INLINE_IMAGE_QUALITY_STEP = 8
        const val STANDALONE_ILLUSTRATION_TEXT_LIMIT = 32
        const val CAPTIONED_ILLUSTRATION_TEXT_LIMIT = 220
        const val DECORATIVE_INITIAL_MAX_WIDTH_PX = 160
        const val DECORATIVE_INITIAL_MAX_HEIGHT_PX = 220
        const val MAX_SCROLL_RESTORE_ATTEMPTS = 6
        const val SCROLL_RESTORE_RETRY_MS = 32L
        const val STATE_INK_MODE = "state_ink_mode"
        const val STATE_TEXT_ZOOM = "state_text_zoom"
        const val STATE_PAGE_INDEX = "state_page_index"
        const val STATE_SCROLL_RATIO = "state_scroll_ratio"
        const val STATE_TTS_WORD_INDEX = "state_tts_word_index"
        val BODY_REGEX = Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val PARAGRAPH_REGEX = Regex("<p\\b[^>]*>.*?</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val BLOCK_REGEX = Regex(
            "<(figure|div|p|img|h[1-6]|blockquote|pre)\\b[^>]*>.*?</\\1>|<img\\b[^>]*?/?>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val TAG_REGEX = Regex("<[^>]+>")
        val ENTITY_REGEX = Regex("&[^;]+;")
        val WHITESPACE_REGEX = Regex("\\s+")
        val CLASS_ATTR_REGEX = Regex("""class\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
        val IMG_TAG_REGEX = Regex("<img\\b[^>]*?/?>", RegexOption.IGNORE_CASE)
        val IMG_SRC_REGEX = Regex("""(<img\b[^>]*?\bsrc\s*=\s*["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)
        val INITIAL_ONLY_BLOCK_REGEX = Regex(
            """<(p|div)\b[^>]*>\s*(?:<span\b[^>]*>\s*)?<img\b[^>]*?/?>\s*(?:</span>\s*)?</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val INITIAL_BLOCK_INNER_REGEX = Regex(
            """<(?:p|div)\b[^>]*>\s*((?:<span\b[^>]*>\s*)?<img\b[^>]*?/?>\s*(?:</span>\s*)?)</(?:p|div)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val BLOCK_OPENING_TAG_REGEX = Regex("""<([a-z0-9]+)\b[^>]*>""", RegexOption.IGNORE_CASE)
        val SIZE_ATTR_REGEX = Regex("""["'=:\s]([0-9]{1,4})""")
        val LETTER_IMAGE_NAME_REGEX = Regex("""[/_ -][a-z](?:\.[a-z0-9]{2,5}|[_-])""", RegexOption.IGNORE_CASE)
        val EMPTY_FIGURE_REGEX = Regex("<figure\\b[^>]*>\\s*</figure>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val EMPTY_DIV_REGEX = Regex("<div\\b[^>]*>\\s*</div>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ttsUnavailable = true
            isTtsReady = false
            availableVoices = emptyList()
            pendingTtsAction = null
            textToSpeech?.shutdown()
            textToSpeech = null
            activity?.runOnUiThread {
                if (_binding != null) {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = getString(R.string.tts_unavailable)
                    updateReadAloudUi()
                }
            }
            return
        }
        val tts = textToSpeech ?: return
        ttsUnavailable = false
        val defaultLanguageResult = tts.setLanguage(Locale.getDefault())
        if (
            defaultLanguageResult == TextToSpeech.LANG_MISSING_DATA ||
            defaultLanguageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            ttsUnavailable = true
            isTtsReady = false
            availableVoices = emptyList()
            pendingTtsAction = null
            textToSpeech?.shutdown()
            textToSpeech = null
            activity?.runOnUiThread {
                if (_binding != null) {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = getString(R.string.tts_unavailable)
                    updateReadAloudUi()
                }
            }
            return
        }

        availableVoices = tts.voices
            ?.filter { voice -> !voice.isNetworkConnectionRequired }
            ?.sortedBy { voice -> voice.locale.displayName }
            .orEmpty()

        val preferredVoiceName = uiPreferences.getPreferredVoiceName()
        val preferredVoice = availableVoices.firstOrNull { it.name == preferredVoiceName }
        if (preferredVoice != null) {
            tts.voice = preferredVoice
            tts.language = preferredVoice.locale
        } else {
            val fallbackVoice = availableVoices.firstOrNull { voice ->
                voice.locale.language == Locale.getDefault().language
            } ?: availableVoices.firstOrNull()
            fallbackVoice?.let { voice ->
                tts.voice = voice
                tts.language = voice.locale
                uiPreferences.setPreferredVoiceName(voice.name)
            }
        }

        isTtsReady = availableVoices.isNotEmpty()
        tts.setSpeechRate(1.0f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                activity?.runOnUiThread {
                    handleReadAloudRangeStart(start, end)
                }
            }

            override fun onDone(utteranceId: String?) {
                activity?.runOnUiThread {
                    handleReadAloudUtteranceComplete()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                activity?.runOnUiThread {
                    isReadingAloud = false
                    isReadAloudPaused = false
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = getString(R.string.tts_unavailable)
                    updateReadAloudUi()
                }
            }
        })

        activity?.runOnUiThread {
            updateReadAloudUi()
            if (_binding != null && isTtsReady && !isReadingAloud) {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.tts_voice_hint)
            }
            when (pendingTtsAction) {
                PendingTtsAction.TOGGLE_READ_ALOUD -> {
                    pendingTtsAction = null
                    toggleReadAloud()
                }
                PendingTtsAction.SHOW_VOICE_PICKER -> {
                    pendingTtsAction = null
                    showVoicePicker()
                }
                null -> Unit
            }
        }
    }

    private fun toggleReadAloud() {
        val tts = textToSpeech
        if (tts == null) {
            pendingTtsAction = PendingTtsAction.TOGGLE_READ_ALOUD
            ensureTextToSpeechInitialized()
            return
        }
        if (ttsUnavailable) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_unavailable)
            return
        }
        if (!isTtsReady) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_initializing)
            return
        }
        if (isReadingAloud) {
            tts.stop()
            isReadingAloud = false
            isReadAloudPaused = true
            clearActiveTtsWordHighlight()
            updateReadAloudUi()
            return
        }

        isReadingAloud = true
        isReadAloudPaused = false
        updateReadAloudUi()
        speakCurrentPageAloud()
    }

    private fun showVoicePicker() {
        if (textToSpeech == null) {
            pendingTtsAction = PendingTtsAction.SHOW_VOICE_PICKER
            ensureTextToSpeechInitialized()
            return
        }
        if (ttsUnavailable) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_unavailable)
            return
        }
        if (!isTtsReady) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_initializing)
            return
        }
        if (availableVoices.isEmpty()) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_no_voices)
            return
        }

        val voiceLabels = availableVoices.map { voice ->
            val localeName = voice.locale.getDisplayName(voice.locale)
            buildString {
                append(localeName.replaceFirstChar { it.titlecase(voice.locale) })
                val variant = voice.name.substringAfterLast("-", "")
                if (variant.isNotBlank() && !variant.equals(voice.locale.language, ignoreCase = true)) {
                    append(" • ")
                    append(variant)
                }
            }
        }.toTypedArray()

        val selectedIndex = availableVoices.indexOfFirst { it.name == uiPreferences.getPreferredVoiceName() }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_voice)
            .setSingleChoiceItems(voiceLabels, selectedIndex) { dialog, which ->
                val voice = availableVoices[which]
                textToSpeech?.voice = voice
                textToSpeech?.language = voice.locale
                uiPreferences.setPreferredVoiceName(voice.name)
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.tts_voice_hint)
                dialog.dismiss()
            }
            .show()
    }

    private fun currentPageSpeakableText(): String {
        val pageBody = pageBodies.getOrNull(currentPageIndex).orEmpty()
        return TAG_REGEX.replace(pageBody, " ")
            .replace(ENTITY_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun speakCurrentPageAloud() {
        val tts = textToSpeech ?: return
        val speakableText = ReaderTtsLogic.speakableSuffix(
            text = currentPageSpeakableContent,
            boundaries = currentPageWordBoundaries,
            startWordIndex = currentTtsWordIndex
        )
        if (speakableText.isBlank()) {
            handleReadAloudUtteranceComplete()
            return
        }
        currentTtsUtteranceStartWordIndex = ReaderTtsLogic.clampWordIndex(
            currentTtsWordIndex,
            currentPageWordBoundaries.size
        )
        highlightActiveTtsWord(currentTtsUtteranceStartWordIndex)
        updateTtsPositionUi()
        tts.stop()
        val speakResult = tts.speak(
            speakableText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "page_${args.bookId}_$currentPageIndex"
        )
        if (speakResult == TextToSpeech.ERROR) {
            isReadingAloud = false
            isReadAloudPaused = false
            ttsUnavailable = true
            isTtsReady = false
            textToSpeech?.shutdown()
            textToSpeech = null
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_unavailable)
            updateReadAloudUi()
        }
    }

    private fun handleReadAloudRangeStart(start: Int, end: Int) {
        if (!isReadingAloud || currentPageWordBoundaries.isEmpty()) return
        currentTtsWordIndex = ReaderTtsLogic.resolveWordIndexForRange(
            boundaries = currentPageWordBoundaries,
            startWordIndex = currentTtsUtteranceStartWordIndex,
            utteranceStart = start,
            utteranceEnd = end
        )
        updateTtsPositionUi()
        highlightActiveTtsWord(currentTtsWordIndex)
    }

    private fun handleReadAloudUtteranceComplete() {
        if (!isReadingAloud) return
        val continuation = ReaderTtsLogic.nextContinuationStep(
            currentPageIndex = currentPageIndex,
            lastPageIndex = pageBodies.lastIndex
        )
        when (continuation.action) {
            TtsContinuationAction.ADVANCE_TO_NEXT_PAGE -> {
                currentTtsWordIndex = 0
                pendingTtsWordIndex = 0
                currentScrollRatio = 0f
                pendingScrollRatio = 0f
                pendingResumeReadAloudAfterPageLoad = true
                animateToPage(continuation.targetPageIndex, forward = false)
            }
            TtsContinuationAction.STOP_READING -> {
                isReadingAloud = false
                isReadAloudPaused = false
                clearActiveTtsWordHighlight()
                updateReadAloudUi()
            }
        }
    }

    private fun updateReadAloudUi() {
        if (_binding == null) return
        binding.readAloudButton.setImageResource(
            if (isReadingAloud) R.drawable.ic_tts_pause else R.drawable.ic_tts_play
        )
        binding.readAloudButton.contentDescription = getString(
            if (isReadingAloud) R.string.pause_read_aloud else R.string.read_aloud
        )
        binding.readAloudButton.alpha = if (ttsUnavailable) 0.45f else 1f
    }

    private fun ensureTextToSpeechInitialized() {
        if (textToSpeech != null || !isAdded) return
        ttsUnavailable = false
        isTtsReady = false
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.tts_initializing)
        textToSpeech = TextToSpeech(requireContext(), this)
    }

    override fun onStop() {
        if (isReadingAloud) {
            textToSpeech?.stop()
            isReadingAloud = false
            isReadAloudPaused = true
            clearActiveTtsWordHighlight()
            updateReadAloudUi()
        }
        super.onStop()
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
        isReadingAloud = false
        isReadAloudPaused = false
        ttsUnavailable = false
        pendingTtsAction = null
        pendingResumeReadAloudAfterPageLoad = false
        super.onDestroy()
    }

    private enum class PendingTtsAction {
        TOGGLE_READ_ALOUD,
        SHOW_VOICE_PICKER
    }
}

internal fun shouldOpenSavedEpub(savedEpub: File?): Boolean = savedEpub != null
