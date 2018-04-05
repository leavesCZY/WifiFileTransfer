package com.czy.wififiletransfer.common;

import android.text.TextUtils;
import android.util.Log;

/**
 * 作者：chenZY
 * 时间：2018/4/3 15:19
 * 描述：https://www.jianshu.com/u/9df45b87cfdf
 * https://github.com/leavesC
 */
public class Logger {

    public static void e(String tag, String log) {
        if (!TextUtils.isEmpty(tag) && !TextUtils.isEmpty(log)) {
            Log.e(tag, log);
        }
    }

}