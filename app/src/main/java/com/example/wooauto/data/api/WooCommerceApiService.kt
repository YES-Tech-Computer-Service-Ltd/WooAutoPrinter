package com.example.wooauto.data.api

import com.example.wooauto.data.api.models.Order
import com.example.wooauto.data.api.models.Product
import com.example.wooauto.data.api.requests.OrderUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface WooCommerceApiService {

    // Orders
    @GET("orders")
    suspend fun getOrders(
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String,
        @Query("status") status: String? = null,
        @Query("after") after: String? = null,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("order") order: String = "desc",
        @Query("orderby") orderBy: String = "date"
    ): Response<List<Order>>

    @GET("orders/{id}")
    suspend fun getOrder(
        @Path("id") orderId: Long,
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String
    ): Response<Order>

    @PUT("orders/{id}")
    suspend fun updateOrder(
        @Path("id") orderId: Long,
        @Body orderUpdateRequest: OrderUpdateRequest,
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String
    ): Response<Order>

    // Products
    @GET("products")
    suspend fun getProducts(
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String,
        @Query("category") categoryId: Long? = null,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("order") order: String = "desc",
        @Query("orderby") orderBy: String = "date"
    ): Response<List<Product>>

    @GET("products/{id}")
    suspend fun getProduct(
        @Path("id") productId: Long,
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String
    ): Response<Product>

    @PUT("products/{id}")
    suspend fun updateProduct(
        @Path("id") productId: Long,
        @Body product: Product,
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String
    ): Response<Product>

    // Categories
    @GET("products/categories")
    suspend fun getCategories(
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<com.example.wooauto.data.api.models.Category>>
}