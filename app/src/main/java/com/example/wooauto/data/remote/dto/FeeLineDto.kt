package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.FeeLine
import com.google.gson.annotations.SerializedName

/**
 * 费用行数据传输对象
 * 用于解析WooCommerce API中的fee_lines字段
 */
data class FeeLineDto(
    val id: Long,
    val name: String,
    val total: String,
    @SerializedName("total_tax")
    val totalTax: String
)

/**
 * 将FeeLineDto转换为领域模型FeeLine
 */
fun FeeLineDto.toFeeLine(): FeeLine {
    return FeeLine(
        id = id,
        name = name,
        total = total,
        totalTax = totalTax
    )
} 