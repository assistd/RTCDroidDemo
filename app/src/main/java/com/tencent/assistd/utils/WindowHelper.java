package com.tencent.assistd.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class WindowHelper {
    private static final String TAG = "WindowHelper";
    /*设置透明状态栏*/
    public static void makeStatusBarTransparent(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = activity.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    /*设置透明状态栏*/
    public static void makeNavigationVisible(Activity activity, Boolean visible) {
        final View decorView = activity.getWindow().getDecorView();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(visible ? View.VISIBLE : View.GONE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final int visibleFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            final int inVisibleFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;;
            decorView.setSystemUiVisibility(visible ? visibleFlags : inVisibleFlags);
            Log.d(TAG, "setSystemUiVisibility: " + (visible ? visibleFlags : inVisibleFlags));
            decorView.setOnSystemUiVisibilityChangeListener(visible ? null : visibility -> {
                Log.d(TAG, "OnSystemUiVisibilityChangeListener" + visibility);
                if (visibility == 0) {
                    decorView.setSystemUiVisibility(inVisibleFlags);
                    Log.d(TAG, "setSystemUiVisibility on listener: " + inVisibleFlags);
                }
            });
        }
    }

    //设置状态栏文字颜色
    public static void setAndroidNativeLightStatusBar(Activity activity, boolean dark) {
        View decor = activity.getWindow().getDecorView();
        if (dark) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
