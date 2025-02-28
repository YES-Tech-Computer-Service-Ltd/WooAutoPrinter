package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.model.Category
import com.example.wooauto.domain.model.Product
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
    return Product(
        id = id,
        name = name,
        permalink = permalink,
        description = description,
        shortDescription = shortDescription,
        sku = sku,
        price = price,
        regularPrice = regularPrice,
        salePrice = salePrice,
        onSale = onSale,
        stockStatus = stockStatus,
        stockQuantity = stockQuantity ?: 0,
        manageStock = manageStock,
        categories = categories?.map { it.toCategory() } ?: emptyList(),
        images = images.map { it.src },
        status = status
    )
} 