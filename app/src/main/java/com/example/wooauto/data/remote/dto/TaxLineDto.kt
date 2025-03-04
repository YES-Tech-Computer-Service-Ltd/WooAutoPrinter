package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.TaxLine
import com.google.gson.annotations.SerializedName

/**
 * 税费行数据传输对象
 * 用于解析WooCommerce API中的tax_lines字段
 */
data class TaxLineDto(
    val id: Long,
    @SerializedName("rate_code")
    val rateCode: String,
    val label: String,
    @SerializedName("rate_percent")
    val ratePercent: Double,
    @SerializedName("tax_total")
    val taxTotal: String
)

/**
 * 将TaxLineDto转换为领域模型TaxLine
 */
fun TaxLineDto.toTaxLine(): TaxLine {
    return TaxLine(
        id = id,
        label = label,
        ratePercent = ratePercent,
        taxTotal = taxTotal
    )
} 