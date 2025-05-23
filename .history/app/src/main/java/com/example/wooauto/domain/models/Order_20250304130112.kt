package com.example.wooauto.domain.models

import java.util.Date

data class Order(
    val id: Long,
    val number: String,
    val status: String,
    val dateCreated: Date,
    val total: String,
    val customerName: String,
    val contactInfo: String,
    val billingInfo: String,
    val paymentMethod: String,
    val items: List<OrderItem>,
    val isPrinted: Boolean,
    val notificationShown: Boolean,
    val notes: String = "",
    // WooFood相关属性
    val woofoodInfo: WooFoodInfo? = null
)

/**
 * WooFood插件相关信息
 */
data class WooFoodInfo(
    val orderMethod: String?, // 订单方式，例如"配送"或"自取"
    val deliveryTime: String?, // 配送时间
    val deliveryAddress: String?, // 配送地址
    val deliveryFee: String?, // 配送费用
    val tip: String?, // 小费
    val isDelivery: Boolean // 是否是外卖订单
) 