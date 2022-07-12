package com.tencent.ncnnyolox

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView


class OffLineDetect {

    private var ncnnyolox: NcnnYolox? = null
    private var detectCallback: DetectCallback? = null
    private var hasOpenCamera = false
    private var surfaceView: SurfaceView? = null
    private var useFrontCamera: Boolean = false

    fun initOfflineDetect(context: Context, surfaceView: SurfaceView,useFrontCamera : Boolean) {
        if(ncnnyolox != null) return
        ncnnyolox = NcnnYolox()
        this.surfaceView = surfaceView
        this.useFrontCamera = useFrontCamera
        surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback{
            override fun surfaceCreated(p0: SurfaceHolder) {

            }

            override fun surfaceChanged(holder:  SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                ncnnyolox?.setOutputWindow(holder.surface)
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) {
            }

        })
        reloadModel(context)
        detectCallback = DetectCallback { data -> Log.e("OffLineDetect", data) }
        NcnnYolox.setDetectCallBack(detectCallback, true)
    }

    private fun reloadModel(context: Context) {
        val retInit = ncnnyolox!!.loadModel(context.assets, 0, 0)
        if (!retInit) {
            Log.e("ncnnyolox", "ncnnyolox loadModel failed")
        }
    }

    private fun openOfflineCamera() {
        if (hasOpenCamera) {
            ncnnyolox?.closeCamera()
        }
        ncnnyolox?.openCamera(if (useFrontCamera) 0 else 1)
        hasOpenCamera = true
    }

    fun onResume() {
        surfaceView?.postDelayed({ openOfflineCamera() }, 5000)
    }

    fun onPause() {
        ncnnyolox?.let {
            hasOpenCamera = false
            it.closeCamera()
        }
    }

    fun onDestroy(){
        ncnnyolox = null
        detectCallback = null
    }

}


