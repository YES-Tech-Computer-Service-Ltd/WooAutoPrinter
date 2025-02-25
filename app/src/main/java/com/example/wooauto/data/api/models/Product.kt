package com.example.wooauto.data.api.models

import com.google.gson.annotations.SerializedName

data class Product(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("slug")
    val slug: String,

    @SerializedName("permalink")
    val permalink: String,

    @SerializedName("date_created")
    val dateCreated: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("featured")
    val featured: Boolean,

    @SerializedName("catalog_visibility")
    val catalogVisibility: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("short_description")
    val shortDescription: String,

    @SerializedName("sku")
    val sku: String,

    @SerializedName("price")
    val price: String,

    @SerializedName("regular_price")
    val regularPrice: String,

    @SerializedName("sale_price")
    val salePrice: String,

    @SerializedName("on_sale")
    val onSale: Boolean,

    @SerializedName("purchasable")
    val purchasable: Boolean,

    @SerializedName("total_sales")
    val totalSales: Int,

    @SerializedName("stock_quantity")
    val stockQuantity: Int?,

    @SerializedName("stock_status")
    val stockStatus: String,

    @SerializedName("backorders")
    val backorders: String,

    @SerializedName("backorders_allowed")
    val backordersAllowed: Boolean,

    @SerializedName("backordered")
    val backordered: Boolean,

    @SerializedName("sold_individually")
    val soldIndividually: Boolean,

    @SerializedName("weight")
    val weight: String,

    @SerializedName("dimensions")
    val dimensions: Dimensions,

    @SerializedName("categories")
    val categories: List<Category>,

    @SerializedName("images")
    val images: List<Image>
)

data class Dimensions(
    @SerializedName("length")
    val length: String,

    @SerializedName("width")
    val width: String,

    @SerializedName("height")
    val height: String
)

data class Category(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("slug")
    val slug: String
)

data class Image(
    @SerializedName("id")
    val id: Long,

    @SerializedName("date_created")
    val dateCreated: String,

    @SerializedName("date_modified")
    val dateModified: String,

    @SerializedName("src")
    val src: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("alt")
    val alt: String
)