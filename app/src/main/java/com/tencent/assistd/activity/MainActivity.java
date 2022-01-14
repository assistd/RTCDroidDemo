package com.tencent.assistd.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.internal.NavigationMenu;
import com.tencent.assistd.R;
import com.tencent.assistd.utils.Settings;
import com.tencent.assistd.widgets.DragFloatActionButton;

import io.github.yavski.fabspeeddial.FabSpeedDial;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {

    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = new Settings(this);
        final EditText serverEditText = findViewById(R.id.ServerEditText);
        serverEditText.setText(settings.getSettings().getString(Settings.SERVER_ADDRESS, "127.0.0.1:8089"));
        findViewById(R.id.ConnectSignalServerBtn).setOnClickListener(view -> {
            String addr = serverEditText.getText().toString();
            settings.putString(Settings.SERVER_ADDRESS, addr);
            Intent intent = new Intent(MainActivity.this, MobileActivity.class);
            intent.putExtra(Settings.SERVER_ADDRESS, addr);
            startActivity(intent);
        });
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