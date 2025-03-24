package com.xc.combo.getsiminfolist;

import android.content.Context;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.List;

public class SimInfoUtil {

    public static class SimInfo {
        int subId;
        int indexId;
        String phoneNumber;
        boolean isEsim;

        public SimInfo(int subId, int indexId, String phoneNumber, boolean isEsim) {
            this.subId = subId;
            this.indexId = indexId;
            this.phoneNumber = phoneNumber;
            this.isEsim = isEsim;
        }

        @Override
        public String toString() {
            return "SubId: " + subId +
                    ", IndexId: " + indexId +
                    ", PhoneNumber: " + phoneNumber +
                    ", isEsim: " + isEsim;
        }
    }

    public static List<SimInfo> getSimInfoList(Context context) {
        List<SimInfo> simInfoList = new ArrayList<>();
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (subscriptionManager != null) {
            List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
            if (subscriptionInfoList != null) {
                for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                    int subId = subscriptionInfo.getSubscriptionId();
                    int indexId = subscriptionInfo.getSimSlotIndex();
                    String phoneNumber = null;
                    if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            TelephonyManager subTelephonyManager = telephonyManager.createForSubscriptionId(subId);
                            phoneNumber = subTelephonyManager.getLine1Number();
                        } else {
                            phoneNumber = telephonyManager.getLine1Number();
                        }
                    }
                    boolean isEsim = subscriptionInfo.isEmbedded();

                    SimInfo simInfo = new SimInfo(subId, indexId, phoneNumber, isEsim);
                    simInfoList.add(simInfo);
                }
            }
        }
        return simInfoList;
    }
}
