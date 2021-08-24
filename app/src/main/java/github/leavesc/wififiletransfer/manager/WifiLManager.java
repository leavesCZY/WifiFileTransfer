package github.leavesc.wififiletransfer.manager;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import java.util.List;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 15:06
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class WifiLManager {

    /**
     * 开启Wifi
     *
     * @param context 上下文
     * @return 是否成功
     */
    public static boolean openWifi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && (wifiManager.isWifiEnabled() || wifiManager.setWifiEnabled(true));
    }

    /**
     * Wifi是否已开启
     *
     * @param context 上下文
     * @return 开关
     */
    public static boolean isWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    /**
     * 开启Wifi扫描
     *
     * @param context 上下文
     */
    public static void startWifiScan(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return;
        }
        ApManager.closeAp(context);
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        wifiManager.startScan();
    }

    /**
     * 开启Wifi扫描
     *
     * @param context 上下文
     */
    public static List<ScanResult> getScanResults(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return null;
        }
        return wifiManager.getScanResults();
    }

    /**
     * 关闭Wifi
     *
     * @param context 上下文
     */
    public static void closeWifi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(false);
        }
    }

    /**
     * 连接指定Wifi
     *
     * @param context  上下文
     * @param ssid     SSID
     * @param password 密码
     * @return 是否连接成功
     */
    public static boolean connectWifi(Context context, String ssid, String password) {
        String connectedSsid = getConnectedSSID(context);
        if (!TextUtils.isEmpty(connectedSsid) && connectedSsid.equals(ssid)) {
            return true;
        }
        ApManager.closeAp(context);
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return false;
        }
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiConfiguration wifiConfiguration = isWifiExist(context, ssid);
        if (wifiConfiguration == null) {
            wifiConfiguration = createWifiConfiguration(ssid, password);
        }
        int networkId = wifiManager.addNetwork(wifiConfiguration);
        return wifiManager.enableNetwork(networkId, true);
    }

    /**
     * 断开Wifi连接
     *
     * @param context 上下文
     */
    public static void disconnectWifi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            wifiManager.disconnect();
        }
    }

    /**
     * 获取当前连接的Wifi的SSID
     *
     * @param context 上下文
     * @return SSID
     */
    public static String getConnectedSSID(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager == null ? null : wifiManager.getConnectionInfo();
        return wifiInfo != null ? wifiInfo.getSSID().replaceAll("\"", "") : "";
    }

    /**
     * 获取连接的Wifi热点的IP地址
     *
     * @param context 上下文
     * @return IP地址
     */
    public static String getHotspotIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiinfo = wifiManager == null ? null : wifiManager.getConnectionInfo();
        if (wifiinfo != null) {
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            if (dhcpInfo != null) {
                int address = dhcpInfo.gateway;
                return ((address & 0xFF)
                        + "." + ((address >> 8) & 0xFF)
                        + "." + ((address >> 16) & 0xFF)
                        + "." + ((address >> 24) & 0xFF));
            }
        }
        return "";
    }

    /**
     * 获取连接Wifi后设备本身的IP地址
     *
     * @param context 上下文
     * @return IP地址
     */
    public static String getLocalIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiinfo = wifiManager == null ? null : wifiManager.getConnectionInfo();
        if (wifiinfo != null) {
            int ipAddress = wifiinfo.getIpAddress();
            return ((ipAddress & 0xFF)
                    + "." + ((ipAddress >> 8) & 0xFF)
                    + "." + ((ipAddress >> 16) & 0xFF)
                    + "." + ((ipAddress >> 24) & 0xFF));
        }
        return "";
    }

    /**
     * 判断本地是否有保存指定Wifi的配置信息（之前是否曾成功连接过该Wifi）
     *
     * @param context 上下文
     * @param ssid    SSID
     * @return Wifi的配置信息
     */
    private static WifiConfiguration isWifiExist(Context context, String ssid) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> wifiConfigurationList = wifiManager == null ? null : wifiManager.getConfiguredNetworks();
        if (wifiConfigurationList != null && wifiConfigurationList.size() > 0) {
            for (WifiConfiguration wifiConfiguration : wifiConfigurationList) {
                if (wifiConfiguration.SSID.equals("\"" + ssid + "\"")) {
                    return wifiConfiguration;
                }
            }
        }
        return null;
    }

    /**
     * 清除指定Wifi的配置信息
     *
     * @param ssid SSID
     */
    public static void cleanWifiInfo(Context context, String ssid) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wifiConfiguration = isWifiExist(context, ssid);
        if (wifiManager != null && wifiConfiguration != null) {
            wifiManager.removeNetwork(wifiConfiguration.networkId);
        }
    }

    /**
     * 创建Wifi网络配置
     *
     * @param ssid     SSID
     * @param password 密码
     * @return Wifi网络配置
     */
    private static WifiConfiguration createWifiConfiguration(String ssid, String password) {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = "\"" + ssid + "\"";
        wifiConfiguration.preSharedKey = "\"" + password + "\"";
        wifiConfiguration.hiddenSSID = true;
        wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        return wifiConfiguration;
    }

}
