package com.tencent.assistd.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;

import com.tencent.assistd.R;

import java.lang.reflect.Method;

public class PopupButtonMenu extends PopupMenu {
    private static final String TAG = "PopupButtonMenu";

    public PopupButtonMenu(Context context, View anchor) {
        super(context, anchor);
    }

    public PopupButtonMenu(Context context, View anchor, int menuRes) {
        super(context, anchor);
        getMenuInflater().inflate(menuRes, getMenu());
        initIcon();
    }

//    public PopupButtonMenu(Context context, View anchor, int gravity) {
//        super(context, anchor, gravity);
//    }

    public PopupButtonMenu(Context context, View anchor, int gravity, int popupStyleAttr, int popupStyleRes) {
        super(context, anchor, gravity, popupStyleAttr, popupStyleRes);
    }

    private void initIcon() {
        try {
            @SuppressLint("PrivateApi") Class<?> clazz = Class.forName("com.android.internal.view.menu.MenuBuilder");
            @SuppressLint("DiscouragedPrivateApi") Method m = clazz.getDeclaredMethod("setOptionalIconsVisible", boolean.class);
            m.setAccessible(true);
            m.invoke(getMenu(), true);
        } catch (Exception e) {
            Log.e(TAG, "set pop menu icon err: " + e.getMessage());
        }
    }
}
