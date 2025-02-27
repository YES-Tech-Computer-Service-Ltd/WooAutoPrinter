package com.wooauto.data.local.dao

import androidx.room.*
import com.wooauto.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * 产品数据访问对象
 * 定义了对产品表的所有数据库操作
 */
@Dao
interface ProductDao {
    /**
     * 插入产品
     * @param product 要插入的产品实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    /**
     * 批量插入产品
     * @param products 要插入的产品实体列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    /**
     * 获取所有产品
     * @return 产品列表流
     */
    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<ProductEntity>>

    /**
     * 根据ID获取产品
     * @param productId 产品ID
     * @return 产品实体
     */
    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductById(productId: Long): ProductEntity?

    /**
     * 删除产品
     * @param product 要删除的产品实体
     */
    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    /**
     * 更新产品
     * @param product 要更新的产品实体
     */
    @Update
    suspend fun updateProduct(product: ProductEntity)

    /**
     * 根据类别获取产品
     * @param category 产品类别
     * @return 产品列表流
     */
    @Query("SELECT * FROM products WHERE category = :category")
    fun getProductsByCategory(category: String): Flow<List<ProductEntity>>
} 