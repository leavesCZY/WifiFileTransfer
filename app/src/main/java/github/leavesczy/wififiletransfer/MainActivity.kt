package github.leavesczy.wififiletransfer

import android.os.Bundle
import android.view.View
import github.leavesczy.wififiletransfer.receiver.FileReceiverActivity
import github.leavesczy.wififiletransfer.sender.FileSenderActivity

/**
 * @Author: CZY
 * @Date: 2022/9/26 17:10
 * @Desc:
 */
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.btnReceiver).setOnClickListener {
            startActivity(FileReceiverActivity::class.java)
        }
        findViewById<View>(R.id.btnSender).setOnClickListener {
            startActivity(FileSenderActivity::class.java)
        }
    }

}