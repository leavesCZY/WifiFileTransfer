# WifiFileTransfer

通过 Android 设备的 Wifi 个人热点，实现 Android 手机之间传输任意文件。理论上此实现方式不受系统版本的限制，只要 Android 手机支持开启 “Wifi 个人热点” 和 “连接 Wifi”，就可以在 Android 手机之间传输任意文件

基本思路：

1. 在 AndroidManifest 中声明需要的两个网络权限：INTERNET、ACCESS_WIFI_STATE
2. 文件接收端开启 Wifi 个人热点，作为服务端，建立 ServerSocket，在指定端口等待客户端连接
3. 文件发送端连接到该 Wifi 热点，作为客户端，建立 Socket，主动连接到服务端
4. 文件发送端先获取到 Socket 对应的 OutputStream，通过 ObjectOutputStream 将待发送的文件信息写入到 OutputStream 中，以便让文件接收端知道该如何命名即将接收到的文件
5. 文件发送端再向 Socket 对应的 OutputStream 遍历写入待发送的文件字节流，文件接收端同步进行保存，待字节流遍历结束后，文件接收端就拿到了完整的文件了

当前版本进行了一次大重构，代码和一开始相比差异较大，但基本思路都是一样的。一开始的实现思路请看：[Wiki](https://github.com/leavesCZY/WifiFileTransfer/wiki)

apk 下载体验：[release](https://github.com/leavesCZY/WifiFileTransfer/releases)
