package com.example.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.wooauto.data.local.converters.RoomConverters
import java.util.Date

/**
 * 订单实体类，用于Room数据库存储
 */
@Entity(tableName = "orders")
@TypeConverters(RoomConverters::class)
data class OrderEntity(
    @PrimaryKey
    val id: Long,
    val number: Int,
    val status: String,
    val dateCreated: Long,
    val dateModified: Long,
    val customerName: String,
    val customerNote: String = "",
    val contactInfo: String = "",
    val total: String,
    val totalTax: String,
    val lineItems: List<OrderLineItemEntity> = emptyList(),
    val shippingAddress: String = "",
    val billingAddress: String = "",
    val paymentMethod: String = "",
    val paymentMethodTitle: String = "",
    val isPrinted: Boolean = false,
    val notificationShown: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 订单行项目（商品）实体
 */
data class OrderLineItemEntity(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val price: String,
    val total: String,
    val sku: String = ""
) 