package com.phunkypixels.projectgutenberglibrary.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.util.Base64
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import android.view.GestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.phunkypixels.projectgutenberglibrary.R
import com.phunkypixels.projectgutenberglibrary.data.local.BookDatabase
import com.phunkypixels.projectgutenberglibrary.data.local.BookEntity
import com.phunkypixels.projectgutenberglibrary.data.remote.BookDto
import com.phunkypixels.projectgutenberglibrary.data.remote.GutenbergMirror
import com.phunkypixels.projectgutenberglibrary.data.remote.RetrofitInstance
import com.phunkypixels.projectgutenberglibrary.data.repository.BookRepository
import com.phunkypixels.projectgutenberglibrary.databinding.FragmentBookWebviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.domain.TOCReference
import nl.siegmann.epublib.epub.EpubReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt

class BookWebViewFragment : Fragment(), ReaderTtsControllerListener {

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
    private var pendingOpeningAnimationAfterCoverLoad = false
    private var restoredFromInstanceState = false
    private var lastNavigationEventAt = 0L
    private var currentScrollRatio = 0f
    private var pendingScrollRatio = 0f
    private var isReaderChromeVisible = true
    private var frontMatterPageCount = 0
    private var readerStructure = ReaderStructure(
        tocEntries = emptyList(),
        chapterTitlesByPage = emptyList(),
        printedPageLabelsByPage = emptyList()
    )
    private lateinit var gestureDetector: GestureDetector
    private var webViewScrollListener: ViewTreeObserver.OnScrollChangedListener? = null
    private lateinit var ttsController: ReaderTtsController
    private var ttsRendererAdapter: ReaderTtsRendererAdapter? = null
    private var pageNavigator: ReaderPageNavigator? = null
    private var ttsSessionState = ReaderTtsSessionState()
    private var ttsUnavailable = false
    private var pendingTtsAction: PendingTtsAction? = null
    private var currentPageSpeakableContent = ""
    private var currentPageWordBoundaries: List<TtsWordBoundary> = emptyList()
    private var currentTtsChunkEndWordIndex = 0

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
        repository = BookRepository(dao, RetrofitInstance.catalogDataSource)
        uiPreferences = ReaderUiPreferences(requireContext())
        ttsController = ReaderTtsController(
            context = requireContext(),
            preferredVoiceNameProvider = uiPreferences::getPreferredVoiceName,
            onPreferredVoiceSelected = uiPreferences::setPreferredVoiceName,
            listener = this
        )
        restoredFromInstanceState = savedInstanceState != null

        restoreReaderState(savedInstanceState)
        requireActivity().title = args.bookTitle
        binding.bookTitleText.text = args.bookTitle

        setupWebView()
        setupHeaderActions()
        setupChapterControls()
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
        binding.contentsButton.setOnClickListener {
            showTableOfContents()
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
        pageNavigator = WebViewReaderPageNavigator(
            webView = binding.webView,
            onPermissionDenied = { resources ->
                Log.d("BookWebViewFragment", "Denied WebView permission request: $resources")
            }
        ).also { navigator ->
            navigator.configure(
                textZoom = currentTextZoom,
                backgroundColor = Color.parseColor(ReaderUiPalette.LIGHT_BACKGROUND),
                onPageFinished = {
                    restorePendingScrollPosition()
                    prepareTtsWebViewState()
                    pendingRevealForward?.let { forward ->
                        startRevealAnimation(forward)
                    }
                    if (ttsSessionState.shouldResumeAfterPageLoad && ttsSessionState.isReadingAloud && !ttsSessionState.isPaused) {
                        ttsSessionState = ReaderTtsSessionLogic.consumeResumeAfterPageLoad(ttsSessionState)
                        binding.webView.postDelayed(
                            { if (ttsSessionState.isReadingAloud && !ttsSessionState.isPaused) speakCurrentPageAloud() },
                            180L
                        )
                    }
                },
                onWordTapped = { wordIndex ->
                    activity?.runOnUiThread {
                        handleReaderWordTapped(wordIndex)
                    }
                }
            )
        }
        ttsRendererAdapter = WebViewReaderTtsRendererAdapter(binding.webView)
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
                    updateChapterPositionText(progress)
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

    private fun setupTouchNavigation() {
        gestureDetector = GestureDetector(requireContext(), ReaderGestureListener())
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
        binding.progressBar.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        binding.statusText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val localBook = withContext(Dispatchers.IO) { repository.getLocalBook(args.bookId) }
            val savedEpub = localBook?.epubPath?.let(::File)?.takeIf { it.exists() && it.length() > 10000 }
            val attemptedSavedEpub = shouldOpenSavedEpub(savedEpub)
            Log.d(
                "BookWebViewFragment",
                "Initial load decision for ${args.bookId}: hasLocalBook=${localBook != null}, hasSavedEpub=${savedEpub != null}, restored=$restoredFromInstanceState"
            )
            try {
                if (!restoredFromInstanceState) {
                    pendingPageIndex = localBook?.lastPageIndex ?: 0
                }
                prepareOpeningCover(
                    localBook?.coverPath
                        ?: localBook?.coverUrl
                        ?: buildFallbackCoverUrl(args.bookId)
                )
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
                if (attemptedSavedEpub && savedEpub != null) {
                    invalidateSavedEpub(savedEpub)
                }
                if (shouldRetrySavedEpubFallback(savedEpub, attemptedSavedEpub)) {
                    val fallbackSavedEpub = checkNotNull(savedEpub)
                    Log.d("BookWebViewFragment", "Falling back to saved EPUB for ${args.bookId} from ${fallbackSavedEpub.absolutePath}")
                    showEpub(fallbackSavedEpub)
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
                            GutenbergMirror.resolve(remoteBook.formats?.get("image/jpeg"))
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
                    _binding?.let { binding ->
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
    }

    private suspend fun showEpub(epubFile: File) {
        fallbackText = null
        val preparedContent = withContext(Dispatchers.IO) {
            prepareEpubDisplayContent(
                epubFile = epubFile,
                coverSource = readerCoverSource,
                requestedPageIndex = pendingPageIndex
            )
        }
        pageBodies = preparedContent.pages
        pendingPageIndex = preparedContent.pendingPageIndex
        readerStructure = preparedContent.structure
        frontMatterPageCount = preparedContent.frontMatterPageCount
        currentPageIndex = pendingPageIndex.coerceIn(0, pageBodies.lastIndex)
        val binding = _binding ?: return
        binding.progressBar.visibility = View.GONE
        binding.statusText.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.chapterControls.visibility = if (pageBodies.size > 1) View.VISIBLE else View.GONE
        binding.pageSeekBar.visibility = if (pageBodies.size > 1) View.VISIBLE else View.GONE
        renderCurrentPage()
        setReaderChromeVisible(false, animate = false)
        playOpeningCoverAnimationIfNeeded()
    }

    private fun prepareEpubDisplayContent(
        epubFile: File,
        coverSource: String?,
        requestedPageIndex: Int
    ): PreparedEpubDisplayContent {
        val epubContent = loadCachedOrExtractPages(epubFile)
        val pages = epubContent.pages.ifEmpty {
            listOf("<p>No readable pages were found in this EPUB.</p>")
        }
        return PreparedEpubDisplayContent(
            pages = pages,
            structure = ReaderStructureLogic.buildStructure(pages, epubContent.tocHints),
            frontMatterPageCount = detectFrontMatterPageCount(pages),
            pendingPageIndex = requestedPageIndex
        )
    }

    private fun extractEpubPages(epubBook: Book): List<String> {
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

    private fun loadCachedOrExtractPages(epubFile: File): ExtractedEpubContent {
        readPageCache(epubFile)?.takeIf { it.isNotEmpty() }?.let { cachedPages ->
            return ExtractedEpubContent(
                pages = cachedPages,
                tocHints = readTocHintCache(epubFile).orEmpty()
            )
        }

        val epubBook = try {
            FileInputStream(epubFile).use { input ->
                EpubReader().readEpub(input)
            }
        } catch (exception: Exception) {
            clearCachedEpubArtifacts(epubFile)
            throw exception
        }
        val tocHints = extractTocHints(epubBook)

        val extractedPages = extractEpubPages(epubBook)
        writePageCache(epubFile, extractedPages)
        writeTocHintCache(epubFile, tocHints)
        return ExtractedEpubContent(
            pages = extractedPages,
            tocHints = tocHints
        )
    }

    private fun extractTocHints(epubBook: Book): List<ReaderTocHint> {
        return flattenTocReferences(epubBook.tableOfContents.tocReferences, depth = 0)
            .mapNotNull { flattenedReference ->
                flattenedReference.reference.title
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { title ->
                        ReaderTocHint(
                            title = title,
                            depth = flattenedReference.depth
                        )
                    }
            }
    }

    private fun flattenTocReferences(
        references: List<TOCReference>,
        depth: Int
    ): List<FlattenedTocReference> {
        return references.flatMap { reference ->
            listOf(FlattenedTocReference(reference, depth)) +
                flattenTocReferences(reference.children, depth + 1)
        }
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

    private fun readTocHintCache(epubFile: File): List<ReaderTocHint>? {
        val cacheFile = tocHintCacheFileFor(epubFile)
        if (!cacheFile.exists() || cacheFile.lastModified() < epubFile.lastModified()) return null

        return runCatching {
            cacheFile.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val separator = line.indexOf('|')
                    if (separator <= 0 || separator >= line.lastIndex) return@mapNotNull null
                    val depth = line.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
                    val title = String(
                        Base64.decode(line.substring(separator + 1), Base64.DEFAULT),
                        Charsets.UTF_8
                    ).trim()
                    title.takeIf { it.isNotBlank() }?.let { ReaderTocHint(it, depth) }
                }
        }.getOrNull()
    }

    private fun writeTocHintCache(epubFile: File, tocHints: List<ReaderTocHint>) {
        if (tocHints.isEmpty()) return

        val cacheFile = tocHintCacheFileFor(epubFile)
        runCatching {
            val encoded = tocHints.joinToString("\n") { hint ->
                val title = Base64.encodeToString(hint.title.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "${hint.depth}|$title"
            }
            cacheFile.writeText(encoded)
        }
    }

    private fun pageCacheFileFor(epubFile: File): File {
        return File(epubFile.parentFile ?: requireContext().cacheDir, "${epubFile.nameWithoutExtension}.v10.pages")
    }

    private fun tocHintCacheFileFor(epubFile: File): File {
        return File(epubFile.parentFile ?: requireContext().cacheDir, "${epubFile.nameWithoutExtension}.v1.toc")
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
        return ReaderPageHtmlBuilder.build(
            pageBody = pageBody,
            inkModeEnabled = inkModeEnabled
        )
    }

    private fun updateCurrentPageTtsModel() {
        val pageState = ttsRendererAdapter?.buildPageState(
            pageBody = pageBodies.getOrNull(currentPageIndex).orEmpty(),
            currentWordIndex = ttsSessionState.currentWordIndex,
            pendingWordIndex = ttsSessionState.pendingWordIndex
        ) ?: return
        currentPageSpeakableContent = pageState.speakableContent
        currentPageWordBoundaries = pageState.wordBoundaries
        ttsSessionState = ReaderTtsSessionLogic.onNewPageRendered(
            state = ttsSessionState,
            selectedWordIndex = pageState.selectedWordIndex
        )
        updateReadAloudCursorVisibility()
    }

    private fun prepareTtsWebViewState() {
        ttsRendererAdapter?.prepareForPage(
            wordBoundaries = currentPageWordBoundaries,
            currentWordIndex = ttsSessionState.currentWordIndex,
            shouldHighlightActiveWord = currentPageWordBoundaries.isNotEmpty() &&
                (ttsSessionState.isReadingAloud || ttsSessionState.currentWordIndex > 0)
        ) { resolvedWordIndex ->
            ttsSessionState = ttsSessionState.copy(currentWordIndex = resolvedWordIndex)
            updateReadAloudCursorVisibility()
        }
    }

    private fun highlightActiveTtsWord(wordIndex: Int) {
        if (_binding == null) return
        ttsRendererAdapter?.highlightWord(wordIndex, currentPageWordBoundaries)
    }

    private fun clearActiveTtsWordHighlight() {
        if (_binding == null) return
        ttsRendererAdapter?.clearActiveWord()
    }

    private fun updateReadAloudCursorVisibility() {
        if (_binding == null) return
        binding.readAloudCursorHandle.visibility = View.GONE
    }

    private fun updateChapterPositionText(pageIndex: Int) {
        val logicalPageLabel = formatDisplayPageNumber(pageIndex)
        val logicalPageCountLabel = formatDisplayPageCount(pageIndex)
        val chapterTitle = readerStructure.chapterTitlesByPage.getOrNull(pageIndex)
        val printedPageLabel = readerStructure.printedPageLabelsByPage.getOrNull(pageIndex)
        binding.chapterPositionText.text = ReaderStructureLogic.formatReaderPosition(
            chapterTitle = chapterTitle,
            printedPageLabel = printedPageLabel,
            logicalPageLabel = logicalPageLabel,
            logicalPageCountLabel = logicalPageCountLabel
        )
    }

    private fun showTableOfContents() {
        if (readerStructure.tocEntries.isEmpty()) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.toc_unavailable)
            return
        }

        val tocLabels = readerStructure.tocEntries.map { entry ->
            val indent = "  ".repeat(entry.depth.coerceAtMost(3))
            val pageLabel = readerStructure.printedPageLabelsByPage.getOrNull(entry.pageIndex)
                ?: formatDisplayPageNumber(entry.pageIndex)
            "$indent${entry.title}  ·  $pageLabel"
        }.toTypedArray()

        val selectedIndex = readerStructure.tocEntries.indexOfLast { entry ->
            entry.pageIndex <= currentPageIndex
        }.coerceAtLeast(0)

        showReaderChoiceDialog(
            titleResId = R.string.contents,
            labels = tocLabels.asList(),
            selectedIndex = selectedIndex
        ) { dialog, which ->
                val entry = readerStructure.tocEntries[which]
                if (entry.pageIndex != currentPageIndex) {
                    animateToPage(
                        targetIndex = entry.pageIndex,
                        forward = entry.pageIndex < currentPageIndex
                    )
                }
                dialog.dismiss()
            }
    }

    private fun renderCurrentPage() {
        if (pageBodies.isEmpty()) return

        val preservedScrollRatio = pendingScrollRatio
        updateCurrentPageTtsModel()
        pageNavigator?.renderPage(
            baseUrl = "https://www.gutenberg.org/",
            html = buildPageHtml(pageBodies[currentPageIndex])
        )
        updateChapterPositionText(currentPageIndex)
        binding.pageSeekBar.max = (pageBodies.size - 1).coerceAtLeast(0)
        if (!binding.pageSeekBar.isPressed) {
            binding.pageSeekBar.progress = currentPageIndex
        }
        currentScrollRatio = preservedScrollRatio.coerceIn(0f, 1f)
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
        val pageTransition = ReaderTtsSessionLogic.onPageTransitionStarted(ttsSessionState)
        ttsSessionState = pageTransition.state
        if (pageTransition.action == ReaderTtsPageTransitionAction.STOP_ENGINE_AND_RESUME_AFTER_LOAD) {
            ttsController.stop()
        }

        val snapshot = captureCurrentPageBitmap()
        if (snapshot == null) {
            currentPageIndex = targetIndex
            currentScrollRatio = 0f
            pendingScrollRatio = 0f
            ttsSessionState = ReaderTtsSessionLogic.resetForPageChange(ttsSessionState)
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
                ttsSessionState = ReaderTtsSessionLogic.resetForPageChange(ttsSessionState)
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

        showReaderChoiceDialog(
            titleResId = R.string.choose_text_size,
            labels = options.map { it.second },
            selectedIndex = selectedIndex
        ) { dialog, which ->
                setTextZoom(options[which].first)
                dialog.dismiss()
            }
    }

    private fun showReaderChoiceDialog(
        titleResId: Int,
        labels: List<String>,
        selectedIndex: Int,
        onSelect: (AlertDialog, Int) -> Unit
    ) {
        val surfaceColor = Color.parseColor(
            if (inkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
        )
        val textColor = Color.parseColor(
            if (inkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
        )
        val secondaryColor = Color.parseColor(
            if (inkModeEnabled) ReaderUiPalette.DARK_SECONDARY_TEXT else ReaderUiPalette.LIGHT_SECONDARY_TEXT
        )

        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_list_item_single_choice,
            labels
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(textColor)
                    setBackgroundColor(surfaceColor)
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(titleResId)
            .setAdapter(adapter, null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(surfaceColor))
            dialog.findViewById<TextView>(
                resources.getIdentifier("alertTitle", "id", "android")
            )?.setTextColor(textColor)
            dialog.listView?.apply {
                choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
                setBackgroundColor(surfaceColor)
                divider = ColorDrawable(secondaryColor)
                dividerHeight = 1
                setItemChecked(selectedIndex, true)
                selector = ColorDrawable(
                    Color.argb(
                        if (inkModeEnabled) 56 else 32,
                        Color.red(secondaryColor),
                        Color.green(secondaryColor),
                        Color.blue(secondaryColor)
                    )
                )
                setOnItemClickListener { _, _, which, _ ->
                    onSelect(dialog, which)
                }
            }
        }

        dialog.show()
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

        _binding?.statusText?.visibility = View.GONE

        val text = withContext(Dispatchers.IO) {
            repository.downloadBookText(textUrl)
        }
        fallbackText = text

        val libraryBook = remoteBook.toLibraryEntity().copy(text = text)
        withContext(Dispatchers.IO) {
            repository.insertBook(libraryBook)
        }

        val binding = _binding ?: return
        binding.progressBar.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.chapterControls.visibility = View.GONE
        readerStructure = ReaderStructure(
            tocEntries = emptyList(),
            chapterTitlesByPage = emptyList(),
            printedPageLabelsByPage = emptyList()
        )
        setReaderChromeVisible(false, animate = false)
        renderFallbackText(text)
    }

    private fun renderFallbackText(text: String) {
        val safeText = text.replace("<", "&lt;").replace(">", "&gt;")
        val body = "<pre style='white-space: pre-wrap; margin: 0;'>$safeText</pre>"
        pageNavigator?.renderPage(
            baseUrl = null,
            html = buildPageHtml(body)
        )
    }

    private suspend fun saveToLibrary(epubFile: File, remoteBook: BookDto? = null) {
        withContext(Dispatchers.IO) {
            val existing = repository.getLocalBook(args.bookId)
            val coverUrl = existing?.coverUrl
                ?: GutenbergMirror.resolve(remoteBook?.formats?.get("image/jpeg"))
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
        return GutenbergMirror.imagesEpubUrl(bookId)
    }

    private fun buildFallbackCoverUrl(bookId: Int): String {
        return GutenbergMirror.coverUrl(bookId)
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
        outState.putInt(STATE_TTS_WORD_INDEX, ttsSessionState.currentWordIndex)
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
        ttsSessionState = ReaderTtsSessionLogic.restorePendingWordIndex(
            state = ttsSessionState,
            pendingWordIndex = savedInstanceState?.getInt(STATE_TTS_WORD_INDEX) ?: 0
        )
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
        binding.backButton.imageTintList = ColorStateList.valueOf(text)

        styleActionButton(binding.contentsButton, buttonBackground, text)
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
        if (!visible && !ReaderReadAloudCursorLogic.canCollapseChrome(ttsSessionState.isReadingAloud)) {
            return
        }
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
        return pageNavigator?.captureScrollRatio(currentScrollRatio)
            ?: currentScrollRatio.coerceIn(0f, 1f)
    }

    private fun restorePendingScrollPosition() {
        if (!isAdded || _binding == null) return
        pageNavigator?.restoreScrollPosition(
            normalizedRatio = pendingScrollRatio,
            maxAttempts = MAX_SCROLL_RESTORE_ATTEMPTS,
            retryDelayMs = SCROLL_RESTORE_RETRY_MS
        ) { restoredRatio ->
            currentScrollRatio = restoredRatio
        }
    }

    private fun prepareOpeningCover(coverSource: String?) {
        readerCoverSource = coverSource
        if (hasPlayedOpeningAnimation || coverSource.isNullOrBlank()) return
        binding.openingCoverImage.load(coverSource) {
            crossfade(false)
            placeholder(android.R.drawable.ic_menu_report_image)
            error(android.R.drawable.ic_menu_report_image)
            listener(
                onSuccess = { _, _ ->
                    if (pendingOpeningAnimationAfterCoverLoad && !hasPlayedOpeningAnimation) {
                        playOpeningCoverAnimationIfNeeded()
                    }
                },
                onError = { _, _ ->
                    if (pendingOpeningAnimationAfterCoverLoad && !hasPlayedOpeningAnimation) {
                        pendingOpeningAnimationAfterCoverLoad = false
                        binding.webView.alpha = 1f
                    }
                }
            )
        }
        openingAnimationViews().forEach { view ->
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            view.rotationY = 0f
            view.scaleX = 0.96f
            view.scaleY = 0.96f
            view.cameraDistance = binding.pageSurface.width.coerceAtLeast(1) * 24f
            view.pivotX = 0f
            view.pivotY = view.height / 2f
        }
    }

    private fun invalidateSavedEpub(epubFile: File) {
        clearCachedEpubArtifacts(epubFile)
        runCatching { epubFile.delete() }
    }

    private fun clearCachedEpubArtifacts(epubFile: File) {
        runCatching { pageCacheFileFor(epubFile).delete() }
        runCatching { tocHintCacheFileFor(epubFile).delete() }
    }

    private fun playOpeningCoverAnimationIfNeeded() {
        if (hasPlayedOpeningAnimation) {
            binding.webView.alpha = 1f
            return
        }
        if (binding.openingCoverImage.drawable == null) {
            pendingOpeningAnimationAfterCoverLoad = true
            binding.webView.alpha = 1f
            return
        }

        hasPlayedOpeningAnimation = true
        pendingOpeningAnimationAfterCoverLoad = false
        binding.webView.alpha = 0f
        binding.webView.scaleX = 1.08f
        binding.webView.scaleY = 1.08f
        val animationViews = openingAnimationViews()
        animationViews.forEach { view ->
            view.visibility = View.VISIBLE
            view.pivotX = 0f
            view.pivotY = view.height / 2f
            view.cameraDistance = binding.pageSurface.width.coerceAtLeast(1) * 24f
            view.animate().cancel()
        }

        binding.openingCoverImage.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .setDuration(180L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                val pageAnimations = listOf(
                    binding.openingPage1 to -150f,
                    binding.openingPage2 to -30f,
                    binding.openingPage3 to -140f,
                    binding.openingPage4 to -40f,
                    binding.openingPage5 to -130f,
                    binding.openingPage6 to -50f
                )
                binding.openingBackCover.animate()
                    .rotationY(-20f)
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(620L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                pageAnimations.forEachIndexed { index, (page, rotation) ->
                    page.animate()
                        .rotationY(rotation)
                        .scaleX(1.08f)
                        .scaleY(1.08f)
                        .setStartDelay((index * 18).toLong())
                        .setDuration(620L)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                binding.openingCoverImage.animate()
                    .rotationY(-160f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .alpha(0.18f)
                    .setDuration(620L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withStartAction {
                        binding.webView.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setStartDelay(250L)
                            .setDuration(360L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    .withEndAction {
                        animationViews.forEach { view ->
                            view.visibility = View.GONE
                            view.rotationY = 0f
                            view.alpha = 1f
                            view.scaleX = 1f
                            view.scaleY = 1f
                        }
                        binding.webView.alpha = 1f
                        binding.webView.scaleX = 1f
                        binding.webView.scaleY = 1f
                    }
                    .start()
            }
            .start()
    }

    private fun openingAnimationViews(): List<View> = listOf(
        binding.openingBackCover,
        binding.openingPage6,
        binding.openingPage5,
        binding.openingPage4,
        binding.openingPage3,
        binding.openingPage2,
        binding.openingPage1,
        binding.openingCoverImage
    )

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
            return when (ReaderPageLogic.resolveSingleTapAction(x = e.x, width = width)) {
                ReaderSingleTapAction.PREVIOUS_PAGE -> {
                    goToPreviousPage()
                    true
                }
                ReaderSingleTapAction.NEXT_PAGE -> {
                    goToNextPage()
                    true
                }
                ReaderSingleTapAction.PASS_THROUGH -> false
            }
        }

        override fun onLongPress(e: MotionEvent) {
            super.onLongPress(e)
            handleReaderWordLongPress(e.x, e.y)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (ttsSessionState.isReadingAloud) {
                return true
            }
            setReaderChromeVisible(!isReaderChromeVisible)
            return true
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
                } else if (!ttsSessionState.isReadingAloud) {
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

    override fun onEngineStateChanged(state: ReaderTtsEngineState) {
        ttsUnavailable = state.isUnavailable
        if (state.isUnavailable) {
            pendingTtsAction = null
            ttsSessionState = ttsSessionState.copy(isReadingAloud = false, isPaused = false)
        }
        if (_binding == null) return
        updateReadAloudCursorVisibility()
        updateReadAloudUi()
        if (state.isUnavailable) {
            clearActiveTtsWordHighlight()
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_unavailable)
            return
        }
        if (state.isReady && !ttsSessionState.isReadingAloud) {
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

    override fun onRangeStart(start: Int, end: Int) {
        handleReadAloudRangeStart(start, end)
    }

    override fun onUtteranceDone() {
        handleReadAloudUtteranceComplete()
    }

    override fun onUtteranceError() {
        ttsSessionState = ttsSessionState.copy(isReadingAloud = false, isPaused = false)
        ttsUnavailable = true
        pendingTtsAction = null
        if (_binding != null) {
            clearActiveTtsWordHighlight()
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_unavailable)
            updateReadAloudUi()
        }
    }

    private fun toggleReadAloud() {
        val ttsState = ttsController.state
        val toggleResult = ReaderTtsSessionLogic.togglePlayback(
            state = ttsSessionState,
            engineReady = ttsState.isReady,
            engineUnavailable = ttsState.isUnavailable
        )
        ttsSessionState = toggleResult.state
        when (toggleResult.action) {
            ReaderTtsToggleAction.WAIT_FOR_ENGINE -> {
            pendingTtsAction = PendingTtsAction.TOGGLE_READ_ALOUD
            ensureTextToSpeechInitialized()
            return
            }
            ReaderTtsToggleAction.UNAVAILABLE -> {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_unavailable)
            return
            }
            ReaderTtsToggleAction.PAUSE -> {
                ttsController.stop()
                clearActiveTtsWordHighlight()
                updateReadAloudUi()
                return
            }
            ReaderTtsToggleAction.START -> {
                setReaderChromeVisible(true)
                updateReadAloudUi()
                speakCurrentPageAloud()
            }
        }
    }

    private fun showVoicePicker() {
        val ttsState = ttsController.state
        if (!ttsState.isReady && !ttsState.isUnavailable) {
            pendingTtsAction = PendingTtsAction.SHOW_VOICE_PICKER
            ensureTextToSpeechInitialized()
            return
        }
        if (ttsState.isUnavailable) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_unavailable)
            return
        }
        if (!ttsState.isReady) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_initializing)
            return
        }
        val availableVoices = ttsState.availableVoices
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
                ttsController.selectVoice(voice)
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.tts_voice_hint)
                dialog.dismiss()
            }
            .show()
    }

    private fun speakCurrentPageAloud() {
        val utteranceChunk = ReaderTtsLogic.utteranceChunk(
            text = currentPageSpeakableContent,
            boundaries = currentPageWordBoundaries,
            startWordIndex = ttsSessionState.currentWordIndex,
            maxCharacters = 3000
        )
        Log.d(
            "BookWebViewFragment",
            "Starting TTS chunk from word=${ttsSessionState.currentWordIndex} chunkStart=${utteranceChunk.startWordIndex} chunkEnd=${utteranceChunk.endWordIndex} textLength=${utteranceChunk.text.length}"
        )
        if (utteranceChunk.text.isBlank()) {
            handleReadAloudUtteranceComplete()
            return
        }
        ttsSessionState = ReaderTtsSessionLogic.onUtteranceAboutToSpeak(
            state = ttsSessionState.copy(currentWordIndex = utteranceChunk.startWordIndex),
            wordCount = currentPageWordBoundaries.size
        )
        currentTtsChunkEndWordIndex = utteranceChunk.endWordIndex
        highlightActiveTtsWord(ttsSessionState.utteranceStartWordIndex)
        val spoke = ttsController.speak(
            text = utteranceChunk.text,
            utteranceId = "page_${args.bookId}_$currentPageIndex"
        )
        if (!spoke) {
            ttsSessionState = ttsSessionState.copy(isReadingAloud = false, isPaused = false)
            ttsUnavailable = true
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_unavailable)
            updateReadAloudUi()
        }
    }

    private fun handleReadAloudRangeStart(start: Int, end: Int) {
        ttsSessionState = ReaderTtsSessionLogic.onRangeStart(
            state = ttsSessionState,
            boundaries = currentPageWordBoundaries,
            utteranceStart = start,
            utteranceEnd = end
        )
        if (!ttsSessionState.isReadingAloud || currentPageWordBoundaries.isEmpty()) return
        highlightActiveTtsWord(ttsSessionState.currentWordIndex)
    }

    private fun handleReadAloudUtteranceComplete() {
        if (!ttsSessionState.isReadingAloud) return
        if (currentPageWordBoundaries.isNotEmpty() && currentTtsChunkEndWordIndex < currentPageWordBoundaries.lastIndex) {
            ttsSessionState = ttsSessionState.copy(currentWordIndex = currentTtsChunkEndWordIndex + 1)
            speakCurrentPageAloud()
            return
        }
        val completion = ReaderTtsSessionLogic.onUtteranceComplete(
            state = ttsSessionState,
            currentPageIndex = currentPageIndex,
            lastPageIndex = pageBodies.lastIndex
        )
        ttsSessionState = completion.state
        when (completion.action) {
            ReaderTtsCompletionAction.ADVANCE_TO_NEXT_PAGE -> {
                currentScrollRatio = 0f
                pendingScrollRatio = 0f
                animateToPage(completion.targetPageIndex, forward = false)
            }
            ReaderTtsCompletionAction.STOP_READING -> {
                clearActiveTtsWordHighlight()
                updateReadAloudUi()
            }
        }
    }

    private fun updateReadAloudUi() {
        if (_binding == null) return
        binding.readAloudButton.setImageResource(
            if (ttsSessionState.isReadingAloud) R.drawable.ic_tts_pause else R.drawable.ic_tts_play
        )
        binding.readAloudButton.contentDescription = getString(
            if (ttsSessionState.isReadingAloud) R.string.pause_read_aloud else R.string.read_aloud
        )
        binding.readAloudButton.alpha = if (ttsUnavailable) 0.45f else 1f
    }

    private fun ensureTextToSpeechInitialized() {
        if (!isAdded) return
        ttsUnavailable = false
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.tts_initializing)
        ttsController.ensureInitialized()
    }

    private fun handleReaderWordTapped(wordIndex: Int) {
        if (currentPageWordBoundaries.isEmpty()) return
        Log.d(
            "BookWebViewFragment",
            "TTS word selected: requested=$wordIndex current=${ttsSessionState.currentWordIndex} reading=${ttsSessionState.isReadingAloud} paused=${ttsSessionState.isPaused}"
        )
        ttsSessionState = ReaderTtsSessionLogic.onWordTapped(
            state = ttsSessionState,
            tappedWordIndex = wordIndex,
            wordCount = currentPageWordBoundaries.size
        )
        Log.d(
            "BookWebViewFragment",
            "TTS word selection applied: current=${ttsSessionState.currentWordIndex} pending=${ttsSessionState.pendingWordIndex} paused=${ttsSessionState.isPaused}"
        )
        highlightActiveTtsWord(ttsSessionState.currentWordIndex)
        if (ttsSessionState.isReadingAloud) {
            speakCurrentPageAloud()
        } else {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.tts_position_ready)
        }
    }

    private fun handleReaderWordLongPress(x: Float, y: Float) {
        if (_binding == null || currentPageWordBoundaries.isEmpty()) return
        val pageSurface = binding.pageSurface
        val xFraction = (x / pageSurface.width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        val yFraction = (y / pageSurface.height.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        ttsRendererAdapter?.resolveWordIndexAtFraction(xFraction, yFraction) { resolvedWordIndex ->
            activity?.runOnUiThread {
                val wordIndex = resolvedWordIndex ?: return@runOnUiThread
                handleReaderWordTapped(wordIndex)
            }
        }
    }

    override fun onStop() {
        if (ttsSessionState.isReadingAloud) {
            ttsController.stop()
            ttsSessionState = ReaderTtsSessionLogic.onStop(ttsSessionState)
            clearActiveTtsWordHighlight()
            updateReadAloudUi()
        }
        super.onStop()
    }

    override fun onDestroy() {
        ttsController.release()
        pageNavigator = null
        ttsSessionState = ReaderTtsSessionLogic.reset(ttsSessionState)
        currentTtsChunkEndWordIndex = 0
        ttsUnavailable = false
        pendingTtsAction = null
        super.onDestroy()
    }

    private enum class PendingTtsAction {
        TOGGLE_READ_ALOUD,
        SHOW_VOICE_PICKER
    }

    private data class FlattenedTocReference(
        val reference: TOCReference,
        val depth: Int
    )

    private data class ExtractedEpubContent(
        val pages: List<String>,
        val tocHints: List<ReaderTocHint>
    ) {
        fun ifEmpty(defaultValue: () -> ExtractedEpubContent): ExtractedEpubContent {
            return if (pages.isEmpty()) defaultValue() else this
        }
    }

    private data class PreparedEpubDisplayContent(
        val pages: List<String>,
        val structure: ReaderStructure,
        val frontMatterPageCount: Int,
        val pendingPageIndex: Int
    )
}

internal fun shouldOpenSavedEpub(savedEpub: File?): Boolean = savedEpub != null

internal fun shouldRetrySavedEpubFallback(savedEpub: File?, attemptedSavedEpub: Boolean): Boolean {
    return savedEpub != null && !attemptedSavedEpub
}
