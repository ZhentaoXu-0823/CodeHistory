package com.xcheng.rwapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import com.custom.mdm.CustomAPI;

public class TestApiActivity extends AppCompatActivity {
    private final static String TAG = TestApiActivity.class.getSimpleName();

    private static final int PERMISSION_REQUEST_CODE = 1;
    private Context mContext;

    private static final String data = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbcccccccccccccccccccccccccc";
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_api);
        mContext = this;

        ((Button) findViewById(R.id.btn_write)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Log.d(TAG, "xzt write result: " + CustomAPI.writeFileToSystem(data.getBytes(), "system_a.txt"));
                Toast.makeText(mContext, "xzt write result: " + CustomAPI.writeFileToSystem(data.getBytes(), "system_a.txt"), Toast.LENGTH_SHORT).show();
            }
        });
        ((Button) findViewById(R.id.btn_read)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Log.d(TAG, "xzt read result: " + new String(CustomAPI.readFileFromSystem("system_a.txt")));
                Toast.makeText(mContext, "xzt read result: " + new String(CustomAPI.readFileFromSystem("system_a.txt")), Toast.LENGTH_SHORT).show();
            }
        });
        ((Button) findViewById(R.id.btn_del)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Log.d(TAG, "xzt delete result: " + CustomAPI.removeFileFromSystem("system_a.txt"));
                Toast.makeText(mContext, new String("xzt delete result: " + CustomAPI.removeFileFromSystem("system_a.txt")), Toast.LENGTH_SHORT).show();
            }
        });

        // 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // 请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            // 权限已授予，进行文件操作
            readFile();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，进行文件操作
                readFile();
            } else {
                Toast.makeText(this, "权限被拒绝，无法访问文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void readFile() {
        try {
//            File file = new File(Environment.getExternalStorageDirectory(), "Download/a.txt");
            File file = new File("/sdcard/download/a.txt");
            FileReader reader = new FileReader(file);
            // 读取文件内容
            StringBuilder content = new StringBuilder();
            int data;
            while ((data = reader.read()) != -1) {
                content.append((char) data);
            }
            reader.close();
            Toast.makeText(this, "文件内容：" + content.toString(), Toast.LENGTH_LONG).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "文件未找到", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "读取文件时出错", Toast.LENGTH_SHORT).show();
        }
    }

    public byte[] readFileFromPath(String path) {
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("文件不存在：" + path);
            return null;
        }

        FileInputStream fis = null;
        byte[] data = null;

        try {
            fis = new FileInputStream(file);
            data = new byte[(int) file.length()];
            fis.read(data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return data;
    }
    public boolean writeFileToPath(byte[] data, String path) {
        if (data == null || data.length == 0) {
            System.out.println("数据为空，无法写入文件");
            return false;
        }

        File file = new File(path);
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                System.out.println("创建目录失败：" + parentDir.getAbsolutePath());
                return false;
            }
        }

        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(file);
            fos.write(data);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomAPI.init(mContext);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CustomAPI.release();
    }
}
