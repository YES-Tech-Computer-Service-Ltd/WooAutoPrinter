package com.example.wooauto.data.mappers

import com.example.wooauto.data.remote.models.AttributeResponse
import com.example.wooauto.data.remote.models.ProductResponse
import com.example.wooauto.data.local.entities.ProductAttributeEntity
import com.example.wooauto.data.local.entities.ProductEntity
import com.example.wooauto.domain.models.Category
import com.example.wooauto.domain.models.Dimensions
import com.example.wooauto.domain.models.Image
import com.example.wooauto.domain.models.Product

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
            attributes = response.attributes?.map { mapAttributeResponseToEntity(it) }, //通过添加映射函数解决
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
            categories = listOf(
                Category(
                    id = 0, 
                    name = entity.category, 
                    slug = entity.category.lowercase().replace(" ", "-"),
                    parent = 0,
                    description = "",
                    count = 0
                )
            ),
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

    private fun mapAttributeResponseToEntity(response: AttributeResponse): ProductAttributeEntity {
        return ProductAttributeEntity(
            id = response.id,
            name = response.name,
            options = response.options,
            variation = response.variation
        )
    }

    /**
     * 将API响应模型直接转换为领域模型（跳过实体）
     * @param response API响应的产品模型
     * @return 领域产品模型
     */
    fun mapResponseToDomain(response: ProductResponse): Product {
        return Product(
            id = response.id,
            name = response.name,
            slug = response.slug ?: response.name.lowercase().replace(" ", "-"),
            permalink = response.permalink ?: "",
            dateCreated = response.dateCreated ?: "",
            status = response.status ?: "publish",
            featured = response.featured ?: false,
            catalogVisibility = response.catalogVisibility ?: "visible",
            description = response.description,
            shortDescription = response.shortDescription ?: response.description.take(100),
            sku = response.sku ?: "",
            price = response.price,
            regularPrice = response.regularPrice,
            salePrice = response.salePrice ?: "",
            onSale = response.onSale ?: (!response.salePrice.isNullOrEmpty() && response.salePrice != "0"),
            purchasable = response.purchasable ?: true,
            totalSales = response.totalSales ?: 0,
            stockQuantity = response.stockQuantity,
            stockStatus = response.stockStatus,
            backorders = response.backorders ?: "no",
            backordersAllowed = response.backordersAllowed ?: false,
            backordered = response.backordered ?: false,
            soldIndividually = response.soldIndividually ?: false,
            weight = response.weight ?: "",
            dimensions = Dimensions(
                length = response.dimensions?.length ?: "",
                width = response.dimensions?.width ?: "",
                height = response.dimensions?.height ?: ""
            ),
            categories = response.categories.map { category ->
                Category(
                    id = category.id,
                    name = category.name,
                    slug = category.slug,
                    parent = category.parent ?: 0,
                    description = category.description ?: "",
                    count = category.count ?: 0
                )
            },
            images = response.images.map { image ->
                Image(
                    id = image.id,
                    dateCreated = image.dateCreated ?: "",
                    alt = image.alt ?: "",
                    src = image.src,
                    name = image.name ?: "",
                    title = image.title ?: ""
                )
            }
        )
    }

    /**
     * 将API响应列表转换为领域模型列表
     * @param responses API响应的产品模型列表
     * @return 领域产品模型列表
     */
    fun mapResponsesListToDomainList(responses: List<ProductResponse>): List<Product> {
        return responses.map { mapResponseToDomain(it) }
    }
}