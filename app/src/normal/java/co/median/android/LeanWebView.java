package co.median.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import co.median.median_core.GoNativeWebviewInterface;

/**
 * Pass calls WebViewClient.shouldOverrideUrlLoading when loadUrl, reload, or goBack are called.
 */
public class LeanWebView extends WebView implements GoNativeWebviewInterface {
    private final String defaultUserAgent = getSettings().getUserAgentString();
    private WebViewClient mClient = null;
    private WebChromeClient mChromeClient = null;
    private boolean checkLoginSignup = true;
    private GestureDetector gd;
    private OnSwipeListener onSwipeListener;
    private boolean zoomed = false;
    private String urlToReloadFromOfflinePage = "";

    public LeanWebView(Context context) {
        super(context);
        gd = new GestureDetector(context, sogl);
    }

    public LeanWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gd = new GestureDetector(context, sogl);
    }

    public LeanWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        gd = new GestureDetector(context, sogl);
    }

    GestureDetector.SimpleOnGestureListener sogl = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
            if (onSwipeListener == null) return false;
            return compareEvents(event1, event2, velocityX, velocityY);
        }

        @Override
        public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX, float distanceY) {
            if (onSwipeListener == null) return false;
            return compareEvents(event1, event2, 0, 0);
        }

        private int getScreenX(){
            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((Activity)getContext()).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.widthPixels;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        private boolean compareEvents(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
            int swipeVelocityThreshold = 0;
            int swipeThreshold = 100;
            int edgeArea = 500;
            int diagonalMovementThreshold = 100;

            float diffY = event2.getY() - event1.getY();
            float diffX = event2.getX() - event1.getX();
            if (Math.abs(diffX) > (Math.abs(diffY) - diagonalMovementThreshold)
                    && Math.abs(velocityX) > swipeVelocityThreshold
                    && Math.abs(diffX) > swipeThreshold) {
                if (diffX > 0 && event1.getX() < edgeArea) {
                    // swipe from edge
                    onSwipeListener.onSwipeRight();
                } else if (event1.getX() > getScreenX() - edgeArea) {
                    // swipe from edge
                    onSwipeListener.onSwipeLeft();
                }
                return true;
            }
            return false;
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gd.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        mClient = client;
        super.setWebViewClient(client);
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        mChromeClient = client;
        super.setWebChromeClient(client);
    }

    @Override
    public void loadUrl(String url) {
        if (url == null) return;
        if (UrlNavigation.OFFLINE_PAGE_URL_RAW.equals(url)) {
            url = UrlNavigation.OFFLINE_PAGE_URL;
        }

        // Note: Use `WebView.loadUrl()` instead of `WebView.evaluateJavascript()` for "javascript:" URLs.
        // This registers any URL changes from the JavaScript in the WebView's native navigation stack, enabling proper back navigation.
        // In contrast, `evaluateJavascript` does not add URL changes to the navigation stack, causing back navigation issues.

        if (url.startsWith("javascript:") || mClient == null || !mClient.shouldOverrideUrlLoading(this, url)) {
            super.loadUrl(url);
        }
    }

    @Override
    public void reload() {
        if (mClient == null || !(mClient instanceof GoNativeWebviewClient)) super.reload();
        else if(!((GoNativeWebviewClient)mClient).shouldOverrideUrlLoading(this, getUrl(), true))
            super.reload();
    }

    @Override
    public void goBack() {
        try {
            WebBackForwardList history = copyBackForwardList();
            // find first non-offline item
            WebHistoryItem item = null;
            int steps = 0;
            for (int i = history.getCurrentIndex() - 1; i >= 0; i--) {
                WebHistoryItem temp = history.getItemAtIndex(i);

                if (!temp.getUrl().equals(UrlNavigation.OFFLINE_PAGE_URL)) {
                    item = temp;
                    steps = i - history.getCurrentIndex();
                    break;
                }
            }

            if (item == null) return;

            // this shouldn't be necessary, but sometimes we are not able to get an updated
            // intercept url from onPageStarted, so this call to shouldOverrideUrlLoading ensures
            // that our html interceptor knows about this url.
            if (mClient.shouldOverrideUrlLoading(this, item.getUrl())) {
                return;
            }
            super.goBackOrForward(steps);
        } catch (Exception ignored) {
            super.goBack();
        }
    }

    @Override
    public boolean canGoForward() {
        WebBackForwardList history = copyBackForwardList();

        // Checks if the next forward item is the offline page
        WebHistoryItem item = history.getItemAtIndex(history.getCurrentIndex() + 1);
        if (item != null && UrlNavigation.OFFLINE_PAGE_URL.equals(item.getUrl())) {
            // return true if the item after the offline page is not null, otherwise, return false
            WebHistoryItem itemAfterOfflinePage = history.getItemAtIndex(history.getCurrentIndex() + 2);
            return itemAfterOfflinePage != null;
        }

        return super.canGoForward();
    }

    @Override
    public void goForward() {
        WebBackForwardList history = copyBackForwardList();

        // Checks if the next forward item is the offline page
        WebHistoryItem item = history.getItemAtIndex(history.getCurrentIndex() + 1);
        if (item != null && UrlNavigation.OFFLINE_PAGE_URL.equals(item.getUrl())) {
            // If item next to the offline page is not null, load the item
            WebHistoryItem itemAfterOfflinePage = history.getItemAtIndex(history.getCurrentIndex() + 2);
            if (itemAfterOfflinePage != null) {
                goBackOrForward(2);
            }
            return;
        }

        super.goForward();
    }

    public void reloadFromOfflinePage() {
        try {
            WebBackForwardList history = copyBackForwardList();
            WebHistoryItem item = null;
            for (int i = history.getCurrentIndex() - 1; i >= 0; i--) {
                WebHistoryItem temp = history.getItemAtIndex(i);

                if (!temp.getUrl().equals(UrlNavigation.OFFLINE_PAGE_URL)) {
                    item = temp;
                    break;
                }
            }
            if (item != null) this.urlToReloadFromOfflinePage = item.getUrl();
            goBack();
        } catch (Exception ignored) {
        }
    }

    public boolean shouldReloadPage(String url) {
        if (this.urlToReloadFromOfflinePage.equals(url)) {
            // consume and reload page forcibly
            this.urlToReloadFromOfflinePage = "";
            this.stopLoading();
            this.reload();
            return true;
        }

        return false;
    }

    private boolean urlEqualsIgnoreSlash(String url1, String url2) {
        if (url1 == null || url2 == null) return false;
        if (url1.endsWith("/")) {
            url1 = url1.substring(0, url1.length() - 1);
        }
        if (url2.endsWith("/")) {
            url2 = url2.substring(0, url2.length() - 1);
        }
        return url1.equals(url2);
    }

    // skip shouldOverrideUrlLoading, including its html override logic.
    public void loadUrlDirect(String url) {
        super.loadUrl(url);
    }

    public boolean checkLoginSignup() {
        return checkLoginSignup;
    }

    public void setCheckLoginSignup(boolean checkLoginSignup) {
        this.checkLoginSignup = checkLoginSignup;
    }

    public void runJavascript(String js, ValueCallback<String> callback) {
        evaluateJavascript(js, callback);
    }

    public void runJavascript(String js) {
        evaluateJavascript(js, null);
    }

    public boolean exitFullScreen() {
        if (mChromeClient != null && mChromeClient instanceof GoNativeWebChromeClient) {
            return ((GoNativeWebChromeClient) mChromeClient).exitFullScreen();
        } else {
            return false;
        }
    }

    static public boolean isCrosswalk() {
        return false;
    }

    @Override
    public void saveStateToBundle(Bundle outBundle) {
        saveState(outBundle);
    }

    @Override
    public void restoreStateFromBundle(Bundle inBundle) {
        restoreState(inBundle);
    }

    @Override
    public int getWebViewScrollY() {
        return getScrollY();
    }

    @Override
    public int getWebViewScrollX() {
        return getScrollX();
    }

    @Override
    public void scrollTo(int scrollX, int scrollY) {
        super.scrollTo(scrollX, scrollY);
    }

    public interface OnSwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }

    /**
     * @deprecated use GoNativeEdgeSwipeLayout in place of LeanWebView's swipe listener.
     */
    @Deprecated
    public OnSwipeListener getOnSwipeListener() {
        return onSwipeListener;
    }

    /**
     * @deprecated use GoNativeEdgeSwipeLayout in place of LeanWebView's swipe listener.
     */
    @Deprecated
    public void setOnSwipeListener(OnSwipeListener onSwipeListener) {
        this.onSwipeListener = onSwipeListener;
    }

    @Override
    public int getMaxHorizontalScroll() {
        return computeHorizontalScrollRange() - getWidth();
    }

    @Override
    public void zoomBy(float zoom){
        super.zoomBy(zoom);
        zoomed = true;
    }

    @Override
    public boolean isZoomed(){
        return zoomed;
    }

    @Override
    public boolean zoomOut(){
        zoomed = false;
        return super.zoomOut();
    }

    @Override
    public String getDefaultUserAgent() {
        return defaultUserAgent;
    }
}
