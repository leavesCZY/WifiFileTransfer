package github.leavesczy.wififiletransfer.sender

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import github.leavesczy.wififiletransfer.BaseActivity
import github.leavesczy.wififiletransfer.R
import github.leavesczy.wififiletransfer.common.FileTransferViewState
import kotlinx.coroutines.launch

/**
 * @Author: CZY
 * @Date: 2022/9/26 17:09
 * @Desc:
 */
class FileSenderActivity : BaseActivity() {

    private val fileSenderViewModel by viewModels<FileSenderViewModel>()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null) {
            fileSenderViewModel.sendFile(fileUri = imageUri)
        }
    }

    private val btnChooseFile by lazy {
        findViewById<Button>(R.id.btnChooseFile)
    }

    private val tvState by lazy {
        findViewById<TextView>(R.id.tvState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_sender)
        supportActionBar?.title = "文件发送端"
        btnChooseFile.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
        initEvent()
    }

    private fun initEvent() {
        lifecycleScope.launch {
            launch {
                fileSenderViewModel.fileTransferViewState.collect {
                    when (it) {
                        FileTransferViewState.Idle -> {
                            tvState.text = ""
                            dismissLoadingDialog()
                        }

                        FileTransferViewState.Connecting -> {
                            showLoadingDialog()
                        }

                        is FileTransferViewState.Receiving -> {
                            showLoadingDialog()
                        }

                        is FileTransferViewState.Success -> {
                            dismissLoadingDialog()
                        }

                        is FileTransferViewState.Failed -> {
                            dismissLoadingDialog()
                            showToast(message = it.throwable.toString())
                        }
                    }
                }
            }
            launch {
                fileSenderViewModel.log.collect {
                    tvState.append(it)
                }
            }
        }
    }

}