package com.example.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.wooauto.data.local.converters.RoomConverters

/**
 * 产品实体类，用于Room数据库存储
 */
@Entity(tableName = "products")
@TypeConverters(RoomConverters::class)
data class ProductEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val sku: String = "",
    val description: String = "",
    val price: String = "",
    val regularPrice: String = "",
    val salePrice: String = "",
    val stockStatus: String = "",
    val stockQuantity: Int? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val images: List<String> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
) 