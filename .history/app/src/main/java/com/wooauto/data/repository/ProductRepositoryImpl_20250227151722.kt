package com.wooauto.data.repository

import com.wooauto.data.local.dao.ProductDao
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.domain.models.Product
import com.wooauto.domain.repositories.ProductRepository
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
) : ProductRepository {

    /**
     * 获取所有产品
     * @return 产品列表流
     */
    override fun getAllProducts(): Flow<List<Product>> {
        return productDao.getAllProducts().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * 根据ID获取产品
     * @param productId 产品ID
     * @return 产品对象
     */
    override suspend fun getProductById(productId: Long): Product? {
        return productDao.getProductById(productId)?.toDomainModel()
    }

    /**
     * 从远程API刷新产品
     * @param page 页码
     */
    override suspend fun refreshProducts(page: Int) {
        val products = apiService.getProducts(page)
        products.forEach { productResponse ->
            productDao.insertProduct(productResponse.toEntity())
        }
    }

    /**
     * 根据类别获取产品
     * @param category 类别ID
     * @return 产品列表流
     */
    override fun getProductsByCategory(category: String): Flow<List<Product>> {
        return productDao.getProductsByCategory(category).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * 更新产品库存
     * @param productId 产品ID
     * @param quantity 新库存数量
     */
    override suspend fun updateProductStock(productId: Long, quantity: Int) {
        val product = productDao.getProductById(productId)
        product?.let {
            productDao.updateProduct(it.copy(stockQuantity = quantity))
        }
    }
} 