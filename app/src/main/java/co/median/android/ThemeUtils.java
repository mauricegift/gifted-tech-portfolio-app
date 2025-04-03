package co.median.android;

import static android.content.Context.UI_MODE_SERVICE;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;

import co.median.median_core.AppConfig;

public class ThemeUtils {

    private static final String THEME_PREFERENCE_NAME = "median_theme_preference";
    private static final String THEME_PREFERENCE_KEY_INITIAL_THEME_SET = "THEME_PREFERENCE_KEY_INITIAL_THEME_SET";

    public static boolean isDarkThemeEnabled(Context context) {
        int nightModeFlags =
                context.getResources().getConfiguration().uiMode &
                        Configuration.UI_MODE_NIGHT_MASK;

        return switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES -> true;
            case Configuration.UI_MODE_NIGHT_NO -> false;
            default -> false; // Default to light mode
        };
    }

    public static String getConfigAppTheme(Context context) {
        ConfigPreferences configPreferences = new ConfigPreferences(context);
        String appTheme = configPreferences.getAppTheme();

        if (TextUtils.isEmpty(appTheme)) {
            AppConfig appConfig = AppConfig.getInstance(context);
            if (!TextUtils.isEmpty(appConfig.androidTheme)) {
                appTheme = appConfig.androidTheme;
            } else {
                appTheme = "light"; // default is 'light' to support apps with no night assets provided
            }
            configPreferences.setAppTheme(appTheme);
        }
        return appTheme;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public static void setAppThemeApi31AndAbove(Context context, String appTheme) {
        UiModeManager uim = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        if ("light".equals(appTheme)) {
            uim.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
        } else if ("dark".equals(appTheme)) {
            uim.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
        } else if ("auto".equals(appTheme)) {
            uim.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
        } else {
            // default
            uim.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
        }
    }

    public static void setAppThemeApi30AndBelow(String appTheme) {
        if ("light".equals(appTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if ("dark".equals(appTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("auto".equals(appTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            // default
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    public static void initialAppThemeSet(Context context) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putBoolean(THEME_PREFERENCE_KEY_INITIAL_THEME_SET, true).apply();
    }

    public static boolean isInitialAppThemeSet(Context context) {
        return getSharedPreferences(context).getBoolean(THEME_PREFERENCE_KEY_INITIAL_THEME_SET, false);
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(THEME_PREFERENCE_NAME, Context.MODE_PRIVATE);
    }
}
