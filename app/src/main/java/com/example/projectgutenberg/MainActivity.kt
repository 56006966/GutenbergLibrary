package com.example.projectgutenberg

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.net.toUri
import androidx.navigation.fragment.NavHostFragment
import com.example.projectgutenberg.databinding.ActivityMainBinding
import com.example.projectgutenberg.ui.SearchOptionsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        binding.openDrawerButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        val header = binding.navView.getHeaderView(0)
        val quickSearchInput = header.findViewById<EditText>(R.id.drawerQuickSearchInput)
        val quickSearchButton = header.findViewById<ImageButton>(R.id.drawerQuickSearchButton)

        quickSearchButton.setOnClickListener {
            val query = quickSearchInput.text?.toString().orEmpty()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            navController.navigate(
                R.id.nav_search_options_screen,
                SearchOptionsFragment.args(query)
            )
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.navigate(R.id.nav_library)
                    true
                }
                R.id.nav_frequently_downloaded -> {
                    navController.navigate(R.id.nav_frequently_downloaded_screen)
                    true
                }
                R.id.nav_master_categories -> {
                    navController.navigate(R.id.nav_master_categories_screen)
                    true
                }
                R.id.nav_reading_lists -> {
                    navController.navigate(R.id.nav_reading_lists_screen)
                    true
                }
                R.id.nav_search_options -> {
                    navController.navigate(R.id.nav_search_options_screen)
                    true
                }
                R.id.nav_about_contact -> openUrl("https://www.gutenberg.org/about/contact_information.html")
                R.id.nav_about_history -> openUrl("https://www.gutenberg.org/about/background/index.html")
                R.id.nav_about_ereaders -> openUrl("https://www.gutenberg.org/help/mobile.html")
                R.id.nav_about_help -> openUrl("https://www.gutenberg.org/help/")
                R.id.nav_about_offline -> openUrl("https://www.gutenberg.org/ebooks/offline_catalogs.html")
                R.id.nav_about_donate -> openUrl("https://www.gutenberg.org/donate/")
                R.id.nav_about_paypal -> openUrl("https://www.gutenberg.org/donate/")
                else -> false
            }
        }
    }

    private fun openUrl(url: String): Boolean {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        return true
    }
}
