package com.example.wooauto.data.mappers

import com.example.wooauto.data.remote.models.AttributeResponse
import com.example.wooauto.data.remote.models.ProductResponse
import com.example.wooauto.data.local.entities.ProductAttributeEntity
import com.example.wooauto.data.local.entities.ProductEntity
import com.example.wooauto.data.local.entities.CategoryEntity
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
        // 转换分类
        val categories = response.categories.map { cat ->
            CategoryEntity(
                id = cat.id,
                name = cat.name,
                slug = cat.slug,
                parent = 0,
                description = "",
                count = 0
            )
        }
        
        // 转换属性
        val attributes = response.attributes?.map { mapAttributeResponseToEntity(it) } ?: emptyList()
        
        return ProductEntity(
            id = response.id,
            name = response.name,
            sku = response.sku ?: "",
            description = response.description,
            price = response.price,
            regularPrice = response.regularPrice,
            salePrice = response.salePrice ?: "",
            stockStatus = response.stockStatus,
            stockQuantity = response.stockQuantity,
            categories = categories,
            images = response.images.map { it.src },
            attributes = attributes,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * 将本地数据库实体转换为领域模型
     * @param entity 数据库产品实体
     * @return 领域产品模型
     */
    fun mapEntityToDomain(entity: ProductEntity): Product {
        // 转换分类
        val categories = entity.categories.map { cat ->
            Category(
                id = cat.id,
                name = cat.name,
                slug = cat.slug,
                parent = cat.parent,
                description = cat.description,
                count = cat.count
            )
        }
        
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
            sku = entity.sku,
            price = entity.price,
            regularPrice = entity.regularPrice,
            salePrice = entity.salePrice,
            onSale = entity.salePrice.isNotEmpty(),
            purchasable = entity.stockStatus == "instock",
            totalSales = 0,
            stockQuantity = entity.stockQuantity ?: 0,
            stockStatus = entity.stockStatus,
            backorders = "no",
            backordersAllowed = false,
            backordered = false,
            soldIndividually = false,
            weight = "",
            dimensions = Dimensions("", "", ""),
            categories = categories,
            images = entity.images.map { Image(0, "", "", it, "", "") }
        )
    }

    /**
     * 将领域模型转换为本地数据库实体
     * @param domain 领域产品模型
     * @return 数据库产品实体
     */
    fun mapDomainToEntity(domain: Product): ProductEntity {
        // 转换分类
        val categories = domain.categories.map { cat ->
            CategoryEntity(
                id = cat.id,
                name = cat.name,
                slug = cat.slug,
                parent = cat.parent,
                description = cat.description,
                count = cat.count
            )
        }
        
        return ProductEntity(
            id = domain.id,
            name = domain.name,
            sku = domain.sku,
            description = domain.description,
            price = domain.price,
            regularPrice = domain.regularPrice,
            salePrice = domain.salePrice,
            stockStatus = domain.stockStatus,
            stockQuantity = domain.stockQuantity,
            categories = categories,
            images = domain.images.map { it.src },
            attributes = emptyList(),
            lastUpdated = System.currentTimeMillis()
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
            slug = response.name.lowercase().replace(" ", "-"),
            permalink = "",
            dateCreated = "",
            status = response.stockStatus,
            featured = false,
            catalogVisibility = "visible",
            description = response.description,
            shortDescription = response.description.take(100),
            sku = response.sku ?: "",
            price = response.price,
            regularPrice = response.regularPrice,
            salePrice = response.salePrice ?: "",
            onSale = !response.salePrice.isNullOrEmpty() && response.salePrice != "0",
            purchasable = response.stockStatus == "instock",
            totalSales = 0,
            stockQuantity = response.stockQuantity ?: 0,
            stockStatus = response.stockStatus,
            backorders = "no",
            backordersAllowed = false,
            backordered = false,
            soldIndividually = false,
            weight = "",
            dimensions = Dimensions("", "", ""),
            categories = response.categories.map { category ->
                Category(
                    id = category.id,
                    name = category.name,
                    slug = category.slug,
                    parent = 0,
                    description = "",
                    count = 0
                )
            },
            images = response.images.map { image ->
                Image(
                    id = image.id,
                    dateCreated = "",
                    alt = image.alt ?: "",
                    src = image.src,
                    name = image.name,
                    dateModified = ""
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