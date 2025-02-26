package com.example.wooauto.data.repositories

import com.example.wooauto.BaseTest
import com.example.wooauto.data.api.WooCommerceApiService
import com.example.wooauto.data.api.models.Order
import com.example.wooauto.data.database.dao.OrderDao
import com.example.wooauto.data.database.entities.OrderEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * OrderRepository 的单元测试
 * 
 * 使用方法：
 * 1. 在 Android Studio 中右键点击类名，选择 "Run 'OrderRepositoryTest'"
 * 2. 或在终端使用 ./gradlew test 运行所有测试
 * 
 * 测试覆盖：
 * - 订单获取和缓存
 * - 订单状态更新
 * - 订单搜索功能
 * - 错误处理
 */
class OrderRepositoryTest : BaseTest() {

    private lateinit var repository: OrderRepository
    private lateinit var orderDao: OrderDao
    private lateinit var apiService: WooCommerceApiService
    private val apiKey = "test_key"
    private val apiSecret = "test_secret"

    @Before
    fun setup() {
        // 创建 mock 对象
        orderDao = mockk(relaxed = true)
        apiService = mockk()

        // 初始化 repository
        repository = OrderRepository(orderDao, apiService, apiKey, apiSecret)
    }

    @Test
    fun `获取订单列表成功时应该更新本地数据库`() = runTest {
        // 准备测试数据
        val mockOrders = listOf(
            Order(id = 1, number = "001"),
            Order(id = 2, number = "002")
        )
        
        // 模拟 API 响应
        coEvery { 
            apiService.getOrders(
                consumerKey = apiKey,
                consumerSecret = apiSecret,
                status = null,
                after = null,
                perPage = any()
            )
        } returns Response.success(mockOrders)

        // 模拟数据库操作
        coEvery { orderDao.insertOrders(any()) } returns Unit

        // 执行
        val result = repository.refreshOrders()

        // 验证
        assertTrue(result.isSuccess)
        coVerify { orderDao.insertOrders(any()) }
    }

    @Test
    fun `获取订单列表失败时应该返回错误结果`() = runTest {
        // 模拟 API 错误
        coEvery { 
            apiService.getOrders(
                consumerKey = any(),
                consumerSecret = any(),
                status = any(),
                after = any(),
                perPage = any()
            )
        } returns Response.error(404, mockk(relaxed = true))

        // 执行
        val result = repository.refreshOrders()

        // 验证
        assertTrue(result.isFailure)
    }

    @Test
    fun `搜索订单应该返回匹配的结果`() = runTest {
        // 准备测试数据
        val mockOrders = listOf(
            OrderEntity(id = 1, number = "TEST001"),
            OrderEntity(id = 2, number = "TEST002")
        )
        
        // 模拟数据库搜索
        coEvery { orderDao.searchOrdersFlow("TEST") } returns flowOf(mockOrders)

        // 执行
        val result = repository.searchOrdersFlow("TEST").first()

        // 验证
        assertEquals(2, result.size)
        assertEquals("TEST001", result[0].number)
    }

    @Test
    fun `更新订单状态成功时应该同步本地数据库`() = runTest {
        // 准备
        val orderId = 1L
        val newStatus = "completed"
        
        // 模拟 API 响应
        coEvery { 
            apiService.updateOrder(
                orderId = orderId,
                orderUpdateRequest = any(),
                consumerKey = apiKey,
                consumerSecret = apiSecret
            )
        } returns Response.success(mockk(relaxed = true))

        // 执行
        val result = repository.updateOrderStatus(orderId, newStatus)

        // 验证
        assertTrue(result.isSuccess)
        coVerify { orderDao.updateOrderStatus(orderId, newStatus) }
    }
} 