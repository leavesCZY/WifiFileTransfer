package com.czy.wififiletransfer.model;

import java.io.File;
import java.io.Serializable;

/**
 * 作者：chenZY
 * 时间：2018/4/3 15:10
 * 描述：https://www.jianshu.com/u/9df45b87cfdf
 * https://github.com/leavesC
 */
public class FileTransfer implements Serializable {

    //文件名
    private String fileName;

    //文件路径
    private String filePath;

    //文件大小
    private long fileSize;

    //MD5码
    private String md5;

    public FileTransfer() {

    }

    public FileTransfer(File file) {
        this.fileName = file.getName();
        this.filePath = file.getPath();
        this.fileSize = file.length();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    @Override
    public String toString() {
        return "FileTransfer{" +
                "fileName='" + fileName + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileSize=" + fileSize +
                ", md5='" + md5 + '\'' +
                '}';
    }

}
