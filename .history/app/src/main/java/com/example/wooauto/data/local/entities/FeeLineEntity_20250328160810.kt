package com.example.wooauto.data.local.entities

/**
 * 费用行实体类
 * 用于存储订单的额外费用项，如配送费、小费等
 */
data class FeeLineEntity(
    val id: Long, // 费用行ID
    val name: String, // 费用名称
    val total: String, // 费用金额
    val totalTax: String // 费用对应的税费
) 