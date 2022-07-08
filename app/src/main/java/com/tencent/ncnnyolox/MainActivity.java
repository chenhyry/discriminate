// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.tencent.ncnnyolox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    public static final int REQUEST_CAMERA = 100;

    private NcnnYolox ncnnyolox = new NcnnYolox();
    private int facing = 0;

    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_model = 0;
    private int current_cpugpu = 0;

    private SurfaceView cameraView;

    private DetectCallback detectCallback;

    // CameraX的预览、分析，由于我们这里不需要捕获所以就不用了
    private ImageAnalysis imageAnalysis;

    ImageView ivBitmap;         // 叠加在textureview上面的bitmap
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        ivBitmap = findViewById(R.id.ivBitmap);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraView = (SurfaceView) findViewById(R.id.cameraview);

        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);

        Button buttonSwitchCamera = (Button) findViewById(R.id.buttonSwitchCamera);
        buttonSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                int new_facing = 1 - facing;

                ncnnyolox.closeCamera();

                ncnnyolox.openCamera(new_facing);

                facing = new_facing;
            }
        });

        spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (position != current_model) {
                    current_model = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (position != current_cpugpu) {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        reload();

        detectCallback = new DetectCallback() {
            @Override
            public void callBack(String data) {
                Log.e("MainActivity", data);
            }
        };
        ncnnyolox.setDetectCallBack(detectCallback, true);

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }

        startCamera();
    }

    private void reload() {
        boolean ret_init = ncnnyolox.loadModel(getAssets(), current_model, current_cpugpu);
        if (!ret_init) {
            Log.e("MainActivity", "ncnnyolox loadModel failed");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        ncnnyolox.setOutputWindow(holder.getSurface());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void onResume() {
        super.onResume();


//        ncnnyolox.openCamera(facing);
    }

    @Override
    public void onPause() {
        super.onPause();

//        ncnnyolox.closeCamera();
    }

    ProcessCameraProvider cameraProvider = null;//相机信息
    Preview preview ;//预览对象
    Camera camera;//相机对象
    ImageCapture imageCapture;//拍照用例
    VideoCapture videoCapture;//录像用例

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> processCameraProvider = ProcessCameraProvider.getInstance(this);
        processCameraProvider.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = processCameraProvider.get();
//                    Preview preview = new Preview.Builder().build();
//                    camera_preview.attachPreview(preview);
                    imageAnalysis = setImageAnalysis();
                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(MainActivity.this, CameraSelector.DEFAULT_BACK_CAMERA,imageAnalysis);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }, ContextCompat.getMainExecutor(this));
    }


    private ImageAnalysis setImageAnalysis() {
        // 配置图像分析并生成imageanalysis
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setImageQueueDepth(1)// 设置图像队列深度为1
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        // 设置分析器
        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), image -> {
            // 从CameraX提供的ImageProxy拉取图像数据
            // 优点：是摄像头获取的真实分辨率
            // 缺点：提供的是YUV格式的Image，转Bitmap比较困难
            Image img = image.getImage();
            final Bitmap bitmap = onImageAvailable(img);
            if (bitmap == null) return;
            Matrix matrix = new Matrix();
            matrix.setRotate(90);
            final Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix
                    , true);

            // 在这里跑ncnn
            int width = result.getWidth();
            int height = result.getHeight();
            int[] pixArr = new int[width * height];
            // bitmap转数组
            result.getPixels(pixArr, 0, width, 0, 0, width, height);
            // 推理
            ncnnyolox.detectDraw(width, height, pixArr);
            // 数组转回去bitmap
            Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            newBitmap.setPixels(pixArr, 0, width, 0, 0, width, height);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ivBitmap.setImageBitmap(newBitmap); // 将推理后的bitmao喂回去
                }
            });
            image.close();
        });
        return imageAnalysis;
    }

    public Bitmap onImageAvailable(Image image) {
        ByteArrayOutputStream outputbytes = new ByteArrayOutputStream();
        ByteBuffer bufferY = image.getPlanes()[0].getBuffer();
        byte[] data0 = new byte[bufferY.remaining()];
        bufferY.get(data0);
        ByteBuffer bufferU = image.getPlanes()[1].getBuffer();
        byte[] data1 = new byte[bufferU.remaining()];
        bufferU.get(data1);
        ByteBuffer bufferV = image.getPlanes()[2].getBuffer();
        byte[] data2 = new byte[bufferV.remaining()];
        bufferV.get(data2);
        try {
            outputbytes.write(data0);
            outputbytes.write(data2);
            outputbytes.write(data1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        final YuvImage yuvImage = new YuvImage(outputbytes.toByteArray(), ImageFormat.NV21, image.getWidth(),
                image.getHeight(), null);
        ByteArrayOutputStream outBitmap = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 95, outBitmap);
        Bitmap bitmap = BitmapFactory.decodeByteArray(outBitmap.toByteArray(), 0, outBitmap.size());
        image.close();
        return bitmap;
    }
}
