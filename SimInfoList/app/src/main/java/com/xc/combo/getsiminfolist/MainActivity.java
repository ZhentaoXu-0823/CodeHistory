package com.xc.combo.getsiminfolist;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PermissionsCheckUtil.PermissionCallback {
    private static final String TAG = "SimInfoList-" + MainActivity.class.getSimpleName();

    private static final String[] REQUEST_PERMISSIONS = {
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_SMS",
            "android.permission.MODIFY_PHONE_STATE"
    };

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        PermissionsCheckUtil.requestPermissions(this, REQUEST_PERMISSIONS, this);

        ((Button) findViewById(R.id.btn_get_sim_info)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                displaySimInfo();
            }
        });

        ((Button) findViewById(R.id.btn_activite_esim)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String esimSubIdContent = getEditText(R.id.et_esim_subid);
                int subId = Integer.parseInt(TextUtils.isEmpty(esimSubIdContent) ? "-1" : esimSubIdContent);
                SimInfoUtil.activateEsimBySubId(context, subId, true);
            }
        });

        ((Button) findViewById(R.id.btn_cancel_esim)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String esimSubIdContent = getEditText(R.id.et_esim_subid);
                int subId = Integer.parseInt(TextUtils.isEmpty(esimSubIdContent) ? "-1" : esimSubIdContent);
                SimInfoUtil.activateEsimBySubId(context, subId, false);
            }
        });
    }

    private void displaySimInfo() {
        TextView textView = findViewById(R.id.tv_show_sim_info);
        List<SimInfoUtil.SimInfo> simInfoList = SimInfoUtil.getSimInfoList(this);

        StringBuilder stringBuilder = new StringBuilder();
        for (SimInfoUtil.SimInfo simInfo : simInfoList) {
            stringBuilder.append(simInfo.toString()).append("\n");
        }
        textView.setText(stringBuilder.toString());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionsCheckUtil.handlePermissionResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted() {
        Toast.makeText(context, "Permission granted.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPermissionsDenied() {
        Toast.makeText(context, "Permission denined.", Toast.LENGTH_SHORT).show();
    }

    private String getEditText(int id) {
        EditText et = null;
        String content = null;
        et = findViewById(id);
        if (et != null) {
            content = et.getText().toString();
            if (TextUtils.isEmpty(content)) {
                content = "";
            }
        } else {
            content = "";
        }
        return content;
    }
}