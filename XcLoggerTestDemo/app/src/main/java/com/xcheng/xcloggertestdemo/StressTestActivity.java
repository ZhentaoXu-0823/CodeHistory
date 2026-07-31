package com.xcheng.xcloggertestdemo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Controls the persistent log stress service and displays its actual runtime state. */
public class StressTestActivity extends Activity {
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusPoller = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            uiHandler.postDelayed(this, 1000);
        }
    };
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            StressFleetController.recordStatus(intent);
            refreshStatus();
        }
    };

    private TextView tvRateValue;
    private TextView tvStatus;
    private TextView tvStatistics;
    private TextView tvFleetStatus;
    private SeekBar seekRate;
    private EditText etRate;
    private Switch switchStress;
    private boolean suppressSwitchCallback;
    private boolean receiverRegistered;
    private int selectedRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_stress_control);

        tvRateValue = findViewById(R.id.tv_rate_value);
        tvStatus = findViewById(R.id.tv_stress_status);
        tvStatistics = findViewById(R.id.tv_stress_statistics);
        tvFleetStatus = findViewById(R.id.tv_fleet_status);
        seekRate = findViewById(R.id.seek_rate);
        etRate = findViewById(R.id.et_rate);
        Button btnApplyRate = findViewById(R.id.btn_apply_rate);
        switchStress = findViewById(R.id.switch_stress);

        selectedRate = StressTestService.getSavedRate(this);
        seekRate.setMax(StressTestService.MAX_RATE - StressTestService.MIN_RATE);
        setSelectedRate(selectedRate, false);

        seekRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) setSelectedRate(StressTestService.MIN_RATE + progress, false);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                applyRate(selectedRate);
            }
        });

        btnApplyRate.setOnClickListener(v -> applyManualRate());
        switchStress.setOnCheckedChangeListener((buttonView, checked) -> {
            if (suppressSwitchCallback) return;
            if (checked) {
                tvStatus.setText("状态：正在启动");
                StressFleetController.ControlResult result =
                        StressFleetController.startAll(this, selectedRate);
                showControlResult("Start", result);
            } else {
                tvStatus.setText("状态：正在停止");
                StressFleetController.ControlResult result = StressFleetController.stopAll(this);
                showControlResult("Stop", result);
            }
            uiHandler.postDelayed(this::refreshStatus, 300);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(StressTestService.ACTION_STATUS);
        filter.addAction(StressFleetController.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, StressFleetController.CONTROL_PERMISSION,
                    null, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter, StressFleetController.CONTROL_PERMISSION, null);
        }
        receiverRegistered = true;
        refreshStatus();
        uiHandler.post(statusPoller);
    }

    @Override
    protected void onStop() {
        uiHandler.removeCallbacks(statusPoller);
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void applyManualRate() {
        String raw = etRate.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            Toast.makeText(this, "请输入 10–100 的整数", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int rate = Integer.parseInt(raw);
            if (rate < StressTestService.MIN_RATE || rate > StressTestService.MAX_RATE) {
                Toast.makeText(this, "速率范围必须为 10–100 行/秒", Toast.LENGTH_SHORT).show();
                return;
            }
            setSelectedRate(rate, true);
            applyRate(rate);
            etRate.setText("");
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入合法整数", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyRate(int rate) {
        StressFleetController.setRateAll(this, rate);
        StressTestService.Status status = StressTestService.getStatus(this);
        if (status.running) {
            Toast.makeText(this, "已调整为 " + rate + " 行/秒", Toast.LENGTH_SHORT).show();
        }
        uiHandler.postDelayed(this::refreshStatus, 200);
    }

    private void setSelectedRate(int rate, boolean updateSeekBar) {
        selectedRate = rate;
        tvRateValue.setText("目标速率：" + rate + " 行/秒");
        if (updateSeekBar) seekRate.setProgress(rate - StressTestService.MIN_RATE);
    }

    private void refreshStatus() {
        StressTestService.Status status = StressTestService.getStatus(this);
        suppressSwitchCallback = true;
        switchStress.setChecked(status.running);
        suppressSwitchCallback = false;

        if (status.running) {
            if (selectedRate != status.ratePerSecond) {
                setSelectedRate(status.ratePerSecond, true);
            }
            tvStatus.setText("状态：运行中 · " + status.ratePerSecond + " 行/秒");
            tvStatus.setTextColor(0xFF188038);
        } else {
            tvStatus.setText("状态：已停止");
            tvStatus.setTextColor(0xFFB3261E);
        }

        long seconds = status.elapsedMs / 1000;
        tvStatistics.setText(String.format(Locale.US,
                "本次已输出 %,d 行 · 运行 %02d:%02d",
                status.emittedLines, seconds / 60, seconds % 60));
        tvFleetStatus.setText(StressFleetController.formatFleetStatus(this, status));
    }

    private void showControlResult(String action, StressFleetController.ControlResult result) {
        String message = String.format(Locale.US,
                "%s: %d instance(s), %d/5 fork APK(s) installed, %d failure(s)",
                action, result.commandedInstances, result.installedAgents, result.failures);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
