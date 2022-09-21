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
static jobject cllbackObj;
static jobject cllbackObjMethodId;
//static JNIEnv* jniEnv;
static const char* class_names[] = {
        "102433",
        "116645",
        "157763",
        "6928804010190",
        "3068320099651",
        "6970399920415"
};


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

            g_yolox->draw(rgb, objects);
            g_yolox->jniCallAndroidDetectMethod(jVm, cllbackObj,objects);
//            __android_log_print(ANDROID_LOG_ERROR, "callAndroidMethodDetect", "dddddddddd");
//            JNIEnv *jni_env = NULL;
//            // 附加当前线程到一个Java(Dalvik)虚拟机
//            JavaVMAttachArgs vmAttachArgs;
//            vmAttachArgs.version = JNI_VERSION_1_6;
//            vmAttachArgs.name = NULL;
//            vmAttachArgs.group = NULL;
//            if (jVm->AttachCurrentThread(&jni_env, NULL) == 0) {
//
//                //1.找到java代码native方法所在的字节码文件 native代码与调用的java代码不在同一个类里
////                jobject nativeActivity = state->activity->clazz;
////                jclass acl = jni_env->GetObjectClass(nativeActivity);
////                jmethodID getClassLoader = jni_env->GetMethodID(acl, "getClassLoader", "()Ljava/lang/ClassLoader;");
////                jobject cls = jni_env->CallObjectMethod(nativeActivity, getClassLoader);
////                jclass classLoader = jni_env->FindClass("java/lang/ClassLoader");
////                jmethodID findClass = jni_env->GetMethodID(classLoader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
////                jstring strClassName = jni_env->NewStringUTF("com.tencent.ncnnyolox.NcnnYolox");
////                jclass flurryClass = (jclass)(jni_env->CallObjectMethod(cls, findClass, strClassName));
////                jni_env->DeleteLocalRef(strClassName);
//
//
//                //native代码与调用的java代码在同一个类里
//                jclass AndroidDetectProviderclazz = jni_env->FindClass("com/tencent/ncnnyolox/NcnnYolox");
//                if(AndroidDetectProviderclazz == NULL){
//                    __android_log_print(ANDROID_LOG_ERROR, "callAndroidMethodDetect", "find class error");
//                    return ;
//                }
//                //将 jclass对象进行newGlobalRef 保存起来，后面再次使用时使用该成员变量即可
//                //由别的native线程（C++）直接调起时，由于没有 java 堆栈是不能找到 java的 class的，会导致findClass失败
////        jniEnv->NewGlobalRef(AndroidDetectProviderclazz);
//
//                //2.找到class里面对应的方法
////        if (jniCallJavaDetectId == NULL) {
//                jmethodID jniCallJavaDetectId = jni_env->GetStaticMethodID(AndroidDetectProviderclazz,"jniCallJavaDetect", "(Ljava/lang/String;)V");
//                if (jniCallJavaDetectId == NULL) {
//                    __android_log_print(ANDROID_LOG_ERROR, "callAndroidMethodDetect", "find jmethodID error");
//                    jni_env->DeleteLocalRef(AndroidDetectProviderclazz);
//                    return ;
//                }
////        }
//
//                if (AndroidDetectProviderclazz == NULL || jniCallJavaDetectId == NULL) {
//                    return ;
//                } else {
//                    // 用于拼接字符串的数组
//                    char buff[100] = {0};
//                    // 用于拼接字符串的“游标”指针
//                    char *pos = buff;
//
//                    if(objects.empty()){
//                        jni_env->CallStaticVoidMethod(AndroidDetectProviderclazz, jniCallJavaDetectId, jni_env->NewStringUTF(buff));
//                    } else {
//
//                        for (size_t i = 0; i < objects.size(); i++) {
//                            // 拼接字符串，sprintf函数返回拼接字符个数
//                            int strLen = sprintf(pos, "%s: ", class_names[objects[i].label]);
//                            pos += strLen;
//                        }
//                        jni_env->CallStaticVoidMethod(AndroidDetectProviderclazz, jniCallJavaDetectId, jni_env->NewStringUTF(buff));
//                    }
//                }
//
//                // 从 Java 虚拟机上分离当前线程
//                jVm->DetachCurrentThread();
//                return ;
//            } else {
//                return ;
//            }

        }
        else
        {
            draw_unsupported(rgb);
        }
    }

    draw_fps(rgb);
}

static MyNdkCamera* g_camera = 0;



extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");
    __android_log_print(ANDROID_LOG_ERROR, "callAndroidMethodDetect", "ccccccccccc");
    jVm = vm;

    JNIEnv * env;
    int nStatus = vm->GetEnv((void**)&env, JNI_VERSION_1_6);
//    JavaVMAttachArgs vmAttachArgs;
//    vmAttachArgs.version = JNI_VERSION_1_6;


    jclass clazz = env->FindClass("com/tencent/ncnnyolox/NcnnYolox");
    if (clazz != NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "callAndroidMethodDetect", "find class sucess");
    }
    cllbackObj = env-> NewGlobalRef(clazz);

//    jmethodID jniCallJavaDetectId = env->GetStaticMethodID(clazz,"jniCallJavaDetect", "(Ljava/lang/String;)V");
//    cllbackObjMethodId = env-> NewGlobalRef(jniCallJavaDetectId);

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

//    if(jniEnv == NULL){
//        jniEnv = env;
//    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* modeltypes[] =
    {
        "yolox-nano",
        "yolox-tiny",
    };

    const int target_sizes[] =
    {
        416,
        416,
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

}

