package com.example.wooauto.data.local.entities

/**
 * WooFood信息实体类
 * 用于存储外卖/自取订单的相关信息
 */
data class WooFoodInfoEntity(
    val orderMethod: String?, // 订单类型：delivery(外卖) 或 takeaway(自取)
    val deliveryTime: String?, // 配送/自取时间
    val deliveryAddress: String?, // 配送地址
    val deliveryFee: String?, // 配送费
    val tip: String?, // 小费
    val isDelivery: Boolean // 是否是外卖订单
) 