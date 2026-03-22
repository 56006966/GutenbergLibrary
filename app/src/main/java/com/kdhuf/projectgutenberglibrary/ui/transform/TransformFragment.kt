package com.kdhuf.projectgutenberglibrary.ui.transform

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kdhuf.projectgutenberglibrary.R
import com.kdhuf.projectgutenberglibrary.data.local.BookDatabase
import com.kdhuf.projectgutenberglibrary.data.local.BookEntity
import com.kdhuf.projectgutenberglibrary.data.repository.BookRepository
import com.kdhuf.projectgutenberglibrary.databinding.FragmentTransformBinding
import com.kdhuf.projectgutenberglibrary.databinding.ItemTransformBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.kdhuf.projectgutenberglibrary.data.remote.RetrofitInstance


class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TransformViewModel
    private val adapter = TransformAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransformBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        binding.recyclerviewTransform.apply {
            adapter = this@TransformFragment.adapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        }

        val dao = BookDatabase.getDatabase(requireContext()).bookDao()

        val repository = BookRepository(
            dao,
            RetrofitInstance.api
        )

        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TransformViewModel(repository) as T
                }
            }
        )[TransformViewModel::class.java]

        // Collect books from repository
        lifecycleScope.launch {
            viewModel.books.collectLatest { books ->
                adapter.submitList(books)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Adapter for bookshelf items */
    class TransformAdapter :
        androidx.recyclerview.widget.ListAdapter<BookEntity, TransformViewHolder>(
            object : androidx.recyclerview.widget.DiffUtil.ItemCallback<BookEntity>() {
                override fun areItemsTheSame(oldItem: BookEntity, newItem: BookEntity) =
                    oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: BookEntity, newItem: BookEntity) =
                    oldItem == newItem
            }
        ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransformViewHolder {
            val binding = ItemTransformBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return TransformViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TransformViewHolder, position: Int) {
            val book = getItem(position)
            holder.bind(book)

            // Optional: click listener
            holder.itemView.setOnClickListener {
                // handle click
            }
        }
    }

    class TransformViewHolder(
        private val binding: ItemTransformBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(book: BookEntity) {
            binding.textViewItemTransform.text = book.title

            binding.imageViewItemTransform.load(book.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.book_placeholder)
                error(R.drawable.book_placeholder)
            }
        }
    }
}
