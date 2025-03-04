package com.example.wooauto.data.remote

/**
 * 定义API错误类型
 */
sealed class ApiError(
    val code: Int, 
    override val message: String
) : Exception(message) {
    
    // 认证错误
    class AuthenticationError(message: String = "认证失败 (401)，请检查消费者密钥和密钥") : 
        ApiError(401, message)
    
    // 授权错误
    class AuthorizationError(message: String = "授权失败 (403)，没有权限访问此资源") : 
        ApiError(403, message)
    
    // 资源未找到错误
    class NotFoundError(message: String = "资源不存在 (404)，请检查API端点和参数") : 
        ApiError(404, message)
    
    // 服务器错误
    class ServerError(message: String = "服务器错误 (5xx)，请稍后重试") : 
        ApiError(500, message)
    
    // 网络连接错误
    class NetworkError(message: String = "网络连接失败，请检查网络连接") : 
        ApiError(0, message)
    
    // 请求超时错误
    class TimeoutError(message: String = "请求超时，请稍后重试") : 
        ApiError(0, message)
    
    // 请求格式错误
    class BadRequestError(message: String = "请求格式错误 (400)，请检查参数") : 
        ApiError(400, message)
    
    // 未知错误
    class UnknownError(code: Int, message: String = "未知错误 (代码: $code)") : 
        ApiError(code, message)
    
    companion object {
        /**
         * 根据HTTP状态码创建相应的错误类型
         */
        fun fromHttpCode(code: Int, message: String? = null): ApiError {
            return when (code) {
                400 -> BadRequestError(message ?: "请求格式错误 (400)，请检查参数")
                401 -> AuthenticationError(message ?: "认证失败 (401)，请检查消费者密钥和密钥")
                403 -> AuthorizationError(message ?: "授权失败 (403)，没有权限访问此资源")
                404 -> NotFoundError(message ?: "资源不存在 (404)，请检查API端点和参数")
                in 500..599 -> ServerError(message ?: "服务器错误 ($code)，请稍后重试")
                else -> UnknownError(code, message ?: "未知错误 (代码: $code)")
            }
        }
        
        /**
         * 从异常创建错误类型
         */
        fun fromException(e: Exception): ApiError {
            return when {
                e is ApiError -> e
                e.message?.contains("timeout") == true -> TimeoutError(e.message ?: "请求超时，请稍后重试")
                e.message?.contains("network") == true || 
                e.message?.contains("connect") == true ||
                e.message?.contains("connection") == true -> NetworkError(e.message ?: "网络连接失败，请检查网络连接")
                else -> UnknownError(0, e.message ?: "未知错误")
            }
        }
    }
} 