package com.example.projectgutenberg.ui

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import coil.load
import com.example.projectgutenberg.data.local.BookEntity
import com.example.projectgutenberg.databinding.ItemBookBinding

class BookAdapter : ListAdapter<BookEntity, BookAdapter.BookViewHolder>(BookDiffCallback()) {

    var onBookClick: ((BookEntity) -> Unit)? = null
    private var darkModeEnabled = false

    fun setDarkMode(enabled: Boolean) {
        if (darkModeEnabled == enabled) return
        darkModeEnabled = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookViewHolder(
        private val binding: ItemBookBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(book: BookEntity) {
            binding.bookTitle.text = book.title
            applyCardTheme()
            loadCover(book)

            binding.root.setOnClickListener {
                onBookClick?.invoke(book)
            }
        }

        private fun applyCardTheme() {
            val cardBackground = Color.parseColor(
                if (darkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
            )
            val titleColor = Color.parseColor(
                if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
            )

            binding.root.setCardBackgroundColor(cardBackground)
            binding.bookTitle.setTextColor(titleColor)
        }

        private fun loadCover(book: BookEntity) {
            val localCover = book.coverPath
            val primaryCover = localCover ?: book.coverUrl
            val fallbackCover = "https://www.gutenberg.org/cache/epub/${book.id}/pg${book.id}.cover.medium.jpg"

            if (primaryCover.isNullOrBlank()) {
                binding.bookCover.load(fallbackCover) {
                    crossfade(false)
                    placeholder(android.R.drawable.ic_menu_report_image)
                    error(android.R.drawable.ic_menu_report_image)
                }
                return
            }

            binding.bookCover.load(primaryCover) {
                crossfade(false)
                placeholder(android.R.drawable.ic_menu_report_image)
                error(android.R.drawable.ic_menu_report_image)
                listener(
                    onError = { _, result ->
                        Log.w("BookAdapter", "Primary cover failed for ${book.id}: ${result.throwable.message}")
                        if (localCover == null && primaryCover != fallbackCover) {
                            binding.bookCover.load(fallbackCover) {
                                crossfade(false)
                                placeholder(android.R.drawable.ic_menu_report_image)
                                error(android.R.drawable.ic_menu_report_image)
                            }
                        }
                    }
                )
            }
        }
    }
}

private class BookDiffCallback : DiffUtil.ItemCallback<BookEntity>() {
    override fun areItemsTheSame(oldItem: BookEntity, newItem: BookEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: BookEntity, newItem: BookEntity): Boolean {
        return oldItem == newItem
    }
}
