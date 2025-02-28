package com.wooauto.domain.usecases.products

import com.wooauto.domain.models.Product
import com.wooauto.domain.repositories.DomainProductRepository
import javax.inject.Inject

/**
 * 管理产品用例
 * 处理产品管理相关的业务逻辑
 */
class ManageProductUseCase @Inject constructor(
    private val productRepository: DomainProductRepository
) {
    /**
     * 获取单个产品详情（根据指定id）
     * @param productId 产品ID
     * @return 获取结果，成功返回产品信息，失败返回错误信息
     */
    suspend fun getProduct(productId: Long): Result<Product> {
        return productRepository.getProduct(productId)
    }

    /**
     * 更新产品
     * @param product 要更新的产品
     * @return 更新结果
     */
    suspend fun updateProduct(product: Product): Result<Product> {
        return productRepository.updateProduct(product)
    }

    /**
     * 更新产品库存
     * @param productId 产品ID
     * @param newStockQuantity 新库存数量
     * @return 更新结果
     */
    suspend fun updateProductStock(productId: Long, newStockQuantity: Int?): Result<Unit> {
        return productRepository.updateProductStock(productId, newStockQuantity)
    }

    /**
     * 更新产品价格
     * @param productId 产品ID
     * @param regularPrice 常规价格
     * @param salePrice 促销价格
     * @return 更新结果
     */
    suspend fun updateProductPrices(
        productId: Long,
        regularPrice: String,
        salePrice: String
    ): Result<Unit> {
        return productRepository.updateProductPrices(productId, regularPrice, salePrice)
    }
} 