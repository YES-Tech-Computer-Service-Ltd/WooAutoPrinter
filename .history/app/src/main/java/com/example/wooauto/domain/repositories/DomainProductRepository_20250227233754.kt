package com.example.wooauto.domain.repositories

import com.example.wooauto.domain.models.Product
import kotlinx.coroutines.flow.Flow

/**
 * 产品仓库接口
 * 定义了与产品相关的所有数据操作
 */
interface DomainProductRepository {
    /**
     * 获取所有产品
     * @return 包含所有产品的数据流
     */
    fun getAllProductsFlow(): Flow<List<Product>>

    /**
     * 根据分类获取产品
     * @param categoryId 分类ID
     * @return 指定分类的产品数据流
     */
    fun getProductsByCategoryFlow(categoryId: Long): Flow<List<Product>>

    /**
     * 搜索产品
     * @param query 搜索关键词
     * @return 符合搜索条件的产品数据流
     */
    fun searchProductsFlow(query: String): Flow<List<Product>>

    /**
     * 根据ID获取产品
     * @param productId 产品ID
     * @return 产品对象，如果不存在则返回null
     */
    suspend fun getProductById(productId: Long): Product?

    /**
     * 刷新产品列表
     * @param categoryId 可选的分类ID过滤条件
     * @return 刷新结果，包含刷新的产品列表
     */
    suspend fun refreshProducts(categoryId: Long? = null): Result<List<Product>>

    /**
     * 获取产品详情
     * @param productId 产品ID
     * @return 获取结果，包含产品详细信息
     */
    suspend fun getProduct(productId: Long): Result<Product>

    /**
     * 获取所有产品分类
     * @return 分类列表，每项包含分类ID和名称
     */
    suspend fun getAllCategories(): List<Pair<Long, String>>

    /**
     * 更新产品
     * @param product 更新后的产品信息
     * @return 更新结果，包含更新后的产品
     */
    suspend fun updateProduct(product: Product): Result<Product>

    /**
     * 更新产品库存
     * @param productId 产品ID
     * @param newStockQuantity 新的库存数量
     * @return 更新结果
     */
    suspend fun updateProductStock(productId: Long, newStockQuantity: Int?): Result<Unit>

    /**
     * 更新产品价格
     * @param productId 产品ID
     * @param regularPrice 常规价格
     * @param salePrice 促销价格
     * @return 更新结果
     */
    suspend fun updateProductPrices(productId: Long, regularPrice: String, salePrice: String): Result<Unit>
} 