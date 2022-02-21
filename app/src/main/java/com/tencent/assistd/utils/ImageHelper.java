package com.tencent.assistd.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageHelper {
    private static final String TAG = "ImageHelper";

    public static boolean isDarkBitamp(Bitmap bitmap){
        boolean isDark = false;
        try {
            if (bitmap != null) {
                isDark = getBitmapSamplings(bitmap);
            }
        }catch (Exception e){
            Log.e(TAG,"read bitmap error");
        }
        return isDark;
    }

    private static boolean getBitmapSamplings(Bitmap bitmap){
        Palette palette = Palette.from(bitmap)
                .setRegion(0, 0, bitmap.getWidth(),bitmap.getHeight())
                .clearFilters()
                .generate();
        return getBitmapPaletteDark(palette);
    }

    private static boolean getBitmapPaletteDark(Palette hotSeatPalette) {
        Palette.Swatch vibrant = hotSeatPalette.getVibrantSwatch();
        if(vibrant != null) {
            double v = ColorUtils.calculateLuminance(vibrant.getRgb());
            return v < 0.5;
        }
        return false;
    }

    public static Bitmap getImageFromCache(Context context, Boolean isHorizontal) {
        String imagePath = imageCachePath(context, isHorizontal);
        if(imagePath.isEmpty()) return null;
        try {
            FileInputStream fis = new FileInputStream(imagePath);
            return BitmapFactory.decodeStream(fis);
        } catch (IOException e) {
            Log.e(TAG,"read bitmap error: " + e.getMessage());
        }
        return null;
    }

    public static void saveImageToCache(Context context,  Bitmap bmp, Boolean isHorizontal) {
        String imagePath = imageCachePath(context, isHorizontal);
        if(imagePath.isEmpty()) return;
        try {
            File file = new File(imagePath);
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG,"save bitmap error: " + e.getMessage());
        }
    }

    private static String imageCachePath(Context context, Boolean isHorizontal) {
        File externalFilesDir = context.getExternalFilesDir("");
        if(externalFilesDir != null) {
            return new File(externalFilesDir,
                    "imageCache" + (isHorizontal ? ".h" : "")).getAbsolutePath();
        }
        return "";
    }
}
