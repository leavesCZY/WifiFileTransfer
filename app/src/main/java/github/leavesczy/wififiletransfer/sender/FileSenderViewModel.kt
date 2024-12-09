package github.leavesczy.wififiletransfer.sender

import android.app.Application
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.leavesczy.wififiletransfer.common.Constants
import github.leavesczy.wififiletransfer.common.FileTransfer
import github.leavesczy.wififiletransfer.common.FileTransferViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

/**
 * @Author: CZY
 * @Date: 2022/9/26 10:38
 * @Desc:
 */
class FileSenderViewModel(context: Application) : AndroidViewModel(context) {

    private val _fileTransferViewState = MutableSharedFlow<FileTransferViewState>()

    val fileTransferViewState: SharedFlow<FileTransferViewState> = _fileTransferViewState

    private val _log = MutableSharedFlow<String>()

    val log: SharedFlow<String> = _log

    private var fileSenderJob: Job? = null

    fun sendFile(fileUri: Uri) {
        fileSenderJob?.cancel()
        fileSenderJob = viewModelScope.launch {
            val ipAddress = getHotspotIpAddress(context = getApplication())
            sendFile(ipAddress = ipAddress, fileUri = fileUri)
        }
    }

    private suspend fun sendFile(ipAddress: String, fileUri: Uri) {
        withContext(context = Dispatchers.IO) {
            _fileTransferViewState.emit(value = FileTransferViewState.Idle)
            log {
                "ipAddress: $ipAddress"
            }
            var socket: Socket? = null
            var outputStream: OutputStream? = null
            var objectOutputStream: ObjectOutputStream? = null
            var fileInputStream: FileInputStream? = null
            try {
                val cacheFile =
                    saveFileToCacheDir(context = getApplication(), fileUri = fileUri)
                val fileTransfer = FileTransfer(fileName = cacheFile.name)
                _fileTransferViewState.emit(value = FileTransferViewState.Connecting)
                log {
                    "待发送的文件: $fileTransfer"
                }
                log {
                    "开启 Socket"
                }
                socket = Socket()
                socket.bind(null)
                log {
                    "socket connect，如果三十秒内未连接成功则放弃"
                }
                socket.connect(InetSocketAddress(ipAddress, Constants.PORT), 30000)
                _fileTransferViewState.emit(value = FileTransferViewState.Receiving)
                log {
                    "连接成功，开始传输文件"
                }
                outputStream = socket.getOutputStream()
                objectOutputStream = ObjectOutputStream(outputStream)
                objectOutputStream.writeObject(fileTransfer)
                fileInputStream = FileInputStream(cacheFile)
                val buffer = ByteArray(1024 * 1024)
                var length: Int
                while (true) {
                    length = fileInputStream.read(buffer)
                    if (length > 0) {
                        outputStream.write(buffer, 0, length)
                    } else {
                        break
                    }
                    log {
                        "正在传输文件，length : $length"
                    }
                }
                log {
                    "文件发送成功"
                }
                _fileTransferViewState.emit(value = FileTransferViewState.Success(file = cacheFile))
            } catch (e: Throwable) {
                e.printStackTrace()
                log {
                    "异常: " + e.message
                }
                _fileTransferViewState.emit(value = FileTransferViewState.Failed(throwable = e))
            } finally {
                fileInputStream?.close()
                outputStream?.close()
                objectOutputStream?.close()
                socket?.close()
            }
        }
    }

    private suspend fun saveFileToCacheDir(context: Context, fileUri: Uri): File {
        return withContext(context = Dispatchers.IO) {
            val documentFile = DocumentFile.fromSingleUri(context, fileUri)
                ?: throw NullPointerException("fileName for given input Uri is null")
            val fileName = documentFile.name
            val outputFile = File(
                context.cacheDir, Random.nextInt(
                    1,
                    200
                ).toString() + "_" + fileName
            )
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.createNewFile()
            val outputFileUri = Uri.fromFile(outputFile)
            copyFile(context, fileUri, outputFileUri)
            return@withContext outputFile
        }
    }

    private suspend fun copyFile(context: Context, inputUri: Uri, outputUri: Uri) {
        withContext(context = Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw NullPointerException("InputStream for given input Uri is null")
            val outputStream = FileOutputStream(outputUri.toFile())
            val buffer = ByteArray(1024)
            var length: Int
            while (true) {
                length = inputStream.read(buffer)
                if (length > 0) {
                    outputStream.write(buffer, 0, length)
                } else {
                    break
                }
            }
            inputStream.close()
            outputStream.close()
        }
    }

    private fun getHotspotIpAddress(context: Application): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        if (wifiInfo != null) {
            val dhcpInfo = wifiManager.dhcpInfo
            if (dhcpInfo != null) {
                val address = dhcpInfo.gateway
                return ((address and 0xFF).toString() + "." + (address shr 8 and 0xFF)
                        + "." + (address shr 16 and 0xFF)
                        + "." + (address shr 24 and 0xFF))
            }
        }
        return ""
    }

    private suspend fun log(log: () -> Any) {
        _log.emit(value = log().toString() + "\n\n")
    }

}