package com.tencent.ncnnyolox.takephoto;

import android.app.Activity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public class CameraTakeManager {

    Activity activity;
    SurfaceView surfaceView;
    CameraTakeListener listener;

    SurfaceHolder surfaceHolder;

    SurfaceViewCallback surfaceViewCallback;


    public CameraTakeManager(Activity activity, SurfaceView surfaceView, CameraTakeListener listener, boolean frontFacing) {
        this.activity = activity;
        this.surfaceView = surfaceView;
        this.listener = listener;

        surfaceViewCallback = new SurfaceViewCallback(activity, listener, frontFacing);
        initCamera();
    }

    /**
     * 初始化相机
     */
    private void initCamera() {
        //在surfaceView中获取holder
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(surfaceViewCallback);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    /**
     * 获取相机当前的照片
     * */
    public void takePhoto() {
        surfaceViewCallback.takePhoto();
    }

    public void destroy() {
        surfaceViewCallback.destroy();
    }

}
