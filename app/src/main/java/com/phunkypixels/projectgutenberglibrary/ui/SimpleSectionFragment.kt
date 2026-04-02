package com.phunkypixels.projectgutenberglibrary.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.phunkypixels.projectgutenberglibrary.R

abstract class SimpleSectionFragment : Fragment(R.layout.fragment_simple_section) {

    @get:StringRes
    protected abstract val titleRes: Int

    @get:StringRes
    protected abstract val bodyRes: Int

    @get:StringRes
    protected open val noticeRes: Int? = R.string.about_help_content_notice

    protected abstract val officialUrl: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.sectionTitle).setText(titleRes)
        view.findViewById<TextView>(R.id.sectionBody).setText(bodyRes)
        view.findViewById<TextView>(R.id.sectionNotice).apply {
            val messageRes = noticeRes
            if (messageRes == null) {
                visibility = View.GONE
            } else {
                setText(messageRes)
                visibility = View.VISIBLE
            }
        }
        view.findViewById<Button>(R.id.openOfficialPageButton).setOnClickListener {
            ExternalLinkPolicy.openWithNotice(requireContext(), officialUrl)
        }
    }
}
