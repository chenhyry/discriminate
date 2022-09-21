package com.tencent.ncnnyolox

import android.app.Activity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View

class DetectManager {

    companion object {
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            DetectManager()
        }
    }

    private var offlineType = false
    var offLineDetect: OffLineDetect? = null

    fun takePhoto() {
        if (!offlineType) {
            OnLineDetect.cameraTakeManager?.takePhoto()
        }
    }

    fun onResume(activity: Activity,isUsb:Boolean, surfaceView: SurfaceView?,textureView : TextureView?,detectCallback: DetectCallback, offlineType: Boolean, useFrontCamera: Boolean) {
        this.offlineType = offlineType

        if (offlineType) {
            if (offLineDetect == null) {
                offLineDetect = OffLineDetect()
            }
            offLineDetect?.initOfflineDetect(activity, surfaceView!!, useFrontCamera,detectCallback)
            offLineDetect?.onResume()
        } else {
            OnLineDetect.initCameraManager(activity,isUsb, surfaceView,textureView, useFrontCamera, detectCallback)
        }
    }

    fun onPause() {
        if (offlineType) {
            offLineDetect?.onPause()
        } else {
            OnLineDetect.cameraTakeManager?.destroy()
        }
    }

    fun onDestroy() {
        if (offlineType) {
            offLineDetect?.onDestroy()
        }
    }

}