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
    val id: Long,
    val status: String,
    val dateCreated: Date,
    val total: String,
    val customerName: String,
    val customerEmail: String?,
    val paymentMethod: String,
    val shippingAddress: String,
    val items: List<OrderItemEntity>,
    val note: String?
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