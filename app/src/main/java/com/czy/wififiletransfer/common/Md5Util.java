package com.czy.wififiletransfer.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * 作者：chenZY
 * 时间：2018/4/3 15:20
 * 描述：https://www.jianshu.com/u/9df45b87cfdf
 * https://github.com/leavesC
 */
public class Md5Util {

    public static String getMd5(File file) {
        InputStream inputStream = null;
        byte[] buffer = new byte[2048];
        int numRead;
        MessageDigest md5;
        try {
            inputStream = new FileInputStream(file);
            md5 = MessageDigest.getInstance("MD5");
            while ((numRead = inputStream.read(buffer)) > 0) {
                md5.update(buffer, 0, numRead);
            }
            inputStream.close();
            inputStream = null;
            return md5ToString(md5.digest());
        } catch (Exception e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String md5ToString(byte[] md5Bytes) {
        StringBuilder hexValue = new StringBuilder();
        for (byte b : md5Bytes) {
            int val = ((int) b) & 0xff;
            if (val < 16) {
                hexValue.append("0");
            }
            hexValue.append(Integer.toHexString(val));
        }
        return hexValue.toString();
    }

}
