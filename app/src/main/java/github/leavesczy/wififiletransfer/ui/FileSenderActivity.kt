package github.leavesczy.wififiletransfer.ui

import android.app.ProgressDialog
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import github.leavesczy.wififiletransfer.R
import github.leavesczy.wififiletransfer.service.Constants
import github.leavesczy.wififiletransfer.service.FileSenderService
import github.leavesczy.wififiletransfer.service.FileSenderService.OnSendProgressChangListener
import github.leavesczy.wififiletransfer.service.FileTransfer
import github.leavesczy.wififiletransfer.utils.WifiLManager
import java.io.File
import java.text.MessageFormat

/**
 * @Author: leavesCZY
 * @Desc:
 */
class FileSenderActivity : BaseActivity() {

    private val getContentLaunch = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null) {
            log("文件路径： $imageUri")
            FileSenderService.startActionTransfer(
                this, imageUri.toString(),
                WifiLManager.getHotspotIpAddress(this)
            )
        }
    }

    private var fileSenderService: FileSenderService? = null

    private val progressChangListener: OnSendProgressChangListener =
        object : OnSendProgressChangListener {
            override fun onStartComputeMD5() {
                runOnUiThread {
                    if (isCreated) {
                        progressDialog.setTitle("发送文件")
                        progressDialog.setMessage("正在计算文件的MD5码")
                        progressDialog.max = 100
                        progressDialog.progress = 0
                        progressDialog.setCancelable(false)
                        progressDialog.show()
                    }
                }
            }

            override fun onProgressChanged(
                fileTransfer: FileTransfer,
                totalTime: Long,
                progress: Int,
                instantSpeed: Double,
                instantRemainingTime: Long,
                averageSpeed: Double,
                averageRemainingTime: Long
            ) {
                runOnUiThread {
                    if (isCreated) {
                        progressDialog.setTitle("正在发送文件： " + File(fileTransfer.filePath).name)
                        if (progress != 100) {
                            progressDialog.setMessage(
                                "文件的MD5码：${fileTransfer.md5}\n" +
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

            override fun onTransferSucceed(fileTransfer: FileTransfer?) {
                runOnUiThread {
                    if (isCreated) {
                        progressDialog.setTitle("文件发送成功")
                        progressDialog.setCancelable(true)
                        progressDialog.show()
                    }
                }
            }

            override fun onTransferFailed(fileTransfer: FileTransfer?, e: Exception) {
                runOnUiThread {
                    if (isCreated) {
                        progressDialog.setTitle("文件发送失败")
                        progressDialog.setMessage("异常信息： " + e.message)
                        progressDialog.setCancelable(true)
                        progressDialog.show()
                    }
                }
            }
        }
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as FileSenderService.MyBinder
            fileSenderService = binder.service
            fileSenderService?.setProgressChangListener(progressChangListener)
            log("onServiceConnected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            fileSenderService = null
            bindService(FileSenderService::class.java, this)
            log("onServiceDisconnected")
        }
    }

    private val tvHint by lazy {
        findViewById<TextView>(R.id.tvHint)
    }

    private val sendFile by lazy {
        findViewById<Button>(R.id.sendFile)
    }

    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_sender)
        initView()
        bindService(FileSenderService::class.java, serviceConnection)
    }

    private fun initView() {
        setTitle("发送文件")
        tvHint.text = MessageFormat.format(
            "在发送文件前需要先连上文件接收端开启的Wifi热点\n热点名：{0} \n密码：{1}",
            Constants.AP_SSID,
            Constants.AP_PASSWORD
        )
        sendFile.setOnClickListener {
            if (Constants.AP_SSID != WifiLManager.getConnectedSSID(this)) {
                showToast("当前连接的 Wifi 并非文件接收端开启的 Wifi 热点，请重试或者检查权限")
                return@setOnClickListener
            }
            getContentLaunch.launch("image/*")
        }
        progressDialog = ProgressDialog(this).apply {
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            max = 100
            setTitle("发送文件")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (fileSenderService != null) {
            fileSenderService?.setProgressChangListener(null)
            unbindService(serviceConnection)
        }
        progressDialog.dismiss()
    }

}