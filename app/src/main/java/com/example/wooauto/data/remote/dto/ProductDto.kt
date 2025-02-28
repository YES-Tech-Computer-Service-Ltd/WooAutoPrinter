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

data class ImageDto(
    val id: Long,
    val src: String
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
            id = img.id,
            dateCreated = "",
            dateModified = "",
            src = img.src,
            name = "",
            alt = ""
        )
    }
    
    return Product(
        id = id,
        name = name,
        slug = name.lowercase().replace(" ", "-"),  // 创建默认的slug
        permalink = permalink,
        dateCreated = "",  // 默认空字符串
        status = status,
        featured = false,  // 默认为非特色
        catalogVisibility = "visible",  // 默认可见
        description = description,
        shortDescription = shortDescription,
        sku = sku,
        price = price,
        regularPrice = regularPrice,
        salePrice = salePrice,
        onSale = onSale,
        purchasable = true,  // 默认可购买
        totalSales = 0,  // 默认销售量为0
        stockQuantity = stockQuantity ?: 0,
        stockStatus = stockStatus,
        backorders = "no",  // 默认不允许后补订单
        backordersAllowed = false,
        backordered = false,
        soldIndividually = false,
        weight = "",  // 默认空重量
        dimensions = defaultDimensions,
        categories = categories?.map { it.toCategory() } ?: emptyList(),
        images = imageModels
    )
} 