package com.example.projectgutenberg.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.example.projectgutenberg.R

class SearchOptionsFragment : Fragment(R.layout.fragment_search_options) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val queryInput = view.findViewById<EditText>(R.id.queryInput)
        val authorInput = view.findViewById<EditText>(R.id.authorInput)
        val titleInput = view.findViewById<EditText>(R.id.titleInput)
        val subjectInput = view.findViewById<EditText>(R.id.subjectInput)

        queryInput.setText(arguments?.getString(ARG_INITIAL_QUERY).orEmpty())

        view.findViewById<Button>(R.id.searchButton).setOnClickListener {
            val pieces = listOf(
                queryInput.text?.toString().orEmpty(),
                authorInput.text?.toString().orEmpty(),
                titleInput.text?.toString().orEmpty(),
                subjectInput.text?.toString().orEmpty()
            ).filter { it.isNotBlank() }

            val uri = Uri.parse("https://www.gutenberg.org/ebooks/")
                .buildUpon()
                .apply {
                    if (pieces.isNotEmpty()) {
                        appendQueryParameter("query", pieces.joinToString(" "))
                    }
                }
                .build()

            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    companion object {
        private const val ARG_INITIAL_QUERY = "initial_query"

        fun args(initialQuery: String) = bundleOf(ARG_INITIAL_QUERY to initialQuery)
    }
}
