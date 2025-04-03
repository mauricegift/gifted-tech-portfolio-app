package co.median.android;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.splashscreen.SplashScreenViewProvider;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.squareup.seismic.ShakeDetector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.CookieHandler;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Pattern;

import co.median.android.widget.GoNativeSwipeRefreshLayout;
import co.median.android.widget.MedianProgressView;
import co.median.android.widget.SwipeHistoryNavigationLayout;
import co.median.android.widget.WebViewContainerView;
import co.median.median_core.AppConfig;
import co.median.median_core.Bridge;
import co.median.median_core.GNLog;
import co.median.median_core.GoNativeActivity;
import co.median.median_core.GoNativeWebviewInterface;
import co.median.median_core.LeanUtils;
import co.median.median_core.animations.MedianProgressViewItem;
import co.median.median_core.dto.ContextMenuConfig;

public class MainActivity extends AppCompatActivity implements Observer,
        GoNativeActivity,
        GoNativeSwipeRefreshLayout.OnRefreshListener,
        ShakeDetector.Listener,
        ShakeDialogFragment.ShakeDialogListener {
    public static final String BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED = "io.gonative.android.MainActivity.Extra.BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED";
    private static final String webviewDatabaseSubdir = "webviewDatabase";
    private static final String TAG = MainActivity.class.getName();
    public static final String INTENT_TARGET_URL = "targetUrl";
    public static final String EXTRA_WEBVIEW_WINDOW_OPEN = "io.gonative.android.MainActivity.Extra.WEBVIEW_WINDOW_OPEN";
    public static final String EXTRA_NEW_ROOT_URL = "newRootUrl";
    public static final String EXTRA_EXCESS_WINDOW_ID = "excessWindowId";
    public static final String EXTRA_IGNORE_INTERCEPT_MAXWINDOWS = "ignoreInterceptMaxWindows";
    private static final int REQUEST_PERMISSION_GENERIC = 199;
    private static final int REQUEST_WEBFORM = 300;
    public static final int REQUEST_WEB_ACTIVITY = 400;
    private static final String ON_RESUME_CALLBACK = "median_app_resumed";
    private static final String ON_RESUME_CALLBACK_GN = "gonative_app_resumed";
    private static final String ON_RESUME_CALLBACK_NPM = "_median_app_resumed";
    private static final String CALLBACK_APP_BROWSER_CLOSED = "median_appbrowser_closed";

    private static final String CONFIGURATION_CHANGED = "configurationChanged";
    private static final String SAVED_STATE_ACTIVITY_ID = "activityId";
    private static final String SAVED_STATE_IS_ROOT = "isRoot";
    private static final String SAVED_STATE_URL_LEVEL = "urlLevel";
    private static final String SAVED_STATE_PARENT_URL_LEVEL = "parentUrlLevel";
    private static final String SAVED_STATE_SCROLL_X = "scrollX";
    private static final String SAVED_STATE_SCROLL_Y = "scrollY";
    private static final String SAVED_STATE_WEBVIEW_STATE = "webViewState";
    private static final String SAVED_STATE_IGNORE_THEME_SETUP = "ignoreThemeSetup";

    private static final int CONTEXT_MENU_ID_COPY = 1;
    private static final int CONTEXT_MENU_ID_OPEN = 2;

    private boolean isActivityPaused = false;

    private WebViewContainerView mWebviewContainer;

    private GoNativeWebviewInterface mWebview;
    boolean isPoolWebview = false;
    private Stack<String> backHistory = new Stack<>();

    private View webviewOverlay;
    private String initialUrl;

    private MedianProgressView mProgress;
    private MySwipeRefreshLayout swipeRefreshLayout;
    private SwipeHistoryNavigationLayout swipeNavLayout;
    private RelativeLayout fullScreenLayout;
    private ConnectivityManager cm = null;
    private TabManager tabManager;
    private ActionManager actionManager;
    private SideNavManager sideNavManager;
    private boolean isRoot;
    private boolean webviewIsHidden = false;
    private Handler handler = new Handler();
    private float hideWebviewAlpha = 0.0f;
    private boolean isFirstHideWebview = false;
    private String activityId;

    private final Runnable statusChecker = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(() -> checkReadyStatus());
            handler.postDelayed(statusChecker, 100); // 0.1 sec
        }
    };
    private ShakeDetector shakeDetector = new ShakeDetector(this);
    private FileDownloader fileDownloader;
    private FileWriterSharer fileWriterSharer;
    private LoginManager loginManager;
    private RegistrationManager registrationManager;
    private ConnectivityChangeReceiver connectivityReceiver;
    private KeyboardManager keyboardManager;
    private BroadcastReceiver navigationTitlesChangedReceiver;
    private BroadcastReceiver navigationLevelsChangedReceiver;
    private BroadcastReceiver webviewLimitReachedReceiver;
    private boolean startedLoading = false; // document readystate checke
    protected String postLoadJavascript;
    protected String postLoadJavascriptForRefresh;
    private Stack<Bundle>previousWebviewStates;
    private LocationServiceHelper locationServiceHelper;
    private ArrayList<PermissionsCallbackPair> pendingPermissionRequests = new ArrayList<>();
    private ArrayList<Intent> pendingStartActivityAfterPermissions = new ArrayList<>();
    private String connectivityCallback;
    private String connectivityOnceCallback;
    private PhoneStateListener phoneStateListener;
    private SignalStrength latestSignalStrength;
    private boolean restoreBrightnessOnNavigation = false;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> appBrowserActivityLauncher;
    private String deviceInfoCallback = "";
    private boolean flagThemeConfigurationChange = false;
    private boolean isContentReady;
    private SplashScreenViewProvider splashScreenViewProvider;
    private String launchSource;
    private MedianEventsManager eventsManager;
    private String appTheme;
    private String contextMenuUrl;
    private UrlLoader urlLoader;
    private boolean shouldRemoveSplash = false;
    private SystemBarManager systemBarManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final AppConfig appConfig = AppConfig.getInstance(this);
        GoNativeApplication application = (GoNativeApplication)getApplication();
        GoNativeWindowManager windowManager = application.getWindowManager();
        this.isRoot = getIntent().getBooleanExtra("isRoot", true);
        this.launchSource = getIntent().getStringExtra("source");
        this.launchSource = TextUtils.isEmpty(this.launchSource) ? "default" : this.launchSource;

        // Splash events
        if (this.isRoot) {

            // always install splash to prevent theme-related crashes, even on configuration changes
            SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
            boolean configChanged = savedInstanceState != null && savedInstanceState.getBoolean(CONFIGURATION_CHANGED, false);

            if (appConfig.splashScreen.getIsAnimated() && !configChanged) {
                splashScreen.setOnExitAnimationListener(provider -> {
                    this.splashScreenViewProvider = provider;

                    application.mBridge.animatedSplashScreen(this, provider, () -> {
                        if (this.isContentReady) {
                            this.removeSplashWithAnimation();
                        } else {
                            this.shouldRemoveSplash = true;
                        }
                    });

                    new Handler(Looper.getMainLooper()).postDelayed(this::removeSplashWithAnimation, 7000);
                });
            }else {
                splashScreen.setKeepOnScreenCondition(() -> !isContentReady);
                new Handler(Looper.getMainLooper()).postDelayed(this::contentReady, 7000);
            }
        }

        this.systemBarManager = new SystemBarManager(this);

        // Enable edge-to-edge. Must be called after splash setup
        this.systemBarManager.applyEdgeToEdge();

        if (appConfig.keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        this.hideWebviewAlpha  = appConfig.hideWebviewAlpha;

        this.appTheme = ThemeUtils.getConfigAppTheme(this);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            // App theme setup for API 30 and below
            boolean ignoreThemeUpdate = false;
            if (savedInstanceState != null) {
                ignoreThemeUpdate = savedInstanceState.getBoolean(SAVED_STATE_IGNORE_THEME_SETUP, false);
            }

            if (ignoreThemeUpdate) {
                // Ignore app theme setup cause its already called from function setupAppTheme()
                Log.d(TAG, "onCreate: configuration change from setupAppTheme(), ignoring theme setup");
            } else {
                ThemeUtils.setAppThemeApi30AndBelow(appTheme);
            }
        }

        super.onCreate(savedInstanceState);

        this.activityId = UUID.randomUUID().toString();
        int urlLevel = getIntent().getIntExtra("urlLevel", -1);
        int parentUrlLevel = getIntent().getIntExtra("parentUrlLevel", -1);

        if (savedInstanceState != null) {
            this.activityId = savedInstanceState.getString(SAVED_STATE_ACTIVITY_ID, activityId);
            this.isRoot = savedInstanceState.getBoolean(SAVED_STATE_IS_ROOT, isRoot);
            urlLevel = savedInstanceState.getInt(SAVED_STATE_URL_LEVEL, urlLevel);
            parentUrlLevel = savedInstanceState.getInt(SAVED_STATE_PARENT_URL_LEVEL, parentUrlLevel);
        }

        windowManager.addNewWindow(activityId, isRoot);
        windowManager.setUrlLevels(activityId, urlLevel, parentUrlLevel);

        if (appConfig.maxWindowsEnabled) {
            windowManager.setIgnoreInterceptMaxWindows(activityId, getIntent().getBooleanExtra(EXTRA_IGNORE_INTERCEPT_MAXWINDOWS, false));
        }

        if (isRoot) {
            initialRootSetup();
        }

        this.loginManager = application.getLoginManager();

        this.fileWriterSharer = new FileWriterSharer(this);
        this.fileDownloader = new FileDownloader(this);
        this.eventsManager = new MedianEventsManager(this);

        // register launchers
        this.requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            runGonativeDeviceInfo(deviceInfoCallback, false);
        });
        this.appBrowserActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    String callback = LeanUtils.createJsForCallback(CALLBACK_APP_BROWSER_CLOSED, null);
                    runJavascript(callback);
                });

        this.locationServiceHelper = new LocationServiceHelper(this);

        // webview pools
        application.getWebViewPool().init(this);

        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        setContentView(R.layout.activity_median);
        application.mBridge.onActivityCreate(this, isRoot);

        final ViewGroup content = findViewById(android.R.id.content);

        this.systemBarManager.setupWindowInsetsListener(content);

        if(appConfig.androidFullScreen){
            toggleFullscreen(true);
        }
        // must be done AFTER toggleFullScreen to force screen orientation
        setScreenOrientationPreference();

        this.fullScreenLayout = findViewById(R.id.fullscreen);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setEnabled(appConfig.pullToRefresh);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setCanChildScrollUpCallback(() -> mWebview.getWebViewScrollY() > 10);

        if (appConfig.isAndroidGestureEnabled()) {
            appConfig.swipeGestures = false;
        }
        swipeNavLayout = findViewById(R.id.swipe_history_nav);
        swipeNavLayout.setEnabled(appConfig.swipeGestures);
        swipeNavLayout.setSwipeNavListener(new SwipeHistoryNavigationLayout.OnSwipeNavListener() {
            @Override
            public boolean canSwipeLeftEdge() {
                if (mWebview.getMaxHorizontalScroll() > 0) {
                    if (mWebview.getScrollX() > 0) return false;
                }
                return canGoBack();
            }

            @Override
            public boolean canSwipeRightEdge() {
                if (mWebview.getMaxHorizontalScroll() > 0) {
                    if (mWebview.getScrollX() < mWebview.getMaxHorizontalScroll()) return false;
                }
                return canGoForward();
            }

            @NonNull
            @Override
            public String getGoBackLabel() {
                return "";
            }

            @Override
            public boolean navigateBack() {
                if (appConfig.swipeGestures && canGoBack()) {
                    goBack();
                    return true;
                }
                return false;
            }

            @Override
            public boolean navigateForward() {
                if (appConfig.swipeGestures && canGoForward()) {
                    goForward();
                    return true;
                }
                return false;
            }

            @Override
            public void leftSwipeReachesLimit() {

            }

            @Override
            public void rightSwipeReachesLimit() {

            }

            @Override
            public boolean isSwipeEnabled() {
                return appConfig.swipeGestures;
            }
        });

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.pull_to_refresh_color));
        swipeNavLayout.setActiveColor(ContextCompat.getColor(this, R.color.pull_to_refresh_color));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.swipe_nav_background));
        swipeNavLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.swipe_nav_background));

        // Progress indicator setup
        // use custom progress view from plugins if available; otherwise, use default.
        this.mProgress = findViewById(R.id.progress);
        MedianProgressViewItem progressItem = application.mBridge.getProgressView(this);
        if (progressItem != null) {
            this.mProgress.setProgressView(progressItem);
        } else {
            this.mProgress.setupDefaultProgress();
        }

        // proxy cookie manager for httpUrlConnection (syncs to webview cookies)
        CookieHandler.setDefault(new WebkitCookieManagerProxy());


        this.postLoadJavascript = getIntent().getStringExtra("postLoadJavascript");
        this.postLoadJavascriptForRefresh = this.postLoadJavascript;

        this.previousWebviewStates = new Stack<>();

        // tab navigation
        this.tabManager = new TabManager(this, findViewById(R.id.bottom_navigation));
        tabManager.showTabs(false);

        // actions in action bar
        this.actionManager = new ActionManager(this);
        this.actionManager.setupActionBar(isRoot);

        this.sideNavManager = new SideNavManager(this);
        this.sideNavManager.setupNavigationMenu(isRoot);

        // Hide action bar if showActionBar is FALSE and showNavigationMenu is FALSE
        if (!appConfig.showActionBar && !appConfig.showNavigationMenu) {
            Objects.requireNonNull(getSupportActionBar()).hide();
        }

        // WebView setup
        this.webviewOverlay = findViewById(R.id.webviewOverlay);
        this.mWebviewContainer = this.findViewById(R.id.webviewContainer);
        this.mWebview = this.mWebviewContainer.getWebview();

        this.urlLoader = new UrlLoader(this, !appConfig.injectMedianJS);

        this.mWebviewContainer.setupWebview(this, isRoot);
        setupWebviewTheme(appTheme);

        boolean isWebViewStateRestored = false;
        if (savedInstanceState != null) {
            Bundle webViewStateBundle = savedInstanceState.getBundle(SAVED_STATE_WEBVIEW_STATE);
            if (webViewStateBundle != null) {
                // Restore page and history
                mWebview.restoreStateFromBundle(webViewStateBundle);
                isWebViewStateRestored = true;
            }

            // Restore scroll state
            int scrollX = savedInstanceState.getInt(SAVED_STATE_SCROLL_X, 0);
            int scrollY = savedInstanceState.getInt(SAVED_STATE_SCROLL_Y, 0);
            mWebview.scrollTo(scrollX, scrollY);
        }

        // load url
        String url;

        if (isWebViewStateRestored && !TextUtils.isEmpty(mWebview.getUrl())) {
            // WebView already has loaded URL when function mWebview.restoreStateFromBundle() was called
            url = mWebview.getUrl();
        } else {
            Intent intent = getIntent();
            url = getUrlFromIntent(intent);

            if (url == null && isRoot) url = appConfig.getInitialUrl();
            // url from intent (hub and spoke nav)
            if (url == null) url = intent.getStringExtra("url");

            if (url != null) {

                // let plugins add query params to url before loading to WebView
                Map<String, String> queries = application.mBridge.getInitialUrlQueryItems(this, isRoot);
                if (queries != null && !queries.isEmpty()) {
                    Uri.Builder builder = Uri.parse(url).buildUpon();
                    for (Map.Entry<String, String> entry : queries.entrySet()) {
                        builder.appendQueryParameter(entry.getKey(), entry.getValue());
                    }
                    url = builder.build().toString();
                }

                this.initialUrl = url;
                this.mWebview.loadUrl(url);
            } else if (intent.getBooleanExtra(EXTRA_WEBVIEW_WINDOW_OPEN, false)) {
                // no worries, loadUrl will be called when this new web view is passed back to the message
            } else {
                GNLog.getInstance().logError(TAG, "No url specified for MainActivity");
            }
        }

        actionManager.setTitleDisplayForUrl(url, true);

        sideNavManager.showNavigationMenu(isRoot && appConfig.showNavigationMenu);

        this.keyboardManager = new KeyboardManager(this, content);

        // respond to navigation titles processed
        this.navigationTitlesChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AppConfig.PROCESSED_NAVIGATION_TITLES.equals(intent.getAction())) {
                    String url = mWebview.getUrl();
                    if (url == null) return;
                    String title = titleForUrl(url);
                    if (title != null) {
                        setTitle(title);
                    } else {
                        setupTitleDisplayForUrl(url);
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.navigationTitlesChangedReceiver,
                new IntentFilter(AppConfig.PROCESSED_NAVIGATION_TITLES));

        this.navigationLevelsChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AppConfig.PROCESSED_NAVIGATION_LEVELS.equals(intent.getAction())) {
                    String url = mWebview.getUrl();
                    if (url == null) return;
                    int level = urlLevelForUrl(url);
                    setUrlLevel(level);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.navigationLevelsChangedReceiver,
                new IntentFilter(AppConfig.PROCESSED_NAVIGATION_LEVELS));

        this.webviewLimitReachedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED.equals(intent.getAction())) {

                    String excessWindowId = intent.getStringExtra(EXTRA_EXCESS_WINDOW_ID);
                    if (!TextUtils.isEmpty(excessWindowId)) {
                        if (excessWindowId.equals(activityId)) finish();
                        return;
                    }

                    boolean isActivityRoot = getGNWindowManager().isRoot(activityId);
                    if (!isActivityRoot) {
                        finish();
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.webviewLimitReachedReceiver,
                new IntentFilter(BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED));

        validateGoogleService();

        if (appConfig.geckoViewEnabled) {
            // ignore status checking for GeckoView
            this.removeSplashWithAnimation();
        }

        setContextMenuEnabled(appConfig.contextMenuConfig.getEnabled());
    }

    public String getActivityId() {
        return this.activityId;
    }

    private void initialRootSetup() {
        File databasePath = new File(getCacheDir(), webviewDatabaseSubdir);
        if (databasePath.mkdirs()) {
            Log.v(TAG, "databasePath " + databasePath.toString() + " exists");
        }

        // url inspector
        UrlInspector.getInstance().init(this);

        // Register launch
        ConfigUpdater configUpdater = new ConfigUpdater(this);
        configUpdater.registerEvent();

        // registration service
        this.registrationManager = ((GoNativeApplication) getApplication()).getRegistrationManager();
    }

    private String getUrlFromIntent(Intent intent) {
        if (intent == null) return null;
        // first check intent in case it was created from push notification
        String targetUrl = intent.getStringExtra(INTENT_TARGET_URL);
        if (targetUrl != null && !targetUrl.isEmpty()){
            return targetUrl;
        }

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null && (uri.getScheme().endsWith(".http") || uri.getScheme().endsWith(".https"))) {
                Uri.Builder builder = uri.buildUpon();
                if (uri.getScheme().endsWith(".https")) {
                    builder.scheme("https");
                } else if (uri.getScheme().endsWith(".http")) {
                    builder.scheme("http");
                }
                return builder.build().toString();
            } else {
                return intent.getDataString();
            }
        }

        return null;
    }

    protected void onPause() {
        super.onPause();
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityPause(this);
        this.isActivityPaused = true;
        stopCheckingReadyStatus();

        if (this.mWebview != null && application.mBridge.pauseWebViewOnActivityPause()) {
            this.mWebview.onPause();
        }

        // unregister connectivity
        if (this.connectivityReceiver != null) {
            unregisterReceiver(this.connectivityReceiver);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush();
        }

        shakeDetector.stop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityStart(this);
        if (AppConfig.getInstance(this).permissions.isWebRTCBluetoothAudioEnabled()) {
            AudioUtils.initAudioFocusListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getGNWindowManager().setCurrentActiveWindowId(activityId);

        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.setAppBackgrounded(false);
        application.mBridge.onActivityResume(this);
        if (this.mWebview != null) this.mWebview.onResume();

        AppConfig appConfig = AppConfig.getInstance(this);

        if (isActivityPaused) {
            this.isActivityPaused = false;
            if (appConfig.injectMedianJS) {
                runJavascript(LeanUtils.createJsForCallback(ON_RESUME_CALLBACK, null));
                runJavascript(LeanUtils.createJsForCallback(ON_RESUME_CALLBACK_GN, null));
            } else {
                runJavascript(LeanUtils.createJsForCallback(ON_RESUME_CALLBACK_NPM, null));
            }
        }

        retryFailedPage();
        // register to listen for connectivity changes
        this.connectivityReceiver = new ConnectivityChangeReceiver();
        registerReceiver(this.connectivityReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // check login status
        this.loginManager.checkLogin();

        if (appConfig.shakeToClearCache) {
            SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            shakeDetector.setSensitivity(ShakeDetector.SENSITIVITY_HARD);
            shakeDetector.start(sensorManager);
        }

        this.fileDownloader.onAppResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityStop(this);
        if (isRoot) {
            if (AppConfig.getInstance(this).clearCache) {
                this.mWebview.clearCache(true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityDestroy(this);
        application.getWindowManager().removeWindow(activityId);

        if (fileDownloader != null) fileDownloader.unbindDownloadService();

        // destroy webview
        if (this.mWebview != null) {
            this.mWebview.stopLoading();
            // must remove from view hierarchy to destroy
            ViewGroup parent = (ViewGroup) this.mWebview.getParent();
            if (parent != null) {
                parent.removeView((View)this.mWebview);
            }
            if (!this.isPoolWebview) {
                this.mWebview.destroy();
            }
        }

        this.loginManager.deleteObserver(this);

        if (this.navigationTitlesChangedReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(this.navigationTitlesChangedReceiver);
        }
        if (this.navigationLevelsChangedReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(this.navigationLevelsChangedReceiver);
        }
        if (this.webviewLimitReachedReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(this.webviewLimitReachedReceiver);
        }

        if (this.tabManager != null) this.tabManager.unregisterReceiver();

        if (application.getWebViewPool() != null) application.getWebViewPool().unregisterReceiver(this);
    }

    @Override
    public void onSubscriptionChanged() {
        if (registrationManager == null) return;
        registrationManager.subscriptionInfoChanged();
    }

    @Override
    public void launchNotificationActivity(String extra) {
        Intent mainIntent = new Intent(this, LaunchActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (extra != null && !extra.isEmpty()) {
            mainIntent.putExtra(INTENT_TARGET_URL, extra);
        }

        startActivity(mainIntent);
    }

    private void retryFailedPage() {
        if (this.mWebview == null) return;

        // skip if webview is currently loading
        if (this.mWebview.getProgress() < 100) return;

        // skip if webview has a page loaded
        String currentUrl = this.mWebview.getUrl();
        if (currentUrl != null && !currentUrl.equals(UrlNavigation.OFFLINE_PAGE_URL)) return;

        // skip if there is nothing in history
        if (this.backHistory.isEmpty()) return;

        // skip if no network connectivity
        if (this.isDisconnected()) return;

        // finally, retry loading the page
        this.loadUrl(this.backHistory.pop());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {

        outState.putBoolean(CONFIGURATION_CHANGED, true);

        if (mWebview != null) {
            // Saves current WebView's history and URL or loaded page state
            Bundle webViewOutState = new Bundle();
            mWebview.saveStateToBundle(webViewOutState);
            outState.putBundle(SAVED_STATE_WEBVIEW_STATE, webViewOutState);

            // Save other WebView data
            outState.putString(SAVED_STATE_ACTIVITY_ID, activityId);
            outState.putBoolean(SAVED_STATE_IS_ROOT, getGNWindowManager().isRoot(activityId));
            outState.putInt(SAVED_STATE_URL_LEVEL, getGNWindowManager().getUrlLevel(activityId));
            outState.putInt(SAVED_STATE_PARENT_URL_LEVEL, getGNWindowManager().getParentUrlLevel(activityId));
            outState.putInt(SAVED_STATE_SCROLL_X, mWebview.getWebViewScrollX());
            outState.putInt(SAVED_STATE_SCROLL_Y, mWebview.getWebViewScrollY());
        }

        if (flagThemeConfigurationChange) {
            outState.putBoolean(SAVED_STATE_IGNORE_THEME_SETUP, true);
        }

        if (getBundleSizeInBytes(outState) > 512000) {
            outState.clear();
        }

        super.onSaveInstanceState(outState);
    }

    private int getBundleSizeInBytes(Bundle bundle) {
        Parcel parcel = Parcel.obtain();
        parcel.writeValue(bundle);

        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return bytes.length;
    }

    public void addToHistory(String url) {
        if (url == null) return;

        if (this.backHistory.isEmpty() || !this.backHistory.peek().equals(url)) {
            this.backHistory.push(url);
        }

        checkNavigationForPage(url);

        // this is a little hack to show the webview after going back in history in single-page apps.
        // We may never get onPageStarted or onPageFinished, hence the webview would be forever
        // hidden when navigating back in single-page apps. We do, however, get an updatedHistory callback.
        showWebview(0.3);
    }

    @Override
    public void hearShake() {
        String FRAGMENT_TAG = "ShakeDialogFragment";
        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) != null) {
            return;
        }

        ShakeDialogFragment dialog = new ShakeDialogFragment();
        dialog.show(getSupportFragmentManager(), FRAGMENT_TAG);
    }

    @Override
    public void onClearCache(DialogFragment dialog) {
        clearWebviewCache();
        Toast.makeText(this, R.string.cleared_cache, Toast.LENGTH_SHORT).show();
    }

    public boolean canGoBack() {
        if (this.mWebview == null) return false;
        return this.mWebview.canGoBack();
    }

    public void goBack() {
        if (this.mWebview == null) return;
        if (LeanWebView.isCrosswalk()) {
            // not safe to do for non-crosswalk, as we may never get a page finished callback
            // for single-page apps
            hideWebview();
        }

        this.mWebview.goBack();
    }

    private boolean canGoForward() {
        return this.mWebview.canGoForward();
    }

    private void goForward() {
        if (LeanWebView.isCrosswalk()) {
            // not safe to do for non-crosswalk, as we may never get a page finished callback
            // for single-page apps
            hideWebview();
        }

        this.mWebview.goForward();
    }

    @Override
    public void sharePage(String optionalUrl, String optionalText) {
        String shareUrl;
        String currentUrl = this.mWebview.getUrl();
        if (TextUtils.isEmpty(optionalUrl)) {
            shareUrl = currentUrl;
        } else {
            try {
                java.net.URI optionalUri = new java.net.URI(optionalUrl);
                if (optionalUri.isAbsolute()) {
                    shareUrl = optionalUrl;
                } else {
                    java.net.URI currentUri = new java.net.URI(currentUrl);
                    shareUrl = currentUri.resolve(optionalUri).toString();
                }
            } catch (URISyntaxException e) {
                shareUrl = optionalUrl;
            }
        }

        if (TextUtils.isEmpty(shareUrl)) return;

        String shareData = TextUtils.isEmpty(optionalText) ? shareUrl : optionalText + System.lineSeparator() + shareUrl;

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareData);
        startActivity(Intent.createChooser(share, getString(R.string.action_share)));
    }

    @Override
    public void loadUrl(String url) {
        if (this.urlLoader != null) urlLoader.loadUrl(url);
    }

    public void logout() {
        this.mWebview.stopLoading();

        // log out by clearing all cookies and going to home page
        clearWebviewCookies();

        updateMenu(false);
        this.loginManager.checkLogin();
        this.mWebview.loadUrl(AppConfig.getInstance(this).getInitialUrl());
    }

    public void runJavascript(String javascript) {
        runJavascript(javascript, null);
    }

    @Override
    public void runJavascript(String javascript, ValueCallback<String> callback) {
        if (javascript == null) return;

        if (callback != null) {
            this.mWebview.runJavascript(javascript, callback);
        }
        else {
            this.mWebview.runJavascript(javascript);
        }
    }

    public boolean isDisconnected(){
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni == null || !ni.isConnected();
    }

    @Override
    public void clearWebviewCache() {
        mWebview.clearCache(true);
    }

    @Override
    public void clearWebviewCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(aBoolean -> Log.d(TAG, "clearWebviewCookies: onReceiveValue callback: " + aBoolean));
        AsyncTask.THREAD_POOL_EXECUTOR.execute(cookieManager::flush);
    }

    @Override
    public void hideWebview() {
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onHideWebview(this);

        if (AppConfig.getInstance(this).disableAnimations) return;

        this.webviewIsHidden = true;
        mProgress.show();

        if (this.isFirstHideWebview) {
            this.webviewOverlay.setAlpha(1.0f);
        } else {
            this.webviewOverlay.setAlpha(1 - this.hideWebviewAlpha);
        }

        showWebview(10);
    }

    private void showWebview(double delay) {
        if (delay > 0) {
            handler.postDelayed(this::showWebview, (int) (delay * 1000));
        } else {
            showWebview();
        }
    }

    // shows webview with no animation
    public void showWebviewImmediately() {
        this.isFirstHideWebview = false;
        webviewIsHidden = false;
        startedLoading = false;
        stopCheckingReadyStatus();
        this.webviewOverlay.setAlpha(0.0f);
        this.mProgress.hideImmediately();
    }


    @Override
    public void showWebview() {
        this.isFirstHideWebview = false;
        startedLoading = false;

        if (!webviewIsHidden) {
            // don't animate if already visible
            this.mProgress.hideImmediately();
            return;
        }

        webviewIsHidden = false;

        webviewOverlay.animate().alpha(0.0f)
                .setDuration(300)
                .setStartDelay(150);

        this.mProgress.hide();
    }

    public void updatePageTitle() {
        if (AppConfig.getInstance(this).useWebpageTitle) {
            setTitle(this.mWebview.getTitle());
        }
    }

    public void update (Observable sender, Object data) {
        if (sender instanceof LoginManager) {
            updateMenu(((LoginManager) sender).isLoggedIn());
        }
    }

    @Override
    public void updateMenu(){
        this.loginManager.checkLogin();
    }

    private void updateMenu(boolean isLoggedIn){

        if (sideNavManager == null) return;
        try {
            if (isLoggedIn)
                sideNavManager.updateMenu("loggedIn");
            else
                sideNavManager.updateMenu("default");
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, e.getMessage(), e);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onPostCreate(this, savedInstanceState, isRoot);

        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (sideNavManager != null)
            sideNavManager.toggleSyncState();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        GoNativeApplication application = (GoNativeApplication)getApplication();

        // Pass any configuration change to the drawer toggles
        if (sideNavManager != null && AppConfig.getInstance(this).showNavigationMenu)
            sideNavManager.toggleConfigurationChanged(newConfig);

        application.mBridge.onConfigurationChange(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityResult(this, requestCode, resultCode, data);

        if (data != null && data.getBooleanExtra("exit", false))
            finish();

        String url = null;
        boolean success = false;
        if (data != null) {
            url = data.getStringExtra("url");
            success = data.getBooleanExtra("success", false);
        }

        if (requestCode == REQUEST_WEBFORM && resultCode == RESULT_OK) {
            if (url != null)
                loadUrl(url);
            else {
                // go to initialURL without login/signup override
                this.mWebview.setCheckLoginSignup(false);
                this.mWebview.loadUrl(AppConfig.getInstance(this).getInitialUrl());
            }

            if (AppConfig.getInstance(this).showNavigationMenu) {
                updateMenu(success);
            }
        }

        if (requestCode == REQUEST_WEB_ACTIVITY && resultCode == RESULT_OK) {
            if (url != null) {
                int urlLevel = data.getIntExtra("urlLevel", -1);
                int parentUrlLevel = getGNWindowManager().getParentUrlLevel(activityId);
                if (urlLevel == -1 || parentUrlLevel == -1 || urlLevel > parentUrlLevel) {
                    // open in this activity
                    this.postLoadJavascript = data.getStringExtra("postLoadJavascript");
                    loadUrl(url);
                } else {
                    // urlLevel <= parentUrlLevel, so pass up the chain
                    setResult(RESULT_OK, data);
                    finish();
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = getUrlFromIntent(intent);
        if (url != null && !url.isEmpty()) {
            if (mWebview.getUrl().isEmpty()) {
                loadUrl(url);
                this.initialUrl  = url;
            } else if (!urlEqualsIgnoreSlash(url, mWebview.getUrl())) {
                urlLoader.loadUrl(url, true);
            }
            return;
        }
        Log.w(TAG, "Received intent without url");

        ((GoNativeApplication) getApplication()).mBridge.onActivityNewIntent(this, intent);
    }

    private boolean urlEqualsIgnoreSlash(String url1, String url2) {
        if (url1 == null || url2 == null) return false;
        if (url1.endsWith("/")) {
            url1 = url1.substring(0, url1.length() - 1);
        }
        if (url2.endsWith("/")) {
            url2 = url2.substring(0, url2.length() - 1);
        }
        if (url1.startsWith("http://")) {
            url1 = "https://" + url1.substring(7);
        }
        return url1.equals(url2);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (AppConfig.getInstance(this).disableBackButton) {
                return true;
            }

            if (this.mWebview.exitFullScreen()) {
                return true;
            }

            if (sideNavManager != null && sideNavManager.isDrawerOpen()) {
                sideNavManager.closeDrawer();
                return true;
            }
            else if (canGoBack()) {
                goBack();
                return true;
            }
            else if (!this.previousWebviewStates.isEmpty()) {
                Bundle state = previousWebviewStates.pop();
                LeanWebView webview = new LeanWebView(this);
                webview.restoreStateFromBundle(state);
                switchToWebview(webview, /* isPool */ false, /* isBack */ true);
                return true;
            }
        }

        if (((GoNativeApplication) getApplication()).mBridge.onKeyDown(keyCode, event)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    // isPoolWebView is used to keep track of whether we are showing a pooled webview, which has implications
    // for page navigation, namely notifying the pool to disown the webview.
    // isBack means the webview is being switched in as part of back navigation behavior. If isBack=false,
    // then we will save the state of the old one switched out.
    public void switchToWebview(GoNativeWebviewInterface newWebview, boolean isPoolWebview, boolean isBack) {
        this.mWebviewContainer.setupWebview(this, isRoot);

        // scroll to top
        ((View)newWebview).scrollTo(0, 0);

        View prev = (View)this.mWebview;

        if (!isBack) {
            // save the state for back button behavior
            Bundle stateBundle = new Bundle();
            this.mWebview.saveStateToBundle(stateBundle);
            this.previousWebviewStates.add(stateBundle);
        }

        // replace the current web view in the parent with the new view
        if (newWebview != prev) {
            // a view can only have one parent, and attempting to add newWebview if it already has
            // a parent will cause a runtime exception. So be extra safe by removing it from its parent.
            ViewParent temp = newWebview.getParent();
            if (temp instanceof  ViewGroup) {
                ((ViewGroup) temp).removeView((View)newWebview);
            }

            ViewGroup parent = (ViewGroup) prev.getParent();
            int index = parent.indexOfChild(prev);
            parent.removeView(prev);
            parent.addView((View) newWebview, index);
            ((View)newWebview).setLayoutParams(prev.getLayoutParams());

            // webviews can still send some extraneous events to this activity if we do not remove
            // its callbacks
            WebViewSetup.removeCallbacks((LeanWebView) prev);

            if (!this.isPoolWebview) {
                ((GoNativeWebviewInterface)prev).destroy();
            }
        }

        this.isPoolWebview = isPoolWebview;
        this.mWebview = newWebview;

        if (this.postLoadJavascript != null) {
            runJavascript(this.postLoadJavascript);
            this.postLoadJavascript = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.topmenu, menu);

        if (this.actionManager != null) {
            this.actionManager.addActions(menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event

        if (sideNavManager != null) {
            if (sideNavManager.isToggleMenuSelected(item)) {
                return true;
            }
        }

        if (item.getItemId() == android.R.id.home) {
            if (this.actionManager != null && this.actionManager.canCloseSearchView()) {
                return true;
            }

            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        refreshPage();
        stopNavAnimation(true, 1000);
    }

    private void stopNavAnimation(boolean isConsumed){
        stopNavAnimation(isConsumed, 100);
    }

    private void stopNavAnimation(boolean isConsumed, int delay){
        // let the refreshing spinner stay for a little bit if the native show/hide is disabled
        // otherwise there isn't enough of a user confirmation that the page is refreshing
        if (isConsumed && AppConfig.getInstance(this).disableAnimations) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, delay);
        } else {
            this.swipeRefreshLayout.setRefreshing(false);
        }
    }

    public void refreshPage() {
        String url = this.mWebview.getUrl();
        if (url != null && url.equals(UrlNavigation.OFFLINE_PAGE_URL)){
            if (this.mWebview.canGoBack()) {
                getLeanWebView().reloadFromOfflinePage();
            } else if (this.initialUrl != null) {
                this.mWebview.loadUrl(this.initialUrl);
            }
            updateMenu();
        }
        else {
            this.postLoadJavascript = this.postLoadJavascriptForRefresh;
            this.mWebview.loadUrl(url);
        }
    }


    private void contentReady() {
        isContentReady = true;
        stopCheckingReadyStatus();

        if (shouldRemoveSplash) {
            removeSplashWithAnimation();
        }
    }

    private void removeSplashWithAnimation() {
        if (this.splashScreenViewProvider != null) {

            final ObjectAnimator fadeOut = ObjectAnimator.ofFloat(
                    splashScreenViewProvider.getView(),
                    View.ALPHA,
                    1f,
                    0f
            );

            fadeOut.setInterpolator(new AccelerateInterpolator()); // for smooth effect
            fadeOut.setDuration(100L); // duration in milliseconds
            fadeOut.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // remove splash as page is ready
                    // note: It's important to check again if splashScreenViewProvider is not null
                    // to prevent the app from crashing, as this method may be called consecutively.
                    if (splashScreenViewProvider != null) {
                        splashScreenViewProvider.remove();
                        splashScreenViewProvider = null;
                    }

                    // Reapply the status bar and system nav bar styles after
                    // removing splash to ensure it's applied correctly.
                    updateStatusBarStyle(AppConfig.getInstance(MainActivity.this).statusBarStyle);
                    updateSystemNavBarStyle(AppConfig.getInstance(MainActivity.this).systemNavBarStyle);
                }
            });

            fadeOut.start();
        }
    }

    // onPageFinished
    @Override
    public void checkNavigationForPage(String url) {
        // don't change anything on navigation if the url that just finished was a file download
        if (url.equals(this.fileDownloader.getLastDownloadedUrl())) return;

        if (this.actionManager != null) {
            this.actionManager.setTitleDisplayForUrl(url, true);
        }

        if (this.tabManager != null) {
            this.tabManager.checkTabs(url);
        }

        if (this.actionManager != null) {
            this.actionManager.checkActions(url);
        }

        if (this.registrationManager != null) {
            this.registrationManager.checkUrl(url);
        }

        if (this.sideNavManager != null) {
            this.sideNavManager.autoSelectItem(url);
        }
    }

    // onPageStarted
    @Override
    public void checkPreNavigationForPage(String url) {
        if (this.tabManager != null) {
            this.tabManager.autoSelectTab(url);
        }

        if (this.sideNavManager != null) {
            this.sideNavManager.autoSelectItem(url);
            this.sideNavManager.checkUrl(url);
        }
    }

    @Override
    public void setupTitleDisplayForUrl(String url) {
        this.setupTitleDisplayForUrl(url, true);
    }

    public void setupTitleDisplayForUrl(String url, boolean allowPageTitle) {
        if (this.actionManager == null) return;
        this.actionManager.setTitleDisplayForUrl(url, allowPageTitle);
    }

    @Override
    public int urlLevelForUrl(String url) {
        ArrayList<Pattern> entries = AppConfig.getInstance(this).navStructureLevelsRegex;
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                Pattern regex = entries.get(i);
                if (regex.matcher(url).matches()) {
                    return AppConfig.getInstance(this).navStructureLevels.get(i);
                }
            }
        }

        // return unknown
        return -1;
    }

    @Override
    public String titleForUrl(String url) {
        ArrayList<HashMap<String,Object>> entries = AppConfig.getInstance(this).navTitles;
        String title = null;

        if (entries != null) {
            for (HashMap<String,Object> entry : entries) {
                Pattern regex = (Pattern)entry.get("regex");
                if (regex != null && regex.matcher(url).matches()) {
                    if (entry.containsKey("title")) {
                        title = (String)entry.get("title");
                        break;
                    }
                }
            }
        }

        return title;
    }

    public void closeDrawers() {
        sideNavManager.closeDrawer();
    }

    public boolean isNotRoot() {
        return !isRoot;
    }

    @Override
    public int getParentUrlLevel() {
        return getGNWindowManager().getParentUrlLevel(activityId);
    }

    @Override
    public int getUrlLevel() {
        return getGNWindowManager().getUrlLevel(activityId);
    }

    @Override
    public void setUrlLevel(int urlLevel) {
        getGNWindowManager().setUrlLevel(activityId, urlLevel);
    }

    public FileDownloader getFileDownloader() {
        return fileDownloader;
    }

    public FileWriterSharer getFileWriterSharer() {
        return fileWriterSharer;
    }

    public StatusCheckerBridge getStatusCheckerBridge() {
        return new StatusCheckerBridge();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if (actionManager != null) {
            actionManager.setTitle(title);
        }
    }

    @Override
    public void startCheckingReadyStatus() {
        statusChecker.run();
    }

    private void stopCheckingReadyStatus() {
        handler.removeCallbacks(statusChecker);
    }

    public void checkReadyStatus() {
        if (this.mWebview != null) {
            this.mWebview.runJavascript("if (median_status_checker && typeof median_status_checker.onReadyState === 'function') median_status_checker.onReadyState(document.readyState);");
        }
    }

    private void checkReadyStatusResult(String status) {
        // if interactiveDelay is specified, then look for readyState=interactive, and show webview
        // with a delay. If not specified, wait for readyState=complete.
        double interactiveDelay = AppConfig.getInstance(this).interactiveDelay;

        if (status.equals("loading") || (Double.isNaN(interactiveDelay) && status.equals("interactive"))) {
            startedLoading = true;
        } else if ((!Double.isNaN(interactiveDelay) && status.equals("interactive"))
                || (startedLoading && status.equals("complete"))) {

            if (status.equals("interactive")) {
                showWebview(interactiveDelay);
            } else {
                showWebview();
            }
            if (isContentReady) {
                stopCheckingReadyStatus();
            }
        }

        if (status.equals("complete") || status.equals("interactive")) {
            contentReady();
        }
    }

    @Override
    public void toggleFullscreen(boolean fullscreen) {
        ActionBar actionBar = this.getSupportActionBar();
        View decorView = getWindow().getDecorView();
        int visibility = decorView.getSystemUiVisibility();
        int fullscreenFlags = View.SYSTEM_UI_FLAG_LOW_PROFILE |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        if (Build.VERSION.SDK_INT >= 16) {
            fullscreenFlags |= View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }

        if (Build.VERSION.SDK_INT >= 19) {
            fullscreenFlags |= View.SYSTEM_UI_FLAG_IMMERSIVE |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        if (fullscreen) {
            visibility |= fullscreenFlags;
            if (actionBar != null) actionBar.hide();
        } else {
            visibility &= ~fullscreenFlags;
            if (actionBar != null && AppConfig.getInstance(this).showActionBar) actionBar.show();

            // Fix for webview keyboard not showing, see https://github.com/mozilla-tw/FirefoxLite/issues/842
            this.mWebview.clearFocus();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (this.systemBarManager != null) this.systemBarManager.enableFullScreen(fullscreen);
        } else {
            decorView.setSystemUiVisibility(visibility);
        }

        // Full-screen is used for playing videos.
        // Allow sensor-based rotation when in full screen (even overriding user rotation preference)
        // If orientation is forced landscape don't set sensor based orientation
        if (fullscreen && AppConfig.getInstance(this).forceScreenOrientation != AppConfig.ScreenOrientations.LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else {
            setScreenOrientationPreference();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ((GoNativeApplication) getApplication()).mBridge.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_GENERIC:
                Iterator<PermissionsCallbackPair> it = pendingPermissionRequests.iterator();
                while (it.hasNext()) {
                    PermissionsCallbackPair pair = it.next();
                    if (pair.permissions.length != permissions.length) continue;
                    boolean skip = false;
                    for (int i = 0; i < pair.permissions.length && i < permissions.length; i++) {
                        if (!pair.permissions[i].equals(permissions[i])) {
                            skip = true;
                            break;
                        }
                    }
                    if (skip) continue;

                    // matches PermissionsCallbackPair
                    if (pair.callback != null) {
                        pair.callback.onPermissionResult(permissions, grantResults);
                    }
                    it.remove();
                }

                if (pendingPermissionRequests.size() == 0 && pendingStartActivityAfterPermissions.size() > 0) {
                    Iterator<Intent> i = pendingStartActivityAfterPermissions.iterator();
                    while (i.hasNext()) {
                        Intent intent = i.next();
                        startActivity(intent);
                        i.remove();
                    }
                }
                break;
        }
    }

    public GoNativeWindowManager getGNWindowManager() {
        return ((GoNativeApplication) getApplication()).getWindowManager();
    }

    @Override
    public int getWindowCount() {
        return getGNWindowManager().getWindowCount();
    }

    public boolean isWindowActive() {
        return activityId.equals(getGNWindowManager().getCurrentActiveWindowId());
    }

    public RelativeLayout getFullScreenLayout() {
        return fullScreenLayout;
    }

    @Override
    public GoNativeWebviewInterface getWebView() {
        return mWebview;
    }

    public LeanWebView getLeanWebView() {
        return (LeanWebView) mWebview;
    }

    public class StatusCheckerBridge {
        @JavascriptInterface
        public void onReadyState(final String state) {
            runOnUiThread(() -> checkReadyStatusResult(state));
        }
    }

    private class ConnectivityChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            retryFailedPage();
            if (connectivityCallback != null) {
                sendConnectivity(connectivityCallback);
            }
        }
    }

    public void getPermission(String[] permissions, PermissionCallback callback) {
        boolean needToRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needToRequest = true;
                break;
            }
        }

        if (needToRequest) {
            if (callback != null) {
                pendingPermissionRequests.add(new PermissionsCallbackPair(permissions, callback));
            }

            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_GENERIC);
        } else {
            // send all granted result
            if (callback != null) {
                int[] results = new int[permissions.length];
                for (int i = 0; i < results.length; i++) {
                    results[i] = PackageManager.PERMISSION_GRANTED;
                }
                callback.onPermissionResult(permissions, results);
            }
        }
    }

    public void startActivityAfterPermissions(Intent intent) {
        if (pendingPermissionRequests.size() == 0) {
            startActivity(intent);
        } else {
            pendingStartActivityAfterPermissions.add(intent);
        }
    }

    private void setScreenOrientationPreference() {
        AppConfig appConfig = AppConfig.getInstance(this);
        if (appConfig.forceScreenOrientation != null) {
            setDeviceOrientation(appConfig.forceScreenOrientation);
            return;
        }

        if (getResources().getBoolean(R.bool.isTablet)) {
            if (appConfig.tabletScreenOrientation != null) {
                setDeviceOrientation(appConfig.tabletScreenOrientation);
                return;
            }
        } else {
            if (appConfig.phoneScreenOrientation != null) {
                setDeviceOrientation(appConfig.phoneScreenOrientation);
                return;
            }
        }

        if (!appConfig.androidFullScreen) {
            setDeviceOrientation(AppConfig.ScreenOrientations.UNSPECIFIED);
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void setDeviceOrientation(AppConfig.ScreenOrientations orientation) {
        switch (orientation) {
            case UNSPECIFIED:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
            case PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
        }
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public interface PermissionCallback {
        void onPermissionResult(String[] permissions, int[] grantResults);
    }

    private class PermissionsCallbackPair {
        String[] permissions;
        PermissionCallback callback;

        PermissionsCallbackPair(String[] permissions, PermissionCallback callback) {
            this.permissions = permissions;
            this.callback = callback;
        }
    }

    public void enableSwipeRefresh() {
        if (this.swipeRefreshLayout != null) {
            this.swipeRefreshLayout.setEnabled(true);
        }
    }

    public void restoreSwipRefreshDefault() {
        if (this.swipeRefreshLayout != null) {
            AppConfig appConfig = AppConfig.getInstance(this);
            this.swipeRefreshLayout.setEnabled(appConfig.pullToRefresh);
        }
    }

    @Override
    public void deselectTabs() {
        this.tabManager.deselectTabs();
    }

    private void listenForSignalStrength() {
        if (this.phoneStateListener != null) return;

        this.phoneStateListener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                latestSignalStrength = signalStrength;
                sendConnectivityOnce();
                if (connectivityCallback != null) {
                    sendConnectivity(connectivityCallback);
                }
            }
        };

        try {
            TelephonyManager telephonyManager = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) {
                GNLog.getInstance().logError(TAG, "Error getting system telephony manager");
            } else {
                telephonyManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, "Error listening for signal strength", e);
        }

    }

    @Override
    public void sendConnectivityOnce(String callback) {
        if (callback == null) return;

        this.connectivityOnceCallback = callback;
        if (this.phoneStateListener != null) {
            sendConnectivity(callback);
        } else {
            listenForSignalStrength();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendConnectivityOnce();
                }
            }, 500);
        }
    }

    private void sendConnectivityOnce() {
        if (this.connectivityOnceCallback == null) return;
        sendConnectivity(this.connectivityOnceCallback);
        this.connectivityOnceCallback = null;
    }

    private void sendConnectivity(String callback) {
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean connected = activeNetwork != null && activeNetwork.isConnected();
        String typeString;
        if (activeNetwork != null) {
            typeString = activeNetwork.getTypeName();
        } else {
            typeString = "DISCONNECTED";
        }

        try {
            JSONObject data = new JSONObject();
            data.put("connected", connected);
            data.put("type", typeString);

            if (this.latestSignalStrength != null) {
                JSONObject signalStrength = new JSONObject();

                signalStrength.put("cdmaDbm", latestSignalStrength.getCdmaDbm());
                signalStrength.put("cdmaEcio", latestSignalStrength.getCdmaEcio());
                signalStrength.put("evdoDbm", latestSignalStrength.getEvdoDbm());
                signalStrength.put("evdoEcio", latestSignalStrength.getEvdoEcio());
                signalStrength.put("evdoSnr", latestSignalStrength.getEvdoSnr());
                signalStrength.put("gsmBitErrorRate", latestSignalStrength.getGsmBitErrorRate());
                signalStrength.put("gsmSignalStrength", latestSignalStrength.getGsmSignalStrength());
                if (Build.VERSION.SDK_INT >= 23) {
                    signalStrength.put("level", latestSignalStrength.getLevel());
                }
                data.put("cellSignalStrength", signalStrength);
            }

            String js = LeanUtils.createJsForCallback(callback, data);
            runJavascript(js);
        } catch (JSONException e) {
            GNLog.getInstance().logError(TAG, "JSON error sending connectivity", e);
        }
    }

    @Override
    public void subscribeConnectivity(final String callback) {
        this.connectivityCallback = callback;
        listenForSignalStrength();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                sendConnectivity(callback);
            }
        }, 500);
    }

    @Override
    public void unsubscribeConnectivity() {
        this.connectivityCallback = null;
    }

    public interface GeolocationPermissionCallback {
        void onResult(boolean granted);
    }

    // set brightness to a negative number to restore default
    @Override
    public void setBrightness(float brightness) {
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = brightness;
        getWindow().setAttributes(layout);
    }

    @Override
    public void setSidebarNavigationEnabled(boolean enabled) {
        if (sideNavManager != null) {
            sideNavManager.setSideBarNavigationEnabled(enabled);
        }
    }

    /**
     * @param appTheme set to null if will use sharedPreferences
     */

    @Override
    public void setupAppTheme(String appTheme) {
        if (TextUtils.isEmpty(appTheme)) return;

        ConfigPreferences preferences = new ConfigPreferences(this);
        preferences.setAppTheme(appTheme);

        this.appTheme = appTheme;

        // Updating app theme on runtime triggers a configuration change and recreates the app
        // To prevent consecutive calls, ignore theme setup on onCreate() by enabling this flag
        flagThemeConfigurationChange = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ThemeUtils.setAppThemeApi31AndAbove(this, appTheme);
        } else {
            ThemeUtils.setAppThemeApi30AndBelow(appTheme);
        }

        // update CSS attribute when changing themes, regardless of configuration changes
        setupCssTheme();
    }

    @Override
    public void resetAppTheme() {
        this.appTheme = AppConfig.getInstance(this).androidTheme;
        setupAppTheme(appTheme);
    }

    @SuppressLint("RequiresFeature")
    private void setupWebviewTheme(String appTheme) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            Log.d(TAG, "Dark mode feature is not supported");
            return;
        }

        if (mWebview.getSettings() == null) {
            return;
        }

        if ("dark".equals(appTheme)) {
            WebSettingsCompat.setForceDark(this.mWebview.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
        } else if ("light".equals(appTheme)) {
            WebSettingsCompat.setForceDark(this.mWebview.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
        } else {
            switch (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) {
                case Configuration.UI_MODE_NIGHT_YES:
                    WebSettingsCompat.setForceDark(this.mWebview.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
                    break;
                case Configuration.UI_MODE_NIGHT_NO:
                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                    WebSettingsCompat.setForceDark(this.mWebview.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
                    break;
            }

            // Force dark on if supported, and only use theme from web
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                        this.mWebview.getSettings(),
                        WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                );
            }
        }

        // update CSS attribute when changing themes, regardless of configuration changes
        setupCssTheme();
    }

    public void setupCssTheme() {
        String js = String.format("document.documentElement.setAttribute('data-color-scheme-option', '%s');", this.appTheme);
        mWebview.runJavascript(js);
    }

    private void validateGoogleService() {
        try {
            if (BuildConfig.GOOGLE_SERVICE_INVALID) {
                Toast.makeText(this, R.string.google_service_required, Toast.LENGTH_LONG).show();
                GNLog.getInstance().logError(TAG, "validateGoogleService: " + R.string.google_service_required, null, GNLog.TYPE_TOAST_ERROR);
            }
        } catch (NullPointerException ex) {
            GNLog.getInstance().logError(TAG, "validateGoogleService: " + ex.getMessage(), null, GNLog.TYPE_TOAST_ERROR);
        }
    }

    @Override
    public void updateStatusBarOverlay(boolean isOverlayEnabled) {
        if (this.systemBarManager == null) return;
        AppConfig.getInstance(this).enableOverlayInStatusBar = isOverlayEnabled;
        this.systemBarManager.requestApplyInsets();
    }

    @Override
    public void updateStatusBarStyle(String statusBarStyle) {
        if (this.systemBarManager != null)
            this.systemBarManager.updateStatusBarStyle(statusBarStyle);
    }

    @Override
    public void setStatusBarColor(int color) {
        if (this.systemBarManager != null)
            this.systemBarManager.setStatusBarColor(color);
    }

    @Override
    public void updateSystemNavBarOverlay(boolean isOverlayEnabled) {
        if (this.systemBarManager == null) return;
        AppConfig.getInstance(this).enableOverlayInSystemNavBar = isOverlayEnabled;
        this.systemBarManager.requestApplyInsets();
    }

    @Override
    public void updateSystemNavBarStyle(String systemNavBarStyle) {
        if (this.systemBarManager != null)
            this.systemBarManager.updateSystemNavBarStyle(systemNavBarStyle);
    }

    @Override
    public void setSystemNavBarColor(int color) {
        if (this.systemBarManager != null)
            this.systemBarManager.setSystemNavBarColor(color);
    }

    @Override
    public void setSystemBarColor(int color) {
        if (this.systemBarManager != null)
            this.systemBarManager.setSystemBarColor(color);
    }

    @Override
    public void runGonativeDeviceInfo(String callback, boolean includeCarrierNames) {
        if (includeCarrierNames) {
            deviceInfoCallback = callback;
            requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
        } else {
            Map<String, Object> installationInfo = Installation.getInfo(this);
            installationInfo.put("isFirstLaunch", ((GoNativeApplication) getApplication()).isFirstLaunch());

            // insert additional device info from other plugins
            GoNativeApplication application = (GoNativeApplication)getApplication();
            installationInfo.putAll(application.mBridge.getExtraDeviceInfo(this));

            JSONObject jsonObject = new JSONObject(installationInfo);
            String js = LeanUtils.createJsForCallback(callback, jsonObject);
            this.runJavascript(js);
        }
    }

    @Override
    public Map<String, Object> getDeviceInfo() {
        return Installation.getInfo(this);
    }

    @Override
    public void windowFlag(boolean add, int flag) {
        if (add) {
            getWindow().addFlags(flag);
        } else {
            getWindow().clearFlags(flag);
        }
    }

    @Override
    public void setCustomTitle(String title) {
        if (!title.isEmpty()) {
            setTitle(title);
        } else {
            setTitle(R.string.app_name);
        }
    }

    @Override
    public void downloadFile(String url, String filename, boolean shouldSaveToGallery, boolean open, String callback) {
        fileDownloader.downloadFile(url, filename, shouldSaveToGallery, open, callback);
    }

    @Override
    public void selectTab(int tabNumber) {
        if (tabManager == null) return;
        tabManager.selectTabNumber(tabNumber, false);
    }

    @Override
    public void setTabsWithJson(JSONObject tabsJson, int tabMenuId) {
        if (tabManager == null) return;
        tabManager.setTabsWithJson(tabsJson, tabMenuId);
    }

    @Override
    public void focusAudio(boolean enabled) {
        if (enabled) {
            AudioUtils.requestAudioFocus(this);
        } else {
            AudioUtils.abandonFocusRequest(this);
        }
    }

    @Override
    public void clipboardSet(String content) {
        if (content.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("copy", content);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    public void clipboardGet(String callback) {
        if (!TextUtils.isEmpty(callback)) {
            Map<String, String> params = new HashMap<>();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence pasteData;
            if (clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                pasteData = item.getText();
                if (pasteData != null)
                    params.put("data", pasteData.toString());
                else
                    params.put("error", "Clipboard item is not a string.");
            } else {
                params.put("error", "No Clipboard item available.");
            }
            JSONObject jsonObject = new JSONObject(params);
            runJavascript(LeanUtils.createJsForCallback(callback, jsonObject));
        }
    }

    @Override
    public void sendRegistration(JSONObject data) {
        if(registrationManager == null) return;

        if(data != null){
            JSONObject customData = data.optJSONObject("customData");
            if(customData == null){
                try { // try converting json string from url to json object
                    customData = new JSONObject(data.optString("customData"));
                } catch (JSONException e){
                    GNLog.getInstance().logError(TAG, "GoNative Registration JSONException:- " + e.getMessage(), e);
                }
            }
            if(customData != null){
                registrationManager.setCustomData(customData);
            }
        }
        registrationManager.sendToAllEndpoints();
    }

    @Override
    public void runCustomNativeBridge(Map<String, String> params) {
        // execute code defined by the CustomCodeHandler
        // call JsCustomCodeExecutor#setHandler to override this default handler
        JSONObject data = JsCustomCodeExecutor.execute(params);
        String callback = params.get("callback");
        if(callback != null && !callback.isEmpty()) {
            final String js = LeanUtils.createJsForCallback(callback, data);
            // run on main thread
            Handler mainHandler = new Handler(getMainLooper());
            mainHandler.post(() -> runJavascript(js));
        }
    }

    @Override
    public void promptLocationService() {
        this.locationServiceHelper.promptLocationService();
    }

    @Override
    public boolean isLocationServiceEnabled() {
        return this.locationServiceHelper.isLocationServiceEnabled();
    }

    public LocationServiceHelper getLocationServiceHelper() {
        return this.locationServiceHelper;
    }

    @Override
    public void setRestoreBrightnessOnNavigation(boolean restore) {
        this.restoreBrightnessOnNavigation = restore;
    }

    public boolean isRestoreBrightnessOnNavigation() {
        return this.restoreBrightnessOnNavigation;
    }

    @Override
    public void closeCurrentWindow() {
        if (!getGNWindowManager().isRoot(activityId)) {
            this.finish();
        }
    }

    @Override
    public void openNewWindow(String url, String mode) {
        if (TextUtils.isEmpty(url)) return;

        Uri uri = Uri.parse(url);

        // Same window
        if ("internal".equals(mode)) {
            loadUrl(url);
            return;
        }

        // External default browser
        if ("external".equals(mode)) {
            openExternalBrowser(uri);
            return;
        }

        // Chrome in-app custom tab
        if ("appbrowser".equals(mode)) {
            openAppBrowser(uri);
            return;
        }

        // Default
        AppConfig appConfig = AppConfig.getInstance(this);

        // Check maxWindows conditions
        if (appConfig.maxWindowsEnabled && appConfig.numWindows > 0 && getGNWindowManager().getWindowCount() >= appConfig.numWindows && onMaxWindowsReached(url))
            return;

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("isRoot", false);
        intent.putExtra("url", url);
        intent.putExtra(MainActivity.EXTRA_IGNORE_INTERCEPT_MAXWINDOWS, true);
        startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);
    }

    public void openExternalBrowser(Uri uri) {
        if (uri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (isAppLink(uri)) {
                // force the URL to launch in the device's default browser to avoid infinite looping bug with AppLink.
                intent.setPackage(getDefaultBrowserPackageName());
            }
            startActivity(intent);
        } catch (Exception ex) {
            if (ex instanceof ActivityNotFoundException) {
                Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_LONG).show();
                GNLog.getInstance().logError(TAG, getString(R.string.app_not_installed), ex, GNLog.TYPE_TOAST_ERROR);
            } else {
                GNLog.getInstance().logError(TAG, "openExternalBrowser: launchError - uri: " + uri, ex);
            }
        }
    }

    public void openAppBrowser(Uri uri) {
        if (uri == null) return;
        try {
            CustomTabColorSchemeParams params = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                    .setSecondaryToolbarColor(ContextCompat.getColor(this, R.color.titleTextColor))
                    .build();

            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(params)
                    .build();
            customTabsIntent.intent.setData(uri);
            appBrowserActivityLauncher.launch(customTabsIntent.intent);
        } catch (Exception ex) {
            if (ex instanceof ActivityNotFoundException) {
                Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_LONG).show();
                GNLog.getInstance().logError(TAG, getString(R.string.app_not_installed), ex, GNLog.TYPE_TOAST_ERROR);
            } else {
                GNLog.getInstance().logError(TAG, "openAppBrowser: launchError - uri: " + uri, ex);
            }
        }
    }

    @Override
    public boolean onMaxWindowsReached(String url) {
        AppConfig appConfig = AppConfig.getInstance(this);
        GoNativeWindowManager windowManager = getGNWindowManager();

        if (appConfig.autoClose && LeanUtils.urlsMatchIgnoreTrailing(url, appConfig.getInitialUrl())) {

            // Set this activity as new root
            isRoot = true;

            windowManager.setAsNewRoot(activityId);

            // Reset URL levels
            windowManager.setUrlLevels(activityId, -1, -1);

            // Reload activity as root
            initialRootSetup();

            if (actionManager != null) {
                actionManager.setupActionBar(isRoot);
                actionManager.setTitleDisplayForUrl(url,true);
            }

            if (sideNavManager != null) {
                sideNavManager.setupNavigationMenu(isRoot);
                sideNavManager.showNavigationMenu(appConfig.showNavigationMenu);
                sideNavManager.toggleSyncState();
            }

            windowManager.setIgnoreInterceptMaxWindows(activityId, true);

            // Send broadcast to close other activity
            Intent intent = new Intent(MainActivity.BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED);
            intent.putExtra(MainActivity.EXTRA_NEW_ROOT_URL, url);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            // Add listener when all excess windows are closed
            windowManager.setOnExcessWindowClosedListener(() -> {
                // Load new URL
                mWebview.loadUrl(url);
                // Remove listener
                windowManager.setOnExcessWindowClosedListener(null);
            });

            return true;
        } else {

            // Get excess window
            String excessWindowId = windowManager.getExcessWindow();

            // Send broadcast to close the excess window
            Intent intent = new Intent(MainActivity.BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED);
            intent.putExtra(MainActivity.EXTRA_EXCESS_WINDOW_ID, excessWindowId);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            // Remove from window list
            windowManager.removeWindow(excessWindowId);
        }

        return false;
    }

    @Override
    public void getKeyboardInfo(String callback) {
        if (keyboardManager == null || TextUtils.isEmpty(callback)) return;
        runJavascript(LeanUtils.createJsForCallback(callback, keyboardManager.getKeyboardData()));
    }

    @Override
    public void addKeyboardListener(String callback) {
        if (keyboardManager == null) return;
        keyboardManager.setCallback(callback);
    }

    @Override
    public Map<String, String> checkPermissionStatus(JSONArray permissions) {
        return PermissionsUtilKt.checkPermissionStatus(this, permissions);
    }

    @Override
    public void invokeCallback(String callback, JSONObject data) {
        if (eventsManager != null) eventsManager.invokeCallback(callback, data);
    }

    @Override
    public void subscribeEvent(String callback) {
        if (eventsManager != null) eventsManager.subscribe(callback);
    }

    @Override
    public void unsubscribeEvent(String callback) {
        if (eventsManager != null) eventsManager.unsubscribe(callback);
    }

    public MedianEventsManager getEventsManager() {
        return this.eventsManager;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    public String getLaunchSource() {
        return launchSource;
    }

    private boolean isAppLink(Uri uri) {
        if (uri == null) return false;

        AppConfig appConfig = AppConfig.getInstance(this);
        List<String> deeplinkDomains = appConfig.deepLinkDomains;

        if (deeplinkDomains == null || deeplinkDomains.isEmpty()) return false;
        return deeplinkDomains.contains(uri.getHost());
    }

    private String getDefaultBrowserPackageName() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfo != null && resolveInfo.activityInfo != null) {
            return resolveInfo.activityInfo.packageName;
        }

        return null;
    }

    public void handleMessage(String message) {
        if(message.isEmpty()) return;
        Bridge bridge = ((GoNativeApplication) getApplication()).mBridge;
        runOnUiThread(() -> {
            try {
                JSONObject commandObject = new JSONObject(message);
                bridge.handleJSBridgeFunctions(this, commandObject);
            } catch (JSONException jsonException){ // pass it as a uri
                bridge.handleJSBridgeFunctions(this, Uri.parse(message));
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        WebView.HitTestResult hitResult = mWebview.getHitTestResult();
        String resultUrl = hitResult.getExtra();
        int resultType = hitResult.getType();

        if (TextUtils.isEmpty(resultUrl) || resultType != WebView.HitTestResult.SRC_ANCHOR_TYPE) return;

        this.contextMenuUrl = resultUrl;

        menu.clear();
        menu.setHeaderTitle(contextMenuUrl);

        ContextMenuConfig config = AppConfig.getInstance(this).contextMenuConfig;
        if (config == null || !config.getEnabled() || !config.getLinkActions().getEnabled()) return;

        if (config.getLinkActions().getCopy()) menu.add(0, CONTEXT_MENU_ID_COPY, 0, R.string.action_copy);
        if (config.getLinkActions().getOpen()) menu.add(0, CONTEXT_MENU_ID_OPEN, 0, R.string.action_open_browser);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == CONTEXT_MENU_ID_COPY) {
            clipboardSet(contextMenuUrl);
            return true;
        } else if (itemId == CONTEXT_MENU_ID_OPEN) {
            openExternalBrowser(Uri.parse(contextMenuUrl));
            return true;
        } else {
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(@NonNull Menu menu) {
        super.onContextMenuClosed(menu);
        this.contextMenuUrl = "";
    }

    @Override
    public void setContextMenuEnabled(boolean enabled) {
        if (mWebviewContainer == null) return;
        if (enabled) {
            registerForContextMenu(mWebviewContainer);
        } else {
            unregisterForContextMenu(mWebviewContainer);
        }
    }

    public UrlLoader getUrlLoader() {
        return urlLoader;
    }
}
