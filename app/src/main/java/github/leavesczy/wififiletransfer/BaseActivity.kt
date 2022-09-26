package github.leavesczy.wififiletransfer

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * @Author: CZY
 * @Date: 2022/9/26 15:52
 * @Desc:
 */
open class BaseActivity : AppCompatActivity() {

    private var progressDialog: ProgressDialog? = null

    protected fun showLoadingDialog() {
        progressDialog?.dismiss()
        progressDialog = ProgressDialog(this).apply {
            setProgressStyle(ProgressDialog.STYLE_SPINNER)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            show()
        }
    }

    protected fun dismissLoadingDialog() {
        progressDialog?.dismiss()
    }

    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    protected fun log(any: Any?) {
        Log.e(javaClass.simpleName, any?.toString() ?: "null")
    }

    protected fun <T : Activity> startActivity(clazz: Class<T>) {
        startActivity(Intent(this, clazz))
    }

}