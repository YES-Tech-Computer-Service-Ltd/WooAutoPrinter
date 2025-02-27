package com.wooauto.domain.usecases.products

import com.wooauto.domain.models.Product
import com.wooauto.domain.repositories.ProductRepository_domain
import kotlinx.coroutines.flow.Flow

/**
 * 获取产品列表用例
 *
 * 该用例负责处理与获取产品列表相关的所有业务逻辑，包括：
 * - 获取所有产品
 * - 按分类获取产品
 * - 搜索产品
 * - 刷新产品列表
 * - 获取产品分类
 */
class GetProductsUseCase(
    private val productRepository: ProductRepository_domain
) {
    /**
     * 获取所有产品的Flow
     * @return 包含所有产品列表的Flow
     */
    fun getAllProducts(): Flow<List<Product>> {
        return productRepository.getAllProductsFlow()
    }

    /**
     * 根据分类获取产品的Flow
     * @param categoryId 分类ID
     * @return 指定分类下的产品列表Flow
     */
    fun getProductsByCategory(categoryId: Long): Flow<List<Product>> {
        return productRepository.getProductsByCategoryFlow(categoryId)
    }

    /**
     * 搜索产品
     * @param query 搜索关键词
     * @return 符合搜索条件的产品列表Flow
     */
    fun searchProducts(query: String): Flow<List<Product>> {
        return productRepository.searchProductsFlow(query)
    }

    /**
     * 刷新产品列表
     * @param categoryId 可选的分类ID过滤
     * @return 刷新结果，成功返回产品列表，失败返回错误信息
     */
    suspend fun refreshProducts(categoryId: Long? = null): Result<List<Product>> {
        return productRepository.refreshProducts(categoryId)
    }

    /**
     * 获取所有产品分类
     * @return 分类列表，每个元素为 分类ID 到 分类名称 的映射
     */
    suspend fun getAllCategories(): List<Pair<Long, String>> {
        return productRepository.getAllCategories()
    }
} 