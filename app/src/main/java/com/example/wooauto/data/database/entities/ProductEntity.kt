package com.example.wooauto.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "short_description")
    val shortDescription: String,

    @ColumnInfo(name = "sku")
    val sku: String,

    @ColumnInfo(name = "price")
    val price: String,

    @ColumnInfo(name = "regular_price")
    val regularPrice: String,

    @ColumnInfo(name = "sale_price")
    val salePrice: String,

    @ColumnInfo(name = "on_sale")
    val onSale: Boolean,

    @ColumnInfo(name = "stock_quantity")
    val stockQuantity: Int?,

    @ColumnInfo(name = "stock_status")
    val stockStatus: String,

    @ColumnInfo(name = "category_ids")
    val categoryIds: List<Long>,

    @ColumnInfo(name = "category_names")
    val categoryNames: List<String>,

    @ColumnInfo(name = "image_url")
    val imageUrl: String?,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)