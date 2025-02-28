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
    val notificationShown: Boolean
) 