package github.leavesc.wififiletransfer;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.text.MessageFormat;
import java.util.Locale;

import github.leavesc.wififiletransfer.common.Constants;
import github.leavesc.wififiletransfer.model.FileTransfer;
import github.leavesc.wififiletransfer.service.FileReceiverService;

/**
 * @Author: leavesC
 * @Date: 2018/4/3 14:53
 * @Desc:
 * @Github：https://github.com/leavesC
 */
public class FileReceiverActivity extends BaseActivity {

    private FileReceiverService fileReceiverService;

    private ProgressDialog progressDialog;

    private static final String TAG = "ReceiverActivity";

    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FileReceiverService.MyBinder binder = (FileReceiverService.MyBinder) service;
            fileReceiverService = binder.getService();
            fileReceiverService.setProgressChangListener(progressChangListener);
            if (!fileReceiverService.isRunning()) {
                FileReceiverService.startActionTransfer(FileReceiverActivity.this);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            fileReceiverService = null;
            bindService(FileReceiverService.class, serviceConnection);
        }
    };

    private final FileReceiverService.OnReceiveProgressChangListener progressChangListener = new FileReceiverService.OnReceiveProgressChangListener() {

        private FileTransfer originFileTransfer;

        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final long totalTime, final int progress, final double instantSpeed, final long instantRemainingTime, final double averageSpeed, final long averageRemainingTime) {
            this.originFileTransfer = fileTransfer;
            runOnUiThread(() -> {
                if (isCreated()) {
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
            });
        }

        @Override
        public void onStartComputeMD5() {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("传输结束，正在计算本地文件的MD5码以校验文件完整性");
                    progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5());
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }
            });
        }

        @Override
        public void onTransferSucceed(final FileTransfer fileTransfer) {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("传输成功");
                    progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                            + "\n" + "本地文件的MD5码是：" + fileTransfer.getMd5()
                            + "\n" + "文件位置：" + fileTransfer.getFilePath());
                    progressDialog.setCancelable(true);
                    progressDialog.show();
                    Glide.with(FileReceiverActivity.this).load(fileTransfer.getFilePath()).into(iv_image);
                }
            });
        }

        @Override
        public void onTransferFailed(final FileTransfer fileTransfer, final Exception e) {
            runOnUiThread(() -> {
                if (isCreated()) {
                    progressDialog.setTitle("传输失败");
                    progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                            + "\n" + "本地文件的MD5码是：" + fileTransfer.getMd5()
                            + "\n" + "文件位置：" + fileTransfer.getFilePath()
                            + "\n" + "异常信息：" + e.getMessage());
                    progressDialog.setCancelable(true);
                    progressDialog.show();
                }
            });
        }
    };

    private ImageView iv_image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_receiver);
        initView();
        bindService(FileReceiverService.class, serviceConnection);
    }

    private void initView() {
        setTitle("接收文件");
        iv_image = findViewById(R.id.iv_image);
        TextView tv_hint = findViewById(R.id.tv_hint);
        tv_hint.setText(MessageFormat.format("接收文件前，需要先主动开启Wifi热点让文件发送端连接\n热点名：{0}\n密码：{1}", Constants.AP_SSID, Constants.AP_PASSWORD));
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("正在接收文件");
        progressDialog.setMax(100);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fileReceiverService != null) {
            fileReceiverService.setProgressChangListener(null);
            unbindService(serviceConnection);
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
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