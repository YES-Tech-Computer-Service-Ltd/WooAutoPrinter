package com.example.wooauto.data.remote.api

import com.example.wooauto.data.remote.models.CategoryResponse
import com.example.wooauto.data.remote.models.ProductResponse
import com.example.wooauto.data.remote.models.OrderResponse
import retrofit2.http.*

/**
 * WooCommerce API服务接口
 * 定义了与WooCommerce REST API的所有交互方法
 */
interface WooCommerceApiService {

    




    /**
     * 获取所有产品
     * @param page 页码
     * @param perPage 每页数量
     * @return 产品列表
     */
    @GET("products")
    suspend fun getProducts(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 20,
        @QueryMap params: Map<String, String> = emptyMap()

    ): List<ProductResponse>

    /**
     * 根据ID获取产品
     * @param id 产品ID
     * @return 产品详情
     */
    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: Long): ProductResponse

    /**
     * 获取所有订单
     * @param page 页码
     * @param perPage 每页数量
     * @return 订单列表
     */
    @GET("orders")
    suspend fun getOrders(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 20,
        @QueryMap params: Map<String, String> = emptyMap()
    ): List<OrderResponse>

    /**
     * 根据ID获取订单
     * @param id 订单ID
     * @return 订单详情
     */
    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: Long): OrderResponse

    /**
     * 更新订单状态
     * @param id 订单ID
     * @param status 新状态
     * @return 更新后的订单
     */
    @POST("orders/{id}")
    suspend fun updateOrderStatus(
        @Path("id") id: Long,
        @Body status: Map<String, String>
    ): OrderResponse

    /**
     * 根据类别获取产品
     * @param category 类别ID
     * @param page 页码
     * @param perPage 每页数量
     * @return 产品列表
     */
    @GET("products")
    suspend fun getProductsByCategory(
        @Query("category") category: Int,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 20
    ): List<ProductResponse>

    // 在WooCommerceApiService接口中添加以下方法
    @GET("products/categories")
    suspend fun getCategories(): List<CategoryResponse>

    @PATCH("products/{id}")
    suspend fun updateProduct(
        @Path("id") id: Long,
        @Body updates: Map<String, Any>
    ): ProductResponse
} 