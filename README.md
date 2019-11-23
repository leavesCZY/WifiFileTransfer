> 于 2019/11/23 更新，主要是适配了运行时权限，修复 bug，优化了传输流程

在我的上一篇文章：[**Android 实现无网络传输文件**](https://www.jianshu.com/p/f5d66e15fbdf)，我介绍了通过 Wifi Direct（Wifi 直连）实现 Android 设备之间进行文件传输的方法，可以在无移动网络的情况下实现点对点的文件传输

本来觉得这样也就够了，可在要应用到实际项目的时候，又考虑到用户的设备系统版本可能并不都符合要求（Wifi Direct 是 Android 4.0 后支持的功能，话说低于 4.4 版本的手机应该都很少了吧？），而且我也不确定 IOS 系统是否支持 Wifi Direct，所以为了让文件传输逻辑可以应用到更多的设备上，就又实现了通过 **Wifi热点** 进行文件传输的功能

相比于通过 Wiif Direct 进行文件传输，通过 Wifi 热点进行设备配对更加方便，逻辑也更为直接，传输一个1G左右的压缩包用了5分钟左右的时间，平均传输速率有 **3.5 M/S** 左右。此外，相对于上个版本，新版本除了提供传输进度外，还提供了传输速率、预估完成时间、文件传输前后的MD5码等数据

**项目地址：[WifiFileTransfer](https://github.com/leavesC/WifiFileTransfer)**

实现的效果如下所示：

开启Ap热点接收文件

![开启Ap热点接收文件](http://upload-images.jianshu.io/upload_images/2552605-dac256fcb8016d66.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

连接Wiif热点发送文件

![连接Wiif热点发送文件](http://upload-images.jianshu.io/upload_images/2552605-cb84e341cfe79326.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

文件传输完成后校验文件完整性

![文件传输完成后校验文件完整性](http://upload-images.jianshu.io/upload_images/2552605-4fe935ea37918cc1.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)



开发步骤分为以下几点：

1. 在 AndroidManifest 中声明相关权限（网络和文件读写权限）
2. 文件接收端开启Ap热点，作为服务器端建立Socket，在指定端口等待客户端的连接
3. 文件发送端连接到Wifi热点，作为客户端主动连接到服务器端
4. 文件发送端将待发送的文件信息模型（包括文件路径，文件大小和文件MD5码等信息）通过Socket发送给文件接收端
5. 文件发送端发起实际的文件传输请求，接收端和发送端根据已接收到或已发送的的文件字节数，计算文件传输进度、文件传输速率和预估完成时间等数据
6. 文件传输结束后，对比文件信息模型携带来的MD5码值与本地文件重新计算生成的MD5码是否相等，以此校验文件完整性

### 一、声明权限

本应用并不会消耗移动数据，但由于要使用到 Wifi 以及 Java Socket，所以需要申请网络相关的权限。此外，由于是要实现文件互传，所以也需要申请SD卡读写权限。

```xml
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

### 二、文件接收端

文件接收端作为服务器存在，需要主动开启Ap热点供文件发送端连接，此处开启Ap热点的方法是通过反射来实现，这种方法虽然方便，但并不保证在所有系统上都能成功，比如我在 7.1.2 版本系统上就开启不了，最好还是引导用户去主动开启

```java
    /**
     * 开启便携热点
     *
     * @param context  上下文
     * @param ssid     SSID
     * @param password 密码
     * @return 是否成功
     */
    public static boolean openAp(Context context, String ssid, String password) {
        WifiManager wifimanager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifimanager == null) {
            return false;
        }
        if (wifimanager.isWifiEnabled()) {
            wifimanager.setWifiEnabled(false);
        }
        try {
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, null, false);
            method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, createApConfiguration(ssid, password), true);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 关闭便携热点
     *
     * @param context 上下文
     */
    public static void closeAp(Context context) {
        WifiManager wifimanager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, null, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
```

此处需要先定义一个文件信息模型 FileTransfer ，FileTransfer 包含三个字段，MD5码值用于校验文件的完整性，fileLength 是为了用于计算文件的传输进度和传输速率

```java
public class FileTransfer implements Serializable {

    //文件路径
    private String filePath;

    //文件大小
    private long fileLength;

    //MD5码
    private String md5;

    ···
    
}
```

Ap热点开启成功后，就可以启动一个服务在后台等待文件发送端来主动连接了，这里使用 IntentService 在后台监听客户端的Socket连接请求，并通过输入输出流来传输文件。此处的代码比较简单，就只是在指定端口一直堵塞监听客户端的连接请求，获取待传输的文件信息模型 FileTransfer ，之后就进行实际的数据传输

文件传输速率是每一秒计算一次，根据这段时间内接收的字节数与消耗的时间做除法，从而得到传输速率，再通过将剩余的未传输字节数与传输速率做除法，从而得到预估的剩余传输时间

```java
    @Override
    protected void onHandleIntent(Intent intent) {
        clean();
        File file = null;
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(Constants.PORT));
            Socket client = serverSocket.accept();
            Log.e(TAG, "客户端IP地址 : " + client.getInetAddress().getHostAddress());
            inputStream = client.getInputStream();
            objectInputStream = new ObjectInputStream(inputStream);
            FileTransfer fileTransfer = (FileTransfer) objectInputStream.readObject();
            Log.e(TAG, "待接收的文件: " + fileTransfer);
            String name = new File(fileTransfer.getFilePath()).getName();
            //将文件存储至指定位置
            file = new File(Environment.getExternalStorageDirectory() + "/" + name);
            fileOutputStream = new FileOutputStream(file);
            byte buf[] = new byte[512];
            int len;
            //文件大小
            long fileSize = fileTransfer.getFileLength();
            //当前的传输进度
            int progress;
            //总的已接收字节数
            long total = 0;
            //缓存-当次更新进度时的时间
            long tempTime = System.currentTimeMillis();
            //缓存-当次更新进度时已接收的总字节数
            long tempTotal = 0;
            //传输速率（Kb/s）
            double speed = 0;
            //预估的剩余完成时间（秒）
            long remainingTime;
            while ((len = inputStream.read(buf)) != -1) {
                fileOutputStream.write(buf, 0, len);
                total += len;
                long time = System.currentTimeMillis() - tempTime;
                //每一秒更新一次传输速率和传输进度
                if (time > 1000) {
                    //当前的传输进度
                    progress = (int) (total * 100 / fileSize);
                    Logger.e(TAG, "---------------------------");
                    Logger.e(TAG, "传输进度: " + progress);
                    Logger.e(TAG, "时间变化：" + time / 1000.0);
                    Logger.e(TAG, "字节变化：" + (total - tempTotal));
                    //计算传输速率，字节转Kb，毫秒转秒   17:45:07
                    speed = ((total - tempTotal) / 1024.0 / (time / 1000.0));
                    //预估的剩余完成时间
                    remainingTime = (long) ((fileSize - total) / 1024.0 / speed);
                    Logger.e(TAG, "传输速率：" + speed);
                    Logger.e(TAG, "预估的剩余完成时间：" + remainingTime);
                    //缓存-当次更新进度时已传输的总字节数
                    tempTotal = total;
                    //缓存-当次更新进度时的时间
                    tempTime = System.currentTimeMillis();
                    if (progressChangListener != null) {
                        progressChangListener.onProgressChanged(fileTransfer, progress, speed, remainingTime);
                    }
                }
            }
            progressChangListener.onProgressChanged(fileTransfer, 100, 0, 0);
            serverSocket.close();
            inputStream.close();
            objectInputStream.close();
            fileOutputStream.close();
            serverSocket = null;
            inputStream = null;
            objectInputStream = null;
            fileOutputStream = null;
            Log.e(TAG, "文件接收成功");
        } catch (Exception e) {
            Log.e(TAG, "文件接收 Exception: " + e.getMessage());
        } finally {
            clean();
            if (progressChangListener != null) {
                FileTransfer fileTransfer = new FileTransfer();
                if (file != null && file.exists()) {
                    String md5 = Md5Util.getMd5(file);
                    fileTransfer.setFilePath(file.getPath());
                    fileTransfer.setFileLength(file.length());
                    fileTransfer.setMd5(md5);
                    Log.e(TAG, "文件的MD5码是：" + md5);
                }
                progressChangListener.onTransferFinished(fileTransfer);
            }
            //再次启动服务，等待客户端下次连接
            startService(new Intent(this, FileReceiverService.class));
        }
    }
```

因为客户端可能会多次发起连接请求，所以当此处文件传输完成后（不管成功或失败），都需要重新 startService ，让服务再次堵塞等待客户端的连接请求

为了让界面能够实时获取到文件的传输状态，所以此处除了需要启动Service外，界面还需要绑定Service，所以需要用到一个更新文件传输状态的接口

```java
     public interface OnProgressChangListener {

        /**
         * 当传输进度发生变化时回调
         *
         * @param fileTransfer  文件发送方传来的文件模型
         * @param progress      文件传输进度
         * @param speed         文件传输速率
         * @param remainingTime 预估的剩余完成时间
         */
        void onProgressChanged(FileTransfer fileTransfer, int progress, double speed, long remainingTime);

        //当传输结束时
        void onTransferFinished(FileTransfer fileTransfer);

    }
```

在界面层刷新UI

```java
private FileReceiverService.OnProgressChangListener progressChangListener = new FileReceiverService.OnProgressChangListener() {

        private FileTransfer originFileTransfer;

        @Override
        public void onProgressChanged(final FileTransfer fileTransfer, final int progress, final double speed, final long remainingTime) {
            this.originFileTransfer = fileTransfer;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.setTitle("正在接收的文件： " + new File(fileTransfer.getFilePath()).getName());
                    progressDialog.setMessage("原始文件的MD5码是：" + fileTransfer.getMd5()
                            + "\n" + "传输速率：" + (int) speed + " Kb/s"
                            + "\n" + "预估的剩余完成时间：" + remainingTime + " 秒");
                    progressDialog.setProgress(progress);
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                }
            });
        }

        @Override
        public void onTransferFinished(final FileTransfer fileTransfer) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.setTitle("传输结束");
                    progressDialog.setMessage("原始文件的MD5码是：" + originFileTransfer.getMd5()
                            + "\n" + "本地文件的MD5码是：" + fileTransfer.getMd5()
                            + "\n" + "文件位置：" + fileTransfer.getFilePath());
                    progressDialog.setCancelable(true);
                }
            });
        }

    };
```

### 三、文件发送端

文件发送端作为客户端存在，需要主动连接文件接收端开启的Wifi热点

```java
    /**
     * 连接指定Wifi
     *
     * @param context  上下文
     * @param ssid     SSID
     * @param password 密码
     * @return 是否连接成功
     */
    public static boolean connectWifi(Context context, String ssid, String password) {
        String connectedSsid = getConnectedSSID(context);
        if (!TextUtils.isEmpty(connectedSsid) && connectedSsid.equals(ssid)) {
            return true;
        }
        openWifi(context);
        WifiConfiguration wifiConfiguration = isWifiExist(context, ssid);
        if (wifiConfiguration == null) {
            wifiConfiguration = createWifiConfiguration(ssid, password);
        }
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return false;
        }
        int networkId = wifiManager.addNetwork(wifiConfiguration);
        return wifiManager.enableNetwork(networkId, true);
    }

    /**
     * 开启Wifi
     *
     * @param context 上下文
     * @return 是否成功
     */
    public static boolean openWifi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && (wifiManager.isWifiEnabled() || wifiManager.setWifiEnabled(true));
    }

    /**
     * 获取当前连接的Wifi的SSID
     *
     * @param context 上下文
     * @return SSID
     */
    public static String getConnectedSSID(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager == null ? null : wifiManager.getConnectionInfo();
        return wifiInfo != null ? wifiInfo.getSSID().replaceAll("\"", "") : "";
    }
```

连接到指定Wifi后，在选择了要发送的文件后，就启动一个后台线程去主动请求连接服务器端，然后就是进行实际的文件传输操作了

发起选取文件请求的方法

```java
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, CODE_CHOOSE_FILE);
```

获取选取的文件的实际路径，并启动 AsyncTask 去进行文件传输操作

```java
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CODE_CHOOSE_FILE && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                String path = getPath(this, uri);
                if (path != null) {
                    File file = new File(path);
                    if (file.exists()) {
                        FileTransfer fileTransfer = new FileTransfer(file.getPath(), file.length());
                        Log.e(TAG, "待发送的文件：" + fileTransfer);
                        new FileSenderTask(this, fileTransfer).execute(WifiLManager.getHotspotIpAddress(this));
                    }
                }
            }
        }
    }

    private String getPath(Context context, Uri uri) {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String data = cursor.getString(cursor.getColumnIndex("_data"));
                    cursor.close();
                    return data;
                }
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }
```

将服务器端的IP地址作为参数传给 FileSenderTask ，在正式发送文件前，先发送包含文件信息的 FileTransfer ，并在发送文件的过程中实时更新文件传输状态

```java
/**
 * 作者：chenZY
 * 时间：2018/2/24 10:21
 * 描述：
 */
public class FileSenderTask extends AsyncTask<String, Double, Boolean> {

    private ProgressDialog progressDialog;

    private FileTransfer fileTransfer;

    private static final String TAG = "FileSenderTask";

    public FileSenderTask(Context context, FileTransfer fileTransfer) {
        this.fileTransfer = fileTransfer;
        progressDialog = new ProgressDialog(context);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle("发送文件：" + fileTransfer.getFilePath());
        progressDialog.setMax(100);
    }

    @Override
    protected void onPreExecute() {
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(String... strings) {
        Logger.e(TAG, "开始计算文件的MD5码");
        fileTransfer.setMd5(Md5Util.getMd5(new File(fileTransfer.getFilePath())));
        Log.e(TAG, "计算结束，文件的MD5码值是：" + fileTransfer.getMd5());
        Socket socket = null;
        OutputStream outputStream = null;
        ObjectOutputStream objectOutputStream = null;
        InputStream inputStream = null;
        try {
            socket = new Socket();
            socket.bind(null);
            socket.connect((new InetSocketAddress(strings[0], Constants.PORT)), 10000);
            outputStream = socket.getOutputStream();
            objectOutputStream = new ObjectOutputStream(outputStream);
            objectOutputStream.writeObject(fileTransfer);
            inputStream = new FileInputStream(new File(fileTransfer.getFilePath()));
            byte buf[] = new byte[512];
            int len;
            //文件大小
            long fileSize = fileTransfer.getFileLength();
            //当前的传输进度
            double progress;
            //总的已传输字节数
            long total = 0;
            //缓存-当次更新进度时的时间
            long tempTime = System.currentTimeMillis();
            //缓存-当次更新进度时已传输的总字节数
            long tempTotal = 0;
            //传输速率（Kb/s）
            double speed;
            //预估的剩余完成时间（秒）
            double remainingTime;
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
                total += len;
                long time = System.currentTimeMillis() - tempTime;
                //每一秒更新一次传输速率和传输进度
                if (time > 1000) {
                    //当前的传输进度
                    progress = total * 100 / fileSize;
                    Logger.e(TAG, "---------------------------");
                    Logger.e(TAG, "传输进度: " + progress);
                    Logger.e(TAG, "时间变化：" + time / 1000.0);
                    Logger.e(TAG, "字节变化：" + (total - tempTotal));
                    //计算传输速率，字节转Kb，毫秒转秒
                    speed = ((total - tempTotal) / 1024.0 / (time / 1000.0));
                    //预估的剩余完成时间
                    remainingTime = (fileSize - total) / 1024.0 / speed;
                    publishProgress(progress, speed, remainingTime);
                    Logger.e(TAG, "传输速率：" + speed);
                    Logger.e(TAG, "预估的剩余完成时间：" + remainingTime);
                    //缓存-当次更新进度时已传输的总字节数
                    tempTotal = total;
                    //缓存-当次更新进度时的时间
                    tempTime = System.currentTimeMillis();
                }
            }
            outputStream.close();
            objectOutputStream.close();
            inputStream.close();
            socket.close();
            outputStream = null;
            objectOutputStream = null;
            inputStream = null;
            socket = null;
            Log.e(TAG, "文件发送成功");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "文件发送异常 Exception: " + e.getMessage());
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (objectOutputStream != null) {
                try {
                    objectOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onProgressUpdate(Double... values) {
        progressDialog.setProgress(values[0].intValue());
        progressDialog.setTitle("传输速率：" + values[1].intValue() + "Kb/s" + "\n"
                + "预计剩余完成时间：" + values[2].longValue() + "秒");
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        progressDialog.cancel();
        Log.e(TAG, "onPostExecute: " + aBoolean);
    }

}
```

### 四、校验文件完整性

文件的完整性主要是通过对比文件前后的MD5码值来校验了，文件发送端在发送文件前，先计算得到文件的MD5码，将值赋给 FileTransfer 模型传给文件接收端，文件接收端在传输结束后，再次计算本地的文件MD5码值，通过对比前后值是否相等，就可以判断文件是否传输完整
MD5码值通过如下方法计算得到

```java
/**
 * 作者：chenZY
 * 时间：2018/4/3 15:20
 * 描述：https://www.jianshu.com/u/9df45b87cfdf
 * https://github.com/leavesC
 */
public class Md5Util {

    public static String getMd5(File file) {
        InputStream inputStream = null;
        byte[] buffer = new byte[2048];
        int numRead;
        MessageDigest md5;
        try {
            inputStream = new FileInputStream(file);
            md5 = MessageDigest.getInstance("MD5");
            while ((numRead = inputStream.read(buffer)) > 0) {
                md5.update(buffer, 0, numRead);
            }
            inputStream.close();
            inputStream = null;
            return md5ToString(md5.digest());
        } catch (Exception e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String md5ToString(byte[] md5Bytes) {
        StringBuilder hexValue = new StringBuilder();
        for (byte b : md5Bytes) {
            int val = ((int) b) & 0xff;
            if (val < 16) {
                hexValue.append("0");
            }
            hexValue.append(Integer.toHexString(val));
        }
        return hexValue.toString();
    }

}
```



### **项目地址：[WifiFileTransfer](https://github.com/leavesC/WifiFileTransfer)**