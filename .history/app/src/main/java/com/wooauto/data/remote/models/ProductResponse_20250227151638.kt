package com.wooauto.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * 产品响应模型
 * 用于解析WooCommerce API返回的产品数据
 */
data class ProductResponse(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("price")
    val price: String,

    @SerializedName("regular_price")
    val regularPrice: String,

    @SerializedName("sale_price")
    val salePrice: String?,

    @SerializedName("stock_status")
    val stockStatus: String,

    @SerializedName("stock_quantity")
    val stockQuantity: Int?,

    @SerializedName("categories")
    val categories: List<CategoryResponse>,

    @SerializedName("images")
    val images: List<ImageResponse>,

    @SerializedName("attributes")
    val attributes: List<AttributeResponse>?,

    @SerializedName("variations")
    val variations: List<Long>?
)

/**
 * 产品类别响应模型
 */
data class CategoryResponse(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("slug")
    val slug: String
)

/**
 * 产品图片响应模型
 */
data class ImageResponse(
    @SerializedName("id")
    val id: Long,

    @SerializedName("src")
    val src: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("alt")
    val alt: String?
)

/**
 * 产品属性响应模型
 */
data class AttributeResponse(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("options")
    val options: List<String>,

    @SerializedName("variation")
    val variation: Boolean
) 