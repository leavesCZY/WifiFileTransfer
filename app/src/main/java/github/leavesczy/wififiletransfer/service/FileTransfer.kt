package github.leavesczy.wififiletransfer.service

import java.io.Serializable

/**
 * @Author: leavesCZY
 * @Desc:
 */
data class FileTransfer(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val md5: String
) : Serializable