package com.example.wooauto.domain.repository

import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.Category
import com.example.wooauto.domain.models.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    suspend fun getProducts(): List<Product>
    suspend fun getCategories(): List<Category>
    suspend fun updateProduct(product: Product): Product
    suspend fun getProductById(id: Long): Product?
    suspend fun getProductsByCategory(categoryId: Long): List<Product>
    suspend fun searchProducts(query: String): List<Product>
    suspend fun refreshProducts(): Boolean
    suspend fun refreshCategories(): Boolean
    suspend fun testConnection(config: WooCommerceConfig? = null): Boolean
} 