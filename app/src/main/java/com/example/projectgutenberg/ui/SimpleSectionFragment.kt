package com.example.projectgutenberg.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.projectgutenberg.R

abstract class SimpleSectionFragment : Fragment(R.layout.fragment_simple_section) {

    @get:StringRes
    protected abstract val titleRes: Int

    @get:StringRes
    protected abstract val bodyRes: Int

    protected abstract val officialUrl: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.sectionTitle).setText(titleRes)
        view.findViewById<TextView>(R.id.sectionBody).setText(bodyRes)
        view.findViewById<Button>(R.id.openOfficialPageButton).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(officialUrl)))
        }
    }
}
