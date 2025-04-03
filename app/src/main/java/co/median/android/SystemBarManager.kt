package co.median.android

import android.content.res.Configuration
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import co.median.median_core.AppConfig
import co.median.median_core.GNLog

class SystemBarManager(val mainActivity: MainActivity) {

    private val isAndroid15orAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    private lateinit var insetsController: WindowInsetsControllerCompat
    private lateinit var statusBarStyle: SystemBarStyle
    private lateinit var systemNavBarStyle: SystemBarStyle
    private var currentStatusBarStyle = AppConfig.getInstance(mainActivity).statusBarStyle
    private var currentSystemNavBarStyle = AppConfig.getInstance(mainActivity).systemNavBarStyle
    private var android15SystemBarColor = mainActivity.resources.getColor(R.color.statusBarBackground, null)

    fun applyEdgeToEdge() {
        // Notes: On Android 15+, system bars are transparent by default.
        // To modify their appearance, set a background color for the app's view.
        // Currently using statusBarBackground color, see setOnApplyWindowInsetsListener.
        if (isAndroid15orAbove) {
            mainActivity.enableEdgeToEdge()
        } else {
            // Set the system bar background colors for Android 14 and lower
            val statusBarBackgroundColor =
                mainActivity.resources.getColor(R.color.statusBarBackground, null)
            statusBarStyle = createSystemBarStyle(currentStatusBarStyle, statusBarBackgroundColor)

            val systemNavBarBackgroundColor =
                mainActivity.resources.getColor(R.color.systemNavBarBackground, null)
            systemNavBarStyle = createSystemBarStyle(currentSystemNavBarStyle, systemNavBarBackgroundColor)

            mainActivity.enableEdgeToEdge(
                statusBarStyle = statusBarStyle,
                navigationBarStyle = systemNavBarStyle
            )
        }
    }

    fun setupWindowInsetsListener(view: ViewGroup) {
        // Sets up the listener to handle system bar insets and adjust view padding accordingly.
        // Re-applies when system insets changes or requestApplyInsets() is called.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v: View, insets: WindowInsetsCompat ->

            if (isAndroid15orAbove) {
                // On Android 15+, set the main view's background color to control system bar appearance.
                v.setBackgroundColor(android15SystemBarColor)
            }

            val appConfig = AppConfig.getInstance(mainActivity)
            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val topPadding = if (appConfig.enableOverlayInStatusBar) 0 else systemBars.top
            val bottomPadding = if (appConfig.enableOverlayInSystemNavBar) 0 else systemBars.bottom + keyboardHeight
            v.setPadding(systemBars.left, topPadding, systemBars.right, bottomPadding)
            WindowInsetsCompat.CONSUMED
        }

        this.insetsController =
            WindowCompat.getInsetsController(mainActivity.window, mainActivity.window.decorView)

        // Disable adding contrast to 3-button navigation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mainActivity.window.isNavigationBarContrastEnforced = false
        }
    }

    fun requestApplyInsets() {
        mainActivity.window.decorView.requestApplyInsets()
    }

    fun setStatusBarColor(color: Int) {
        if (isAndroid15orAbove) {
            Log.d(TAG, "setStatusBarColor: Not supported on Android 15+. Use systemBars.set instead.")
            return
        }

        this.statusBarStyle = createSystemBarStyle(currentStatusBarStyle, color)
        mainActivity.enableEdgeToEdge(
            statusBarStyle = this.statusBarStyle,
            navigationBarStyle = this.systemNavBarStyle
        )
    }

    fun setSystemNavBarColor(color: Int) {
        if (isAndroid15orAbove) {
            Log.d(TAG, "setSystemNavBarColor: Not supported on Android 15+. Use systemBars.set instead.")
            return
        }

        this.systemNavBarStyle = createSystemBarStyle(currentSystemNavBarStyle, color)
        mainActivity.enableEdgeToEdge(
            statusBarStyle = this.statusBarStyle,
            navigationBarStyle = this.systemNavBarStyle
        )
    }

    fun setSystemBarColor(color: Int) {
        if (!isAndroid15orAbove) {
            setStatusBarColor(color)
            setSystemNavBarColor(color)
            return
        }

        this.android15SystemBarColor = color
        requestApplyInsets()
    }

    private fun createSystemBarStyle(style: String, color: Int): SystemBarStyle {
        return if (isLightMode(style)) {
            SystemBarStyle.light(color, color)
        } else {
            SystemBarStyle.dark(color)
        }
    }

    fun updateStatusBarStyle(style: String?) {
        if (style.isNullOrBlank()) return
        this.currentStatusBarStyle = style
        insetsController.isAppearanceLightStatusBars = isLightMode(style)
    }

    fun updateSystemNavBarStyle(style: String?) {
        if (style.isNullOrBlank()) return
        this.currentSystemNavBarStyle = style
        insetsController.isAppearanceLightNavigationBars = isLightMode(style)
    }

    fun enableFullScreen(fullscreen: Boolean) {
        if (fullscreen) {
            insetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            insetsController.apply {
                show(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }

    private fun isLightMode(theme: String): Boolean {
        if (!TextUtils.isEmpty(theme)) {
            when (theme) {
                "light" -> return true
                "dark" -> return false
                "auto" -> {
                    val nightModeFlags: Int =
                        mainActivity.getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    when (nightModeFlags) {
                        Configuration.UI_MODE_NIGHT_YES -> {
                            return false
                        }

                        Configuration.UI_MODE_NIGHT_NO -> {
                            return true
                        }

                        else -> {
                            GNLog.getInstance()
                                .logError(TAG, "isLightMode: Current mode is undefined")
                        }
                    }
                }
            }
        }
        return true // Default to light mode if nothing matches
    }

    companion object {
        private const val TAG = "SystemBarManager"
    }
}

