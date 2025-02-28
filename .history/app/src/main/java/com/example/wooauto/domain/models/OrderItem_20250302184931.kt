package com.example.wooauto.domain.models

data class OrderItem(
    val id: Long,
    val productId: Long,
    val name: String,
    val quantity: Int,
    val price: String,
    val subtotal: String,
    val total: String,
    val image: String,
    val options: List<ItemOption> = emptyList()
)

/**
 * 商品选项
 */
data class ItemOption(
    val name: String,
    val value: String
) 