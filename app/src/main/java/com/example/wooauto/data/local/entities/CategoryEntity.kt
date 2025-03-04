package com.example.wooauto.data.local.entities

/**
 * 分类实体类
 * 用于存储产品分类信息
 */
data class CategoryEntity(
    val id: Long,
    val name: String,
    val slug: String,
    val parent: Long = 0,
    val description: String = "",
    val count: Int = 0
) 