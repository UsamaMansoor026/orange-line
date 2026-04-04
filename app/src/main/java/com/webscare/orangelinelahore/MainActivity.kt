    package com.webscare.orangelinelahore

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import com.webscare.orangelinelahore.common.LocaleHelper
import com.webscare.orangelinelahore.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangelinelahore.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangelinelahore.common.enums.NavigationMode
import com.webscare.orangelinelahore.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private var _navController: NavController? = null
    private val navController get() = _navController!!
    private lateinit var dataStore: PreferencesDataStoreHelper

    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        dataStore = PreferencesDataStoreHelper(this)

        val isDark = runBlocking {
            dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.DARK_MODE, false
            )
        }

        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = ActivityMainBinding.inflate(layoutInflater)
        installSplashScreen().setKeepOnScreenCondition { false }
        setContentView(binding.root)

        window.statusBarColor = getColor(android.R.color.transparent)

        setupSystemBars()
        setupNavController()
        setupBackPress()
        setupBottomNav()
        setupKeyboardListener()
        observeNavigationAndState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
//        if (intent.getBooleanExtra("EXIT_NAV", false)) {
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && appViewModel.isInPipMode.value == true) {
//                // If in PiP, kill everything as requested
//                finishAndRemoveTask()
//            } else {
//                // If in Normal Mode, clear stack and go to Home
//                val navHostFragment = supportFragmentManager
//                    .findFragmentById(R.id.nav_host_main) as? NavHostFragment
//                navHostFragment?.navController?.navigate(R.id.homeFragment, null, navOptions {
//                    popUpTo(R.id.nav_graph) { inclusive = true }
//                    launchSingleTop = true
//                })
//            }
//        }

        setIntent(intent)

        if (intent.getBooleanExtra("EXIT_NAV", false)) {
            appViewModel.setPipMode(false)

            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_main) as? NavHostFragment

            val navController = navHostFragment?.navController
            navController?.navigate(R.id.homeFragment, null, navOptions {
                popUpTo(R.id.nav_graph) {
                    inclusive = true
                }
                launchSingleTop = true
            })
        }
    }

    private fun setupKeyboardListener() {

        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {

            // 🔐 SAFETY GUARD
            val bindingLocal = _binding ?: return@OnGlobalLayoutListener
            val nav = _navController ?: return@OnGlobalLayoutListener

            val rect = android.graphics.Rect()
            bindingLocal.root.getWindowVisibleDisplayFrame(rect)

            val screenHeight = bindingLocal.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            val isKeyboardOpen = keypadHeight > screenHeight * 0.15

            val currentId = nav.currentDestination?.id
            val isMenuFragment = currentId in listOf(
                R.id.homeFragment,
                R.id.departuresFragment,
                R.id.routeFragment,
                R.id.tCashFragment,
                R.id.settingsFragment
            )

            val isNavigating = appViewModel.navigationMode.value == NavigationMode.NAVIGATING

            if (isMenuFragment) {
                bindingLocal.bottomNavigation.visibility =
                    if (isKeyboardOpen || isNavigating) View.GONE
                    else View.VISIBLE
            }
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.setupWithNavController(navController)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }

                R.id.nav_settings -> {
                    navController.navigate(R.id.settingsFragment)
                    true
                }

                R.id.nav_t_cash -> {
                    navController.navigate(R.id.tCashFragment)
                    true
                }

                R.id.nav_departures -> {
                    navController.navigate(R.id.departuresFragment)
                    true
                }

                R.id.nav_route -> {
                    navController.navigate(R.id.routeFragment)
                    true
                }

                else -> false
            }
        }
    }

    private fun setupNavController() {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_main) as NavHostFragment
        _navController = navHost.navController
    }

    private fun observeNavigationAndState() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            calculateBottomNavVisibility(destination.id)

            val menu = binding.bottomNavigation.menu

            when (destination.id) {
                R.id.homeFragment -> menu.findItem(R.id.nav_home)?.isChecked = true
                R.id.departuresFragment -> menu.findItem(R.id.nav_departures)?.isChecked = true
                R.id.routeFragment -> menu.findItem(R.id.nav_route)?.isChecked = true
                R.id.tCashFragment -> menu.findItem(R.id.nav_t_cash)?.isChecked = true
                R.id.settingsFragment -> menu.findItem(R.id.nav_settings)?.isChecked = true
            }
        }

        appViewModel.navigationMode.observe(this) { mode ->
            calculateBottomNavVisibility(navController.currentDestination?.id)
        }

        appViewModel.isInPipMode.observe(this) { inPip ->
            calculateBottomNavVisibility(navController.currentDestination?.id)
        }
    }

    private fun calculateBottomNavVisibility(currentId: Int?) {
        if (currentId == null) {
            binding.bottomNavigation.visibility = View.GONE
            return
        }

        val menuFragments = listOf(
            R.id.homeFragment,
            R.id.departuresFragment,
            R.id.routeFragment,
            R.id.tCashFragment,
            R.id.settingsFragment
        )

        val isMenuFragment = currentId in menuFragments

        val isNavigating = appViewModel.navigationMode.value == NavigationMode.NAVIGATING

        binding.bottomNavigation.isVisible = isMenuFragment && !isNavigating
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If user leaves while navigating, go PiP automatically
        if (appViewModel.navigationMode.value == NavigationMode.NAVIGATING) {
            enterNavPictureInPictureMode()
        }
    }

    private fun enterNavPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use default system PiP controls - no custom RemoteActions
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(15, 21))
                .build()
            enterPictureInPictureMode(params)
        }
    }


    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        
        appViewModel.setPipMode(isInPictureInPictureMode)

        if (isInPictureInPictureMode) {
            hideSystemBarsForPip()
        } else {
            setupSystemBars()
        }
    }

    private fun hideSystemBarsForPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this) {
            when (navController.currentDestination?.id) {
                R.id.homeFragment -> finish()
                else -> navController.navigateUp()
            }
        }
    }

    private fun setupSystemBars() {

        val isDark =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                // Hide status and navigation bars
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())

                // Make sure the system bars do not overlap content
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                setSystemBarsAppearance(
                    if (isDark) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        } else {
            // For older versions, hide status bar and enable immersive mode
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        // Apply padding for edge-to-edge UI experience
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
    }

    override fun attachBaseContext(newBase: Context) {

        val dataStore = PreferencesDataStoreHelper(newBase)

        val langCode = runBlocking {
            dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.LANGUAGE, "en" // default English
            )
        }

        val context = LocaleHelper.applyLanguage(newBase, langCode)
        super.attachBaseContext(context)
    }

    override fun onDestroy() {
        super.onDestroy()

        keyboardLayoutListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }

        keyboardLayoutListener = null
        _navController = null
        _binding = null
    }
}