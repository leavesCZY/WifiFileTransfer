package github.leavesczy.wififiletransfer

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import github.leavesczy.wififiletransfer.ui.BaseActivity
import github.leavesczy.wififiletransfer.ui.FileReceiverActivity
import github.leavesczy.wififiletransfer.ui.FileSenderActivity

/**
 * @Author: leavesCZY
 * @Desc:
 */
class MainActivity : BaseActivity() {

    private val requestPermissionLaunch = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { it ->
        if (it.all { it.value }) {
            showToast("已获得权限")
        } else {
            showToast("缺少权限，请先授予权限")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.btnCheckPermission).setOnClickListener {
            requestPermissionLaunch.launch(
                arrayOf(
                    Manifest.permission.CHANGE_NETWORK_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
        findViewById<View>(R.id.btnSender).setOnClickListener {
            startActivity(FileSenderActivity::class.java)
        }
        findViewById<View>(R.id.btnReceiver).setOnClickListener {
            startActivity(FileReceiverActivity::class.java)
        }
    }

}