package github.leavesczy.wififiletransfer.service

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import github.leavesczy.wififiletransfer.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.Socket
import java.util.Date
import java.util.Random
import java.util.concurrent.ScheduledExecutorService

/**
 * @Author: leavesCZY
 * @Desc:
 */
class FileSenderService : IntentService("FileSenderService") {

    companion object {

        private const val ACTION_START_SEND =
            BuildConfig.APPLICATION_ID + ".service.action.startSend"

        private const val EXTRA_PARAM_FILE_TRANSFER =
            BuildConfig.APPLICATION_ID + ".service.extra.FileUri"

        private const val EXTRA_PARAM_IP_ADDRESS =
            BuildConfig.APPLICATION_ID + ".service.extra.IpAddress"

        private const val TAG = "FileSenderService"

        //计算瞬时传输速率的间隔时间
        private const val PERIOD = 400

        fun startActionTransfer(context: Context, fileUri: String, ipAddress: String) {
            val intent = Intent(context, FileSenderService::class.java)
            intent.action = ACTION_START_SEND
            intent.putExtra(EXTRA_PARAM_FILE_TRANSFER, fileUri)
            intent.putExtra(EXTRA_PARAM_IP_ADDRESS, ipAddress)
            context.startService(intent)
        }
    }

    private var socket: Socket? = null

    private var outputStream: OutputStream? = null

    private var objectOutputStream: ObjectOutputStream? = null

    private var inputStream: InputStream? = null

    private var progressChangListener: OnSendProgressChangListener? = null

    interface OnSendProgressChangListener {

        /**
         * 如果待发送的文件还没计算MD5码，则在开始计算MD5码时回调
         */
        fun onStartComputeMD5()

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
         * 当文件传输成功时回调
         *
         * @param fileTransfer FileTransfer
         */
        fun onTransferSucceed(fileTransfer: FileTransfer?)

        /**
         * 当文件传输失败时回调
         *
         * @param fileTransfer FileTransfer
         * @param e            Exception
         */
        fun onTransferFailed(fileTransfer: FileTransfer?, e: Exception)
    }

    inner class MyBinder : Binder() {
        val service: FileSenderService
            get() = this@FileSenderService
    }

    @Deprecated("Deprecated in Java")
    override fun onBind(intent: Intent): IBinder {
        return MyBinder()
    }

    private var callbackService: ScheduledExecutorService? = null

    private var fileTransfer: FileTransfer? = null

    //总的已传输字节数
    private var total: Long = 0

    //在上一次更新进度时已传输的文件总字节数
    private var tempTotal: Long = 0

    //传输操作开始时间
    private var startTime: Date? = null

//    private fun startCallback() {
//        stopCallback()
//        startTime = Date()
//        callbackService = Executors.newScheduledThreadPool(1)
//        val runnable = Runnable {
//            if (fileTransfer != null) {
//                //过去 PERIOD 秒内文件的瞬时传输速率（Kb/s）
//                var instantSpeed = 0.0
//                //根据瞬时速率计算的-预估的剩余完成时间（秒）
//                var instantRemainingTime: Long = 0
//                //到现在所用的总的传输时间
//                var totalTime: Long = 0
//                //总的平均文件传输速率（Kb/s）
//                var averageSpeed = 0.0
//                //根据总的平均传输速率计算的预估的剩余完成时间（秒）
//                var averageRemainingTime: Long = 0
//                //文件大小
//                val fileSize = fileTransfer.fileSize
//                //当前的传输进度
//                val progress = (total * 100L / fileSize).toInt()
//                //距离上一次计算进度到现在之间新传输的字节数
//                val temp = total - tempTotal
//                if (temp > 0) {
//                    instantSpeed = temp / 1024.0 / PERIOD
//                    instantRemainingTime = ((fileSize - total) / 1024.0 / instantSpeed).toLong()
//                }
//                if (startTime != null) {
//                    totalTime = (Date().time - startTime!!.time) / 1000
//                    averageSpeed = total / 1024.0 / totalTime
//                    averageRemainingTime = ((fileSize - total) / 1024.0 / averageSpeed).toLong()
//                }
//                tempTotal = total
//                Logger.e(TAG, "---------------------------")
//                Logger.e(TAG, "传输进度（%）: $progress")
//                Logger.e(TAG, "所用时间：$totalTime")
//                Logger.e(TAG, "瞬时-传输速率（Kb/s）: $instantSpeed")
//                Logger.e(TAG, "瞬时-预估的剩余完成时间（秒）: $instantRemainingTime")
//                Logger.e(TAG, "平均-传输速率（Kb/s）: $averageSpeed")
//                Logger.e(TAG, "平均-预估的剩余完成时间（秒）: $averageRemainingTime")
//                Logger.e(TAG, "字节变化：$temp")
//                if (progressChangListener != null) {
//                    progressChangListener!!.onProgressChanged(
//                        fileTransfer!!,
//                        totalTime,
//                        progress,
//                        instantSpeed,
//                        instantRemainingTime,
//                        averageSpeed,
//                        averageRemainingTime
//                    )
//                }
//            }
//        }
//        //每隔 PERIOD 毫秒执行一次任务 runnable（定时任务内部要捕获可能发生的异常，否则如果异常抛出到上层的话，会导致定时任务停止）
//        callbackService?.scheduleAtFixedRate(runnable, 0, PERIOD.toLong(), TimeUnit.MILLISECONDS)
//    }

    private fun stopCallback() {
        callbackService?.shutdownNow()
        callbackService = null
    }

    @Throws(Exception::class)
    private fun getOutputFilePath(context: Context, fileUri: Uri): String {
        val outputFilePath = context.externalCacheDir!!.absolutePath +
                File.separatorChar + Random().nextInt(10000) +
                Random().nextInt(10000) + ".jpg"
        val outputFile = File(outputFilePath)
        if (!outputFile.exists()) {
            outputFile.mkdirs()
            outputFile.createNewFile()
        }
        val outputFileUri = Uri.fromFile(outputFile)
        copyFile(context, fileUri, outputFileUri)
        return outputFilePath
    }

    @Throws(NullPointerException::class, IOException::class)
    private fun copyFile(context: Context, inputUri: Uri, outputUri: Uri) {
        context.contentResolver.openInputStream(inputUri).use { inputStream ->
            FileOutputStream(outputUri.path).use { outputStream ->
                if (inputStream == null) {
                    throw NullPointerException("InputStream for given input Uri is null")
                }
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
//        if (intent != null && ACTION_START_SEND == intent.action) {
//            try {
//                clean()
//                var ipAddress = intent.getStringExtra(EXTRA_PARAM_IP_ADDRESS)
//                Log.e(TAG, "IP地址：$ipAddress")
//                if (TextUtils.isEmpty(ipAddress)) {
//                    return
//                }
//                val imageUri = Uri.parse(intent.getStringExtra(EXTRA_PARAM_FILE_TRANSFER))
//                val outputFilePath = getOutputFilePath(this, imageUri)
//                val outputFile = File(outputFilePath)
//                fileTransfer = FileTransfer(
//                    fileName = outputFile.name,
//                    fileSize = outputFile.length(),
//                    filePath = outputFilePath,
//                    md5 = fileTransfer.md5
//                )
//                fileTransfer.setFileName(outputFile.name)
//                fileTransfer.setFileSize(outputFile.length())
//                fileTransfer.setFilePath(outputFilePath)
//                if (TextUtils.isEmpty(fileTransfer.getMd5())) {
//                    Logger.e(TAG, "MD5码为空，开始计算文件的MD5码")
//                    if (progressChangListener != null) {
//                        progressChangListener!!.onStartComputeMD5()
//                    }
//                    fileTransfer.setMd5(Md5Util.getMd5(File(fileTransfer.getFilePath())))
//                    Log.e(TAG, "计算结束，文件的MD5码值是：" + fileTransfer.getMd5())
//                } else {
//                    Logger.e(TAG, "MD5码不为空，无需再次计算，MD5码为：" + fileTransfer.getMd5())
//                }
//                var index = 0
//                while (ipAddress == "0.0.0.0" && index < 5) {
//                    Log.e(TAG, "ip: $ipAddress")
//                    ipAddress = WifiLManager.getHotspotIpAddress(this)
//                    index++
//                    try {
//                        Thread.sleep(1000)
//                    } catch (e: InterruptedException) {
//                        e.printStackTrace()
//                    }
//                }
//                if (ipAddress == "0.0.0.0") {
//                    return
//                }
//                socket = Socket()
//                socket!!.bind(null)
//                socket!!.connect(InetSocketAddress(ipAddress, Constants.PORT), 20000)
//                outputStream = socket!!.getOutputStream()
//                objectOutputStream = ObjectOutputStream(outputStream)
//                objectOutputStream!!.writeObject(fileTransfer)
//                inputStream = FileInputStream(File(fileTransfer.getFilePath()))
//                startCallback()
//                val buf = ByteArray(512)
//                var len: Int
//                while (inputStream!!.read(buf).also { len = it } != -1) {
//                    outputStream.write(buf, 0, len)
//                    total += len.toLong()
//                }
//                Log.e(TAG, "文件发送成功")
//                stopCallback()
//                if (progressChangListener != null) {
//                    //因为上面在计算文件传输进度时因为小数点问题可能不会显示到100%，所以此处手动将之设为100%
//                    progressChangListener!!.onProgressChanged(
//                        fileTransfer!!,
//                        0,
//                        100,
//                        0.0,
//                        0,
//                        0.0,
//                        0
//                    )
//                    progressChangListener!!.onTransferSucceed(fileTransfer)
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "文件发送异常 Exception: " + e.message)
//                if (progressChangListener != null) {
//                    progressChangListener!!.onTransferFailed(fileTransfer, e)
//                }
//            } finally {
//                clean()
//            }
//        }
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        super.onDestroy()
        clean()
    }

    private fun clean() {
        socket?.close()
        outputStream?.close()
        objectOutputStream?.close()
        inputStream?.close()
        socket = null
        outputStream = null
        objectOutputStream = null
        inputStream = null
        stopCallback()
        total = 0
        tempTotal = 0
        startTime = null
        fileTransfer = null
    }

    fun setProgressChangListener(progressChangListener: OnSendProgressChangListener?) {
        this.progressChangListener = progressChangListener
    }

}