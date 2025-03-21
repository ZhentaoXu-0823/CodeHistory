package com.xcheng.certwifidemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.custom.mdm.CertInstallReceiver;
import com.custom.mdm.CustomAPI;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CustomAPI.init(this);
        ((Button) findViewById(R.id.btn_cert_wifi)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CustomAPI.setWifiEnabled(false);
                CustomAPI.installWlanCaCert("123", "/storage/emulated/0/Download/ca.cer", new CertInstallReceiver.CaCertInstallListener() {
                    @Override
                    public void onCaCertInstallSuccess(String s) {
                        Toast.makeText(MainActivity.this, "s: " + s, Toast.LENGTH_SHORT).show();
                        CustomAPI.installWlanPersonalCert("whatever", "456", "/storage/emulated/0/Download/per.pfx", new CertInstallReceiver.PersonCertInstallListener() {
                            @Override
                            public void onPersonCertInstallSuccess(String s) {
                                CustomAPI.setWifiEnabled(true);
                                Toast.makeText(MainActivity.this, "s: " + s, Toast.LENGTH_SHORT).show();
                                CustomAPI.connectWifiForTLS("XC-802.1X", "123", "xc.com", "456", "user");
                            }

                            @Override
                            public void onPersonCertInstallFail(String s) {
                                Toast.makeText(MainActivity.this, "s: " + s, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onCaCertInstallFail(String s) {
                        Toast.makeText(MainActivity.this, "s: " + s, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CustomAPI.release();
    }
}