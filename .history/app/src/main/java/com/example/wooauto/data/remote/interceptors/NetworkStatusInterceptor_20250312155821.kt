package com.example.wooauto.data.remote.interceptors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * 网络状态拦截器
 * 检测网络状态并据此优化请求策略
 */
class NetworkStatusInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {
    
    companion object {
        private const val TAG = "NetworkStatusInterceptor"
    }
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isConnected()) {
            Log.e(TAG, "网络未连接，请求可能会失败")
            throw IOException("网络未连接，请检查网络设置")
        }
        
        // 检查网络类型
        val networkInfo = getNetworkInfo()
        val originalRequest = chain.request()
        
        // 根据网络类型调整请求
        val request = when (networkInfo.type) {
            NetworkType.WIFI -> {
                // Wi-Fi网络，使用正常超时
                Log.d(TAG, "当前使用Wi-Fi网络，使用标准请求策略")
                originalRequest
            }
            NetworkType.MOBILE -> {
                // 移动网络，延长超时
                Log.d(TAG, "当前使用移动网络，调整请求策略以优化性能")
                originalRequest.newBuilder()
                    .header("X-Network-Type", "mobile")
                    .build()
            }
            NetworkType.OTHER -> {
                // 其他网络（如以太网），使用正常超时
                Log.d(TAG, "当前使用其他类型网络")
                originalRequest
            }
            NetworkType.NONE -> {
                // 没有网络，应该不会到这里，因为我们已经在前面检查过
                Log.e(TAG, "无网络连接，请求将失败")
                throw IOException("网络未连接")
            }
        }
        
        // 添加网络信息
        val enhancedRequest = request.newBuilder()
            .header("X-Network-Quality", networkInfo.quality.toString())
            .build()
        
        return try {
            chain.proceed(enhancedRequest)
        } catch (e: IOException) {
            Log.e(TAG, "网络请求失败: ${e.message}, 网络类型: ${networkInfo.type}")
            throw e
        }
    }
    
    /**
     * 检查网络是否连接
     */
    private fun isConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * 获取网络信息
     */
    private fun getNetworkInfo(): NetworkInfo {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return NetworkInfo(NetworkType.NONE, NetworkQuality.POOR)
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkInfo(NetworkType.NONE, NetworkQuality.POOR)
        
        // 确定网络类型
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            else -> NetworkType.OTHER
        }
        
        // 确定网络质量
        val quality = when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkQuality.EXCELLENT
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkQuality.GOOD
            else -> NetworkQuality.FAIR
        }
        
        return NetworkInfo(type, quality)
    }
    
    /**
     * 网络类型
     */
    enum class NetworkType {
        WIFI, MOBILE, OTHER, NONE
    }
    
    /**
     * 网络质量
     */
    enum class NetworkQuality {
        EXCELLENT, GOOD, FAIR, POOR
    }
    
    /**
     * 网络信息
     */
    data class NetworkInfo(val type: NetworkType, val quality: NetworkQuality)
} 