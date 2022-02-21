package com.tencent.assistd.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.tencent.assistd.R;
import com.tencent.assistd.utils.ImageHelper;
import com.tencent.assistd.utils.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pub.devrel.easypermissions.EasyPermissions;

import static com.tencent.assistd.utils.WindowHelper.makeStatusBarTransparent;
import static com.tencent.assistd.utils.WindowHelper.setAndroidNativeLightStatusBar;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Settings settings;
    private ConstraintLayout mainLayout;
    private boolean isHorizontal = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = new Settings(this);
        mainLayout = findViewById(R.id.mainLayout);
        final EditText serverEditText = findViewById(R.id.ServerEditText);
        serverEditText.setText(settings.getSettings().
                getString(Settings.SERVER_ADDRESS, "127.0.0.1:8089"));
        findViewById(R.id.ConnectSignalServerBtn).setOnClickListener(view -> {
            String addr = serverEditText.getText().toString();
            settings.putString(Settings.SERVER_ADDRESS, addr);
            Intent intent = new Intent(MainActivity.this, MobileActivity.class);
            intent.putExtra(Settings.SERVER_ADDRESS, addr);
            startActivity(intent);
        });
        isHorizontal = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        Log.i(TAG, "curent isHorizontal: " + isHorizontal);
        makeStatusBarTransparent(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        setAndroidNativeLightStatusBar(this, true);
        initBackground();
    }

    private void initBackground() {
        Bitmap bmDefault = ImageHelper.getImageFromCache(this, isHorizontal);
        if(bmDefault !=null) {
            boolean isDarkMode = ImageHelper.isDarkBitamp(bmDefault);
            runOnUiThread(() -> {
                setAndroidNativeLightStatusBar(MainActivity.this, !isDarkMode);
                mainLayout.setBackground(new BitmapDrawable(getResources(),bmDefault));
            });
        }
        new Thread(() -> {
            String backgroundUrl = getBackgroundUrl();
            if(!backgroundUrl.isEmpty()) {
                Bitmap backGroundImgBM = getBackgroundBitmap(backgroundUrl);
                if(backGroundImgBM != null) {
                    boolean isDarkMode = ImageHelper.isDarkBitamp(backGroundImgBM);
                    ImageHelper.saveImageToCache(MainActivity.this, backGroundImgBM, isHorizontal);
                    runOnUiThread(() -> {
                        setAndroidNativeLightStatusBar(MainActivity.this, !isDarkMode);
                        mainLayout.setBackground(new BitmapDrawable(getResources(),backGroundImgBM));
                    });
                }
            }
        }).start();
    }

    private String getBackgroundUrl() {
        ResponseBody body = okHttpResponse("https://api.xygeng.cn/Bing/url/");
        if(body != null) {
            try {
                String bodyString = body.string();
                Log.d(TAG, String.format("background json body: %s", bodyString));
                JSONObject result = new JSONObject(bodyString);
                String data = result.getString("data");
                if(!isHorizontal) {
                    data = data.replaceAll("_1920x1080", "_1080x1920");
                }
                return data;
            } catch (JSONException | IOException e) {
                Log.e(TAG, String.format("background json err: %s", e.getMessage()));
            }
        }
        return "";
    }

    private Bitmap getBackgroundBitmap(String url) {
        ResponseBody body = okHttpResponse(url);
        if(body != null) {
            InputStream bodyStream = body.byteStream();
            Bitmap backgroundBitmap = BitmapFactory.decodeStream(bodyStream);
            Log.d(TAG, "background image: " + (backgroundBitmap != null));
            return backgroundBitmap;
        }
        return null;
    }

    private ResponseBody okHttpResponse(String url) {
        try {
            OkHttpClient client = new OkHttpClient();//创建OkHttpClient对象
            Request request = new Request.Builder()
                    .url(url)//请求接口。如果需要传参拼接到接口后面。
                    .build();//创建Request 对象
            Response response = null;
            response = client.newCall(request).execute();//得到Response 对象
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                if(body != null) {
                    return body;
                }
                Log.e(TAG, "ok http body is empty");
            }
            Log.e(TAG, "ok http response fail !");
        } catch (Exception e) {
            Log.e(TAG, "ok http err: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    protected void onDestroy() {
        super.onDestroy();
    }
}