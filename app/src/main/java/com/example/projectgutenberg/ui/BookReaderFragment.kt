package com.example.projectgutenberg.ui

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.projectgutenberg.databinding.FragmentBookWebviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import com.example.projectgutenberg.R

class BookReaderFragment : Fragment() {

    private var _binding: FragmentBookWebviewBinding? = null
    private val binding get() = _binding!!

    private val args: BookReaderFragmentArgs by navArgs()
    private lateinit var bookText: String

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

        requireActivity().title = args.bookTitle

        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE

        // Setup WebView
        binding.webView.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = false
        }

        lifecycleScope.launch {
            try {
                val localFile = File(requireContext().filesDir, "${args.bookId}.txt")
                bookText = withContext(Dispatchers.IO) {
                    if (localFile.exists()) {
                        localFile.readText()
                    } else {
                        val text = URL(args.bookUrl).readText()
                        localFile.writeText(text)
                        text
                    }
                }

                // Load text into WebView with proper HTML escaping
                val htmlContent = "<pre>${Html.escapeHtml(bookText)}</pre>"
                binding.webView.loadDataWithBaseURL(
                    null,
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )

            } catch (e: Exception) {
                binding.errorText.visibility = View.VISIBLE
                // Use string resource instead of hardcoded string
                binding.errorText.text = getString(
                    R.string.failed_to_load_book,
                    e.message ?: "Unknown error"
                )
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}