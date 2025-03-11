package com.xcheng.ethinfo.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

public class EthernetConfigUtils {
    private static final String TAG = "EthernetConfigUtils";

    public static class EthernetConfig {
        public String ipAddress;
        public String gateway;
        public String subnetMask;
        public String dns1;
        public String dns2;
        public boolean isStaticIp;

        @Override
        public String toString() {
            return "EthernetConfig{" +
                    "ipAddress='" + ipAddress + '\'' +
                    ", gateway='" + gateway + '\'' +
                    ", subnetMask='" + subnetMask + '\'' +
                    ", dns1='" + dns1 + '\'' +
                    ", dns2='" + dns2 + '\'' +
                    ", isStaticIp=" + isStaticIp +
                    '}';
        }
    }

    public static EthernetConfig getEthernetConfig(Context context) {
        EthernetConfig config = new EthernetConfig();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                NetworkRequest.Builder builder = new NetworkRequest.Builder();
                builder.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET);
                NetworkRequest request = builder.build();
                Network[] networks = connectivityManager.getAllNetworks();
                for (Network network : networks) {
                    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                        if (linkProperties != null) {
                            // 检查是否为静态 IP 配置
                            config.isStaticIp = isStaticIpConfig(linkProperties);
                            if (config.isStaticIp) {
                                // 获取 IP 地址
                                config.ipAddress = getIpAddress(linkProperties);
                                // 获取网关
                                config.gateway = getGateway(linkProperties);
                                // 获取子网掩码
                                config.subnetMask = getSubnetMask(linkProperties);
                                // 获取 DNS 服务器
                                List<InetAddress> dnsServers = linkProperties.getDnsServers();
                                if (dnsServers.size() > 0) {
                                    config.dns1 = dnsServers.get(0).getHostAddress();
                                }
                                if (dnsServers.size() > 1) {
                                    config.dns2 = dnsServers.get(1).getHostAddress();
                                }
                            }
                        }
                    }
                }
            } else {
                // 对于 Android 5.0 以下版本，处理方式可能不同，这里省略具体实现
                Log.w(TAG, "Android version below Lollipop is not fully supported.");
            }
        }
        return config;
    }

    private static boolean isStaticIpConfig(LinkProperties linkProperties) {
        // 这里简单判断，如果有明确的 IP 地址和网关配置，认为是静态 IP
        return getIpAddress(linkProperties) != null && getGateway(linkProperties) != null;
    }

    private static String getIpAddress(LinkProperties linkProperties) {
        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            InetAddress address = linkAddress.getAddress();
            if (address instanceof Inet4Address) {
                return address.getHostAddress();
            }
        }
        return null;
    }

    private static String getGateway(LinkProperties linkProperties) {
        if (linkProperties.getRoutes() != null) {
            for (android.net.RouteInfo route : linkProperties.getRoutes()) {
                if (route.getGateway() != null && route.getGateway() instanceof Inet4Address) {
                    return route.getGateway().getHostAddress();
                }
            }
        }
        return null;
    }

    private static String getSubnetMask(LinkProperties linkProperties) {
        for (android.net.LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            if (linkAddress.getAddress() instanceof Inet4Address) {
                int prefixLength = linkAddress.getPrefixLength();
                return getSubnetMaskFromPrefixLength(prefixLength);
            }
        }
        return null;
    }

    private static String getSubnetMaskFromPrefixLength(int prefixLength) {
        int mask = 0xFFFFFFFF << (32 - prefixLength);
        int octet1 = (mask & 0xFF000000) >>> 24;
        int octet2 = (mask & 0x00FF0000) >>> 16;
        int octet3 = (mask & 0x0000FF00) >>> 8;
        int octet4 = mask & 0x000000FF;
        return octet1 + "." + octet2 + "." + octet3 + "." + octet4;
    }
}
