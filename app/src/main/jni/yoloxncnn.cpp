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

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "yolox.h"

#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON



static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}


static Yolox* g_yolox = 0;
static ncnn::Mutex lock;

static JavaVM *jVm = NULL;
static jobject cllbackObj;//全局类
static jfloat recognitionRate = 0.7;//识别率

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
};

void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // nanodet
    {
        ncnn::MutexLockGuard g(lock);

        if (g_yolox)
        {
            std::vector<Object> objects;
            g_yolox->detect(rgb, objects);

            g_yolox->draw(rgb, objects, recognitionRate);

            g_yolox->jniCallAndroidDetectMethod(jVm, cllbackObj,objects, recognitionRate);
        }
        else
        {
            draw_unsupported(rgb);
        }
    }

    //TODO 帧数
    draw_fps(rgb);
}

static MyNdkCamera* g_camera = 0;



extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_ERROR, "ncnn", "JNI_OnLoad");
    jVm = vm;

    JNIEnv * env;
    int nStatus = vm->GetEnv((void**)&env, JNI_VERSION_1_4);

    jclass clazz = env->FindClass("com/tencent/ncnnyolox/NcnnYolox");
    if (clazz != NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "callAndroidMethodDetect", "find class sucess");
    }
    cllbackObj = env-> NewGlobalRef(clazz);

    g_camera = new MyNdkCamera;
    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_yolox;
        g_yolox = 0;
    }

    delete g_camera;
    g_camera = 0;

    jVm = NULL;

}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_tencent_ncnnyolox_NcnnYolox_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint modelid, jint cpugpu)
{
    if (modelid < 0 || modelid > 6 || cpugpu < 0 || cpugpu > 1)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* modeltypes[] =
    {
        "yolox-nano",
//        "yolox-tiny",
    };

//    const int target_sizes[] =
//    {
//        416,
//        416,
//    };

    const int target_sizes[] =
    {
        320,
        320,
    };

    const float mean_vals[][3] =
    {
        {255.f * 0.485f, 255.f * 0.456, 255.f * 0.406f},
        {255.f * 0.485f, 255.f * 0.456, 255.f * 0.406f},
    };

    const float norm_vals[][3] =
    {
        {1 / (255.f * 0.229f), 1 / (255.f * 0.224f), 1 / (255.f * 0.225f)},
        {1 / (255.f * 0.229f), 1 / (255.f * 0.224f), 1 / (255.f * 0.225f)},
    };

    const char* modeltype = modeltypes[(int)modelid];
    int target_size = target_sizes[(int)modelid];
    bool use_gpu = (int)cpugpu == 1;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (use_gpu && ncnn::get_gpu_count() == 0)
        {
            // no gpu
            delete g_yolox;
            g_yolox = 0;
        }
        else
        {
            if (!g_yolox)
                g_yolox = new Yolox;
            g_yolox->load(mgr, modeltype, target_size, mean_vals[(int)modelid], norm_vals[(int)modelid], use_gpu);
        }
    }

    return JNI_TRUE;
}

// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL Java_com_tencent_ncnnyolox_NcnnYolox_openCamera(JNIEnv* env, jobject thiz, jint facing)
{
    if (facing < 0 || facing > 1)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    g_camera->open((int)facing);

    return JNI_TRUE;
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL Java_com_tencent_ncnnyolox_NcnnYolox_closeCamera(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_com_tencent_ncnnyolox_NcnnYolox_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_tencent_ncnnyolox_NcnnYolox_detectDraw(JNIEnv* env, jobject thiz, jint jw, jint jh, jintArray jPixArr)
{
    jint *cPixArr = env->GetIntArrayElements(jPixArr, JNI_FALSE);
    if (cPixArr == NULL) {
        return JNI_FALSE;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // 用传入的数组构建Mat，然后从RGBA转成RGB
    cv::Mat mat_image_src(jh, jw, CV_8UC4, (unsigned char *) cPixArr);
    cv::Mat rgb;
    cvtColor(mat_image_src, rgb, cv::COLOR_RGBA2RGB, 3);

    // 将RGB图喂入ncnn进行推理，绘制bbox和fps
    {
        ncnn::MutexLockGuard g(lock);
        std::vector<Object> objects;
        g_yolox->detect(rgb, objects);
        g_yolox->draw(rgb, objects,recognitionRate);
    }
    draw_fps(rgb);

    // 将Mat从RGB转回去RGBA刷新java数据
    cvtColor(rgb, mat_image_src, cv::COLOR_RGB2RGBA, 4);
    // 释放掉C数组
    env->ReleaseIntArrayElements(jPixArr, cPixArr, 0);

    ////////////////////////////////////////////////////////////////////////////////////////////////

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_tencent_ncnnyolox_NcnnYolox_decodeYUV420SP(JNIEnv* env, jobject thiz, jint width, jint height,jbyteArray buf) {
    jbyte *yuv420sp = env->GetByteArrayElements(buf, 0);
    int frameSize = width * height;
    jint rgb[frameSize]; // 新图像像素值

    int i = 0, j = 0, yp = 0;
    int uvp = 0, u = 0, v = 0;
    for (j = 0, yp = 0; j < height; j++) {
        uvp = frameSize + (j >> 1) * width;
        u = 0;
        v = 0;
        for (i = 0; i < width; i++, yp++) {
            int y = (0xff & ((int) yuv420sp[yp])) - 16;
            if (y < 0)
                y = 0;
            if ((i & 1) == 0) {
                v = (0xff & yuv420sp[uvp++]) - 128;
                u = (0xff & yuv420sp[uvp++]) - 128;
            }

            int y1192 = 1192 * y;
            int r = (y1192 + 1634 * v);
            int g = (y1192 - 833 * v - 400 * u);
            int b = (y1192 + 2066 * u);

            if (r < 0) r = 0; else if (r > 262143) r = 262143;
            if (g < 0) g = 0; else if (g > 262143) g = 262143;
            if (b < 0) b = 0; else if (b > 262143) b = 262143;

            rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
        }
    }

    jintArray result = env->NewIntArray(frameSize);
    env->SetIntArrayRegion(result, 0, frameSize, rgb);

    // 释放掉C数组
    env->ReleaseByteArrayElements(buf, yuv420sp, 0);


    jint *cPixArr = env->GetIntArrayElements(result, JNI_FALSE);
    if (cPixArr == NULL) {
        return JNI_FALSE;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    // 用传入的数组构建Mat，然后从RGBA转成RGB
    cv::Mat mat_image_src(height, width, CV_8UC4, (unsigned char *) cPixArr);
    cv::Mat rgb2;
    cvtColor(mat_image_src, rgb2, cv::COLOR_RGBA2RGB, 3);

    // 将RGB图喂入ncnn进行推理，绘制bbox和fps
    {
        ncnn::MutexLockGuard g(lock);
        std::vector<Object> objects;
        g_yolox->detect(rgb2, objects);
        g_yolox->draw(rgb2, objects,recognitionRate);
    }
    draw_fps(rgb2);

    // 将Mat从RGB转回去RGBA刷新java数据
    cvtColor(rgb2, mat_image_src, cv::COLOR_RGB2RGBA, 4);
    // 释放掉C数组
    env->ReleaseIntArrayElements(result, cPixArr, 0);

    return JNI_TRUE;
}



}

