package com.xc.combo.getsiminfolist;
import android.app.Activity;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionsCheckUtil {
    private static final int PERMISSION_REQUEST_CODE = 1;

    public static void requestPermissions(Activity activity, String[] permissions, PermissionCallback callback) {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(activity,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            callback.onPermissionsGranted();
        }
    }

    public static void handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, PermissionCallback callback) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                callback.onPermissionsGranted();
            } else {
                callback.onPermissionsDenied();
            }
        }
    }

    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied();
    }
}
