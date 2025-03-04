package com.example.wooauto.data.local.entities

/**
 * 订单项实体类
 * 用于存储订单中的商品信息
 */
data class OrderItemEntity(
    val id: Long,
    val name: String,
    val productId: Long,
    val quantity: Int,
    val price: String,
    val total: String,
    val sku: String = "",
    val variation: List<String> = emptyList()
) 