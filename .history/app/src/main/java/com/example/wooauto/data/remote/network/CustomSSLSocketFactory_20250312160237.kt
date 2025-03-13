package com.example.wooauto.data.remote.network

import android.os.Build
import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * 自定义SSL套接字工厂
 * 用于解决TLS握手问题
 */
class CustomSSLSocketFactory private constructor(
    private val delegate: SSLSocketFactory,
    private val trustManagers: Array<TrustManager>
) : SSLSocketFactory() {

    companion object {
        private const val TAG = "CustomSSLSocketFactory"
        
        /**
         * 创建自定义SSL套接字工厂实例
         * @return 套接字工厂和信任管理器的对
         */
        fun create(): Pair<SSLSocketFactory, X509TrustManager> {
            try {
                // 创建一个信任所有证书的TrustManager
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                
                // 使用TLS 1.2
                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                
                val delegate = sslContext.socketFactory
                return Pair(
                    CustomSSLSocketFactory(delegate, trustAllCerts),
                    trustAllCerts[0] as X509TrustManager
                )
            } catch (e: Exception) {
                Log.e(TAG, "创建自定义SSL工厂失败: ${e.message}", e)
                // 退回到系统默认工厂
                val trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
                )
                trustManagerFactory.init(null as KeyStore?)
                val trustManagers = trustManagerFactory.trustManagers
                
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustManagers, java.security.SecureRandom())
                
                return Pair(
                    sslContext.socketFactory,
                    trustManagers[0] as X509TrustManager
                )
            }
        }
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return delegate.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        val sslSocket = delegate.createSocket(socket, host, port, autoClose) as SSLSocket
        return optimizeSocket(sslSocket)
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int): Socket {
        val sslSocket = delegate.createSocket(host, port) as SSLSocket
        return optimizeSocket(sslSocket)
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        val sslSocket = delegate.createSocket(host, port, localHost, localPort) as SSLSocket
        return optimizeSocket(sslSocket)
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        val sslSocket = delegate.createSocket(host, port) as SSLSocket
        return optimizeSocket(sslSocket)
    }

    @Throws(IOException::class)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        val sslSocket = delegate.createSocket(address, port, localAddress, localPort) as SSLSocket
        return optimizeSocket(sslSocket)
    }
    
    /**
     * 优化SSL套接字，设置适当的TLS版本和密码套件
     */
    private fun optimizeSocket(sslSocket: SSLSocket): SSLSocket {
        try {
            // 记录原始配置
            Log.d(TAG, "优化套接字配置，设备API级别: ${Build.VERSION.SDK_INT}")
            
            // 启用TLS 1.2，禁用其他不兼容协议
            val protocols = arrayOf("TLSv1.2")
            sslSocket.enabledProtocols = protocols
            
            // 设置适当的密码套件
            val cipherSuites = getCipherSuites(sslSocket)
            if (cipherSuites.isNotEmpty()) {
                sslSocket.enabledCipherSuites = cipherSuites
            }
            
            // 设置较短的超时时间以避免挂起
            sslSocket.soTimeout = 30000 // 30秒
            
            Log.d(TAG, "套接字配置完成，启用的协议: ${sslSocket.enabledProtocols.joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "优化套接字时出错: ${e.message}")
        }
        
        return sslSocket
    }
    
    /**
     * 获取适当的密码套件
     */
    private fun getCipherSuites(sslSocket: SSLSocket): Array<String> {
        // 建议的安全密码套件
        val recommendedCiphers = arrayOf(
            // 非常安全的现代密码套件
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            // 兼容的套件，仍然相当安全
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            // 旧版本兼容，保持基本安全性
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"
        )
        
        // 获取此套接字支持的所有密码套件
        val supportedCiphers = sslSocket.supportedCipherSuites
        
        // 找出哪些推荐的密码套件被支持
        return supportedCiphers.filter { cipher ->
            recommendedCiphers.contains(cipher)
        }.toTypedArray()
    }
} 