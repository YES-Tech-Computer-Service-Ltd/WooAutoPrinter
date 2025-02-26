package com.example.wooauto.data.repositories

import android.util.Log
import com.example.wooauto.data.api.WooCommerceApiService
import com.example.wooauto.data.api.models.Product
import com.example.wooauto.data.database.dao.ProductDao
import com.example.wooauto.data.database.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(
    private val productDao: ProductDao,
    private val apiService: WooCommerceApiService,
    private val apiKey: String,
    private val apiSecret: String
) {
    private val TAG = "ProductRepository"

    // Local data access
    fun getAllProductsFlow(): Flow<List<ProductEntity>> {
        return productDao.getAllProductsFlow()
    }

    fun getProductsByCategoryFlow(categoryId: Long): Flow<List<ProductEntity>> {
        return productDao.getProductsByCategoryFlow(categoryId)
    }

    fun searchProductsFlow(query: String): Flow<List<ProductEntity>> {
        return productDao.searchProductsFlow(query)
    }

    suspend fun getProductById(productId: Long): ProductEntity? {
        return productDao.getProductById(productId)
    }

    suspend fun getAllCategories(): List<Pair<Long, String>> {
        val categoryResults = productDao.getAllCategories()
        val result = mutableListOf<Pair<Long, String>>()

        categoryResults.forEach { categoryResult ->
            val ids = categoryResult.category_ids
            val names = categoryResult.category_names

            for (i in ids.indices) {
                if (i < names.size) {
                    result.add(Pair(ids[i], names[i]))
                }
            }
        }

        return result.distinctBy { it.first }
    }

    // Network operations
    suspend fun refreshProducts(categoryId: Long? = null): Result<List<Product>> {
        return try {
            val response = apiService.getProducts(
                consumerKey = apiKey,
                consumerSecret = apiSecret,
                categoryId = categoryId,
                perPage = 100
            )

            if (response.isSuccessful) {
                val products = response.body() ?: emptyList()

                // Save to database
                val productEntities = products.map { it.toProductEntity() }
                productDao.insertProducts(productEntities)

                Result.success(products)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing products", e)
            Result.failure(e)
        }
    }

    suspend fun getProduct(productId: Long): Result<Product> {
        return try {
            val response = apiService.getProduct(
                productId = productId,
                consumerKey = apiKey,
                consumerSecret = apiSecret
            )

            if (response.isSuccessful) {
                val product = response.body()
                    ?: return Result.failure(Exception("Product not found"))

                // Update local database
                productDao.insertProduct(product.toProductEntity())

                Result.success(product)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching product", e)
            Result.failure(e)
        }
    }

    suspend fun updateProduct(product: Product): Result<Product> {
        return try {
            val response = apiService.updateProduct(
                productId = product.id,
                product = product,
                consumerKey = apiKey,
                consumerSecret = apiSecret
            )

            if (response.isSuccessful) {
                val updatedProduct = response.body()
                    ?: return Result.failure(Exception("Failed to update product"))

                // Update local database
                productDao.insertProduct(updatedProduct.toProductEntity())

                Result.success(updatedProduct)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating product", e)
            Result.failure(e)
        }
    }

    suspend fun updateProductStock(productId: Long, newStockQuantity: Int?): Result<Unit> {
        return try {
            val currentProduct = getProduct(productId).getOrNull()
                ?: return Result.failure(Exception("Product not found"))

            // Update locally first
            productDao.updateProductStock(productId, newStockQuantity)

            // Create updated product object
            val updatedProduct = currentProduct.copy(
                stockQuantity = newStockQuantity
            )

            // Send to API
            val updateResult = updateProduct(updatedProduct)

            if (updateResult.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(updateResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating product stock", e)
            Result.failure(e)
        }
    }

    suspend fun updateProductPrices(
        productId: Long,
        regularPrice: String,
        salePrice: String
    ): Result<Unit> {
        return try {
            val currentProduct = getProduct(productId).getOrNull()
                ?: return Result.failure(Exception("Product not found"))

            // Update locally first
            productDao.updateProductPrices(productId, regularPrice, salePrice)

            // Create updated product object
            val updatedProduct = currentProduct.copy(
                regularPrice = regularPrice,
                salePrice = salePrice
            )

            // Send to API
            val updateResult = updateProduct(updatedProduct)

            if (updateResult.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(updateResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating product prices", e)
            Result.failure(e)
        }
    }

    // Helper methods
    private fun Product.toProductEntity(): ProductEntity {
        return ProductEntity(
            id = id,
            name = name,
            status = status,
            description = description,
            shortDescription = shortDescription,
            sku = sku,
            price = price,
            regularPrice = regularPrice,
            salePrice = salePrice,
            onSale = onSale,
            stockQuantity = stockQuantity,
            stockStatus = stockStatus,
            categoryIds = categories.map { it.id },
            categoryNames = categories.map { it.name },
            imageUrl = images.firstOrNull()?.src
        )
    }
}