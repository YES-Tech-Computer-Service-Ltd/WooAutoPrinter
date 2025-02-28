package com.example.wooauto.domain.repository

import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.Order

interface OrderRepository {
    suspend fun getOrders(status: String? = null): List<Order>
    suspend fun getOrder(id: Long): Order?
    suspend fun updateOrderStatus(id: Long, status: String): Order
    suspend fun refreshOrders(status: String? = null): Boolean
    suspend fun markOrderAsPrinted(orderId: Long): Boolean
    suspend fun searchOrders(query: String): List<Order>
    suspend fun testConnection(config: WooCommerceConfig? = null): Boolean
} 