package github.leavesczy.wififiletransfer.common

import java.io.File
import java.io.Serializable

/**
 * @Author: leavesCZY
 * @Date: 2024/4/1 10:58
 * @Desc:
 */
data class FileTransfer(val fileName: String) : Serializable

sealed class FileTransferViewState {

    data object Idle : FileTransferViewState()

    data object Connecting : FileTransferViewState()

    data object Receiving : FileTransferViewState()

    class Success(val file: File) : FileTransferViewState()

    class Failed(val throwable: Throwable) : FileTransferViewState()

}