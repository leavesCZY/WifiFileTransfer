package com.czy.wififiletransfer;

import android.os.Bundle;
import android.view.View;

/**
 * 作者：chenZY
 * 时间：2018/4/3 14:56
 * 描述：https://www.jianshu.com/u/9df45b87cfdf
 * https://github.com/leavesC
 */
public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void startFileSenderActivity(View view) {
        startActivity(FileSenderActivity.class);
    }

    public void startFileReceiverActivity(View view) {
        startActivity(FileReceiverActivity.class);
    }

}
