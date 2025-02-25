package com.example.wooauto.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey
    val id: Long,

    @ColumnInfo(name = "number")
    val number: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "date_created")
    val dateCreated: Date,

    @ColumnInfo(name = "total")
    val total: String,

    @ColumnInfo(name = "customer_id")
    val customerId: Long,

    @ColumnInfo(name = "customer_name")
    val customerName: String,

    @ColumnInfo(name = "billing_address")
    val billingAddress: String,

    @ColumnInfo(name = "shipping_address")
    val shippingAddress: String,

    @ColumnInfo(name = "payment_method")
    val paymentMethod: String,

    @ColumnInfo(name = "payment_method_title")
    val paymentMethodTitle: String,

    @ColumnInfo(name = "line_items_json")
    val lineItemsJson: String,

    @ColumnInfo(name = "is_printed")
    val isPrinted: Boolean = false,

    @ColumnInfo(name = "notification_shown")
    val notificationShown: Boolean = false,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Date = Date()
)