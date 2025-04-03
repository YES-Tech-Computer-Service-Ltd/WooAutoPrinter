package com.example.wooauto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 订单阅读状态实体
 */
@Entity(tableName = "read_status")
data class ReadStatus(
    @PrimaryKey
    val orderId: Long,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) 