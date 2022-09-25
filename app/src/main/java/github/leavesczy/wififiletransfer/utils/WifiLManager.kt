package github.leavesczy.wififiletransfer.utils

import android.content.Context
import android.net.wifi.WifiManager

/**
 * @Author: leavesCZY
 * @Desc:
 */
object WifiLManager {

    /**
     * 获取当前连接的Wifi的SSID
     * @param context 上下文
     * @return SSID
     */
    fun getConnectedSSID(context: Context): String {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo?.ssid?.replace("\"".toRegex(), "") ?: ""
    }

    /**
     * 获取连接的Wifi热点的IP地址
     * @param context 上下文
     * @return IP地址
     */
    fun getHotspotIpAddress(context: Context): String {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo != null) {
            val dhcpInfo = wifiManager.dhcpInfo
            if (dhcpInfo != null) {
                val address = dhcpInfo.gateway
                return ((address and 0xFF).toString() + "." + (address shr 8 and 0xFF)
                        + "." + (address shr 16 and 0xFF)
                        + "." + (address shr 24 and 0xFF))
            }
        }
        return ""
    }

}