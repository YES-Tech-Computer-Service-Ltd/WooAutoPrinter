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
    val woofoodInfo: WooFoodInfo? = null,
    // 价格明细
    val subtotal: String = "", // 商品小计（所有商品总价，不含税费和其他费用）
    val totalTax: String = "", // 总税费
    val discountTotal: String = "", // The total discount amount
    val feeLines: List<FeeLine> = emptyList(), // 额外费用项（如小费、服务费等）
    val taxLines: List<TaxLine> = emptyList() // 税费明细
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
) {
    override fun toString(): String {
        return "WooFoodInfo(orderMethod=$orderMethod, deliveryTime=$deliveryTime, " +
               "deliveryFee=$deliveryFee, tip=$tip, isDelivery=$isDelivery)"
    }
}

/**
 * 费用行
 */
data class FeeLine(
    val id: Long,
    val name: String,
    val total: String,
    val totalTax: String
)

/**
 * 税费行
 */
data class TaxLine(
    val id: Long,
    val label: String,
    val ratePercent: Double,
    val taxTotal: String
) 