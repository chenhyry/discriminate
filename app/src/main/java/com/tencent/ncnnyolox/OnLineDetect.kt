package com.tencent.ncnnyolox

import android.app.Activity
import android.hardware.usb.UsbDevice
import android.text.TextUtils
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import com.blankj.utilcode.util.EncodeUtils
import com.blankj.utilcode.util.EncryptUtils
import com.blankj.utilcode.util.GsonUtils
import com.lgh.uvccamera.UVCCameraProxy
import com.lgh.uvccamera.callback.ConnectCallback
import com.tencent.ncnnyolox.takephoto.*
import java.io.File
import java.util.*


class OnLineDetect {

    companion object {
        //在线识别
        var cameraTakeManager: CameraTakeManager? = null
        var mUVCCamera: UVCCameraProxy? = null
        var isUsb: Boolean = false

        fun initCameraManager(
            activity: Activity,
            isUsb: Boolean,
            surfaceView: SurfaceView?,
            textureView: TextureView?,
            frontFacing: Boolean,
            detectCallback: DetectCallback
        ) {

            if (isUsb) {
                mUVCCamera = UVCCameraProxy(activity)
                mUVCCamera?.setPreviewTexture(textureView)
                mUVCCamera?.setPictureTakenCallback { path ->
                    Log.e("图片路径：", "${path}")
                    val params = HashMap<String, Any>()
                    params["img_data"] = UploadFileUtils.fileToBase64(File(path))
                    params["sys_code"] = "s11"
                    Thread {
                        postImg(GsonUtils.toJson(params).toString(), detectCallback)
                    }.start()
                }

                mUVCCamera!!.setConnectCallback(object : ConnectCallback {
                    override fun onAttached(usbDevice: UsbDevice) {
                        mUVCCamera!!.requestPermission(usbDevice)
                    }

                    override fun onGranted(usbDevice: UsbDevice, granted: Boolean) {
                        if (granted) {
                            mUVCCamera!!.connectDevice(usbDevice)
                        }
                    }

                    override fun onConnected(usbDevice: UsbDevice) {
                        mUVCCamera!!.openCamera()
                    }

                    override fun onCameraOpened() {
//                    showAllPreviewSizes()
//                    mUVCCamera!!.setPreviewSize(640, 480)
                        mUVCCamera!!.startPreview()
                    }

                    override fun onDetached(usbDevice: UsbDevice) {
                        mUVCCamera!!.closeCamera()
                    }
                })
            } else {
                cameraTakeManager = CameraTakeManager(activity, surfaceView, object : CameraTakeListener {
                    override fun onSuccess(bitmapFile: File) {
                        Log.e("图片路径：", bitmapFile.path)
                        val params = HashMap<String, Any>()
                        params["img_data"] = UploadFileUtils.fileToBase64(bitmapFile)
                        params["sys_code"] = "s11"
                        Thread {
                            postImg(GsonUtils.toJson(params).toString(), detectCallback)
                        }.start()
                    }

                    override fun onFail(error: String) {
                        Log.e("CameraTakeManager", error)
                    }
                }, frontFacing)
            }

        }

        fun takePhoto() {
            if (isUsb) {
                mUVCCamera?.let {
                    it.takePicture()
                }
            } else {
                cameraTakeManager?.takePhoto()
            }
        }

        private fun postImg(params: String, detectCallback: DetectCallback) {
            // URL path做MD5加密
//            val pathMd5 = EncryptUtils.encryptMD5ToString(Constant.Uploadurl).toLowerCase()
            // 使用加密后的URL
            val requestTime = SystemClock.now().toString()
//            val contentWrapper = getRequestData(Constant.Uploadurl,params,requestTime)
//            val response: String? = UploadFileUtils.uploadJson(Constant.UploadHosturl + pathMd5, contentWrapper)
            val response: String? = UploadFileUtils.uploadJson(Constant.UploadHosturl + Constant.Uploadurl, params)
            if (!TextUtils.isEmpty(response)) {
                Log.e("postImg", "$response")
//                val resContent = decryptResult(response!!, requestTime)
//                Log.e("postImg", "${resContent}")
                try {
                    val entity = GsonUtils.fromJson<DiscriminateOnLineRes>(response, DiscriminateOnLineRes::class.java)
                    val stringBuilder = StringBuilder()
                    if (!entity?.data.isNullOrEmpty()) {
                        entity.data!![0].skus?.forEach {
                            stringBuilder.append(it.sku_id).append(",")
                        }
                    }

                    detectCallback.callBack(stringBuilder.toString())
                } catch (e: Exception) {

                }
            }
        }


        /**
         * 加密请求体，外包装一层参数
         */
        private fun getRequestData(path: String, content: String, requestTime: String): String {

            val key = getUUID()
            val nonceStr = getUUID()
            val contentTemp = EncodeUtils.base64Encode2String(content.toByteArray())

            val skey = cutKey(contentTemp) + getOdd(path) + requestTime
            val validateSign = EncryptUtils.encryptMD5ToString(contentTemp + skey).toLowerCase()

            val encryptedContent = CryptAES.encrypt(
                contentTemp,
                getOdd(key).substring(0, 5) + getOdd(nonceStr).substring(0, 6) + requestTime
            )
            return "{\"timeStamp\":\"$requestTime\",\"sign\":\"$validateSign\",\"nonceStr\":\"$nonceStr\",\"content\":\"$encryptedContent\",\"key\":\"$key\"}"
        }

        /**
         * 解密结果
         */
        private fun decryptResult(result: String, requestTime: String): String {
            val tempStr = requestTime.substring(requestTime.length - 4, requestTime.length)
            var count = 0
            for (element in tempStr) {
                count += element.toString().toInt()
            }
            count %= 10
            if (count == 0) {
                count = 5
            }
            return String(
                EncodeUtils.base64Decode(
                    result.substring(0, count) + result.substring(
                        count + 5,
                        result.length
                    )
                )
            )
        }

        private fun getUUID(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }

        private fun getOdd(content: String): String {
            val stringBuilder = StringBuilder()
            val charArray = content.toCharArray()
            for (i in charArray.indices) {
                if (i % 2 == 0) {
                    stringBuilder.append(charArray[i])
                }
            }
            return stringBuilder.toString()
        }

        private fun cutKey(content: String): String {
            var contentTemp = content
            do {
                contentTemp += "0"
            } while (contentTemp.length < 27)
            return contentTemp.substring(11, 27)
        }
    }
}