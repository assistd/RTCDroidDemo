package com.tencent.assistd.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

public class Settings {
    public static final String SERVER_ADDRESS = "ServerAddress";
    static SharedPreferences configurationPrefs;
    static SharedPreferences.Editor editor;

    @SuppressLint("CommitPrefEdits")
    public Settings(Context context){
        if (editor == null) {
            if (configurationPrefs == null) {
                configurationPrefs = context.getSharedPreferences(
                        context.getPackageName(),
                        Context.MODE_PRIVATE);
            }
            editor = configurationPrefs.edit();
        }
    }

    public SharedPreferences getSettings() {
        return configurationPrefs;
    }

    public void putString(String key, String value) {
        editor.putString(key, value);
        editor.commit();
    }
}
