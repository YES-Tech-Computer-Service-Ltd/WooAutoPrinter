package com.example.wooauto.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.wooauto.data.database.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT DISTINCT category_ids, category_names FROM products")
    suspend fun getAllCategories(): List<CategoryResult>

    @Query("SELECT * FROM products WHERE :categoryId IN (category_ids) ORDER BY name ASC")
    fun getProductsByCategoryFlow(categoryId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE :categoryId IN (category_ids) ORDER BY name ASC")
    suspend fun getProductsByCategory(categoryId: Long): List<ProductEntity>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchProductsFlow(query: String): Flow<List<ProductEntity>>

    @Query("UPDATE products SET stock_quantity = :quantity WHERE id = :productId")
    suspend fun updateProductStock(productId: Long, quantity: Int?)

    @Query("UPDATE products SET regular_price = :regularPrice, sale_price = :salePrice WHERE id = :productId")
    suspend fun updateProductPrices(productId: Long, regularPrice: String, salePrice: String)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProduct(productId: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}

data class CategoryResult(
    val category_ids: List<Long>,
    val category_names: List<String>
)