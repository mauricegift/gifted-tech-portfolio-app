package co.median.android

import co.median.median_core.GoNativeWebviewInterface
import org.json.JSONObject

class UrlLoader(
    private val mainActivity: MainActivity,
    private val usingNpmPackage: Boolean
) {
    private val mWebView: GoNativeWebviewInterface = mainActivity.webView
    lateinit var urlNavigation: UrlNavigation
    private var hasCalledOnPageStarted: Boolean = false

    fun loadUrl(url: String?) {
        loadUrl(url, enableNpmCallback = false, isFromTab = false)
    }

    fun loadUrl(url: String?, enableNpmCallback: Boolean) {
        loadUrl(url, enableNpmCallback = enableNpmCallback, isFromTab = false);
    }

    fun loadUrl(url: String?, enableNpmCallback: Boolean, isFromTab: Boolean) {
        if (url == null) return
        mainActivity.postLoadJavascript = null
        mainActivity.postLoadJavascriptForRefresh = null
        if (url.equals("median_logout", ignoreCase = true) || url.equals("gonative_logout", ignoreCase = true))
            mainActivity.logout()
        else
            this.load(url, enableNpmCallback, isFromTab)
        if (!isFromTab && mainActivity.tabManager != null) mainActivity.tabManager.selectTab(url, null)
    }

    fun loadUrlAndJavascript(url: String?, javascript: String, enableNpmCallback: Boolean, isFromTab: Boolean) {
        val currentUrl: String? = this.mWebView.url
        if (url.isNullOrBlank() && currentUrl.isNullOrBlank() && url == currentUrl) {
            mainActivity.runJavascript(javascript)
            mainActivity.postLoadJavascriptForRefresh = javascript
        } else {
            mainActivity.postLoadJavascript = javascript
            mainActivity.postLoadJavascriptForRefresh = javascript
            load(url, enableNpmCallback, isFromTab)
        }
        if (!isFromTab && mainActivity.tabManager != null) mainActivity.tabManager.selectTab(url, javascript)
    }

    private fun load(url: String?, enableNpmCallback: Boolean, ignoreOverride: Boolean = false) {
        if (url.isNullOrBlank()) return
        if (usingNpmPackage && enableNpmCallback && mainActivity.eventsManager.hasCallbackEvent(NPM_CALLBACK)) {
            // intercept and execute if javascript
            if (url.startsWith("javascript:")) {
                mWebView.loadUrlDirect(url)
                return
            }

            if (!ignoreOverride && urlNavigation.shouldOverrideUrlLoadingNoIntercept(mWebView, url, false))
                // intercepted by the app
                return

            runUrlChangedEvent(url)
        } else {
            mWebView.loadUrl(url)
        }
    }

    // For single-page apps, onPageStarted() is usually not called on load.
    // Check hasCalledOnPageStarted on onHistoryUpdated()
    // and trigger UrlNavigation.onPageStarted() manually for necessary setup.

    fun notifyOnPageStartedCalled() {
        hasCalledOnPageStarted = true
    }

    fun notifyOnPageFinishedCalled() {
        // page finished loading, reset
        hasCalledOnPageStarted = false
    }

    fun onHistoryUpdated(url: String?) {
        if (usingNpmPackage && !hasCalledOnPageStarted) urlNavigation.onPageStarted(url)
    }

    private fun runUrlChangedEvent(url: String?) {
        if (url.isNullOrBlank()) return
        mainActivity.invokeCallback(NPM_CALLBACK, JSONObject().put("url", url))
    }

    companion object {
        private const val NPM_CALLBACK = "_median_url_changed"
    }
}