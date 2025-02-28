package com.wooauto.data.repository

import com.wooauto.data.local.dao.ProductDao
import com.wooauto.data.mappers.ProductMapper
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.domain.models.Product
import com.wooauto.domain.repositories.DomainProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val apiService: WooCommerceApiService
) : DomainProductRepository {

    override fun getAllProductsFlow(): Flow<List<Product>> {
        return productDao.getAllProducts().map { entities ->
            ProductMapper.mapEntitiesListToDomainList(entities)
        }
    }

    override fun getProductsByCategoryFlow(categoryId: Long): Flow<List<Product>> {
        return productDao.getProductsByCategory(categoryId.toString()).map { entities ->
            ProductMapper.mapEntitiesListToDomainList(entities)
        }
    }

    override fun searchProductsFlow(query: String): Flow<List<Product>> {
        return productDao.searchProducts("%$query%").map { entities ->
            ProductMapper.mapEntitiesListToDomainList(entities)
        }
    }

    override suspend fun getProductById(productId: Long): Product? {
        return productDao.getProductById(productId)?.let {
            ProductMapper.mapEntityToDomain(it)
        }
    }

    override suspend fun refreshProducts(categoryId: Long?): Result<List<Product>> {
        return try {
            val params = mutableMapOf<String, String>()
            categoryId?.let { params["category"] = it.toString() }

            val response = apiService.getProducts(1, params = params)
            val entities = ProductMapper.mapResponsesListToEntitiesList(response)
            productDao.insertProducts(entities)

            Result.success(ProductMapper.mapEntitiesListToDomainList(entities))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getProduct(productId: Long): Result<Product> {
        return try {
            val response = apiService.getProduct(productId)
            val entity = ProductMapper.mapResponseToEntity(response)
            productDao.insertProduct(entity)
            Result.success(ProductMapper.mapEntityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllCategories(): List<Pair<Long, String>> {
        return try {
            val response = apiService.getCategories()
            response.map { it.id to it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun updateProduct(product: Product): Result<Product> {
        return try {
            val updates = mapOf(
                "regular_price" to product.regularPrice,
                "sale_price" to product.salePrice,
                "stock_quantity" to product.stockQuantity,
                "status" to product.status
            )

            val response = apiService.updateProduct(product.id, updates)
            val entity = ProductMapper.mapResponseToEntity(response)
            productDao.updateProduct(entity)
            Result.success(ProductMapper.mapEntityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProductStock(productId: Long, newStockQuantity: Int?): Result<Unit> {
        return try {
            val updates = mapOf(
                "stock_quantity" to newStockQuantity
            )

            val response = apiService.updateProduct(productId, updates)
            val entity = ProductMapper.mapResponseToEntity(response)
            productDao.updateProduct(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProductPrices(
        productId: Long,
        regularPrice: String,
        salePrice: String
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "regular_price" to regularPrice,
                "sale_price" to salePrice
            )

            val response = apiService.updateProduct(productId, updates)
            val entity = ProductMapper.mapResponseToEntity(response)
            productDao.updateProduct(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}