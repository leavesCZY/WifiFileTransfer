package github.leavesczy.wififiletransfer.ui

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * @Author: leavesCZY
 * @Desc:
 */
open class BaseActivity : AppCompatActivity() {

    protected val isCreated: Boolean
        get() = !isFinishing && !isDestroyed

    protected fun setTitle(title: String) {
        supportActionBar?.title = title
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

    protected fun bindService(
        service: Class<out Service>,
        serviceConnection: ServiceConnection
    ) {
        bindService(Intent(this, service), serviceConnection, Context.BIND_AUTO_CREATE)
    }

}