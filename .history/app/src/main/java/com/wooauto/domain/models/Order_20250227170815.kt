package com.wooauto.domain.models

import java.util.Date

/**
 * Domain Model for an Order in WooCommerce.
 *
 * 此模型表示 WooCommerce 中的订单对象，
 * 仅包含业务逻辑所需的属性，不依赖任何持久化或序列化框架。
 */
data class Order(
    val id: Long,                      // WooCommerce 订单的唯一标识符（Order ID）
    val number: String,                // 订单编号（在 WooCommerce 中显示的订单号）
    val status: String,                // 订单状态（例如 "pending", "processing", "completed" 等）
    val dateCreated: Date,             // 订单创建日期（订单在 WooCommerce 中生成的时间）
    val total: String,                 // 订单总金额（通常为字符串格式，以便保留货币符号和格式）
    val customerId: Long,              // 下单客户的唯一标识符（Customer ID）
    val customerName: String,          // 客户姓名（通常通过账单地址信息获取）
    val billingAddress: String,        // 账单地址（格式化后的地址字符串）
    val shippingAddress: String,       // 配送地址（格式化后的地址字符串）
    val paymentMethod: String,         // 支付方式标识（如 "credit_card", "paypal" 等）
    val paymentMethodTitle: String,    // 支付方式的标题（用于显示的支付方式名称）
    val customerNote: String?,         // 客户备注（订单备注）
    val lineItemsJson: String,         // 订单项目的 JSON 字符串（包含订单中各产品的详细信息）
    val isPrinted: Boolean = false,    // 订单是否已打印（用于内部处理，如打印机队列）
    val notificationShown: Boolean = false, // 订单通知是否已展示（用于判断是否已提醒用户）
    val lastUpdated: Date = Date(),    // 订单最后更新时间（用于缓存同步或数据刷新判断）
    val deliveryDate: String? = null,  // 配送日期（仅针对需要配送的订单，例如外卖订单）
    val deliveryTime: String? = null,  // 配送时间（具体配送时间或时间段）
    val orderMethod: String? = null,   // 订单方式（例如 "delivery" 或 "pickup"，由 WooCommerce Food 插件定义）
    val tip: String? = null,           // 小费金额（如果订单包含小费信息）
    val deliveryFee: String? = null    // 配送费（如果订单中包含配送费用）
)
