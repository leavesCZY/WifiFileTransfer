package github.leavesczy.wififiletransfer.sender

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import github.leavesczy.wififiletransfer.BaseActivity
import github.leavesczy.wififiletransfer.R
import github.leavesczy.wififiletransfer.models.ViewState
import kotlinx.coroutines.launch

/**
 * @Author: CZY
 * @Date: 2022/9/26 17:09
 * @Desc:
 */
class FileSenderActivity : BaseActivity() {

    private val fileSenderViewModel by viewModels<FileSenderViewModel>()

    private val getContentLaunch = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null) {
            fileSenderViewModel.send(
                ipAddress = getHotspotIpAddress(
                    context = applicationContext
                ),
                fileUri = imageUri
            )
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
            getContentLaunch.launch("image/*")
        }
        initEvent()
    }

    private fun initEvent() {
        lifecycleScope.launch {
            fileSenderViewModel.viewState.collect {
                when (it) {
                    ViewState.Idle -> {
                        tvState.text = ""
                        dismissLoadingDialog()
                    }

                    ViewState.Connecting -> {
                        showLoadingDialog()
                    }

                    is ViewState.Receiving -> {
                        showLoadingDialog()
                    }

                    is ViewState.Success -> {
                        dismissLoadingDialog()
                    }

                    is ViewState.Failed -> {
                        dismissLoadingDialog()
                    }
                }
            }
        }
        lifecycleScope.launch {
            fileSenderViewModel.log.collect {
                tvState.append(it)
                tvState.append("\n\n")
            }
        }
    }

    private fun getHotspotIpAddress(context: Context): String {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
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

}