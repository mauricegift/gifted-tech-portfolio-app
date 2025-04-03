package co.median.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.view.MenuItem
import android.view.View
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.median.android.icons.Icon
import co.median.android.widget.GoNativeDrawerLayout
import co.median.median_core.AppConfig
import com.google.android.material.navigation.NavigationView

class SideNavManager(val main: MainActivity) {

    private val drawerLayout: GoNativeDrawerLayout = main.findViewById(R.id.drawer_layout)
    private var drawerToggle: ActionBarDrawerToggle? = null
    private val navMenu: NavigationView = main.findViewById(R.id.nav_menu)
    private val headerView = navMenu.findViewById<RelativeLayout>(R.id.header_layout)
    private val expandableListView = navMenu.findViewById<ExpandableListView>(R.id.drawer_list)
    private val jsonMenuAdapter = JsonMenuAdapter(main, expandableListView)
    private val menuActions: MutableMap<MenuItem, String> = mutableMapOf()
    private var isRoot = true
    private var sidebarNavigationEnabled = true
    private var status: String = "default"

    init {
        // broadcast messages
        val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != null && intent.action == AppConfig.PROCESSED_MENU_MESSAGE) {
                    updateMenu(status)
                }
            }
        }
        LocalBroadcastManager.getInstance(main)
            .registerReceiver(
                broadcastReceiver,
                IntentFilter(AppConfig.PROCESSED_MENU_MESSAGE)
            )
    }

    fun setupNavigationMenu(isRoot: Boolean) {
        this.isRoot = isRoot
        val appConfig = AppConfig.getInstance(main)

        // Drawer toggle/Hamburger icon click events
        // Do not initialize drawer toggle for non-root window or back button will not work
        if (isRoot) {
            drawerToggle = object :
                ActionBarDrawerToggle(
                    main,
                    drawerLayout,
                    R.string.drawer_open,
                    R.string.drawer_close
                ) {
                override fun onDrawerClosed(view: View) {
                    drawerLayout.setDisableTouch(appConfig.swipeGestures && main.canGoBack());
                }

                override fun onDrawerOpened(drawerView: View) {
                    drawerLayout.setDisableTouch(false);
                }
            }.apply {
                isDrawerIndicatorEnabled = true
                drawerArrowDrawable.color = ContextCompat.getColor(main, R.color.titleTextColor)
            }
        }

        navMenu.apply {
            setNavigationItemSelectedListener { item ->
                closeDrawer()
                handleMenuClick(item)
            }
        }

        setupHeader()
        updateMenu(status)
    }

    private fun handleMenuClick(menuItem: MenuItem): Boolean {
        val url = menuActions[menuItem]
        url?.let {
            this.main.urlLoader.loadUrl(url, true)
            return true
        } ?: return false
    }

    fun showNavigationMenu(show: Boolean) {
        if (show) {
            drawerLayout.apply {
                setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                drawerToggle?.let { addDrawerListener(it) }
            }
        } else {
            drawerLayout.apply {
                setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                drawerToggle?.let { removeDrawerListener(it) }
            }
        }
    }

    private fun setupHeader() {
        val appConfig = AppConfig.getInstance(main)
        if (!appConfig.showLogoInSideBar && !appConfig.showAppNameInSideBar) {
            headerView.visibility = View.GONE
        }

        if (!appConfig.showLogoInSideBar) {
            val appIcon: ImageView? = headerView.findViewById(R.id.app_logo)
            appIcon?.visibility = View.GONE
        }

        val appName: TextView? = headerView.findViewById(R.id.app_name)
        appName?.apply {
            if (appConfig.showAppNameInSideBar) {
                text = appConfig.appName
            } else {
                visibility = View.INVISIBLE
            }
        }
    }

    fun autoSelectItem(url: String) {
        this.jsonMenuAdapter.autoSelectItem(url)
    }

    fun checkUrl(url: String) {
        val appConfig = AppConfig.getInstance(main)
        setDrawerEnabled(appConfig.shouldShowSidebarForUrl(url))

        // When current URL canGoBack and swipeGestures are enabled, disable touch events on DrawerLayout
        if (this.drawerLayout.getDrawerLockMode(GravityCompat.START) != DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
            drawerLayout.setDisableTouch(appConfig.swipeGestures && main.canGoBack())
        }
    }

    fun setSideBarNavigationEnabled(enabled: Boolean) {
        sidebarNavigationEnabled = enabled
        setDrawerEnabled(enabled)
    }

    private fun setDrawerEnabled(enabled: Boolean) {
        if (!isRoot) return

        val appConfig = AppConfig.getInstance(main)
        if (!appConfig.showNavigationMenu) return

        drawerLayout.setDrawerLockMode(if (enabled) GoNativeDrawerLayout.LOCK_MODE_UNLOCKED else GoNativeDrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        if ((sidebarNavigationEnabled || appConfig.showActionBar) && enabled) {
            val toolbar: Toolbar? = main.findViewById(R.id.toolbar)
            if (toolbar != null) {
                toolbar.visibility = View.VISIBLE
            }
        }

        main.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(enabled)

            // Set navigation menu icon
            if (!appConfig.sideBarMenuIcon.isNullOrBlank()) {
                val menuItemSize: Int =
                    main.resources.getDimensionPixelSize(R.dimen.action_menu_icon_size)
                val colorForeground = ContextCompat.getColor(main, R.color.titleTextColor)
                val drawableIcon = Icon(main, appConfig.sideBarMenuIcon, menuItemSize, colorForeground).getDrawable()
                setHomeAsUpIndicator(drawableIcon)
            }
        }

    }

    fun isDrawerOpen(): Boolean = drawerLayout.isDrawerOpen(navMenu)
    fun closeDrawer() = drawerLayout.closeDrawers()

    fun isToggleMenuSelected(menuItem: MenuItem): Boolean =
        drawerToggle?.onOptionsItemSelected(menuItem) == true

    fun toggleSyncState() = drawerToggle?.syncState()
    fun toggleConfigurationChanged(newConfig: Configuration) =
        drawerToggle?.onConfigurationChanged(newConfig)

    fun updateMenu(status: String = "default") {
        this.status = status
        val menuItems = AppConfig.getInstance(main).menus[status]
        this.jsonMenuAdapter.update(menuItems)
    }
}