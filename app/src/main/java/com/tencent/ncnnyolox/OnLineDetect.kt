package com.tencent.ncnnyolox

import android.app.Activity
import android.util.Log
import android.view.SurfaceView
import com.blankj.utilcode.util.GsonUtils
import com.tencent.ncnnyolox.net.CryptoInterceptor
import com.tencent.ncnnyolox.takephoto.CameraTakeListener
import com.tencent.ncnnyolox.takephoto.CameraTakeManager
import com.tencent.ncnnyolox.takephoto.UploadFileUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException


class OnLineDetect {

    companion object {
        //在线识别
        var cameraTakeManager: CameraTakeManager? = null

        fun initCameraManager(
            activity: Activity,
            surfaceView: SurfaceView,
            frontFacing: Boolean,
            detectListener: DetectListener
        ) {
            cameraTakeManager = CameraTakeManager(activity, surfaceView, object : CameraTakeListener {
                override fun onSuccess(bitmapFile: File) {
                    Log.e("图片路径：", bitmapFile.path)
                    val params = HashMap<String, Any>()
                    params["img_data"] = UploadFileUtils.fileToBase64(bitmapFile)
                    params["sys_code"] = "s11"
                    postImg(GsonUtils.toJson(params).toString(), detectListener)
                }

                override fun onFail(error: String) {
                    Log.e("CameraTakeManager", error)
                }
            }, frontFacing)
        }

        private fun postImg(params: String, detectListener: DetectListener) {

//            val builder = OkHttpClient.Builder()
//            val sslParams4 = HttpsUtils.getSslSocketFactory()
//            builder.sslSocketFactory(sslParams4.sSLSocketFactory, sslParams4.trustManager)

            val okHttpClient: OkHttpClient = OkHttpClient.Builder()
                .addInterceptor(CryptoInterceptor())
                .build()
            val requestBody: RequestBody = params.toRequestBody("application/json".toMediaTypeOrNull())
            val request: Request = Request.Builder()
                .url(Constant.Uploadurl) //请求的url
                .addHeader("tag", Constant.Tag)
                .addHeader("alias", "true")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .post(requestBody)
                .build()
            val call = okHttpClient.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                }

                override fun onResponse(call: Call, response: Response) {
                    //InputStream is=response.body().byteStream();
                    // 执行IO操作时，能够下载很大的文件，并且不会占用很大内存
                    println(response.body!!.string())
                    try {
                        val entity =
                            GsonUtils.fromJson<DiscriminateOnLine>(
                                response.body!!.string(),
                                DiscriminateOnLine::class.java
                            )
                        val stringBuilder = StringBuilder()
                        entity.skus?.forEach {
                            stringBuilder.append(it).append(",")
                        }
                        detectListener.detect(stringBuilder.toString())
                    } catch (e: Exception) {

                    }
                }
            })
        }
    }

}