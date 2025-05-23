package co.median.android;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.RelativeLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import co.median.median_core.AppConfig;
import co.median.median_core.GNLog;

/**
* Created by weiyin on 2/2/15.
* Copyright 2014 GoNative.io LLC
*/
class GoNativeWebChromeClient extends WebChromeClient {
    private final MainActivity mainActivity;
    private final UrlNavigation urlNavigation;
    private final boolean webviewConsoleLogEnabled;
    private View customView;
    private CustomViewCallback callback;
    private boolean isFullScreen = false;
    private long deniedGeolocationUptime;

    public GoNativeWebChromeClient(MainActivity mainActivity, UrlNavigation urlNavigation) {
        this.mainActivity = mainActivity;
        this.urlNavigation = urlNavigation;
        this.deniedGeolocationUptime = 0;
        this.webviewConsoleLogEnabled = AppConfig.getInstance(mainActivity).enableWebConsoleLogs;
        if (this.webviewConsoleLogEnabled) {
            Log.d("GoNative WebView", "Web Console logs enabled");
        }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result){
        new MaterialAlertDialogBuilder(mainActivity)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, which) -> result.confirm())
                .setOnDismissListener(dialog -> result.cancel()).show();
        return true;
    }

    @Override
    public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
        urlNavigation.cancelLoadTimeout();
        return super.onJsBeforeUnload(view, url, message, result);
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
        if (!AppConfig.getInstance(mainActivity).permissions.usesGeolocation()) {
            callback.invoke(origin, false, false);
            return;
        }

        // There is a bug in Android webview where this function will be continuously called in
        // a loop if we run callback.invoke asynchronously with granted=false, degrading webview
        // and javascript performance. If we have recently been denied geolocation by the user,
        // run callback.invoke(granted=false) synchronously and do not prompt user.
        //
        // Note: this infinite loop situation also happens if we run callback.invoke(origin, true, false),
        // regardless if we do it synchronously or async.
        long elapsed = SystemClock.uptimeMillis() - deniedGeolocationUptime;
        if (elapsed < 1000 /* 1 second */) {
            callback.invoke(origin, false, false);
            return;
        }

        mainActivity.getLocationServiceHelper().promptLocationService(enabled -> {
            if (enabled) {
                callback.invoke(origin, true, false);
            } else {
                callback.invoke(origin, false, false);
                deniedGeolocationUptime = SystemClock.uptimeMillis();
            }
        });
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        RelativeLayout fullScreen = this.mainActivity.getFullScreenLayout();
        if (fullScreen == null) return;

        this.customView = view;
        this.callback = callback;
        this.isFullScreen = true;

        fullScreen.setVisibility(View.VISIBLE);
        fullScreen.addView(view, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        this.mainActivity.toggleFullscreen(this.isFullScreen);
    }

    @Override
    public void onHideCustomView() {
        this.customView = null;
        this.isFullScreen = false;

        RelativeLayout fullScreen = this.mainActivity.getFullScreenLayout();
        if (fullScreen != null) {
            fullScreen.setVisibility(View.INVISIBLE);
            fullScreen.removeAllViews();
        }

        if (this.callback != null) {
            callback.onCustomViewHidden();
        }

        this.mainActivity.toggleFullscreen(this.isFullScreen);
    }

    public boolean exitFullScreen() {
        if (this.isFullScreen) {
            onHideCustomView();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onCloseWindow(WebView window) {
        if (mainActivity.isNotRoot()) mainActivity.finish();
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        // make sure there is no existing message
        urlNavigation.cancelFileUpload();

        if (fileChooserParams.getMode() == FileChooserParams.MODE_SAVE) {
            // MODE_SAVE is unimplemented
            filePathCallback.onReceiveValue(null);
            return false;
        }

        urlNavigation.launchFileUpload(filePathCallback, fileChooserParams);
        return true;
    }

    @Override
    public void onReceivedTitle(WebView view, String title){
        mainActivity.updatePageTitle();
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        urlNavigation.createNewWindow(view, resultMsg);
        return true;
    }

    @Override
    @TargetApi(21)
    public void onPermissionRequest(final PermissionRequest request) {
        String[] resources = request.getResources();

        ArrayList<String> permissions = new ArrayList<>();
        for (int i = 0; i < resources.length; i++) {
            if (resources[i].equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
                permissions.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);
            } else if (resources[i].equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                permissions.add(Manifest.permission.CAMERA);
            }
        }

        String[] permissionsArray = new String[permissions.size()];
        permissionsArray = permissions.toArray(permissionsArray);

        mainActivity.getPermission(permissionsArray, new MainActivity.PermissionCallback() {
            @Override
            public void onPermissionResult(String[] permissions, int[] grantResults) {
                ArrayList<String> grantedPermissions = new ArrayList<String>();
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        continue;
                    }

                    if (permissions[i].equals(Manifest.permission.RECORD_AUDIO)) {
                        grantedPermissions.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
                    } else if (permissions[i].equals(Manifest.permission.CAMERA)) {
                        grantedPermissions.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
                    }
                }

                if (grantedPermissions.isEmpty()) {
                    request.deny();
                } else {
                    String[] grantedPermissionsArray = new String[grantedPermissions.size()];
                    grantedPermissionsArray = grantedPermissions.toArray(grantedPermissionsArray);
                    request.grant(grantedPermissionsArray);
                }
            }
        });
    }

    @Override
    public void onPermissionRequestCanceled(PermissionRequest request) {
        super.onPermissionRequestCanceled(request);
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        if (webviewConsoleLogEnabled) {
            switch (consoleMessage.messageLevel()) {
                case LOG:
                    Log.i("[console.log]", consoleMessage.message());
                    break;
                case DEBUG:
                case TIP:
                    Log.d("[console.debug]", consoleMessage.message());
                    break;
                case WARNING:
                    Log.w("[console.warn]", consoleMessage.message());
                    break;
                case ERROR:
                    GNLog.getInstance().logError("[console.error]", consoleMessage.message(), new Exception(consoleMessage.message()), GNLog.TYPE_WEB_CONSOLE);
                    break;
            }
        }
        return true;
    }
}
