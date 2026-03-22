package com.kdhuf.projectgutenberglibrary.ui

import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.math.roundToInt

internal interface ReaderPageNavigator {
    fun configure(
        textZoom: Int,
        backgroundColor: Int,
        onPageFinished: () -> Unit,
        onWordTapped: (Int) -> Unit
    )

    fun renderPage(baseUrl: String?, html: String)

    fun setTextZoom(textZoom: Int)

    fun setBackgroundColor(backgroundColor: Int)

    fun captureScrollRatio(fallbackRatio: Float): Float

    fun restoreScrollPosition(
        normalizedRatio: Float,
        maxAttempts: Int,
        retryDelayMs: Long,
        onRestored: (Float) -> Unit
    )
}

internal class WebViewReaderPageNavigator(
    private val webView: WebView,
    private val onPermissionDenied: (String?) -> Unit
) : ReaderPageNavigator {

    private var onPageFinishedListener: (() -> Unit)? = null
    private var onWordTappedListener: ((Int) -> Unit)? = null

    override fun configure(
        textZoom: Int,
        backgroundColor: Int,
        onPageFinished: () -> Unit,
        onWordTapped: (Int) -> Unit
    ) {
        onPageFinishedListener = onPageFinished
        onWordTappedListener = onWordTapped
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
                onPermissionDenied(request?.resources?.joinToString())
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = true

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinishedListener?.invoke()
            }
        }
        webView.setBackgroundColor(backgroundColor)
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false
        webView.setOnLongClickListener { true }
        webView.addJavascriptInterface(WordTapJavascriptBridge { wordIndex ->
            onWordTappedListener?.invoke(wordIndex)
        }, "AndroidReader")
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            this.textZoom = textZoom
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
        }
    }

    override fun renderPage(baseUrl: String?, html: String) {
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }

    override fun setTextZoom(textZoom: Int) {
        webView.settings.textZoom = textZoom
    }

    override fun setBackgroundColor(backgroundColor: Int) {
        webView.setBackgroundColor(backgroundColor)
    }

    override fun captureScrollRatio(fallbackRatio: Float): Float {
        val maxScroll = currentMaxScrollY()
        return ReaderPageLocationLogic.captureScrollRatio(
            scrollY = webView.scrollY,
            maxScroll = maxScroll,
            fallbackRatio = fallbackRatio
        )
    }

    override fun restoreScrollPosition(
        normalizedRatio: Float,
        maxAttempts: Int,
        retryDelayMs: Long,
        onRestored: (Float) -> Unit
    ) {
        restoreScrollPositionInternal(
            normalizedRatio = normalizedRatio,
            attempt = 0,
            maxAttempts = maxAttempts,
            retryDelayMs = retryDelayMs,
            onRestored = onRestored
        )
    }

    private fun restoreScrollPositionInternal(
        normalizedRatio: Float,
        attempt: Int,
        maxAttempts: Int,
        retryDelayMs: Long,
        onRestored: (Float) -> Unit
    ) {
        val maxScroll = currentMaxScrollY()
        if (maxScroll <= 0) {
            if (attempt < maxAttempts) {
                webView.postDelayed(
                    {
                        restoreScrollPositionInternal(
                            normalizedRatio = normalizedRatio,
                            attempt = attempt + 1,
                            maxAttempts = maxAttempts,
                            retryDelayMs = retryDelayMs,
                            onRestored = onRestored
                        )
                    },
                    retryDelayMs
                )
            }
            return
        }

        val safeRatio = normalizedRatio.coerceIn(0f, 1f)
        val targetScroll = ReaderPageLocationLogic.targetScrollY(safeRatio, maxScroll)
        webView.post {
            webView.scrollTo(0, targetScroll)
            onRestored(safeRatio)
        }
    }

    private fun currentMaxScrollY(): Int {
        val contentHeightPx = (webView.contentHeight * webView.resources.displayMetrics.density).roundToInt()
        return (contentHeightPx - webView.height).coerceAtLeast(0)
    }

    private class WordTapJavascriptBridge(
        private val onWordTapped: (Int) -> Unit
    ) {
        @JavascriptInterface
        fun onWordTapped(wordIndex: Int) {
            onWordTapped(wordIndex)
        }
    }
}

internal object ReaderPageLocationLogic {
    fun captureScrollRatio(scrollY: Int, maxScroll: Int, fallbackRatio: Float): Float {
        if (maxScroll <= 0) return fallbackRatio.coerceIn(0f, 1f)
        return (scrollY.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
    }

    fun targetScrollY(normalizedRatio: Float, maxScroll: Int): Int {
        if (maxScroll <= 0) return 0
        return (normalizedRatio.coerceIn(0f, 1f) * maxScroll).roundToInt()
    }
}
