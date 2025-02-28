package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.Category
import com.example.wooauto.domain.models.Dimensions
import com.example.wooauto.domain.models.Image
import com.example.wooauto.domain.models.Product
import com.google.gson.annotations.SerializedName

data class ProductDto(
    val id: Long,
    val name: String,
    val permalink: String,
    val description: String,
    @SerializedName("short_description")
    val shortDescription: String,
    val sku: String,
    val price: String,
    @SerializedName("regular_price")
    val regularPrice: String,
    @SerializedName("sale_price")
    val salePrice: String,
    @SerializedName("on_sale")
    val onSale: Boolean,
    @SerializedName("stock_status")
    val stockStatus: String,
    @SerializedName("stock_quantity")
    val stockQuantity: Int?,
    @SerializedName("manage_stock")
    val manageStock: Boolean,
    val categories: List<CategoryDto>?,
    val images: List<ImageDto>,
    val status: String
)

// 扩展函数，将DTO转换为领域模型
fun ProductDto.toProduct(): Product {
    // 创建默认尺寸对象
    val defaultDimensions = Dimensions(
        length = "0",
        width = "0",
        height = "0"
    )
    
    // 将图片DTO转换为图片领域模型
    val imageModels = images.map { img ->
        Image(
            id = img.id ?: 0,
            src = img.src ?: ""
        )
    }
    
    // 将类别DTO转换为类别领域模型
    val categoryModels = categories?.map { cat ->
        Category(
            id = cat.id,
            name = cat.name,
            slug = cat.slug
        )
    } ?: emptyList()
    
    // 构建产品模型
    return Product(
        id = id,
        name = name,
        description = description,
        shortDescription = shortDescription,
        permalink = permalink,
        sku = sku,
        price = price,
        regularPrice = regularPrice,
        salePrice = salePrice,
        onSale = onSale,
        stockStatus = stockStatus,
        stockQuantity = stockQuantity ?: 0,
        manageStock = manageStock,
        status = status,
        categories = categoryModels,
        images = imageModels,
        dimensions = defaultDimensions
    )
}

data class CategoryDto(
    val id: Long,
    val name: String,
    val slug: String
) 