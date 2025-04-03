package com.example.wooauto.data.remote

import com.example.wooauto.data.remote.dto.CategoryDto
import com.example.wooauto.data.remote.dto.OrderDto
import com.example.wooauto.data.remote.dto.ProductDto

interface WooCommerceApi {
    suspend fun getProducts(page: Int = 1, perPage: Int = 100): List<ProductDto>
    suspend fun getProduct(id: Long): ProductDto
    suspend fun updateProduct(id: Long, data: Map<String, Any>): ProductDto
    
    suspend fun getCategories(page: Int = 1, perPage: Int = 100): List<CategoryDto>
    suspend fun getCategory(id: Long): CategoryDto
    
    suspend fun getOrders(page: Int = 1, perPage: Int = 100, status: String? = null): List<OrderDto>
    suspend fun getOrdersWithParams(page: Int = 1, perPage: Int = 100, params: Map<String, String>): List<OrderDto>
    suspend fun getOrder(id: Long): OrderDto
    suspend fun updateOrder(id: Long, data: Map<String, Any>): OrderDto
} 