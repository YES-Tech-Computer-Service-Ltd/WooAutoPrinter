package com.wooauto.domain.repositories

import com.wooauto.domain.models.Product
import kotlinx.coroutines.flow.Flow

interface DomainProductRepository {
    fun getAllProductsFlow(): Flow<List<Product>>
    fun getProductsByCategoryFlow(categoryId: Long): Flow<List<Product>>
    fun searchProductsFlow(query: String): Flow<List<Product>>
    suspend fun getProductById(productId: Long): Product?
    suspend fun refreshProducts(categoryId: Long?): Result<List<Product>>
    suspend fun getProduct(productId: Long): Result<Product>
    suspend fun getAllCategories(): List<Pair<Long, String>>
    suspend fun updateProduct(product: Product): Result<Product>
    suspend fun updateProductStock(productId: Long, newStockQuantity: Int?): Result<Unit>
    suspend fun updateProductPrices(productId: Long, regularPrice: String, salePrice: String): Result<Unit>
} 