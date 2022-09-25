package github.leavesczy.wififiletransfer.ui

import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import github.leavesczy.wififiletransfer.R
import github.leavesczy.wififiletransfer.service.Constants
import github.leavesczy.wififiletransfer.service.FileReceiverService
import github.leavesczy.wififiletransfer.service.FileReceiverService.OnReceiveProgressChangListener
import github.leavesczy.wififiletransfer.service.FileTransfer
import java.io.File
import java.text.MessageFormat

/**
 * @Author: leavesCZY
 * @Desc:
 */
class FileReceiverActivity : BaseActivity() {

    private var fileReceiverService: FileReceiverService? = null

    private val serviceConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as FileReceiverService.MyBinder
            fileReceiverService = binder.service
            fileReceiverService?.setProgressChangListener(progressChangListener)
            if (fileReceiverService?.isRunning != false) {
                FileReceiverService.startActionTransfer(this@FileReceiverActivity)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            fileReceiverService = null
            bindService(FileReceiverService::class.java, this)
        }
    }

    private val progressChangListener: OnReceiveProgressChangListener =
        object : OnReceiveProgressChangListener {

            private lateinit var originFileTransfer: FileTransfer

            override fun onProgressChanged(
                fileTransfer: FileTransfer,
                totalTime: Long,
                progress: Int,
                instantSpeed: Double,
                instantRemainingTime: Long,
                averageSpeed: Double,
                averageRemainingTime: Long
            ) {
                originFileTransfer = fileTransfer
                runOnUiThread {
                    if (isCreated) {
                        progressDialog.setTitle("正在接收的文件： " + originFileTransfer.fileName)
                        if (progress != 100) {
                            progressDialog.setMessage(
                                "原始文件的MD5码是：${originFileTransfer.md5}\n" +
                                        "总的传输时间：$totalTime 秒\n" +
                                        "瞬时-传输速率：${instantSpeed.toInt()} Kb/s\n" +
                                        "瞬时-预估的剩余完成时间：$instantRemainingTime 秒\n" +
                                        "平均-传输速率：${averageSpeed.toInt()} Kb/s\n" +
                                        "平均-预估的剩余完成时间：$averageRemainingTime 秒"
                            )
                        }
                        progressDialog.progress = progress
                        progressDialog.setCancelable(true)
                        progressDialog.show()
                    }
                }
            }

            override fun onStartComputeMD5() {
                runOnUiThread {
                    if (isCreated) {
                        progressDialog.setTitle("传输结束，正在计算本地文件的MD5码以校验文件完整性")
                        progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.md5)
                        progressDialog.setCancelable(false)
                        progressDialog.show()
                    }
                }
            }

            override fun onTransferSucceed(fileTransfer: FileTransfer) {
                runOnUiThread {
                    if (isCreated) {
                        progressDialog.setTitle("传输成功")
                        progressDialog.setMessage(
                            "原始文件的MD5码是：${originFileTransfer.md5}\n" +
                                    "本地文件的MD5码是：${fileTransfer.md5}\n" +
                                    "文件位置：${fileTransfer.filePath}"
                        )
                        progressDialog.setCancelable(true)
                        progressDialog.show()
                        Glide.with(this@FileReceiverActivity).load(fileTransfer.filePath)
                            .into(ivImage)
                    }
                }
            }

            override fun onTransferFailed(fileTransfer: FileTransfer, e: Exception) {
                runOnUiThread {
                    if (isCreated) {
                        progressDialog.setTitle("传输失败")
                        progressDialog.setMessage(
                            " 原始文件的MD5码是：${originFileTransfer.md5}\n" +
                                    "本地文件的MD5码是：${fileTransfer.md5}\n" +
                                    "文件位置：${fileTransfer.filePath}\n" +
                                    "异常信息：${e.message}"
                        )
                        progressDialog.setCancelable(true)
                        progressDialog.show()
                    }
                }
            }
        }

    private val ivImage by lazy {
        findViewById<ImageView>(R.id.ivImage)
    }

    private val tvHint by lazy {
        findViewById<TextView>(R.id.tvHint)
    }

    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_receiver)
        initView()
        bindService(FileReceiverService::class.java, serviceConnection)
    }

    private fun initView() {
        setTitle("接收文件")
        tvHint.text = MessageFormat.format(
            "接收文件前，需要先主动开启Wifi热点让文件发送端连接\n热点名：{0}\n密码：{1}",
            Constants.AP_SSID,
            Constants.AP_PASSWORD
        )
        progressDialog = ProgressDialog(this).apply {
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            max = 100
            setTitle("正在接收文件")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (fileReceiverService != null) {
            fileReceiverService?.setProgressChangListener(null)
            unbindService(serviceConnection)
        }
        progressDialog.dismiss()
    }

    private fun openFile(filePath: String) {
        val ext = filePath.substring(filePath.lastIndexOf('.')).lowercase()
        try {
            val mimeTypeMap = MimeTypeMap.getSingleton()
            var mime = mimeTypeMap.getMimeTypeFromExtension(ext.substring(1))
            mime = if (TextUtils.isEmpty(mime)) "" else mime
            val intent = Intent()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.action = Intent.ACTION_VIEW
            intent.setDataAndType(Uri.fromFile(File(filePath)), mime)
            startActivity(intent)
        } catch (e: Throwable) {
            log("文件打开异常：" + e.message)
            showToast("文件打开异常：" + e.message)
        }
    }
}