package com.example.wooauto.data.remote.service

import com.example.wooauto.domain.models.Order
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * WooCommerce API服务接口
 */
interface WooCommerceService {
    /**
     * 获取所有订单
     */
    @GET("orders")
    suspend fun getOrders(): Response<List<Order>>
    
    /**
     * 获取特定状态的订单
     */
    @GET("orders")
    suspend fun getOrdersByStatus(@Query("status") status: String): Response<List<Order>>
    
    /**
     * 获取订单详情
     */
    @GET("orders/{id}")
    suspend fun getOrderDetails(@Path("id") orderId: Long): Response<Order>
    
    /**
     * 更新订单状态
     */
    @PUT("orders/{id}")
    suspend fun updateOrderStatus(
        @Path("id") orderId: Long,
        @Query("status") status: String
    ): Response<Order>
} 