/*
package com.tencent.ncnnyolox

import android.util.Base64
import com.blankj.utilcode.util.EncryptUtils
import com.blankj.utilcode.util.TimeUtils
import com.jy.pos.BuildConfig
import com.jy.pos.global.DomainKey
import com.jy.pos.manager.UserManager
import me.jessyan.retrofiturlmanager.RetrofitUrlManager
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

*/
/**
 * 接口安全拦截器
 * 配置通用Header
 * 配置 session、数据加解密等
 * @author wfq
 * created at 2019/9/17
 *//*

class SecurityInterceptor : Interceptor {

    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)

    override fun intercept(chain: Interceptor.Chain): Response {
        val oldRequest = chain.request()
        val requestBuilder = oldRequest.newBuilder()

        requestBuilder.header("tag",  Constant.Tag)
        requestBuilder.build()
        return chain.proceed(request)
    }

    */
/**
     * Request加密
     *
     * @param request
     * @param base64Key
     * @return
     * @throws IOException
     *//*

    @Throws(IOException::class)
    private fun encrypt(request: Request, base64Key: String): Request {
        var newRequest = request
        val requestBody = newRequest.body()
        if (requestBody != null) {
            val buffer = okio.Buffer()
            requestBody.writeTo(buffer)
            var charset: Charset? = Charset.forName("UTF-8")
            val contentType = requestBody.contentType()
            if (contentType != null) {
                charset = contentType.charset(charset)
            }
            val string = buffer.readString(charset!!)
            //加密
            val encryptStr = EncryptUtils.encryptAES2HexString(
                string.toByteArray(),
                Base64.decode(base64Key, Base64.NO_WRAP),
                "AES", null
            )

            val body = MultipartBody.create(contentType, encryptStr)
            newRequest = newRequest.newBuilder()
                .post(body)
                .build()
        }
        return newRequest
    }

    private fun getRandomKey(): String {
        var hashCode = UUID.randomUUID().toString().hashCode()
        if (hashCode < 0) { //有可能是负数
            hashCode = -hashCode
        }
        return String.format("9%015d", hashCode)
    }
}*/
