package com.example.projectgutenberg.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.projectgutenberg.data.local.BookDatabase
import com.example.projectgutenberg.data.repository.BookRepository
import com.example.projectgutenberg.data.remote.RetrofitInstance
import com.example.projectgutenberg.databinding.FragmentLibraryBinding
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LibraryViewModel
    private val adapter = BookAdapter()

    private var currentStatusFilter = "To Read"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupViewModel()
        collectBooks()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = this@LibraryFragment.adapter
            addItemDecoration(HorizontalSpaceItemDecoration(12))
        }

        adapter.onBookClick = { book ->
            val action = LibraryFragmentDirections
                .actionLibraryFragmentToBookWebViewFragment(book.id, book.title)
            findNavController().navigate(action)
        }
    }

    private fun setupViewModel() {
        val dao = BookDatabase.getDatabase(requireContext()).bookDao()
        val repository = BookRepository(dao, RetrofitInstance.api)

        viewModel = androidx.lifecycle.ViewModelProvider(
            this,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return LibraryViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        )[LibraryViewModel::class.java]

        viewModel.loadBooks()
        viewModel.fetchGutenbergBooks()
    }

    private fun collectBooks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.books.collect {
                    filterBooks()
                }
            }
        }
    }

    private fun filterBooks() {
        val filtered = viewModel.books.value.filter { it.status == currentStatusFilter }
        adapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Horizontal spacing for RecyclerView items */
class HorizontalSpaceItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.right = space
    }
}