package com.wooauto.domain.repositories

import com.wooauto.domain.models.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository_domain {
    //获取类型
    // 获取所有产品
    fun getAllProductsFlow(): Flow<List<Product>>

    // 根据分类获取产品
    fun getProductsByCategoryFlow(categoryId: Long): Flow<List<Product>>

    // 搜索产品
    fun searchProductsFlow(query: String): Flow<List<Product>>

    // 根据ID获取产品
    suspend fun getProductById(productId: Long): Product?

    // 刷新产品列表（同步API数据到本地）
    suspend fun refreshProducts(categoryId: Long? = null): Result<List<Product>>

    // 获取单个产品详情
    suspend fun getProduct(productId: Long): Result<Product>

    // 获取所有产品分类
    suspend fun getAllCategories(): List<Pair<Long, String>>




    //更新类型
    // 更新产品
    suspend fun updateProduct(product: Product): Result<Product>

    // 更新产品库存
    suspend fun updateProductStock(productId: Long, newStockQuantity: Int?): Result<Unit>

    // 更新产品价格
    suspend fun updateProductPrices(productId: Long, regularPrice: String, salePrice: String): Result<Unit>


}
