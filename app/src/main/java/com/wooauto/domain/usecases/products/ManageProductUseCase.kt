package com.wooauto.domain.usecases.products

import com.wooauto.domain.models.Product
import com.wooauto.domain.repositories.DomainProductRepository

/**
 * 产品管理用例
 *
 * 该用例负责处理与产品管理相关的业务逻辑，包括：
 * - 获取单个产品详情（根据指定id）
 * - 更新产品信息（根据指定id）
 * - 更新产品库存（根据指定id）
 * - 更新产品价格（根据指定id）
 */
class ManageProductUseCase(
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
     * 更新产品信息（根据指定id）
     * @param product 更新后的产品信息
     * @return 更新结果，成功返回更新后的产品，失败返回错误信息
     */
    suspend fun updateProduct(product: Product): Result<Product> {
        return productRepository.updateProduct(product)
    }

    /**
     * 更新产品库存
     * @param productId 产品ID
     * @param newStockQuantity 新的库存数量，null表示无限库存
     * @return 更新结果，成功返回Unit，失败返回错误信息
     */
    suspend fun updateProductStock(productId: Long, newStockQuantity: Int?): Result<Unit> {
        return productRepository.updateProductStock(productId, newStockQuantity)
    }

    /**
     * 更新产品价格（根据指定id）
     * @param productId 产品ID
     * @param regularPrice 常规价格
     * @param salePrice 促销价格
     * @return 更新结果，成功返回Unit，失败返回错误信息
     */
    suspend fun updateProductPrices(
        productId: Long,
        regularPrice: String,
        salePrice: String
    ): Result<Unit> {
        return productRepository.updateProductPrices(productId, regularPrice, salePrice)
    }
} 