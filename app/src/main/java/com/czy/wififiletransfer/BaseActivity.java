package com.czy.wififiletransfer;


import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

/**
 * 作者：chenZY
 * 时间：2018/4/3 14:51
 * 描述：https://www.jianshu.com/u/9df45b87cfdf
 * https://github.com/leavesC
 */
@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {

    protected void setTitle(String title) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    protected void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void bindService(Class<? extends Service> service, ServiceConnection serviceConnection) {
        bindService(new Intent(this, service), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    protected void startActivity(Class c) {
        startActivity(new Intent(this, c));
    }

    protected boolean isFinishingOrDestroyed() {
        return isFinishing() || isDestroyed();
    }

}