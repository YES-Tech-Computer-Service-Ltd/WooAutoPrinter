package com.wooauto.data.repository

import app.cash.turbine.test
import com.wooauto.data.local.dao.OrderDao
import com.wooauto.data.local.entities.OrderEntity
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.data.remote.models.BillingResponse
import com.wooauto.data.remote.models.OrderResponse
import com.wooauto.data.remote.models.ShippingResponse
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.*
import java.util.*

class OrderRepositoryImplTest : BaseRepositoryTest() {

    @Mock
    private lateinit var orderDao: OrderDao

    @Mock
    private lateinit var apiService: WooCommerceApiService

    private lateinit var repository: OrderRepositoryImpl

    @Before
    override fun setup() {
        super.setup()
        repository = OrderRepositoryImpl(orderDao, apiService)
    }

    @Test
    fun `getAllOrders 应返回转换后的领域模型列表`() = runTest {
        // 准备测试数据
        val orderEntities = listOf(createTestOrderEntity())
        whenever(orderDao.getAllOrders()).thenReturn(flowOf(orderEntities))

        // 执行测试
        repository.getAllOrders().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(orderEntities[0].id, result[0].id)
            assertEquals(orderEntities[0].status, result[0].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getOrderById 应返回转换后的领域模型`() = runTest {
        // 准备测试数据
        val orderEntity = createTestOrderEntity()
        whenever(orderDao.getOrderById(1L)).thenReturn(orderEntity)

        // 执行测试
        val result = repository.getOrderById(1L)
        assertEquals(orderEntity.id, result?.id)
        assertEquals(orderEntity.status, result?.status)
    }

    @Test
    fun `refreshOrders 应从API获取数据并存入数据库`() = runTest {
        // 准备测试数据
        val orderResponse = createTestOrderResponse()
        whenever(apiService.getOrders(1)).thenReturn(listOf(orderResponse))

        // 执行测试
        repository.refreshOrders(1)

        // 验证交互
        verify(apiService).getOrders(1)
        verify(orderDao).insertOrder(any())
    }

    @Test
    fun `updateOrderStatus 应更新状态并保存到数据库`() = runTest {
        // 准备测试数据
        val orderResponse = createTestOrderResponse()
        whenever(apiService.updateOrderStatus(1L, mapOf("status" to "completed")))
            .thenReturn(orderResponse)

        // 执行测试
        repository.updateOrderStatus(1L, "completed")

        // 验证交互
        verify(apiService).updateOrderStatus(1L, mapOf("status" to "completed"))
        verify(orderDao).insertOrder(any())
    }

    private fun createTestOrderEntity() = OrderEntity(
        id = 1L,
        number = "WC-1",
        status = "pending",
        dateCreated = Date(),
        total = "99.99",
        customerId = 1L,
        customerName = "Test Customer",
        billingAddress = "Test Billing Address",
        shippingAddress = "Test Shipping Address",
        paymentMethod = "cod",
        paymentMethodTitle = "Cash on Delivery",
        lineItemsJson = "[]",
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

    private fun createTestOrderResponse() = OrderResponse(
        id = 1L,
        number = "WC-1",
        status = "pending",
        dateCreated = "2024-03-15T14:00:00",
        total = "99.99",
        customerId = 1L,
        billing = BillingResponse(
            firstName = "Test",
            lastName = "Customer",
            email = "test@example.com",
            address1 = "Test Address 1",
            address2 = null,
            city = "Test City",
            state = "Test State",
            postcode = "12345",
            country = "Test Country"
        ),
        shipping = ShippingResponse(
            firstName = "Test",
            lastName = "Customer",
            address1 = "Test Address 1",
            address2 = null,
            city = "Test City",
            state = "Test State",
            postcode = "12345",
            country = "Test Country"
        ),
        paymentMethod = "cod",
        paymentMethodTitle = "Cash on Delivery",
        lineItems = emptyList(),
        customerNote = "Test note",
        metaData = emptyList(),
        deliveryDate = "2024-03-15",
        deliveryTime = "14:00-15:00",
        orderMethod = "delivery",
        tip = "10.00",
        deliveryFee = "5.00"
    )
} 