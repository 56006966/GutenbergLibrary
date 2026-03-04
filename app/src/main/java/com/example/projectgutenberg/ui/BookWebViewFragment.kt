package com.example.projectgutenberg.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.projectgutenberg.data.local.BookDatabase
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.data.local.BookDao
import com.example.projectgutenberg.data.remote.RetrofitInstance
import com.example.projectgutenberg.data.repository.BookRepository
import com.example.projectgutenberg.databinding.FragmentBookWebviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.text.Html

class BookWebViewFragment : Fragment() {

    private var _binding: FragmentBookWebviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: BookRepository
    private lateinit var dao: BookDao

    private val args: BookWebViewFragmentArgs by navArgs()

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

        dao = BookDatabase.getDatabase(requireContext()).bookDao()
        repository = BookRepository(dao, RetrofitInstance.api)

        requireActivity().title = args.bookTitle

        binding.webView.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = false
        }

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val bookText = withContext(Dispatchers.IO) {
                    // Try local DB first
                    var book = dao.getBookById(args.bookId)

                    if (book?.text.isNullOrEmpty()) {
                        // Fetch from remote
                        val remoteBook = repository.getBookDetails(args.bookId)
                        val textUrl = remoteBook.formats?.get("text/plain; charset=utf-8")
                            ?: remoteBook.formats?.get("text/plain")

                        val text = textUrl?.let { repository.downloadBookText(it) }
                            ?: "Text not available."

                        // Save locally
                        book = BookEntity(
                            id = remoteBook.id,
                            title = remoteBook.title,
                            author = remoteBook.authors.joinToString { it.name },
                            genre = remoteBook.subjects?.firstOrNull() ?: "Unknown",
                            downloads = remoteBook.download_count,
                            coverUrl = remoteBook.formats?.get("image/jpeg"),
                            text = text,
                            status = "To Read"
                        )
                        repository.insertBook(book)
                    }
                    book?.text ?: "Text not available."
                }

                // Debug: check if text was loaded
                println("DEBUG: bookText length = ${bookText.length}")

                // Load text into WebView
                val cleanedText = bookText.replace("_", " ") // simple replacement
                val htmlContent = "<pre>${Html.escapeHtml(cleanedText)}</pre>"
                binding.webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)

            } catch (e: Exception) {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = "Failed to load book: ${e.message}"
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