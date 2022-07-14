package com.tencent.ncnnyolox.takephoto;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;


public class SurfaceViewCallback implements SurfaceHolder.Callback {

    private static final String PIC_PATH = "/T11/pos/discriminate/";

    private Activity activity;

    boolean previewing;

    private boolean hasSurface;

    Camera mCamera;

    int mCurrentCamIndex = 0;

    /**
     * 为true时则开始捕捉照片
     */
    boolean canTake;

    /**
     * 拍照回调接口
     */
    CameraTakeListener listener;

    boolean frontFacing = true;

    public SurfaceViewCallback(Activity activity, CameraTakeListener listener, boolean frontFacing) {
        previewing = false;
        hasSurface = false;

        this.activity = activity;
        this.listener = listener;
        this.frontFacing = frontFacing;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            mCamera = openFacingCameraGingerbread(frontFacing);

            if (mCamera == null) {
                listener.onFail("没有可用的摄像头");
                return;
            }
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] bytes, Camera camera) {
//                    Log.i("SurfaceViewCallback", "onPreviewFrame " + canTake);
                    if (canTake) {
                        getSurfacePic(bytes, camera);
                        canTake = false;
                    }
                }
            });
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (previewing) {
            mCamera.stopPreview();
            previewing = false;
        }

        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            previewing = true;
            setCameraDisplayOrientation(activity, mCurrentCamIndex, mCamera);
        } catch (Exception e) {
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (!previewing) return;
        holder.removeCallback(this);
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.lock();
        mCamera.release();
        mCamera = null;
    }

    /**
     * 设置照相机播放的方向
     */
    private void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        /** 度图片顺时针旋转的角度。有效值为0、90、180和270*/
        /** 起始位置为0（横向）*/
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {
            /** 背面*/
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    /**
     * 打开摄像头面板
     */
    private Camera openFacingCameraGingerbread(boolean frontFacing) {
        int cameraCount = 0;
        Camera cam = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            try {
                int CAMERA_FACING = frontFacing ? Camera.CameraInfo.CAMERA_FACING_FRONT: Camera.CameraInfo.CAMERA_FACING_BACK;
                if(cameraInfo.facing == CAMERA_FACING){
                    cam = Camera.open(camIdx);
                    mCurrentCamIndex = camIdx;
                }
            } catch (RuntimeException e) {
                Log.e("SurfaceViewCallback", "Camera failed to open: " + e.getLocalizedMessage());
            }
        }

        return cam;
    }

    /**
     * 获取照片
     */
    public void getSurfacePic(byte[] data, Camera camera) {
        Camera.Size size = camera.getParameters().getPreviewSize();
        YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
        if (image != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);

            Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
            /** 因为图片会放生旋转，因此要对图片进行旋转到和手机在一个方向上*/
            rotateMyBitmap(bmp);
        }
    }

    /**
     * 旋转图片
     */
    public void rotateMyBitmap(Bitmap bmp) {
        Matrix matrix = new Matrix();
        matrix.postRotate(0);
        Bitmap nbmp2 = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        saveMyBitmap(nbmp2);
    }

    /**
     * 保存图片
     */
    public void saveMyBitmap(final Bitmap mBitmap) {
        final File filePic = saveBitmap(mBitmap);
        if (filePic == null) {
            /** 图片保存失败*/
            listener.onFail("图片保存失败");
            return;
        }

        compressPic(activity, filePic, listener);
    }

    private File saveBitmap(Bitmap bitmap) {
        try {
            File fileDirPath = new File(Environment.getExternalStorageDirectory().toString() + PIC_PATH);
            if (!fileDirPath.exists()) {
                fileDirPath.mkdirs();
            }
            File filePic = new File(fileDirPath.getPath() + File.separator + System.currentTimeMillis() + ".jpg");

            FileOutputStream fos = new FileOutputStream(filePic);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
            Log.e("SurfaceViewCallback", "savePicture, .writeFile sucess!");
            return filePic;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("SurfaceViewCallback", "savePicture, .writeFile failure!");
        }
        return null;
    }

    /**
     * 压缩图片文件
     */
    public static void compressPic(Context context, final File picFile, CameraTakeListener listener) {
        Luban.with(context).load(picFile).ignoreBy(100)
                .filter(path -> !(TextUtils.isEmpty(path) || path.toLowerCase().endsWith(".gif"))).setCompressListener(new OnCompressListener() {
            @Override
            public void onStart() {

            }

            @Override
            public void onSuccess(File file) {
                listener.onSuccess(file);
            }

            @Override
            public void onError(Throwable e) {
                listener.onFail("图片保存失败");
            }
        }).launch();
    }

    /**
     * 文件夹删除
     */
    public static void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                deleteFile(f);
            }
            file.delete();//如要保留文件夹，只删除文件，请注释这行
        } else if (file.exists()) {
            file.delete();
        }
    }



    /**
     * 获取相机当前的照片
     */
    public void takePhoto() {
        this.canTake = true;
    }

    /**
     * 释放
     */
    public void destroy() {
        hasSurface = false;
    }

}
