package com.wooauto.data.local.dao

import app.cash.turbine.test
import com.wooauto.data.local.entities.ProductAttributeEntity
import com.wooauto.data.local.entities.ProductEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProductDaoTest : BaseDaoTest() {
    
    private lateinit var productDao: ProductDao

    override fun setup() {
        super.setup()
        productDao = database.productDao()
    }

    @Test
    fun `插入产品后应能正确查询`() = runTest {
        // 准备测试数据
        val product = createTestProduct()
        
        // 执行插入
        productDao.insertProduct(product)
        
        // 验证查询结果
        val result = productDao.getProductById(product.id)
        assertEquals(product, result)
    }

    @Test
    fun `批量插入产品后应全部可查`() = runTest {
        // 准备测试数据
        val products = listOf(
            createTestProduct(id = 1),
            createTestProduct(id = 2),
            createTestProduct(id = 3)
        )
        
        // 执行批量插入
        productDao.insertProducts(products)
        
        // 验证查询结果
        productDao.getAllProducts().test {
            val result = awaitItem()
            assertEquals(3, result.size)
            assertEquals(products.toSet(), result.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `更新产品后应反映新值`() = runTest {
        // 准备并插入初始数据
        val product = createTestProduct()
        productDao.insertProduct(product)
        
        // 更新数据
        val updatedProduct = product.copy(
            price = "199.99",
            stockQuantity = 50
        )
        productDao.updateProduct(updatedProduct)
        
        // 验证更新结果
        val result = productDao.getProductById(product.id)
        assertEquals("199.99", result?.price)
        assertEquals(50, result?.stockQuantity)
    }

    @Test
    fun `删除产品后应返回null`() = runTest {
        // 准备并插入数据
        val product = createTestProduct()
        productDao.insertProduct(product)
        
        // 删除数据
        productDao.deleteProduct(product)
        
        // 验证删除结果
        val result = productDao.getProductById(product.id)
        assertNull(result)
    }

    @Test
    fun `按类别查询应返回正确产品`() = runTest {
        // 准备不同类别的产品数据
        val products = listOf(
            createTestProduct(id = 1, category = "food"),
            createTestProduct(id = 2, category = "drink"),
            createTestProduct(id = 3, category = "food")
        )
        
        // 插入所有产品
        productDao.insertProducts(products)
        
        // 验证按类别查询结果
        productDao.getProductsByCategory("food").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertTrue(result.all { it.category == "food" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createTestProduct(
        id: Long = 1L,
        category: String = "food"
    ) = ProductEntity(
        id = id,
        name = "Test Product $id",
        description = "Test Description",
        price = "99.99",
        regularPrice = "99.99",
        salePrice = null,
        stockStatus = "instock",
        stockQuantity = 10,
        category = category,
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
} 