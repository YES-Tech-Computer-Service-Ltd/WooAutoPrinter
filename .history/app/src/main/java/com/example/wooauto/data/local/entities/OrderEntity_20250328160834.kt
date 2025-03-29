package com.example.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.wooauto.data.local.converters.RoomConverters
import java.util.Date

/**
 * 订单实体
 * 用于在本地数据库中存储订单信息
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
    val total: String,
    val totalTax: String,
    val customerName: String,
    val customerNote: String,
    val contactInfo: String,
    val lineItems: List<OrderLineItemEntity>,
    val billingAddress: String,
    val shippingAddress: String,
    val paymentMethod: String,
    val paymentMethodTitle: String,
    val isPrinted: Boolean,
    val notificationShown: Boolean,
    val lastUpdated: Long,
    val woofoodInfo: WooFoodInfoEntity? = null,
    val feeLines: List<FeeLineEntity> = emptyList(),
    val taxLines: List<TaxLineEntity> = emptyList(),
    val subtotal: String = "",
    val discountTotal: String = ""
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

/**
 * WooFood信息实体
 */
data class WooFoodInfoEntity(
    val orderMethod: String?,
    val deliveryTime: String?,
    val deliveryAddress: String?,
    val deliveryFee: String?,
    val tip: String?,
    val isDelivery: Boolean
)

/**
 * 费用行实体
 */
data class FeeLineEntity(
    val id: Long,
    val name: String,
    val total: String,
    val totalTax: String
)

/**
 * 税费行实体
 */
data class TaxLineEntity(
    val id: Long,
    val label: String,
    val ratePercent: Double,
    val taxTotal: String
) 