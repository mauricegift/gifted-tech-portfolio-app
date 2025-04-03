package co.median.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import co.median.android.icons.Icon
import co.median.median_core.AppConfig
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONArray
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.regex.Pattern


class ActionManager(private val main: MainActivity) {

    private var isRoot = true
    private var actionBar: ActionBar? = null
    private val toolbar: MaterialToolbar = main.findViewById(R.id.toolbar)
    private val titleImageView = main.findViewById<ImageView>(R.id.title_image)
    private val itemToUrl: HashMap<MenuItem, String> = HashMap()
    private val menuItemSize: Int =
        main.resources.getDimensionPixelSize(R.dimen.action_menu_icon_size)
    private val overflowListWidth = main.resources.getDimensionPixelSize(R.dimen.action_custom_overflow_menu_width)
    private val colorForeground = ContextCompat.getColor(main, R.color.titleTextColor)
    private var menu: Menu? = null
    private var searchView: SearchView? = null
    private var currentMenuID: String? = null
    private var leftActionConfigured = false
    private var overflowPopupWindow: PopupWindow? = null

    init {
        main.setSupportActionBar(toolbar)
    }

    fun setupActionBar(isRoot: Boolean) {
        this.isRoot = isRoot
        this.actionBar = main.supportActionBar ?: return
        val appConfig = AppConfig.getInstance(main)

        actionBar?.apply {
            if (isRoot) {
                if (!appConfig.showNavigationMenu) {
                    setDisplayHomeAsUpEnabled(false)
                }
            } else {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }
        }

        toolbar.apply {

            // Menu item on click events
            setOnMenuItemClickListener { menuItem ->
                handleMenuClick(menuItem)
            }

            // Home/Up button
            navigationIcon?.colorFilter =
                BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    colorForeground,
                    BlendModeCompat.SRC_ATOP
                )
        }
    }

    private fun handleMenuClick(menuItem: MenuItem): Boolean {
        val url = itemToUrl[menuItem]
        return handleAction(url)
    }

    private fun handleAction(urlAction: String?): Boolean {
        urlAction?.let {
            when (it) {
                ACTION_SHARE -> main.sharePage(null, null)
                ACTION_REFRESH -> main.onRefresh()
                ACTION_SEARCH -> {
                    // ignore action as this is handled by the SearchView widget
                }
                ACTION_OVERFLOW -> {
                    showPopupOverflowWindow()
                }

                else -> {
                    this.main.urlLoader.loadUrl(urlAction, true);
                }
            }
            return true
        } ?: return false
    }

    fun checkActions(url: String?) {
        if (url.isNullOrBlank()) return

        setTitleDisplayForUrl(url)

        val appConfig = AppConfig.getInstance(this.main)

        val regexes = appConfig.actionRegexes
        val ids = appConfig.actionIDs
        if (regexes == null || ids == null) {
            setMenuID(null)
            return
        }

        for (i in regexes.indices) {
            val regex = regexes[i]
            if (regex.matcher(url).matches()) {
                setMenuID(ids[i])
                return
            }
        }
    }

    private fun setMenuID(menuID: String?) {
        val changed = if (this.currentMenuID == null) {
            menuID != null
        } else {
            this.currentMenuID != menuID
        }

        if (changed) {
            this.currentMenuID = menuID
            main.invalidateOptionsMenu()
        }
    }

    fun addActions(menu: Menu) {
        this.menu = menu
        this.itemToUrl.clear()

        if (leftActionConfigured) {
            resetLeftNavigationMenu()
        }

        val appConfig = AppConfig.getInstance(this.main)
        if (appConfig.actions == null || currentMenuID == null) return

        val actionGroup = appConfig.actions[currentMenuID] ?: return

        val actions = actionGroup.optJSONArray("items")
        if (actions == null || actions.length() == 0) {
            return
        }

        val allowLeftMenu = actionGroup.optBoolean("allowLeftMenu")

        val overflowMenus = mutableListOf<JSONObject>()
        var menuOverflowed = false

        for (i in 0 until actions.length()) {

            val entry = actions.getJSONObject(i)
            val remainingItems = actions.length() - i

            if (!menuOverflowed && menu.size() == 1 && remainingItems > 1) {
                menuOverflowed = true
            }

            if (menuOverflowed) {
                overflowMenus.add(entry)
                continue
            }

            if (i == 0
                && allowLeftMenu
                && !leftActionConfigured
                && !appConfig.showNavigationMenu
                && addAsLeftActionMenu(entry)
            ) {
                leftActionConfigured = true
                continue
            }

            addMenuItem(menu, i, entry)
        }

        setupOverflowMenu(menu, overflowMenus)
    }

    private fun addMenuItem(menu: Menu, itemID: Int, entry: JSONObject?) {
        entry ?: return

        if (addSystemMenuItem(menu, entry, itemID))
            return

        val label = entry.optString("label")
        val icon = entry.optString("icon")
        val url = entry.optString("url")

        val drawableIcon = Icon(main, icon, menuItemSize, colorForeground).getDrawable()

        val menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, label)
            .setIcon(drawableIcon)
            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

        itemToUrl[menuItem] = url
    }

    private fun addSystemMenuItem(
        menu: Menu,
        entry: JSONObject,
        itemID: Int
    ): Boolean {
        val system = entry.optString("system")

        if (TextUtils.isEmpty(system))
            return false

        var label = entry.optString("label")
        var icon = entry.optString("icon")
        val url = entry.optString("url")

        val (action, defaultIcon, defaultLabel) = when (system) {
            "refresh" -> Triple(ACTION_REFRESH, "fa-rotate-right", "Refresh")
            "share" -> Triple(ACTION_SHARE, "fa-share", "Share")
            "search" -> Triple(ACTION_SEARCH, "fa fa-search", "Search")
            else -> return false
        }

        if (TextUtils.isEmpty(label)) label = defaultLabel
        if (TextUtils.isEmpty(icon)) icon = defaultIcon

        val drawableIcon = Icon(main, icon, menuItemSize, colorForeground).getDrawable()

        val menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, label)
            .setIcon(drawableIcon)
            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)

        if (action == ACTION_SEARCH) {
            createSearchView(menuItem, url, drawableIcon)
        }

        itemToUrl[menuItem] = action
        return true
    }

    private fun addAsLeftActionMenu(entry: JSONObject?): Boolean {
        entry ?: return false

        if (!isRoot)
        // Consume the event but keep the menu hidden on non-root windows for the native back button.
            return true

        // Show navigation button
        var url = entry.optString("url")
        var icon = entry.optString("icon")
        val system = entry.optString("system")

        if (!system.isNullOrBlank()) {
            when (system) {
                "refresh" -> {
                    url = ACTION_REFRESH
                    if (!icon.isNullOrBlank()) icon = "fa-rotate-right"
                }

                "share" -> {
                    url = ACTION_SHARE
                    if (!icon.isNullOrBlank()) icon = "fa fa-search"
                }

                "search" -> {
                    Log.e(TAG, "addAsLeftActionMenu: The \"search\" system menu is not supported as a left-menu yet.")
                    return false
                }
            }
        }

        val drawableIcon = Icon(main, icon, menuItemSize, colorForeground).getDrawable()
        toolbar.navigationIcon = drawableIcon
        toolbar.setNavigationIconTint(colorForeground)

        toolbar.setNavigationOnClickListener {
            when (url) {
                ACTION_SHARE -> main.sharePage(null, null)
                ACTION_REFRESH -> main.onRefresh()
                else -> this.main.urlLoader.loadUrl(url, true)
            }
        }
        return true
    }

    private fun resetLeftNavigationMenu() {
        toolbar.apply {
            navigationIcon = null
            setNavigationOnClickListener(null)
        }
        leftActionConfigured = false
    }

    private fun setupOverflowMenu(menu: Menu, overflowMenus: List<JSONObject>) {
        if (overflowMenus.isEmpty()) return

        val overflowIcon = toolbar.overflowIcon?.constantState?.newDrawable()?.mutate()

        overflowIcon?.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                colorForeground,
                BlendModeCompat.SRC_ATOP
            )

        val menuItem = menu.add(Menu.NONE, 99, Menu.NONE, "Overflow")
            .setIcon(overflowIcon)
            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

        itemToUrl[menuItem] = ACTION_OVERFLOW

        val popupView = LayoutInflater.from(main).inflate(R.layout.overflow_menu_list_view, null)

        val listView = popupView.findViewById<ListView>(R.id.menuListView)

        var overflowMenuHasIcons = false
        for (menuJson in overflowMenus) {
            val icon = menuJson.optString("icon")
            if (!icon.isNullOrBlank()) {
                overflowMenuHasIcons = true
                break
            }
        }

        val adapter = OverFlowMenuItemAdapter(main, overflowMenus, overflowMenuHasIcons)
        listView.adapter = adapter

        overflowPopupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // focusable
        ).apply {
            width = overflowListWidth
            elevation = 10f
        }

        // handle item click
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = overflowMenus[position]
            val system = item.optString("system")
            var url = item.optString("url")

            if (system.isNullOrBlank()) {
                when (system) {
                    "refresh" -> {
                        url = ACTION_REFRESH
                    }

                    "share" -> {
                        url = ACTION_SHARE
                    }

                    "search" -> {
                        Log.e(TAG, "setupOverflowMenu: The \"search\" system menu is not supported on the custom overflow menu yet.")
                        url = ""
                    }
                }
            }

            if (url.isNotBlank()) handleAction(url)

            overflowPopupWindow?.dismiss()
        }
    }

    private fun showPopupOverflowWindow() {
        overflowPopupWindow?.let{
            val padding = 15
            val popupWidth = overflowListWidth
            val xOffset = toolbar.width - popupWidth - padding
            it.showAsDropDown(toolbar, xOffset, 0)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun createSearchView(menuItem: MenuItem, url: String, icon: Drawable?) {
        this.searchView = SearchView(main).apply {
            layoutParams = Toolbar.LayoutParams(
                Toolbar.LayoutParams.MATCH_PARENT,
                Toolbar.LayoutParams.WRAP_CONTENT
            )
            maxWidth = Int.MAX_VALUE

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    try {
                        val q = URLEncoder.encode(query, "UTF-8")
                        main.loadUrl(url + q)
                    } catch (e: UnsupportedEncodingException) {
                        return true
                    }

                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    // do nothing
                    return true
                }
            })

            setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus && !isIconified) {
                    closeSearchView()
                }
            }

            // edit text color
            val editText =
                findViewById<SearchView.SearchAutoComplete>(androidx.appcompat.R.id.search_src_text)

            editText?.apply {
                setTextColor(colorForeground)
                var hintColor = colorForeground
                hintColor = Color.argb(
                    192, Color.red(hintColor), Color.green(hintColor),
                    Color.blue(hintColor)
                )
                setHintTextColor(hintColor)
            }

            // close button color
            val closeButton: ImageView? = findViewById(androidx.appcompat.R.id.search_close_btn)
            closeButton?.setColorFilter(colorForeground)
        }

        menuItem.apply {
            actionView = searchView
            setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)

            setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    // hide other menus
                    setMenuItemsVisible(false, menuItem)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    // re-show other menus
                    closeSearchView()
                    return true
                }
            })
        }
    }

    fun setMenuItemsVisible(visible: Boolean, exception: MenuItem) {
        if (menu == null) return

        menu?.let {
            for (i in 0 until it.size()) {
                val item: MenuItem = it.getItem(i)
                if (item === exception) {
                    continue
                }

                item.setVisible(visible)
                item.setEnabled(visible)
            }
        }

    }

    private fun closeSearchView() {
        searchView?.isIconified = true
        main.invalidateOptionsMenu()
    }

    fun canCloseSearchView(): Boolean {
        searchView?.let {
            if (it.hasFocus()) {
                closeSearchView()
                return true
            }
        }
        return false
    }

    fun setTitleDisplayForUrl(url: String?, allowPageTitle: Boolean = true) {
        if (actionBar == null || url.isNullOrBlank()) return

        val appConfig = AppConfig.getInstance(main)

        var urlHasNavTitle = false
        var urlHasActionMenu = false

        // Check for Nav title
        val urlNavTitle: HashMap<String, Any>? = appConfig.getNavigationTitleForUrl(url)

        if (urlNavTitle != null) {
            urlHasNavTitle = true
        }

        // Check for Action Menus
        val regexes: ArrayList<Pattern>? = appConfig.actionRegexes
        val ids: ArrayList<String>? = appConfig.actionIDs
        if (regexes != null && ids != null) {
            for (i in regexes.indices) {
                val regex = regexes[i]
                if (regex.matcher(url).matches()) {
                    val items: JSONArray? = appConfig.actions[ids[i]]?.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        urlHasActionMenu = true
                    }
                    break
                }
            }
        }

        if (!appConfig.showActionBar && !appConfig.showNavigationMenu && !urlHasNavTitle && !urlHasActionMenu) {
            actionBar?.hide()
            return
        }

        // default title
        var title: String = if (main.webView.title != null) main.webView.title else main.getString(R.string.app_name)

        if (!urlHasNavTitle) {
            showImageOrTextTitle(appConfig.shouldShowNavigationTitleImageForUrl(url), title)
        } else {

            val urlNavTitleString = (urlNavTitle?.get("title") as? String).orEmpty()

            if (urlNavTitleString.isEmpty() && !allowPageTitle)
                // If config title is empty and allowPageTitle is false,
                // ignore using the page title as it may not be the actual title
                // due to the method being called before the URL is loaded.
                return

            title = urlNavTitleString.ifEmpty { title }

            main.setTitle(title)

            val showImage = urlNavTitle?.get("showImage") as? Boolean ?: false
            showImageOrTextTitle(showImage, title)
        }
        actionBar?.show()
    }

    fun setTitle(title: CharSequence) {
        if (title.isBlank()) return
        titleImageView.visibility = View.GONE
        toolbar.title = title
    }

    private fun showImageOrTextTitle(show: Boolean, title: String) {
        if (show) {
            titleImageView.visibility = View.VISIBLE
            toolbar.title = ""
        } else {
            titleImageView.visibility = View.GONE
            toolbar.title = title
        }
    }

    companion object {
        const val ACTION_SHARE: String = "share"
        const val ACTION_REFRESH: String = "refresh"
        const val ACTION_SEARCH: String = "search"
        const val ACTION_OVERFLOW: String = "overflow"
        private const val TAG = "ActionManager"
    }
}

class OverFlowMenuItemAdapter(context: Context, items: List<JSONObject>, private val hasIcons: Boolean) : ArrayAdapter<JSONObject>(context, 0, items) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.overflow_menu_item, parent, false)
        val menuItem = getItem(position)

        val menuItemTextView = view.findViewById<TextView>(R.id.overFlowItemTextView)
        val menuItemImageView = view.findViewById<ImageView>(R.id.overFlowItemDrawable)

        menuItem?.let {
            val label = it.optString("label")

            val menuItemSize: Int =
                context.resources.getDimensionPixelSize(R.dimen.action_menu_icon_size)
            val colorForeground = ContextCompat.getColor(context, R.color.titleTextColor)

            menuItemTextView.text = label

            if (hasIcons) {
                val icon = it.optString("icon")
                if (icon.isNotBlank()) {
                    val drawableIcon = Icon(context, icon, menuItemSize, colorForeground).getDrawable()
                    menuItemImageView.setImageDrawable(drawableIcon)
                    menuItemImageView.visibility = View.VISIBLE
                } else {
                    menuItemImageView.visibility = View.INVISIBLE
                }
            } else {
                menuItemImageView.visibility = View.GONE
            }
        }
        return view
    }
}