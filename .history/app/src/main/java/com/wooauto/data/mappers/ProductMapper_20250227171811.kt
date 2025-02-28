package com.wooauto.data.mappers

import com.wooauto.data.local.entities.ProductEntity
import com.wooauto.data.remote.models.ProductResponse
import com.wooauto.domain.models.Category
import com.wooauto.domain.models.Dimensions
import com.wooauto.domain.models.Image
import com.wooauto.domain.models.Product

/**
 * 产品数据映射器
 * 负责在不同层的产品模型之间进行转换
 */
object ProductMapper {

    /**
     * 将远程API响应模型转换为本地数据库实体
     * @param response API响应的产品模型
     * @return 数据库产品实体
     */
    fun mapResponseToEntity(response: ProductResponse): ProductEntity {
        return ProductEntity(
            id = response.id,
            name = response.name,
            description = response.description,
            price = response.price,
            regularPrice = response.regularPrice,
            salePrice = response.salePrice,
            stockStatus = response.stockStatus,
            stockQuantity = response.stockQuantity,
            category = response.categories.firstOrNull()?.name ?: "",
            images = response.images.map { it.src },
            attributes = response.attributes,
            variations = response.variations
        )
    }

    /**
     * 将本地数据库实体转换为领域模型
     * @param entity 数据库产品实体
     * @return 领域产品模型
     */
    fun mapEntityToDomain(entity: ProductEntity): Product {
        return Product(
            id = entity.id,
            name = entity.name,
            slug = entity.name.lowercase().replace(" ", "-"),
            permalink = "",
            dateCreated = "",
            status = entity.stockStatus,
            featured = false,
            catalogVisibility = "visible",
            description = entity.description,
            shortDescription = entity.description.take(100),
            sku = "",
            price = entity.price,
            regularPrice = entity.regularPrice,
            salePrice = entity.salePrice ?: "",
            onSale = !entity.salePrice.isNullOrEmpty(),
            purchasable = entity.stockStatus == "instock",
            totalSales = 0,
            stockQuantity = entity.stockQuantity,
            stockStatus = entity.stockStatus,
            backorders = "no",
            backordersAllowed = false,
            backordered = false,
            soldIndividually = false,
            weight = "",
            dimensions = Dimensions("", "", ""),
            categories = listOf(Category(0, entity.category, "")),
            images = entity.images.map { Image(0, "", "", it, "", "") }
        )
    }

    /**
     * 将领域模型转换为本地数据库实体
     * @param domain 领域产品模型
     * @return 数据库产品实体
     */
    fun mapDomainToEntity(domain: Product): ProductEntity {
        return ProductEntity(
            id = domain.id,
            name = domain.name,
            description = domain.description,
            price = domain.price,
            regularPrice = domain.regularPrice,
            salePrice = domain.salePrice.takeIf { it.isNotEmpty() },
            stockStatus = domain.stockStatus,
            stockQuantity = domain.stockQuantity,
            category = domain.categories.firstOrNull()?.name ?: "",
            images = domain.images.map { it.src },
            attributes = null, // 由于领域模型中的属性结构可能与数据库不同，需要进一步处理
            variations = null  // 由于领域模型中的变体结构可能与数据库不同，需要进一步处理
        )
    }

    /**
     * 将远程API响应模型列表转换为本地数据库实体列表
     * @param responses API响应的产品模型列表
     * @return 数据库产品实体列表
     */
    fun mapResponsesListToEntitiesList(responses: List<ProductResponse>): List<ProductEntity> {
        return responses.map { mapResponseToEntity(it) }
    }

    /**
     * 将本地数据库实体列表转换为领域模型列表
     * @param entities 数据库产品实体列表
     * @return 领域产品模型列表
     */
    fun mapEntitiesListToDomainList(entities: List<ProductEntity>): List<Product> {
        return entities.map { mapEntityToDomain(it) }
    }
}