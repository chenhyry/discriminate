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

import android.content.res.AssetManager;
import android.util.Log;
import android.view.Surface;

/**
 * some notes
 * Android ndk camera is used for best efficiency
 * Crash may happen on very old devices for lacking HAL3 camera interface
 * All models are manually modified to accept dynamic input shape
 * Most small models run slower on GPU than on CPU, this is common
 * FPS may be lower in dark environment because of longer camera exposure time
 */
public class NcnnYolox {

    private static DetectCallback detectCallbackStatic;
    private static String lastDetectContent;
    private static Boolean needDifference;

    public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);

    public native boolean openCamera(int facing);

    public native boolean closeCamera();

    public native boolean detectDraw(int w, int h, int[] pixArr);

    public native boolean setOutputWindow(Surface surface);

    public static void jniCallJavaDetect(String data) {
        if (detectCallbackStatic != null) {
            if (needDifference) {
                if (lastDetectContent == null) {
                    if (data != null) {
                        lastDetectContent = data;
                        detectCallbackStatic.callBack(data);
                    }
                } else {
                    if (!lastDetectContent.equals(data)) {
                        lastDetectContent = data;
                        detectCallbackStatic.callBack(data);
                    }
                }
            } else {
                detectCallbackStatic.callBack(data);
            }
        }
        Log.e("NcnnYolox", data);
    }

    /**
     * 设置回调
     * @param detectCallback
     * @param needDiff 是否只有识别内容变化才回调
     */
    public static void setDetectCallBack(DetectCallback detectCallback, boolean needDiff) {
        detectCallbackStatic = detectCallback;
        needDifference = needDiff;
    }

    static {
        System.loadLibrary("ncnnyolox");
    }
}
