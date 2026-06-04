package com.xcheng.xclogger.control;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;

public class SourceResolver {
    private static final String SOURCE_ADB = "adb";

    public String resolveFromBroadcast(Context context, Intent intent) {
        if (intent != null && intent.getPackage() != null && !intent.getPackage().isEmpty()) {
            return intent.getPackage();
        }
        return SOURCE_ADB;
    }

    public String resolveFromAidl(Context context) {
        try {
            int uid = Binder.getCallingUid();
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                return packages[0];
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }
}
