package com.tencent.ncnnyolox

import android.app.Activity
import android.view.SurfaceView

class DetectManager {

    companion object {
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            DetectManager()
        }
    }

    private var offlineType = false
    private var useFrontCamera = false
    private lateinit var surfaceView: SurfaceView

    var offLineDetect: OffLineDetect? = null

    fun takePhoto() {
        if (!offlineType) {
            OnLineDetect.cameraTakeManager?.takePhoto()
        }
    }

    fun onResume(activity: Activity, surfaceView: SurfaceView,detectCallback: DetectCallback, offlineType: Boolean, useFrontCamera: Boolean) {
        this.surfaceView = surfaceView
        this.useFrontCamera = useFrontCamera
        this.offlineType = offlineType

        if (offlineType) {
            if (offLineDetect == null) {
                offLineDetect = OffLineDetect()
            }
            offLineDetect?.initOfflineDetect(activity, surfaceView, useFrontCamera,detectCallback)
            offLineDetect?.onResume()
        } else {
            OnLineDetect.initCameraManager(activity, surfaceView, useFrontCamera, detectCallback)
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