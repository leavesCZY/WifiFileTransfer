package github.leavesc.wififiletransfer.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import github.leavesc.wififiletransfer.BuildConfig;
import github.leavesc.wififiletransfer.common.Constants;
import github.leavesc.wififiletransfer.common.Logger;
import github.leavesc.wififiletransfer.common.Md5Util;
import github.leavesc.wififiletransfer.manager.WifiLManager;
import github.leavesc.wififiletransfer.model.FileTransfer;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 17:32
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class FileSenderService extends IntentService {

    private Socket socket;

    private OutputStream outputStream;

    private ObjectOutputStream objectOutputStream;

    private InputStream inputStream;

    private OnSendProgressChangListener progressChangListener;

    private static final String ACTION_START_SEND = BuildConfig.APPLICATION_ID + ".service.action.startSend";

    private static final String EXTRA_PARAM_FILE_TRANSFER = BuildConfig.APPLICATION_ID + ".service.extra.FileUri";

    private static final String EXTRA_PARAM_IP_ADDRESS = BuildConfig.APPLICATION_ID + ".service.extra.IpAddress";

    private static final String TAG = "FileSenderService";

    public interface OnSendProgressChangListener {

        /**
         * 如果待发送的文件还没计算MD5码，则在开始计算MD5码时回调
         */
        void onStartComputeMD5();

        /**
         * 当传输进度发生变化时回调
         *
         * @param fileTransfer         待发送的文件模型
         * @param totalTime            传输到现在所用的时间
         * @param progress             文件传输进度
         * @param instantSpeed         瞬时-文件传输速率
         * @param instantRemainingTime 瞬时-预估的剩余完成时间
         * @param averageSpeed         平均-文件传输速率
         * @param averageRemainingTime 平均-预估的剩余完成时间
         */
        void onProgressChanged(FileTransfer fileTransfer, long totalTime, int progress, double instantSpeed, long instantRemainingTime, double averageSpeed, long averageRemainingTime);

        /**
         * 当文件传输成功时回调
         *
         * @param fileTransfer FileTransfer
         */
        void onTransferSucceed(FileTransfer fileTransfer);

        /**
         * 当文件传输失败时回调
         *
         * @param fileTransfer FileTransfer
         * @param e            Exception
         */
        void onTransferFailed(FileTransfer fileTransfer, Exception e);

    }

    public FileSenderService() {
        super("FileSenderService");
    }

    public class MyBinder extends Binder {
        public FileSenderService getService() {
            return FileSenderService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new FileSenderService.MyBinder();
    }

    private ScheduledExecutorService callbackService;

    private FileTransfer fileTransfer;

    //总的已传输字节数
    private long total;

    //在上一次更新进度时已传输的文件总字节数
    private long tempTotal = 0;

    //计算瞬时传输速率的间隔时间
    private static final int PERIOD = 400;

    //传输操作开始时间
    private Date startTime;

    private void startCallback() {
        stopCallback();
        startTime = new Date();
        callbackService = Executors.newScheduledThreadPool(1);
        Runnable runnable = () -> {
            if (fileTransfer != null) {
                //过去 PERIOD 秒内文件的瞬时传输速率（Kb/s）
                double instantSpeed = 0;
                //根据瞬时速率计算的-预估的剩余完成时间（秒）
                long instantRemainingTime = 0;
                //到现在所用的总的传输时间
                long totalTime = 0;
                //总的平均文件传输速率（Kb/s）
                double averageSpeed = 0;
                //根据总的平均传输速率计算的预估的剩余完成时间（秒）
                long averageRemainingTime = 0;
                //文件大小
                long fileSize = fileTransfer.getFileSize();
                //当前的传输进度
                int progress = (int) (total * 100 / fileSize);
                //距离上一次计算进度到现在之间新传输的字节数
                long temp = total - tempTotal;
                if (temp > 0) {
                    instantSpeed = (temp / 1024.0 / PERIOD);
                    instantRemainingTime = (long) ((fileSize - total) / 1024.0 / instantSpeed);
                }
                if (startTime != null) {
                    totalTime = (new Date().getTime() - startTime.getTime()) / 1000;
                    averageSpeed = (total / 1024.0 / totalTime);
                    averageRemainingTime = (long) ((fileSize - total) / 1024.0 / averageSpeed);
                }
                tempTotal = total;
                Logger.e(TAG, "---------------------------");
                Logger.e(TAG, "传输进度（%）: " + progress);
                Logger.e(TAG, "所用时间：" + totalTime);
                Logger.e(TAG, "瞬时-传输速率（Kb/s）: " + instantSpeed);
                Logger.e(TAG, "瞬时-预估的剩余完成时间（秒）: " + instantRemainingTime);
                Logger.e(TAG, "平均-传输速率（Kb/s）: " + averageSpeed);
                Logger.e(TAG, "平均-预估的剩余完成时间（秒）: " + averageRemainingTime);
                Logger.e(TAG, "字节变化：" + temp);
                if (progressChangListener != null) {
                    progressChangListener.onProgressChanged(fileTransfer, totalTime, progress, instantSpeed, instantRemainingTime, averageSpeed, averageRemainingTime);
                }
            }
        };
        //每隔 PERIOD 毫秒执行一次任务 runnable（定时任务内部要捕获可能发生的异常，否则如果异常抛出到上层的话，会导致定时任务停止）
        callbackService.scheduleAtFixedRate(runnable, 0, PERIOD, TimeUnit.MILLISECONDS);
    }

    private void stopCallback() {
        if (callbackService != null) {
            if (!callbackService.isShutdown()) {
                callbackService.shutdownNow();
            }
            callbackService = null;
        }
    }

    private String getOutputFilePath(Context context, Uri fileUri) throws Exception {
        String outputFilePath = context.getExternalCacheDir().getAbsolutePath() +
                File.separatorChar + new Random().nextInt(10000) +
                new Random().nextInt(10000) + ".jpg";
        File outputFile = new File(outputFilePath);
        if (!outputFile.exists()) {
            outputFile.getParentFile().mkdirs();
            outputFile.createNewFile();
        }
        Uri outputFileUri = Uri.fromFile(outputFile);
        copyFile(context, fileUri, outputFileUri);
        return outputFilePath;
    }

    private void copyFile(Context context, Uri inputUri, Uri outputUri) throws NullPointerException,
            IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(inputUri);
             OutputStream outputStream = new FileOutputStream(outputUri.getPath())) {
            if (inputStream == null) {
                throw new NullPointerException("InputStream for given input Uri is null");
            }
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && ACTION_START_SEND.equals(intent.getAction())) {
            try {
                clean();

                String ipAddress = intent.getStringExtra(EXTRA_PARAM_IP_ADDRESS);
                Log.e(TAG, "IP地址：" + ipAddress);
                if (TextUtils.isEmpty(ipAddress)) {
                    return;
                }

                Uri imageUri = Uri.parse(intent.getStringExtra(EXTRA_PARAM_FILE_TRANSFER));
                String outputFilePath = getOutputFilePath(this, imageUri);
                File outputFile = new File(outputFilePath);

                fileTransfer = new FileTransfer();
                fileTransfer.setFileName(outputFile.getName());
                fileTransfer.setFileSize(outputFile.length());
                fileTransfer.setFilePath(outputFilePath);

                if (TextUtils.isEmpty(fileTransfer.getMd5())) {
                    Logger.e(TAG, "MD5码为空，开始计算文件的MD5码");
                    if (progressChangListener != null) {
                        progressChangListener.onStartComputeMD5();
                    }
                    fileTransfer.setMd5(Md5Util.getMd5(new File(fileTransfer.getFilePath())));
                    Log.e(TAG, "计算结束，文件的MD5码值是：" + fileTransfer.getMd5());
                } else {
                    Logger.e(TAG, "MD5码不为空，无需再次计算，MD5码为：" + fileTransfer.getMd5());
                }
                int index = 0;
                while (ipAddress.equals("0.0.0.0") && index < 5) {
                    Log.e(TAG, "ip: " + ipAddress);
                    ipAddress = WifiLManager.getHotspotIpAddress(this);
                    index++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (ipAddress.equals("0.0.0.0")) {
                    return;
                }

                socket = new Socket();
                socket.bind(null);
                socket.connect((new InetSocketAddress(ipAddress, Constants.PORT)), 20000);
                outputStream = socket.getOutputStream();
                objectOutputStream = new ObjectOutputStream(outputStream);
                objectOutputStream.writeObject(fileTransfer);
                inputStream = new FileInputStream(new File(fileTransfer.getFilePath()));
                startCallback();
                byte[] buf = new byte[512];
                int len;
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                    total += len;
                }
                Log.e(TAG, "文件发送成功");
                stopCallback();
                if (progressChangListener != null) {
                    //因为上面在计算文件传输进度时因为小数点问题可能不会显示到100%，所以此处手动将之设为100%
                    progressChangListener.onProgressChanged(fileTransfer, 0, 100, 0, 0, 0, 0);
                    progressChangListener.onTransferSucceed(fileTransfer);
                }
            } catch (Exception e) {
                Log.e(TAG, "文件发送异常 Exception: " + e.getMessage());
                if (progressChangListener != null) {
                    progressChangListener.onTransferFailed(fileTransfer, e);
                }
            } finally {
                clean();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clean();
    }

    public void clean() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (outputStream != null) {
            try {
                outputStream.close();
                outputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (objectOutputStream != null) {
            try {
                objectOutputStream.close();
                objectOutputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (inputStream != null) {
            try {
                inputStream.close();
                inputStream = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        stopCallback();
        total = 0;
        tempTotal = 0;
        startTime = null;
        fileTransfer = null;
    }

    public static void startActionTransfer(Context context, String fileUri, String ipAddress) {
        Intent intent = new Intent(context, FileSenderService.class);
        intent.setAction(ACTION_START_SEND);
        intent.putExtra(EXTRA_PARAM_FILE_TRANSFER, fileUri);
        intent.putExtra(EXTRA_PARAM_IP_ADDRESS, ipAddress);
        context.startService(intent);
    }

    public void setProgressChangListener(OnSendProgressChangListener progressChangListener) {
        this.progressChangListener = progressChangListener;
    }

}
