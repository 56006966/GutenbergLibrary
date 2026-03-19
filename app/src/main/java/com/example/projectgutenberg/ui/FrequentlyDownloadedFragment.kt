package com.example.projectgutenberg.ui

import com.example.projectgutenberg.R

class FrequentlyDownloadedFragment : SimpleSectionFragment() {
    override val titleRes = R.string.frequently_downloaded_title
    override val bodyRes = R.string.frequently_downloaded_body
    override val officialUrl = "https://www.gutenberg.org/browse/scores/top"
}
