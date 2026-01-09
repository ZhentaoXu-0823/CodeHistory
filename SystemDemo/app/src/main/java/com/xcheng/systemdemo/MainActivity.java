package com.xcheng.systemdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;

        ((Button) findViewById(R.id.btn_adb_on)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Settings.Global.putInt(mContext.getContentResolver(), "development_settings_enabled", 1);
                Settings.Global.putInt(mContext.getContentResolver(), "adb_enabled", 1);
            }
        });
        ((Button) findViewById(R.id.btn_adb_off)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Settings.Global.putInt(mContext.getContentResolver(), "adb_enabled", 0);
                Settings.Global.putInt(mContext.getContentResolver(), "development_settings_enabled", 0);
            }
        });
    }
}