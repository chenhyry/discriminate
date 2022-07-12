package com.tencent.ncnnyolox.takephoto;


import java.io.File;

/**
 * 图片拍摄回调
 * */
public interface CameraTakeListener {

    void onSuccess(File bitmapFile);

    void onFail(String error);

}
