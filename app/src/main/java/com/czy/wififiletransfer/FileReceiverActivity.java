package com.czy.wififiletransfer;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;

import com.czy.wififiletransfer.common.Constants;
import com.czy.wififiletransfer.common.Logger;
import com.czy.wififiletransfer.manager.ApManager;
import com.czy.wififiletransfer.model.FileTransfer;
import com.czy.wififiletransfer.service.FileReceiverService;

import java.io.File;
import java.util.Locale;

/**
 * 作者：chenZY
 * 时间：2018/4/3 14:53
 * 描述：https://www.jianshu.com/u/9df45b87cfdf
 * https://github.com/leavesC
 */
public class FileReceiverActivity extends BaseActivity {

    private FileReceiverService mFileReceiverService;

    private ProgressDialog progressDialog;

    private static final String TAG = "ReceiverActivity";

    private BroadcastReceiver wifiBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            //便携式热点的状态为：10---正在关闭；11---已关闭；12---正在开启；13---已开启
            int state = intent.getIntExtra("wifi_state", 0);
            Logger.e(TAG, "接受到Wifi热点变化的广播： " + state);
            if (state == 11) {
                showToast("Wifi热点已关闭");
            } else if (state == 13) {
                showToast("Wifi热点已开启");
            }
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FileReceiverService.MyBinder binder = (FileReceiverService.MyBinder) service;
            mFileReceiverService = binder.getService();
            mFileReceiverService.setProgressChangListener(progressChangListener);
            if (!mFileReceiverService.isRunning()) {
                FileReceiverService.startActionTransfer(FileReceiverActivity.this);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mFileReceiverService = null;
            bindService(FileReceiverService.class, serviceConnection);
        }
    };

    private FileReceiverService.OnReceiveProgressChangListener progressChangListener = new FileReceiverService.OnReceiveProgressChangListener() {

        private FileTransfer originFileTransfer;

        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final long totalTime, final int progress, final double instantSpeed, final long instantRemainingTime, final double averageSpeed, final long averageRemainingTime) {
            this.originFileTransfer = fileTransfer;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("正在接收的文件： " + originFileTransfer.getFileName());
                        if (progress != 100) {
                            progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                                    + "\n\n" + "总的传输时间：" + totalTime + " 秒"
                                    + "\n\n" + "瞬时-传输速率：" + (int) instantSpeed + " Kb/s"
                                    + "\n" + "瞬时-预估的剩余完成时间：" + instantRemainingTime + " 秒"
                                    + "\n\n" + "平均-传输速率：" + (int) averageSpeed + " Kb/s"
                                    + "\n" + "平均-预估的剩余完成时间：" + averageRemainingTime + " 秒"
                            );
                        }
                        progressDialog.setProgress(progress);
                        progressDialog.setCancelable(true);
                        progressDialog.show();
                    }
                }
            });
        }

        @Override
        public void onStartComputeMD5() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("传输结束，正在计算本地文件的MD5码以校验文件完整性");
                        progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5());
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                    }
                }
            });
        }

        @Override
        public void onTransferSucceed(final FileTransfer fileTransfer) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("传输成功");
                        progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                                + "\n" + "本地文件的MD5码是：" + fileTransfer.getMd5()
                                + "\n" + "文件位置：" + fileTransfer.getFilePath());
                        progressDialog.setCancelable(true);
                        progressDialog.show();
                        openFile(fileTransfer.getFilePath());
                    }
                }
            });
        }

        @Override
        public void onTransferFailed(final FileTransfer fileTransfer, final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishingOrDestroyed()) {
                        progressDialog.setTitle("传输失败");
                        progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                                + "\n" + "本地文件的MD5码是：" + fileTransfer.getMd5()
                                + "\n" + "文件位置：" + fileTransfer.getFilePath()
                                + "\n" + "异常信息：" + e.getMessage());
                        progressDialog.setCancelable(true);
                        progressDialog.show();
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_receiver);
        initView();
        IntentFilter intentFilter = new IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED");
        registerReceiver(wifiBroadcastReceiver, intentFilter);
        bindService(FileReceiverService.class, serviceConnection);
    }

    private void initView() {
        setTitle("接收文件");
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("正在接收文件");
        progressDialog.setMax(100);
    }

    public void openAp(View view) {
        if (ApManager.isApOn(this)) {
            String[] params = ApManager.getApSSIDAndPwd(this);
            if (params != null && params.length == 2 && Constants.AP_SSID.equals(params[0]) && Constants.AP_PASSWORD.equals(params[1])) {
                showToast("Wifi热点已开启");
                return;
            }
        }
        ApManager.openAp(this, Constants.AP_SSID, Constants.AP_PASSWORD);
        bindService(FileReceiverService.class, serviceConnection);
        showToast("正在开启Wifi热点");
    }

    public void closeAp(View view) {
        ApManager.closeAp(this);
        unbindService(serviceConnection);
        mFileReceiverService = null;
        stopService(new Intent(this, FileReceiverService.class));
        showToast("Wifi热点已关闭");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mFileReceiverService != null) {
            mFileReceiverService.setProgressChangListener(null);
            unbindService(serviceConnection);
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        unregisterReceiver(wifiBroadcastReceiver);
    }

    private void openFile(String filePath) {
        String ext = filePath.substring(filePath.lastIndexOf('.')).toLowerCase(Locale.US);
        try {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            String mime = mimeTypeMap.getMimeTypeFromExtension(ext.substring(1));
            mime = TextUtils.isEmpty(mime) ? "" : mime;
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(filePath)), mime);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "文件打开异常：" + e.getMessage());
            showToast("文件打开异常：" + e.getMessage());
        }
    }

}
