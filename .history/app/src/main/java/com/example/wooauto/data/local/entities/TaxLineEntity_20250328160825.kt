package com.example.wooauto.data.local.entities

/**
 * 税费行实体类
 * 用于存储订单的税费信息
 */
data class TaxLineEntity(
    val id: Long, // 税费行ID
    val label: String, // 税费标签
    val ratePercent: String, // 税率百分比
    val taxTotal: String // 税费总额
) 