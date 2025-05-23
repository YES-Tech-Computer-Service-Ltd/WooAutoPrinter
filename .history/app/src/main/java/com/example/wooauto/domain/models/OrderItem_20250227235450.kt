package com.example.wooauto.domain.models

data class OrderItem(
    val id: Long,
    val productId: Long,
    val name: String,
    val quantity: Int,
    val price: String,
    val subtotal: String,
    val total: String
) 