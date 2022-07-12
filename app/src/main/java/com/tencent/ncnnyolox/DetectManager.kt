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

    fun setSurfaceView(surfaceView: SurfaceView, offlineType: Boolean, useFrontCamera: Boolean) {
        this.surfaceView = surfaceView
        this.offlineType = offlineType
        this.useFrontCamera = useFrontCamera
        if (offlineType) {
            offLineDetect = OffLineDetect()
        }
    }

    fun onResume(activity: Activity, detectListener: DetectListener?) {
        if (offlineType) {
            offLineDetect?.initOfflineDetect(activity, surfaceView, useFrontCamera)
            offLineDetect?.onResume()
        } else {
            OnLineDetect.initCameraManager(activity, surfaceView, useFrontCamera, detectListener!!)
        }
    }

    fun onPause() {
        if (offlineType) {
            offLineDetect?.onPause()
        } else {
            OnLineDetect.cameraTakeManager?.destroy()
        }
    }


}