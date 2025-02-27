package com.wooauto.data.repository

import app.cash.turbine.test
import com.wooauto.data.local.dao.ProductDao
import com.wooauto.data.local.entities.ProductAttributeEntity
import com.wooauto.data.local.entities.ProductEntity
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.data.remote.models.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.*

class ProductRepositoryImplTest : BaseRepositoryTest() {

    @Mock
    private lateinit var productDao: ProductDao

    @Mock
    private lateinit var apiService: WooCommerceApiService

    private lateinit var repository: ProductRepositoryImpl

    @Before
    override fun setup() {
        super.setup()
        repository = ProductRepositoryImpl(productDao, apiService)
    }

    @Test
    fun `getAllProducts 应返回转换后的领域模型列表`() = runTest {
        // 准备测试数据
        val productEntities = listOf(createTestProductEntity())
        whenever(productDao.getAllProducts()).thenReturn(flowOf(productEntities))

        // 执行测试
        repository.getAllProducts().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(productEntities[0].id, result[0].id)
            assertEquals(productEntities[0].name, result[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getProductById 应返回转换后的领域模型`() = runTest {
        // 准备测试数据
        val productEntity = createTestProductEntity()
        whenever(productDao.getProductById(1L)).thenReturn(productEntity)

        // 执行测试
        val result = repository.getProductById(1L)
        assertEquals(productEntity.id, result?.id)
        assertEquals(productEntity.name, result?.name)
    }

    @Test
    fun `refreshProducts 应从API获取数据并存入数据库`() = runTest {
        // 准备测试数据
        val productResponse = createTestProductResponse()
        whenever(apiService.getProducts(1)).thenReturn(listOf(productResponse))

        // 执行测试
        repository.refreshProducts(1)

        // 验证交互
        verify(apiService).getProducts(1)
        verify(productDao).insertProduct(any())
    }

    @Test
    fun `getProductsByCategory 应返回指定类别的产品`() = runTest {
        // 准备测试数据
        val productEntities = listOf(createTestProductEntity(category = "food"))
        whenever(productDao.getProductsByCategory("food")).thenReturn(flowOf(productEntities))

        // 执行测试
        repository.getProductsByCategory("food").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("food", result[0].category)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateProductStock 应更新产品库存`() = runTest {
        // 准备测试数据
        val productEntity = createTestProductEntity()
        whenever(productDao.getProductById(1L)).thenReturn(productEntity)

        // 执行测试
        repository.updateProductStock(1L, 20)

        // 验证交互
        verify(productDao).updateProduct(any())
    }

    private fun createTestProductEntity() = ProductEntity(
        id = 1L,
        name = "Test Product",
        description = "Test Description",
        price = "99.99",
        regularPrice = "99.99",
        salePrice = null,
        stockStatus = "instock",
        stockQuantity = 10,
        category = "food",
        images = listOf("image1.jpg", "image2.jpg"),
        attributes = listOf(
            ProductAttributeEntity(
                id = 1,
                name = "Size",
                options = listOf("Small", "Medium", "Large"),
                variation = true
            )
        ),
        variations = listOf(1L, 2L, 3L)
    )

    private fun createTestProductResponse() = ProductResponse(
        id = 1L,
        name = "Test Product",
        description = "Test Description",
        price = "99.99",
        regularPrice = "99.99",
        salePrice = null,
        stockStatus = "instock",
        stockQuantity = 10,
        categories = listOf(
            CategoryResponse(
                id = 1L,
                name = "Food",
                slug = "food"
            )
        ),
        images = listOf(
            ImageResponse(
                id = 1L,
                src = "image1.jpg",
                name = "Image 1",
                alt = null
            )
        ),
        attributes = listOf(
            AttributeResponse(
                id = 1L,
                name = "Size",
                options = listOf("Small", "Medium", "Large"),
                variation = true
            )
        ),
        variations = listOf(1L, 2L, 3L)
    )
} 