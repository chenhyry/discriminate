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

package com.tencent.ncnnyolox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
将 apply plugin: ‘com.android.application’ 修改为 apply plugin: ‘com.android.library’
注释掉applicationId这一行。由于打包后该Module不再是一个独立的应用，而是一个其它项目的附属，所以不需要独立的applicationId
如果有自定义的Application类，把name属性和icon属性删掉。因为打包成aar并被其它项目引用后，该AnroidManifest.xml会和所在项目的AnroidManifest.xml合并，这时会产生冲突。
<application
android:label="@string/app_name"
android:theme="@style/AppTheme">
<activity
android:name=".MainActivity"
android:exported="true"
android:label="@string/app_name"
android:screenOrientation="portrait">
<intent-filter>
<action android:name="android.intent.action.MAIN" />
<category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
</activity>
</application>

进入到Gradle界面，双击assemble就编译生成aar包了
app-build-outputs-aar文件夹
 */
class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var textureView: TextureView

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        surfaceView = findViewById(R.id.surfaceView)
        textureView = findViewById(R.id.textureView)
        findViewById<View>(R.id.buttonPostImg).setOnClickListener {
            DetectManager.instance.takePhoto()
        }

    }

    public override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }

        DetectManager.instance.onResume(this, true, surfaceView, textureView,DetectCallback { },false,false)
    }

    public override fun onPause() {
        super.onPause()
        DetectManager.instance.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        DetectManager.instance.onDestroy()
    }

    companion object {
        const val REQUEST_CAMERA = 100
    }
}