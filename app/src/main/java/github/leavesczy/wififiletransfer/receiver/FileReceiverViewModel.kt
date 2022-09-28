package github.leavesczy.wififiletransfer.receiver

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.leavesczy.wififiletransfer.Constants
import github.leavesczy.wififiletransfer.models.FileTransfer
import github.leavesczy.wififiletransfer.models.ViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * @Author: CZY
 * @Date: 2022/9/26 14:18
 * @Desc:
 */
class FileReceiverViewModel(context: Application) :
    AndroidViewModel(context) {

    private val _viewState = MutableSharedFlow<ViewState>()

    val viewState: SharedFlow<ViewState> = _viewState

    private val _log = MutableSharedFlow<String>()

    val log: SharedFlow<String> = _log

    private var job: Job? = null

    fun startListener() {
        if (job != null) {
            return
        }
        job = viewModelScope.launch(context = Dispatchers.IO) {
            _viewState.emit(value = ViewState.Idle)

            var serverSocket: ServerSocket? = null
            var clientInputStream: InputStream? = null
            var objectInputStream: ObjectInputStream? = null
            var fileOutputStream: FileOutputStream? = null
            try {
                _viewState.emit(value = ViewState.Connecting)
                _log.emit(value = "开启 Socket")

                serverSocket = ServerSocket()
                serverSocket.bind(InetSocketAddress(Constants.PORT))
                serverSocket.reuseAddress = true
                serverSocket.soTimeout = 30000

                _log.emit(value = "socket accept，三十秒内如果未成功则断开链接")

                val client = serverSocket.accept()

                _viewState.emit(value = ViewState.Receiving)

                clientInputStream = client.getInputStream()
                objectInputStream = ObjectInputStream(clientInputStream)
                val fileTransfer = objectInputStream.readObject() as FileTransfer
                val file = File(getCacheDir(context = getApplication()), fileTransfer.fileName)

                _log.emit(value = "连接成功，待接收的文件: $fileTransfer")
                _log.emit(value = "文件将保存到: $file")
                _log.emit(value = "开始传输文件")

                fileOutputStream = FileOutputStream(file)
                val buffer = ByteArray(1024 * 512)
                while (true) {
                    val length = clientInputStream.read(buffer)
                    if (length > 0) {
                        fileOutputStream.write(buffer, 0, length)
                    } else {
                        break
                    }
                    _log.emit(value = "正在传输文件，length : $length")
                }
                _viewState.emit(value = ViewState.Success(file = file))
                _log.emit(value = "文件接收成功")
            } catch (e: Throwable) {
                _log.emit(value = "异常: " + e.message)
                _viewState.emit(value = ViewState.Failed(throwable = e))
            } finally {
                serverSocket?.close()
                clientInputStream?.close()
                objectInputStream?.close()
                fileOutputStream?.close()
            }
        }
        job?.invokeOnCompletion {
            job = null
        }
    }

    private fun getCacheDir(context: Context): File {
        val cacheDir = File(context.cacheDir, "FileTransfer")
        cacheDir.mkdirs()
        return cacheDir
    }

}