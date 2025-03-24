package com.xcheng.rwapp;

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String FILE_NAME = "data/customMdmFiles/system_a.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ((Button) findViewById(R.id.btn_test_api)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, TestApiActivity.class));
            }
        });

        // 检查并请求权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            // 权限已授予，进行文件读写操作
            performFileOperations();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，进行文件读写操作
                performFileOperations();
            } else {
                Toast.makeText(this, "权限被拒绝，无法进行文件操作", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performFileOperations() {
        // 获取外部存储的公共目录
        File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File file = new File(FILE_NAME);

        // 写入文件
        writeToFile(file, "Hello, World!");

        // 读取文件
        String content = readFromFile(file);
        Log.d("FileContent", content);
    }

    private void writeToFile(File file, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final TextView tv_write_result = findViewById(R.id.tv_write_result);
                    tv_write_result.setText("文件写入成功");
                }
            });
            Toast.makeText(this, "文件写入成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final TextView tv_write_result = findViewById(R.id.tv_write_result);
                    tv_write_result.setText("文件写入失败" + e.getMessage());
                }
            });
            Toast.makeText(this, "文件写入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String readFromFile(File file) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final TextView tv_read_result = findViewById(R.id.tv_read_result);
                    tv_read_result.setText("文件读取成功");
                }
            });
            Toast.makeText(this, "文件读取成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final TextView tv_read_result = findViewById(R.id.tv_read_result);
                    tv_read_result.setText("文件读取失败: " + e.getMessage());
                }
            });
            Toast.makeText(this, "文件读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return content.toString();
    }
}