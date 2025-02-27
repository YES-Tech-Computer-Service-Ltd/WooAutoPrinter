package com.wooauto.data.repository

import com.wooauto.data.local.dao.ProductDao
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.domain.models.Product
import com.wooauto.domain.repositories.ProductRepository_domain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 产品仓库实现类
 * 实现了领域层定义的ProductRepository接口
 */
@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val apiService: WooCommerceApiService
) : ProductRepository_domain {

    /**
     * 获取所有产品的Flow
     */
    override fun getAllProductsFlow(): Flow<List<Product>> {
        return productDao.getAllProducts().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * 根据类别获取产品的Flow
     */
    override fun getProductsByCategoryFlow(category: String): Flow<List<Product>> {
        return productDao.getProductsByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * 根据ID获取产品
     */
    override suspend fun getProductById(productId: Long): Product? {
        return productDao.getProductById(productId)?.toDomainModel()
    }

    /**
     * 刷新产品列表
     */
    override suspend fun refreshProducts(category: String?): Result<List<Product>> {
        return try {
            val params = mutableMapOf<String, String>()
            category?.let { params["category"] = it }
            
            val response = apiService.getProducts(1, params = params)
            response.forEach { productResponse ->
                productDao.insertProduct(productResponse.toEntity())
            }
            
            Result.success(response.map { it.toEntity().toDomainModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 更新产品库存
     */
    override suspend fun updateProductStock(productId: Long, quantity: Int): Result<Product> {
        return try {
            val product = productDao.getProductById(productId) ?: throw IllegalArgumentException("Product not found")
            val updatedProduct = product.copy(stockQuantity = quantity)
            productDao.updateProduct(updatedProduct)
            Result.success(updatedProduct.toDomainModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 