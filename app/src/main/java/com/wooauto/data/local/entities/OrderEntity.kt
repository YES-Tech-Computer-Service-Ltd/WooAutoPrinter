package com.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

/**
 * 订单实体类
 * 用于本地数据库存储订单信息
 */
@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey
    val id: Long,                      // WooCommerce 订单的唯一标识符
    val number: String,                // 订单编号
    val status: String,                // 订单状态
    val dateCreated: Date,             // 订单创建日期
    val total: String,                 // 订单总金额
    val customerId: Long,              // 客户ID
    val customerName: String,          // 客户姓名
    val billingAddress: String,        // 账单地址
    val shippingAddress: String,       // 配送地址
    val paymentMethod: String,         // 支付方式标识
    val paymentMethodTitle: String,    // 支付方式标题
    val lineItemsJson: String,         // 订单项目的JSON字符串
    val customerNote: String?,         // 客户备注
    val isPrinted: Boolean = false,    // 订单是否已打印
    val notificationShown: Boolean = false, // 订单通知是否已展示
    val lastUpdated: Date = Date(),    // 订单最后更新时间
    val deliveryDate: String? = null,  // 配送日期
    val deliveryTime: String? = null,  // 配送时间
    val orderMethod: String? = null,   // 订单方式（例如 "delivery" 或 "pickup"）
    val tip: String? = null,           // 小费金额
    val deliveryFee: String? = null    // 配送费
)

/**
 * 订单项实体类
 * 用于存储订单中的商品信息
 */
data class OrderItemEntity(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val price: String,
    val total: String
) 