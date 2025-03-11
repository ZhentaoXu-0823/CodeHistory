package com.xcheng.ethinfo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import com.xcheng.ethinfo.util.EthernetConfigUtils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTextView(R.id.tv_eth_info, EthernetConfigUtils.getEthernetConfig(this).toString());
    }

    private void updateTextView(int id, String content) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(id)).setText(content);
            }
        });
    }
}