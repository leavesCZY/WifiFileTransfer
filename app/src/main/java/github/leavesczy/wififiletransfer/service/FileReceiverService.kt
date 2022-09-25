package github.leavesczy.wififiletransfer.service

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import github.leavesczy.wififiletransfer.BuildConfig
import github.leavesczy.wififiletransfer.utils.Logger
import github.leavesczy.wififiletransfer.utils.Md5Util
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * @Author: leavesCZY
 * @Desc:
 */
class FileReceiverService : IntentService("FileReceiverService") {

    interface OnReceiveProgressChangListener {
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
        fun onProgressChanged(
            fileTransfer: FileTransfer,
            totalTime: Long,
            progress: Int,
            instantSpeed: Double,
            instantRemainingTime: Long,
            averageSpeed: Double,
            averageRemainingTime: Long
        )

        /**
         * 当文件传输结束后，开始计算MD5码时回调
         */
        fun onStartComputeMD5()

        /**
         * 当文件传输成功时回调
         *
         * @param fileTransfer FileTransfer
         */
        fun onTransferSucceed(fileTransfer: FileTransfer)

        /**
         * 当文件传输失败时回调
         *
         * @param fileTransfer FileTransfer
         * @param e            Exception
         */
        fun onTransferFailed(fileTransfer: FileTransfer, e: Exception)
    }

    private var serverSocket: ServerSocket? = null

    private var inputStream: InputStream? = null

    private var objectInputStream: ObjectInputStream? = null

    private var fileOutputStream: FileOutputStream? = null

    private var progressChangListener: OnReceiveProgressChangListener? = null

    inner class MyBinder : Binder() {
        val service: FileReceiverService
            get() = this@FileReceiverService
    }

    @Deprecated("Deprecated in Java")
    override fun onBind(intent: Intent): IBinder {
        return MyBinder()
    }

    private var callbackService: ScheduledExecutorService? = null

    private var fileTransfer: FileTransfer? = null

    //总的已接收字节数
    private var total: Long = 0

    //在上一次更新进度时已接收的文件总字节数
    private var tempTotal: Long = 0

    //传输操作开始时间
    private var startTime: Date? = null

    //用于标记是否正在进行文件接收操作
    var isRunning = false
        private set

    private fun startCallback() {
        stopCallback()
        startTime = Date()
        isRunning = true
        callbackService = Executors.newScheduledThreadPool(1)
        val runnable = Runnable {
            val mFileTransfer = fileTransfer
            if (mFileTransfer != null) {
                //过去 PERIOD 秒内文件的瞬时传输速率（Kb/s）
                var instantSpeed = 0.0
                //根据瞬时速率计算的-预估的剩余完成时间（秒）
                var instantRemainingTime: Long = 0
                //到现在所用的总的传输时间
                var totalTime: Long = 0
                //总的平均文件传输速率（Kb/s）
                var averageSpeed = 0.0
                //根据总的平均传输速率计算的预估的剩余完成时间（秒）
                var averageRemainingTime: Long = 0
                //文件大小
                val fileSize = mFileTransfer.fileSize
                //当前的传输进度
                val progress = (total * 100L / fileSize).toInt()
                //距离上一次计算进度到现在之间新传输的字节数
                val temp = total - tempTotal
                if (temp > 0) {
                    instantSpeed = temp / 1024.0 / PERIOD
                    instantRemainingTime = ((fileSize - total) / 1024.0 / instantSpeed).toLong()
                }
                if (startTime != null) {
                    totalTime = (Date().time - startTime!!.time) / 1000
                    averageSpeed = total / 1024.0 / totalTime
                    averageRemainingTime = ((fileSize - total) / 1024.0 / averageSpeed).toLong()
                }
                tempTotal = total
                Logger.e(TAG, "---------------------------")
                Logger.e(TAG, "传输进度（%）: $progress")
                Logger.e(TAG, "所用时间：$totalTime")
                Logger.e(TAG, "瞬时-传输速率（Kb/s）: $instantSpeed")
                Logger.e(TAG, "瞬时-预估的剩余完成时间（秒）: $instantRemainingTime")
                Logger.e(TAG, "平均-传输速率（Kb/s）: $averageSpeed")
                Logger.e(TAG, "平均-预估的剩余完成时间（秒）: $averageRemainingTime")
                Logger.e(TAG, "字节变化：$temp")
                progressChangListener?.onProgressChanged(
                    mFileTransfer,
                    totalTime,
                    progress,
                    instantSpeed,
                    instantRemainingTime,
                    averageSpeed,
                    averageRemainingTime
                )
            }
        }
        //每隔 PERIOD 毫秒执行一次任务 runnable（定时任务内部要捕获可能发生的异常，否则如果异常抛出到上层的话，会导致定时任务停止）
//        callbackService.scheduleAtFixedRate(runnable, 0, PERIOD.toLong(), TimeUnit.MILLISECONDS)
    }

    private fun stopCallback() {
        isRunning = false
        if (callbackService != null) {
            if (!callbackService!!.isShutdown) {
                callbackService!!.shutdownNow()
            }
            callbackService = null
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null && ACTION_START_RECEIVE == intent.action) {
            clean()
            var file: File? = null
            var exception: Exception? = null
            try {
                serverSocket = ServerSocket()
                serverSocket!!.reuseAddress = true
                serverSocket!!.bind(InetSocketAddress(Constants.PORT))
                val client = serverSocket!!.accept()
                Log.e(TAG, "客户端IP地址 : " + client.inetAddress.hostAddress)
                inputStream = client.getInputStream()
                objectInputStream = ObjectInputStream(inputStream)
                fileTransfer = objectInputStream!!.readObject() as FileTransfer
                Log.e(TAG, "待接收的文件: $fileTransfer")
                if (fileTransfer == null) {
                    exception = Exception("从文件发送端发来的文件模型为null")
                    return
                } else if (TextUtils.isEmpty(fileTransfer.getMd5())) {
                    exception = Exception("从文件发送端发来的文件模型不包含MD5码")
                    return
                }
                val name = File(fileTransfer.getFilePath()).name
                //将文件存储至指定位置
                file = File(externalCacheDir, name)
                fileOutputStream = FileOutputStream(file)
                startCallback()
                val buf = ByteArray(512)
                var len: Int
                while (inputStream.read(buf).also { len = it } != -1) {
                    fileOutputStream!!.write(buf, 0, len)
                    total += len.toLong()
                }
                Log.e(TAG, "文件接收成功")
                stopCallback()
                if (progressChangListener != null) {
                    //因为上面在计算文件传输进度时因为小数点问题可能不会显示到100%，所以此处手动将之设为100%
                    progressChangListener!!.onProgressChanged(fileTransfer, 0, 100, 0.0, 0, 0.0, 0)
                    //开始计算传输到本地的文件的MD5码
                    progressChangListener!!.onStartComputeMD5()
                }
            } catch (e: Exception) {
                Log.e(TAG, "文件接收 Exception: " + e.message)
                exception = e
            } finally {
                val transfer = FileTransfer()
                if (file != null && file.exists()) {
                    transfer.filePath = file.path
                    transfer.fileSize = file.length()
                    transfer.md5 = Md5Util.getMd5(file)
                    Log.e(TAG, "计算出的文件的MD5码是：" + transfer.md5)
                }
                if (exception != null) {
                    if (progressChangListener != null) {
                        progressChangListener!!.onTransferFailed(transfer, exception)
                    }
                } else {
                    if (progressChangListener != null && fileTransfer != null) {
                        if (fileTransfer.getMd5() == transfer.md5) {
                            progressChangListener!!.onTransferSucceed(transfer)
                        } else {
                            //如果本地计算出的MD5码和文件发送端传来的值不一致，则认为传输失败
                            progressChangListener!!.onTransferFailed(
                                transfer,
                                Exception("MD5码不一致")
                            )
                        }
                    }
                }
                clean()
                //再次启动服务，等待客户端下次连接
                startActionTransfer(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clean()
    }

    private fun clean() {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            try {
                serverSocket!!.close()
                serverSocket = null
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        if (inputStream != null) {
            try {
                inputStream!!.close()
                inputStream = null
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        if (objectInputStream != null) {
            try {
                objectInputStream!!.close()
                objectInputStream = null
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        if (fileOutputStream != null) {
            try {
                fileOutputStream!!.close()
                fileOutputStream = null
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        stopCallback()
        total = 0
        tempTotal = 0
        startTime = null
        fileTransfer = null
    }

    fun setProgressChangListener(progressChangListener: OnReceiveProgressChangListener?) {
        this.progressChangListener = progressChangListener
    }

    companion object {
        private const val ACTION_START_RECEIVE =
            BuildConfig.APPLICATION_ID + ".service.action.startReceive"
        private const val TAG = "FileReceiverService"

        //计算瞬时传输速率的间隔时间
        private const val PERIOD = 400
        fun startActionTransfer(context: Context) {
            val intent = Intent(context, FileReceiverService::class.java)
            intent.action = ACTION_START_RECEIVE
            context.startService(intent)
        }
    }
}