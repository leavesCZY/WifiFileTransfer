package github.leavesczy.wififiletransfer

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * @Author: CZY
 * @Date: 2022/9/26 15:52
 * @Desc:
 */
open class BaseActivity : AppCompatActivity() {

    private var loadingDialog: ProgressDialog? = null

    protected fun showLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = ProgressDialog(this).apply {
            setProgressStyle(ProgressDialog.STYLE_SPINNER)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            show()
        }
    }

    protected fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    protected fun <T : Activity> startActivity(clazz: Class<T>) {
        startActivity(Intent(this, clazz))
    }

    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}