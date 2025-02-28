package com.example.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.wooauto.data.local.db.Converters

/**
 * 产品实体类
 * 用于本地数据库存储产品信息
 */
@Entity(tableName = "products")
@TypeConverters(Converters::class)
data class ProductEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val description: String,
    val price: String,
    val regularPrice: String,
    val salePrice: String?,
    val stockStatus: String,
    val stockQuantity: Int?,
    val category: String,
    val images: List<String>,
    val attributes: List<ProductAttributeEntity>?,
    val variations: List<Long>?
)

/**
 * 产品属性实体类
 * 用于存储产品的属性信息
 */
data class ProductAttributeEntity(
    val id: Long,
    val name: String,
    val options: List<String>,
    val variation: Boolean
) 