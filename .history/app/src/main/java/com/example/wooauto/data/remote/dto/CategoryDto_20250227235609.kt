package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.Category
import com.google.gson.annotations.SerializedName

data class CategoryDto(
    val id: Long,
    val name: String,
    val slug: String,
    val parent: Long?,
    val description: String,
    val count: Int
)

// 扩展函数，将DTO转换为领域模型
fun CategoryDto.toCategory(): Category {
    return Category(
        id = id,
        name = name,
        slug = slug,
        parent = parent ?: 0,
        description = description,
        count = count
    )
} 