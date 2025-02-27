package com.wooauto.data.local.dao

import app.cash.turbine.test
import com.wooauto.data.local.entities.OrderEntity
import com.wooauto.data.local.entities.OrderItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.*

class OrderDaoTest : BaseDaoTest() {
    
    private lateinit var orderDao: OrderDao

    override fun setup() {
        super.setup()
        orderDao = database.orderDao()
    }

    @Test
    fun `插入订单后应能正确查询`() = runTest {
        // 准备测试数据
        val order = createTestOrder()
        
        // 执行插入
        orderDao.insertOrder(order)
        
        // 验证查询结果
        val result = orderDao.getOrderById(order.id)
        assertEquals(order, result)
    }

    @Test
    fun `更新订单后应反映新值`() = runTest {
        // 准备并插入初始数据
        val order = createTestOrder()
        orderDao.insertOrder(order)
        
        // 更新数据
        val updatedOrder = order.copy(
            status = "completed",
            isPrinted = true
        )
        orderDao.updateOrder(updatedOrder)
        
        // 验证更新结果
        val result = orderDao.getOrderById(order.id)
        assertEquals("completed", result?.status)
        assertEquals(true, result?.isPrinted)
    }

    @Test
    fun `删除订单后应返回null`() = runTest {
        // 准备并插入数据
        val order = createTestOrder()
        orderDao.insertOrder(order)
        
        // 删除数据
        orderDao.deleteOrder(order)
        
        // 验证删除结果
        val result = orderDao.getOrderById(order.id)
        assertNull(result)
    }

    @Test
    fun `getAllOrders应返回所有订单`() = runTest {
        // 准备多个测试数据
        val orders = listOf(
            createTestOrder(id = 1),
            createTestOrder(id = 2),
            createTestOrder(id = 3)
        )
        
        // 插入所有数据
        orders.forEach { orderDao.insertOrder(it) }
        
        // 使用Flow测试工具验证结果
        orderDao.getAllOrders().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals(orders.toSet(), result.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `插入重复ID的订单应覆盖原有数据`() = runTest {
        // 准备两个ID相同的订单
        val originalOrder = createTestOrder(id = 1, status = "pending")
        val newOrder = createTestOrder(id = 1, status = "completed")
        
        // 先插入原始订单
        orderDao.insertOrder(originalOrder)
        
        // 再插入新订单
        orderDao.insertOrder(newOrder)
        
        // 验证结果是否为新订单
        val result = orderDao.getOrderById(1)
        assertEquals("completed", result?.status)
    }

    private fun createTestOrder(
        id: Long = 1L,
        status: String = "pending"
    ) = OrderEntity(
        id = id,
        number = "WC-$id",
        status = status,
        dateCreated = Date(),
        total = "99.99",
        customerId = 1L,
        customerName = "Test Customer",
        billingAddress = "Test Billing Address",
        shippingAddress = "Test Shipping Address",
        paymentMethod = "cod",
        paymentMethodTitle = "Cash on Delivery",
        lineItemsJson = "[{\"productId\":1,\"name\":\"Test Product\",\"quantity\":1,\"price\":\"99.99\",\"total\":\"99.99\"}]",
        customerNote = "Test note",
        isPrinted = false,
        notificationShown = false,
        lastUpdated = Date(),
        deliveryDate = "2024-03-15",
        deliveryTime = "14:00-15:00",
        orderMethod = "delivery",
        tip = "10.00",
        deliveryFee = "5.00"
    )
} 