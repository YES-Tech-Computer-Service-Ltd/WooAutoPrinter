package com.example.wooauto.data.remote.interceptors

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import java.security.cert.CertificateException

/**
 * SSL错误拦截器
 * 处理SSL相关的错误并提供详细的日志
 */
class SSLErrorInterceptor @Inject constructor() : Interceptor {
    
    companion object {
        private const val TAG = "SSLErrorInterceptor"
    }
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        try {
            Log.d(TAG, "开始处理请求: ${request.url()}")
            return chain.proceed(request)
        } catch (e: SSLHandshakeException) {
            // SSL握手失败
            Log.e(TAG, "SSL握手失败: ${e.message}", e)
            throw IOException("SSL握手失败，请检查网站证书: ${e.message}", e)
        } catch (e: SSLException) {
            // SSL异常，如Connection reset by peer
            Log.e(TAG, "SSL连接异常: ${e.message}", e)
            
            // 连接重置错误的特殊处理
            if (e.message?.contains("Connection reset by peer") == true) {
                Log.e(TAG, "连接被重置，可能是服务器拒绝了连接或证书问题")
                
                // 添加TLS指纹信息以便分析
                try {
                    val url = request.url().toString()
                    Log.d(TAG, "尝试分析连接问题，URL: $url")
                    
                    // 在这里我们可以尝试使用不同的SSL版本或参数重建请求
                    // 但在拦截器中我们只能报告问题
                } catch (ex: Exception) {
                    Log.e(TAG, "分析连接问题时出错: ${ex.message}")
                }
            }
            
            throw IOException("SSL连接错误: ${e.message}", e)
        } catch (e: CertificateException) {
            // 证书验证失败
            Log.e(TAG, "证书验证失败: ${e.message}", e)
            throw IOException("证书验证失败: ${e.message}", e)
        } catch (e: SocketTimeoutException) {
            // 连接超时
            Log.e(TAG, "连接超时: ${e.message}", e)
            throw IOException("连接超时，请检查网络: ${e.message}", e)
        } catch (e: UnknownHostException) {
            // 无法解析主机名
            Log.e(TAG, "无法解析主机名: ${e.message}", e)
            throw IOException("无法解析主机名，请检查网址或网络连接: ${e.message}", e)
        } catch (e: IOException) {
            // 其他IO异常
            Log.e(TAG, "IO异常: ${e.message}", e)
            throw e
        }
    }
} 