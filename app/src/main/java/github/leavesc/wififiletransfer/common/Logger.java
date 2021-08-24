package github.leavesc.wififiletransfer.common;

import android.text.TextUtils;
import android.util.Log;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 15:19
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class Logger {

    public static void e(String tag, String log) {
        if (!TextUtils.isEmpty(tag) && !TextUtils.isEmpty(log)) {
            Log.e(tag, log);
        }
    }

}