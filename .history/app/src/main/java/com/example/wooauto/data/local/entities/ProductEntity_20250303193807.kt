package com.example.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.wooauto.data.local.converters.CategoryListConverter
import com.example.wooauto.data.local.converters.ImageListConverter

/**
 * 产品实体类，用于Room数据库存储
 */
@Entity(tableName = "products")
@TypeConverters(CategoryListConverter::class, ImageListConverter::class)
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

/**
 * 分类实体（嵌套在产品实体中）
 */
data class CategoryEntity(
    val id: Long,
    val name: String,
    val slug: String = ""
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