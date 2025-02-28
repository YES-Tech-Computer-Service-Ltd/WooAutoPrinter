package com.wooauto.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.data.remote.models.OrderResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WooCommerceApiIntegrationTest {

    private lateinit var apiService: WooCommerceApiService
    private val baseUrl = "https://blanchedalmond-hamster-446110.hostingersite.com/wp-json/wc/v3/"
    private val consumerKey = "Test_admin"
    private val consumerSecret = "lc%VS3GpYQs&mLHqIVTmo0j3"

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

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
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
} 