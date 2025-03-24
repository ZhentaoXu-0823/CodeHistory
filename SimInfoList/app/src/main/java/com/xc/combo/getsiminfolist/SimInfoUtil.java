package com.xc.combo.getsiminfolist;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class SimInfoUtil {
    private static final String TAG = SimInfoUtil.class.getSimpleName();

    public static class SimInfo {
        int subId;
        int indexId;
        String phoneNumber;
        boolean isEsim;
        String iccid;

        public SimInfo(int subId, int indexId, String phoneNumber, boolean isEsim, String iccid) {
            this.subId = subId;
            this.indexId = indexId;
            this.phoneNumber = phoneNumber;
            this.isEsim = isEsim;
            this.iccid = iccid;
        }

        @Override
        public String toString() {
            return "SubId: " + subId +
                    ", IndexId: " + indexId +
                    ", PhoneNumber: " + phoneNumber +
                    ", isEsim: " + isEsim +
                    ", ICCID: " + iccid;
        }
    }

    public static List<SimInfo> getSimInfoList(Context context) {
        List<SimInfo> simInfoList = new ArrayList<>();
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (subscriptionManager != null) {
            List<SubscriptionInfo> subscriptionInfoList = getSubscriptionInfoList(subscriptionManager);
            if (subscriptionInfoList != null) {
                for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                    int subId = subscriptionInfo.getSubscriptionId();
                    int indexId = subscriptionInfo.getSimSlotIndex();
                    String phoneNumber = null;
                    String iccid = null;
                    if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            TelephonyManager subTelephonyManager = telephonyManager.createForSubscriptionId(subId);
                            phoneNumber = subTelephonyManager.getLine1Number();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                iccid = subscriptionInfo.getIccId();
                            }
                        } else {
                            phoneNumber = telephonyManager.getLine1Number();
                        }
                    }
                    boolean isEsim = subscriptionInfo.isEmbedded();

                    SimInfo simInfo = new SimInfo(subId, indexId, phoneNumber, isEsim, iccid);
                    simInfoList.add(simInfo);
                }
            }
        }
        return simInfoList;
    }

    @SuppressWarnings("unchecked")
    private static List<SubscriptionInfo> getSubscriptionInfoList(SubscriptionManager subscriptionManager) {
        try {
            Method method = SubscriptionManager.class.getDeclaredMethod("getAllSubscriptionInfoList");
            method.setAccessible(true);
            return (List<SubscriptionInfo>) method.invoke(subscriptionManager);
        } catch (Exception e) {
            e.printStackTrace();
            return subscriptionManager.getActiveSubscriptionInfoList();
        }
    }

    public static void activateEsimBySubId(Context context, int subId, boolean enabled) {
        if (context.checkSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "缺少 MODIFY_PHONE_STATE 权限，无法激活 eSIM 卡");
            return;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subscriptionManager != null) {
            List<SubscriptionInfo> subscriptionInfoList = getSubscriptionInfoList(subscriptionManager);
            if (subscriptionInfoList != null) {
                for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                    if (subscriptionInfo.getSubscriptionId() == subId) {
                        if (!subscriptionInfo.isEmbedded()) {
                            Log.e(TAG, "输入的 subId 对应的不是 eSIM 卡，激活失败");
                            return;
                        }
                        try {
                            Method setSubscriptionEnabledMethod = SubscriptionManager.class.getDeclaredMethod("setSubscriptionEnabled", int.class, boolean.class);
                            setSubscriptionEnabledMethod.setAccessible(true);
                            boolean result = (boolean) setSubscriptionEnabledMethod.invoke(subscriptionManager, subId, enabled);
                            if (result) {
                                Log.i(TAG, "eSIM 卡激活成功，subId: " + subId);
                            } else {
                                Log.e(TAG, "eSIM 卡激活失败，subId: " + subId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "调用激活方法出错，subId: " + subId, e);
                        }
                        return;
                    }
                }
            }
            Log.e(TAG, "未找到对应的 subId: " + subId);
        }
    }
}