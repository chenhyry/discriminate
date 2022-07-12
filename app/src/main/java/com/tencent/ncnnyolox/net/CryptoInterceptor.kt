package com.tencent.ncnnyolox.net

import com.blankj.utilcode.util.EncodeUtils
import com.blankj.utilcode.util.EncryptUtils
import com.tencent.ncnnyolox.SystemClock
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.HashSet


/**
 * 接口加解密拦截器
 * @author wfq
 * created at 2019/9/24
 */
class CryptoInterceptor : Interceptor {


    override fun intercept(chain: Interceptor.Chain): Response {
        val oldRequest = chain.request()
        val httpUrl = oldRequest.url

        // 判断是否属于加密请求，否则直接调用返回
        val domainUrl = domainUrlList.find {
            it.contains(httpUrl.host)
        } ?: return chain.proceed(oldRequest)

        val requestTime = SystemClock.now().toString()

        val requestBuilder = oldRequest.newBuilder()
        val path = if (oldRequest.header("alias") != "true") {
            // URL path做MD5加密
            val pathMd5 = EncryptUtils.encryptMD5ToString(httpUrl.encodedPath).toLowerCase()
            // 使用加密后的URL
            requestBuilder.url(domainUrl + pathMd5)
            pathMd5
        } else {
            httpUrl.encodedPath.substring(1) // 去除开始的‘/’
        }

        // 加密请求体
        val newRequestBody = oldRequest.body?.let {
            val charset = it.contentType()?.charset() ?: Charset.defaultCharset()
            val buffer = Buffer()
            it.writeTo(buffer)
            // 获取请求参数字符串
            val content = buffer.readString(charset)
            // 加密参数并包装
            val contentWrapper = getRequestData(path, content, requestTime)
            // 返回新的 RequestBody
            contentWrapper.toRequestBody(it.contentType())
        }

        newRequestBody?.apply {
            when (oldRequest.method) {
                "PUT" -> requestBuilder.put(this)
                "POST" -> requestBuilder.post(this)
                "PATCH" -> requestBuilder.patch(this)
                "DELETE" -> requestBuilder.delete(this)
            }
        }

        // 执行接口请求
        val oldResponse: Response = chain.proceed(requestBuilder.build())

        val responseBuilder = oldResponse.newBuilder()
        if (oldResponse.isSuccessful) {
            // 解密请求结果
            val newResponseBody = oldResponse.body?.let {
                val encryptedContent = it.string()
                // 解密
                val content = decryptResult(encryptedContent, requestTime)
                // 返回新的ResponseBody
                ResponseBody.create(it.contentType(), content)
            }
            newResponseBody?.apply {
                responseBuilder.body(this)
            }
        }

        return responseBuilder.build()
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

    companion object {

        const val noEncryptUrl = "alias: true"

        /**
         * 需要加密的URL前缀管理列表
         */
        @JvmField
        val domainUrlList = HashSet<String>()

        @JvmStatic
        private fun getUUID(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }

        @JvmStatic
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

        @JvmStatic
        private fun cutKey(content: String): String {
            var contentTemp = content
            do {
                contentTemp += "0"
            } while (contentTemp.length < 27)
            return contentTemp.substring(11, 27)
        }
    }
}