package github.leavesc.wififiletransfer.manager;

import static android.content.Context.WIFI_SERVICE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 15:05
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class ApManager {

    private static final String TAG = "ApManager";

    private static final String KEY_SHARED_NAME = "sharedName";

    private static final String KEY_SSID = "apSSID";

    private static final String KEY_PASSWORD = "apPassword";

    /**
     * 持久化保存设备的Ap热点的名称和密码
     *
     * @param context 上下文
     */
    public static void saveApCache(Context context) {
        String[] params = ApManager.getApSSIDAndPwd(context);
        if (params != null && !TextUtils.isEmpty(params[0])) {
            Log.e(TAG, "本地的Ap热点的名称: " + params[0]);
            Log.e(TAG, "本地的Ap热点的密码: " + params[1]);
            saveApCache(context, params[0], params[1]);
        } else {
            Log.e(TAG, "无Ap热点信息");
            cleanApCache(context);
        }
    }

    /**
     * 还原Ap热点信息
     *
     * @param context 上下文
     */
    public static void resetApCache(Context context) {
        String[] params = getApCache(context);
        if (TextUtils.isEmpty(params[0])) {
            return;
        }
        openAp(context, params[0], params[1]);
        closeAp(context);
    }

    /**
     * 获取本地持久化保存的Ap热点的名称和密码
     *
     * @param context 上下文
     * @return String[0]:Ap热点名称、String[1]:Ap热点的密码（密码可能为空）
     */
    private static String[] getApCache(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(KEY_SHARED_NAME, Context.MODE_PRIVATE);
        String ssid = sharedPreferences.getString(KEY_SSID, "");
        if (TextUtils.isEmpty(ssid)) {
            return new String[2];
        }
        String[] params = new String[2];
        params[0] = ssid;
        params[1] = sharedPreferences.getString(KEY_PASSWORD, "");
        return params;
    }

    private static void saveApCache(Context context, String ssid, String password) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(KEY_SHARED_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_SSID, TextUtils.isEmpty(ssid) ? "" : ssid);
        editor.putString(KEY_PASSWORD, TextUtils.isEmpty(password) ? "" : password);
        editor.apply();
    }

    private static void cleanApCache(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(KEY_SHARED_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear().apply();
    }

    /**
     * 判断Ap热点是否开启
     *
     * @param context 上下文
     * @return 开关
     */
    public static boolean isApOn(Context context) {
        WifiManager wifimanager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifimanager == null) {
            return false;
        }
        try {
            @SuppressLint("PrivateApi")
            Method method = wifimanager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifimanager);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 开启Ap热点
     *
     * @param context  上下文
     * @param ssid     SSID
     * @param password 密码
     * @return 是否成功
     */
    public static boolean openAp(Context context, String ssid, String password) {
        WifiManager wifimanager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifimanager == null) {
            return false;
        }
        if (wifimanager.isWifiEnabled()) {
            wifimanager.setWifiEnabled(false);
        }
        try {
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, null, false);
            method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, createApConfiguration(ssid, password), true);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 关闭Ap热点
     *
     * @param context 上下文
     */
    public static void closeAp(Context context) {
        WifiManager wifimanager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifimanager == null) {
            return;
        }
        try {
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, null, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取设备曾开启过的Ap热点的名称和密码（无关设备现在是否有开启Ap）
     *
     * @param context 上下文
     * @return Ap热点名、Ap热点密码（密码可能为空）
     */
    public static String[] getApSSIDAndPwd(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "wifiManager == null");
            return null;
        }
        try {
            Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
            method.setAccessible(true);
            WifiConfiguration wifiConfiguration = (WifiConfiguration) method.invoke(wifiManager);
            String[] params = new String[2];
            params[0] = wifiConfiguration.SSID;
            params[1] = wifiConfiguration.preSharedKey;
            return params;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return null;
        }
    }

    /**
     * 获取开启Ap热点后设备本身的IP地址
     *
     * @param context 上下文
     * @return IP地址
     */
    public static String getHotspotIpAddress(Context context) {
        WifiManager wifimanager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifimanager == null ? null : wifimanager.getDhcpInfo();
        if (dhcpInfo != null) {
            int address = dhcpInfo.serverAddress;
            return ((address & 0xFF)
                    + "." + ((address >> 8) & 0xFF)
                    + "." + ((address >> 16) & 0xFF)
                    + "." + ((address >> 24) & 0xFF));
        }
        return "";
    }

    /**
     * 配置Ap热点信息
     *
     * @param ssid     Ap热点SSID
     * @param password Ap热点密码
     * @return Ap热点信息
     */
    private static WifiConfiguration createApConfiguration(String ssid, String password) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = ssid;
        config.preSharedKey = password;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        return config;
    }

}
