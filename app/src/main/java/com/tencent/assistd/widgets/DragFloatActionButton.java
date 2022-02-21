package com.tencent.assistd.widgets;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.view.menu.MenuBuilder;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.internal.NavigationMenu;
import com.tencent.assistd.R;
import com.tencent.assistd.fabspeeddial.FabSpeedDial;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class DragFloatActionButton extends FabSpeedDial {
    private static final String TAG = "DragFloatActionButton";
    private FloatingActionButton fabBtn;
    private Class<?> superClass;
    private int screenWidth;
    private int screenHeight;
    private int screenWidthHalf;
    private int screenHeightHalf;

    public DragFloatActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DragFloatActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public boolean isTopRight() {
        return getX() > screenWidthHalf && getY() < screenHeightHalf;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initFab();
    }

    @SuppressLint({"ClickableViewAccessibility", "RestrictedApi"})
    private void initFab() {
        Log.i(TAG, "initFab ");
        if(fabBtn == null) {
            try {
                superClass = getClass().getSuperclass();
                if(superClass == null) return;
                @SuppressLint("DiscouragedPrivateApi") Field f = superClass.getDeclaredField("fab");
                f.setAccessible(true);
                fabBtn = (FloatingActionButton) f.get(this);
                assert fabBtn != null;
                fabBtn.setOnTouchListener((v, event) -> onFabTouchEvent(event));
                f = superClass.getDeclaredField("navigationMenu");
                f.setAccessible(true);
                NavigationMenu menu = (NavigationMenu) f.get(this);
                assert menu != null;
                @SuppressLint("PrivateApi") Class<?> clazz = Class.forName("com.android.internal.view.menu.MenuBuilder");
                @SuppressLint("DiscouragedPrivateApi") Method m = clazz.getDeclaredMethod("setOptionalIconsVisible", boolean.class);
                m.setAccessible(true);
                MenuBuilder mb = (MenuBuilder)menu.getRootMenu();
                m.invoke(mb, true);
            }catch (Exception e) {
                Log.e(TAG, "initFab err " + e.getMessage());
            }
        }
    }

    private void setFbGravity(int gravity) {
        try {
            if(superClass == null) return;
            @SuppressLint("DiscouragedPrivateApi") Field f = superClass.getDeclaredField("fabGravity");
            f.setAccessible(true);
            f.set(this, gravity);
            f = superClass.getDeclaredField("menuItemsLayout");
            f.setAccessible(true);
            f.get(this);

            LayoutParams layoutParams =
                    new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int coordinatorLayoutOffset = 0;//getResources().getDimensionPixelSize(R.dimen.coordinator_layout_offset);
            switch (gravity) {
                case TOP_START: setGravity(Gravity.TOP | Gravity.START); break;
                case TOP_END: setGravity(Gravity.TOP | Gravity.END); break;
                case BOTTOM_END: setGravity(Gravity.BOTTOM | Gravity.START); break;
                case BOTTOM_START: setGravity(Gravity.BOTTOM | Gravity.END); break;
            }
            LinearLayout menuItemsLayout = (LinearLayout) f.get(this);
            assert menuItemsLayout != null;
            menuItemsLayout.setLayoutParams(layoutParams);
            Log.i(TAG, "setFbGravity " + gravity);
        }catch (IllegalAccessException | NoSuchFieldException e) {
            Log.e(TAG, "setFbGravity err " + e.getMessage());
        }
    }

    private void init() {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        screenWidth = displayMetrics.widthPixels;
        screenWidthHalf = screenWidth / 2;
        screenHeight = displayMetrics.heightPixels;
        screenHeightHalf = screenHeight / 2;
        Log.i(TAG, String.format("init w: %d, h: %d", screenWidth, screenHeight));
    }

    public void setPosRightTop() {
        post(() -> {
            setX(0);//(screenWidth - getWidth());
            setY(0);//(screenHeight - getHeight());
            setFbGravity(TOP_START);
            Log.i(TAG, String.format("setPosRightBottom w: %d, h: %d, wa: %d, ha: %d, x: %f, y: %f", getWidth(), getHeight(), screenWidth, screenHeight, getX(), getY()));
        });
    }

    private int lastOrientation = getResources().getConfiguration().orientation;
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(lastOrientation == newConfig.orientation) return;
        lastOrientation = newConfig.orientation;
        post(() -> {
            float x = getX();
            float y = getY();
            int eH = getHeight();
            int eW = getWidth();
            float relativeX = (x + eW * 1.0f / 2) / screenWidth;
            float relativeY = (y + eH * 1.0f / 2) / screenHeight;
            init();

            x = screenWidth * relativeX - eW * 1.0f / 2;
            y = screenHeight * relativeY - eH * 1.0f / 2;
            if(x < (screenWidth*1.0/2)) {
                x = 0;
            }else {
                x = screenWidth - eW;
            }
            if(y < 0) {
                y = 0;
            }else if (y > screenHeight - eH) {
                y = screenHeight - eH - 20;
            }
            setX(x);
            setY(y);
            setFbGravity(y >= screenHeightHalf ? (x >= screenWidthHalf ? BOTTOM_END : BOTTOM_START) : (x >= screenWidthHalf ? TOP_END : TOP_START));
        });
    }

    private int lastX;
    private int lastY;
    private int downX;
    private int downY;

    private boolean isDrag;

    public boolean onFabTouchEvent(MotionEvent event) {
        int rawX = (int) event.getRawX();
        int rawY = (int) event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                init();
                isDrag = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                lastX = rawX;
                lastY = rawY;
                downX = rawX;
                downY = rawY;
                Log.i(TAG, "ACTION_DOWN----> getX=" + getX() + "；getY=" + getY());
                break;
            case MotionEvent.ACTION_MOVE:
                isDrag = true;
                //计算手指移动了多少
                int dx = rawX - lastX;
                int dy = rawY - lastY;
                int ddx = rawX - downX;
                int ddy = rawY - downY;
                //这里修复一些手机无法触发点击事件的问题
                int distance= (int) Math.sqrt(ddx*ddx+ddy*ddy);
                if(distance<2){//给个容错范围，不然有部分手机还是无法点击
                    isDrag=false;
                    break;
                }

                float x = getX() + dx;
                float y = getY() + dy;

                //检测是否到达边缘 左上右下
                x = x < 0 ? 0 : x > screenWidth - getWidth() ? screenWidth - getWidth() : x;
                y = y < 0 ? 0 : y > screenHeight - getHeight() ? screenHeight - getHeight() : y;

                setX(x);
                setY(y);
                Log.i(TAG, "ACTION_MOVE----> getX=" + getX() + "；getY=" + getY());
                lastX = rawX;
                lastY = rawY;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (isDrag) {
                    //恢复按压效果
                    setPressed(false);
                    Log.i(TAG, "ACTION_UP----> getX=" + getX() + "；screenWidthHalf=" + screenWidthHalf);
                    if (rawX >= screenWidthHalf) {
                        animate().setInterpolator(new DecelerateInterpolator())
                                .setDuration(500)
                                .xBy(screenWidth - getWidth() - getX())
                                .start();
                    } else {
                        ObjectAnimator oa = ObjectAnimator.ofFloat(this, "x", getX(), 0);
                        oa.setInterpolator(new DecelerateInterpolator());
                        oa.setDuration(500);
                        oa.start();
                    }
                    setFbGravity(rawY >= screenHeightHalf ? (rawX >= screenWidthHalf ? BOTTOM_END : BOTTOM_START) : (rawX >= screenWidthHalf ? TOP_END : TOP_START));
                    return true;
                }
                Log.i(TAG,"ACTION_UP----> " + isDrag);
                break;
        }
        //如果是拖拽则消耗事件，否则正常传递即可。
        return false;

    }
}
