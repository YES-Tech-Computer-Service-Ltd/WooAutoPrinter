package com.example.wooauto.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 通用图片DTO
 */
data class ImageDto(
    val id: Long?,
    val src: String?
)

/**
 * 通用元数据DTO
 */
data class MetaDataDto(
    val id: Long?,
    val key: String?,
    val value: Any?
) 