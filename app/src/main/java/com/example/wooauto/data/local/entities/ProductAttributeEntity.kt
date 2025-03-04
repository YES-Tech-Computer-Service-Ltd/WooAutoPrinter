package com.example.wooauto.data.local.entities

/**
 * 产品属性实体类
 * 用于存储产品的属性信息
 */
data class ProductAttributeEntity(
    val id: Long,
    val name: String,
    val options: List<String> = emptyList(),
    val position: Int = 0,
    val visible: Boolean = true,
    val variation: Boolean = false
) 