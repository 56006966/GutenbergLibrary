package com.example.projectgutenberg

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.example.projectgutenberg.data.local.BookDatabase
import com.example.projectgutenberg.data.local.ShelfCachePolicy
import com.example.projectgutenberg.data.local.ShelfCache
import com.example.projectgutenberg.data.remote.RetrofitInstance
import com.example.projectgutenberg.data.repository.BookRepository
import com.example.projectgutenberg.databinding.ActivityMainBinding
import com.example.projectgutenberg.ui.OverlayTransitionLogic
import com.example.projectgutenberg.ui.ReaderUiPalette
import com.example.projectgutenberg.ui.ReaderUiPreferences
import com.example.projectgutenberg.ui.SearchOptionsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private val DRAWER_DESTINATIONS = setOf(
            R.id.nav_library,
            R.id.nav_my_library_screen,
            R.id.nav_frequently_downloaded_screen
        )
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var uiPreferences: ReaderUiPreferences
    private var lastDestinationId: Int? = null
    private var isTransitionOverlayRunning = false
    private var startupOverlayShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        uiPreferences = ReaderUiPreferences(this)
        binding.launchTileWall.setPlaceholderTiles()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        preloadTileWallCovers()
        showStartupOverlay()

        binding.openDrawerButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            maybeShowTransitionOverlay(destination.id)
            val showDrawer = destination.id in DRAWER_DESTINATIONS
            binding.openDrawerButton.visibility = if (showDrawer) android.view.View.VISIBLE else android.view.View.GONE
            binding.drawerLayout.setDrawerLockMode(
                if (showDrawer) DrawerLayout.LOCK_MODE_UNLOCKED
                else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            )
            if (!showDrawer) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        val header = binding.navView.getHeaderView(0)
        val quickSearchTitle = header.findViewById<TextView>(R.id.drawerQuickSearchTitle)
        val quickSearchInput = header.findViewById<EditText>(R.id.drawerQuickSearchInput)
        val quickSearchButton = header.findViewById<ImageButton>(R.id.drawerQuickSearchButton)

        applyDrawerTheme(quickSearchTitle, quickSearchInput, quickSearchButton)

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
                R.id.nav_my_library -> {
                    navController.navigate(R.id.nav_my_library_screen)
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

    private fun preloadTileWallCovers() {
        val shelfCache = ShelfCache(this)
        val repository = BookRepository(
            BookDatabase.getDatabase(this).bookDao(),
            RetrofitInstance.api
        )

        val cachedBooks = shelfCache.getTopDownloadedBooks()
        if (cachedBooks.isNotEmpty()) {
            binding.launchTileWall.setBooks(cachedBooks)
        }

        lifecycleScope.launch {
            if (shelfCache.hasTopDownloadedBooks() &&
                shelfCache.isTopDownloadedCacheFresh(ShelfCachePolicy.TOP_DOWNLOADED_MAX_AGE_MS)
            ) {
                return@launch
            }

            val topBooksDeferred = async(Dispatchers.IO) {
                runCatching { repository.getTopDownloadedBooks(limit = 100) }.getOrNull().orEmpty()
            }

            val books = topBooksDeferred.await()
            if (books.isNotEmpty()) {
                shelfCache.saveTopDownloadedBooks(books)
                binding.launchTileWall.setBooks(books)
            }
        }
    }

    private fun maybeShowTransitionOverlay(destinationId: Int) {
        val previous = lastDestinationId
        lastDestinationId = destinationId
        if (!OverlayTransitionLogic.shouldShowTransition(
                startupOverlayShown = startupOverlayShown,
                previousDestinationId = previous,
                destinationId = destinationId,
                isOverlayRunning = isTransitionOverlayRunning
            )
        ) return

        runOverlayAnimation(OverlayTransitionLogic.navigationSpec)
    }

    private fun showStartupOverlay() {
        runOverlayAnimation(OverlayTransitionLogic.startupSpec, onFinished = { startupOverlayShown = true })
    }

    private fun runOverlayAnimation(
        spec: com.example.projectgutenberg.ui.OverlayAnimationSpec,
        onFinished: (() -> Unit)? = null
    ) {
        isTransitionOverlayRunning = true
        binding.launchLoadingOverlay.animate().cancel()
        binding.launchLoadingOverlay.visibility = android.view.View.VISIBLE
        binding.launchLoadingOverlay.alpha = if (spec.fadeInDuration == 0L) 1f else 0f
        binding.launchTileWall.resumeAnimation()

        val startFadeOut = {
            binding.launchLoadingOverlay.animate()
                .alpha(0f)
                .setDuration(spec.fadeOutDuration)
                .withEndAction {
                    binding.launchLoadingOverlay.visibility = android.view.View.GONE
                    binding.launchTileWall.pauseAnimation()
                    isTransitionOverlayRunning = false
                    onFinished?.invoke()
                }
                .start()
        }

        if (spec.fadeInDuration == 0L) {
            lifecycleScope.launch {
                delay(spec.holdDuration)
                startFadeOut()
            }
            return
        }

        binding.launchLoadingOverlay.animate()
            .alpha(1f)
            .setDuration(spec.fadeInDuration)
            .withEndAction {
                lifecycleScope.launch {
                    delay(spec.holdDuration)
                    startFadeOut()
                }
            }
            .start()
    }

    private fun openUrl(url: String): Boolean {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        return true
    }

    private fun applyDrawerTheme(
        quickSearchTitle: TextView,
        quickSearchInput: EditText,
        quickSearchButton: ImageButton
    ) {
        val darkModeEnabled = uiPreferences.isDarkModeEnabled()
        val background = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_BACKGROUND else ReaderUiPalette.LIGHT_BACKGROUND
        )
        val surface = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SURFACE else ReaderUiPalette.LIGHT_SURFACE
        )
        val button = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_BUTTON else ReaderUiPalette.LIGHT_BUTTON
        )
        val text = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_TEXT else ReaderUiPalette.LIGHT_TEXT
        )
        val secondary = Color.parseColor(
            if (darkModeEnabled) ReaderUiPalette.DARK_SECONDARY_TEXT else ReaderUiPalette.LIGHT_SECONDARY_TEXT
        )

        binding.navView.setBackgroundColor(background)
        binding.navView.itemTextColor = ColorStateList.valueOf(text)
        binding.navView.itemIconTintList = ColorStateList.valueOf(text)
        binding.navView.menu.findItem(R.id.nav_about_group)?.title =
            SpannableString(getString(R.string.menu_about)).apply {
                setSpan(ForegroundColorSpan(text), 0, length, 0)
            }

        quickSearchTitle.setTextColor(text)
        quickSearchInput.setTextColor(text)
        quickSearchInput.setHintTextColor(secondary)
        quickSearchInput.backgroundTintList = ColorStateList.valueOf(button)
        quickSearchButton.imageTintList = ColorStateList.valueOf(text)
        quickSearchButton.backgroundTintList = ColorStateList.valueOf(surface)
    }
}
