package com.wooauto.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.data.remote.models.OrderResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.google.gson.GsonBuilder
import com.wooauto.data.remote.adapters.FlexibleTypeAdapter

@RunWith(AndroidJUnit4::class)
class WooCommerceApiIntegrationTest {

    private lateinit var apiService: WooCommerceApiService
    private val baseUrl = "https://blanchedalmond-hamster-446110.hostingersite.com/wp-json/wc/v3/"
    private val consumerKey = "ck_619dec8d9197cfb2234e72f58017f6c735cef728"
    private val consumerSecret = "cs_879f1d083186b8733a3be1cdec9af340f6481270"
    @Before
    fun setup() {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val originalHttpUrl = original.url

                val url = originalHttpUrl.newBuilder()
                    .addQueryParameter("consumer_key", consumerKey)
                    .addQueryParameter("consumer_secret", consumerSecret)
                    .build()

                val requestBuilder = original.newBuilder()
                    .url(url)

                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // 在这里使用GsonBuilder，替换原有的Gson创建方式
        val gson = GsonBuilder()
            .registerTypeAdapter(Any::class.java, FlexibleTypeAdapter())
            .create()

        // 修改现有的retrofit变量初始化，不要创建新的变量
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson)) // 使用配置的Gson
            .build()

        apiService = retrofit.create(WooCommerceApiService::class.java)
    }

    @Test
    fun testGetOrders() = runBlocking {
        try {
            // 获取订单列表
            val orders = apiService.getOrders(1)
            
            // 验证订单列表不为空
            assertNotNull("订单列表不应为空", orders)
            
            // 打印订单信息用于调试
            orders.forEach { order ->
                println("订单ID: ${order.id}")
                println("订单编号: ${order.number}")
                println("订单状态: ${order.status}")
                println("订单总额: ${order.total}")
                println("------------------------")
            }
        } catch (e: Exception) {
            throw AssertionError("获取订单失败: ${e.message}", e)
        }
    }

    @Test
    fun testGetProducts() = runBlocking {
        try {
            // 获取产品列表
            val products = apiService.getProducts(1)
            
            // 验证产品列表不为空
            assertNotNull("产品列表不应为空", products)
            
            // 打印产品信息用于调试
            products.forEach { product ->
                println("产品ID: ${product.id}")
                println("产品名称: ${product.name}")
                println("产品价格: ${product.price}")
                println("库存状态: ${product.stockStatus}")
                println("------------------------")
            }
        } catch (e: Exception) {
            throw AssertionError("获取产品失败: ${e.message}", e)
        }
    }

    @Test
    fun testGetCategories() = runBlocking {
        try {
            // 获取类别列表
            val categories = apiService.getCategories()
            
            // 验证类别列表不为空
            assertNotNull("类别列表不应为空", categories)
            
            // 打印类别信息用于调试
            categories.forEach { category ->
                println("类别ID: ${category.id}")
                println("类别名称: ${category.name}")
                println("类别别名: ${category.slug}")
                println("------------------------")
            }
        } catch (e: Exception) {
            throw AssertionError("获取类别失败: ${e.message}", e)
        }
    }

    @Test
    fun testGetOrderDetails() = runBlocking {
        try {
            // 1. 首先获取订单列表
            val orders = apiService.getOrders(1)
            assertNotNull("订单列表不应为空", orders)
            assertTrue("订单列表应该包含至少一个订单", orders.isNotEmpty())

            // 2. 获取第一个订单的详细信息
            val firstOrder = orders.first()
            val orderDetail = apiService.getOrder(firstOrder.id)

            // 3. 验证基本订单信息
            println("\n====== 订单基本信息 ======")
            println("订单ID: ${orderDetail.id}")
            println("订单编号: ${orderDetail.number}")
            println("订单状态: ${orderDetail.status}")
            println("订单总额: ${orderDetail.total}")
            println("创建时间: ${orderDetail.dateCreated}")

            // 4. 验证客户信息
            println("\n====== 客户信息 ======")
            println("客户ID: ${orderDetail.customerId}")
            println("账单姓名: ${orderDetail.billing.firstName} ${orderDetail.billing.lastName}")
            println("账单邮箱: ${orderDetail.billing.email}")
            println("账单地址: ${orderDetail.billing.address1}")
            println("账单城市: ${orderDetail.billing.city}")
            println("账单邮编: ${orderDetail.billing.postcode}")

            // 5. 验证配送信息
            println("\n====== 配送信息 ======")
            println("订单方式: ${orderDetail.orderMethod ?: "未指定"}")
            println("配送姓名: ${orderDetail.shipping.firstName} ${orderDetail.shipping.lastName}")
            println("配送地址: ${orderDetail.shipping.address1}")
            println("配送城市: ${orderDetail.shipping.city}")
            println("配送邮编: ${orderDetail.shipping.postcode}")
            println("配送日期: ${orderDetail.deliveryDate ?: "未指定"}")
            println("配送时间: ${orderDetail.deliveryTime ?: "未指定"}")

            // 6. 验证支付信息
            println("\n====== 支付信息 ======")
            println("支付方式: ${orderDetail.paymentMethod}")
            println("支付方式标题: ${orderDetail.paymentMethodTitle}")
            println("小费: ${orderDetail.tip ?: "无"}")
            println("配送费: ${orderDetail.deliveryFee ?: "无"}")

            // 7. 验证订单项目
            println("\n====== 订单项目 ======")
            orderDetail.lineItems.forEach { item ->
                println("- 商品ID: ${item.productId}")
                println("  商品名称: ${item.name}")
                println("  数量: ${item.quantity}")
                println("  单价: ${item.price}")
                println("  小计: ${item.total}")
                println("  ---------------")
            }

            // 8. 验证元数据
            println("\n====== 订单元数据 ======")
            orderDetail.metaData.forEach { meta ->
                println("${meta.key}: ${meta.value}")
            }

            // 9. 验证其他信息
            println("\n====== 其他信息 ======")
            println("客户备注: ${orderDetail.customerNote ?: "无"}")

            // 10. 断言验证
            assertNotNull("订单ID不应为空", orderDetail.id)
            assertNotNull("订单编号不应为空", orderDetail.number)
            assertNotNull("订单状态不应为空", orderDetail.status)
            assertNotNull("订单总额不应为空", orderDetail.total)
            assertNotNull("订单创建时间不应为空", orderDetail.dateCreated)
            assertNotNull("支付方式不应为空", orderDetail.paymentMethod)
            assertTrue("订单应该包含商品项", orderDetail.lineItems.isNotEmpty())
            
            // 验证地址信息完整性
            with(orderDetail.billing) {
                assertFalse("账单姓名不应为空", firstName.isEmpty())
                assertFalse("账单地址不应为空", address1.isEmpty())
            }
            
            // 根据订单方式验证配送信息
            when (orderDetail.orderMethod?.lowercase()) {
                "pickup", "自取" -> {
                    println("自取订单：不验证配送信息")
                }
                "delivery", "外送" -> {
                    with(orderDetail.shipping) {
                        assertFalse("外送订单的配送姓名不应为空", firstName.isEmpty())
                        assertFalse("外送订单的配送地址不应为空", address1.isEmpty())
                        assertNotNull("外送订单的配送日期不应为空", orderDetail.deliveryDate)
                        assertNotNull("外送订单的配送时间不应为空", orderDetail.deliveryTime)
                        assertNotNull("外送订单的配送费不应为空", orderDetail.deliveryFee)
                    }
                }
                else -> {
                    println("未知订单方式：${orderDetail.orderMethod}")
                    // 对于未知订单方式，我们仍然验证基本的配送信息
                    with(orderDetail.shipping) {
                        if (firstName.isNotEmpty()) {
                            assertFalse("如果提供了配送姓名，配送地址不应为空", address1.isEmpty())
                        }
                    }
                }
            }

        } catch (e: Exception) {
            throw AssertionError("测试订单详情失败: ${e.message}", e)
        }
    }
} 