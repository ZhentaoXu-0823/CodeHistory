package com.xcheng.xcloggertestdemo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * UI wrapper for AutoTestReceiver — triggers test suites and shows live results.
 */
public class AutoTestActivity extends Activity {
    private Button btnSmoke, btnFull, btnClear;
    private TextView tvLog;
    private ScrollView svLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autotest);

        btnSmoke = findViewById(R.id.btn_suite_smoke);
        btnFull = findViewById(R.id.btn_suite_full);
        btnClear = findViewById(R.id.btn_clear);
        tvLog = findViewById(R.id.tv_autotest_log);
        svLog = findViewById(R.id.sv_autotest_log);

        btnSmoke.setOnClickListener(v -> trigger("suite_smoke"));
        btnFull.setOnClickListener(v -> trigger("suite_full"));
        btnClear.setOnClickListener(v -> tvLog.setText(""));
    }

    private void trigger(String op) {
        append("→ Triggering " + op);
        // Send broadcast to ourselves (AutoTestReceiver will pick it up)
        android.content.Intent i = new android.content.Intent(AutoTestReceiver.ACTION_TEST);
        i.putExtra("op", op);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void append(String line) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvLog.append("[" + ts + "] " + line + "\n");
        svLog.post(() -> svLog.fullScroll(View.FOCUS_DOWN));
    }
}
