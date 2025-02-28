package com.example.wooauto.data.repository

import android.util.Log
import com.example.wooauto.data.local.dao.ProductDao
import com.example.wooauto.data.mappers.ProductMapper
import com.example.wooauto.data.remote.api.WooCommerceApiService
import com.example.wooauto.data.remote.WooCommerceApi
import com.example.wooauto.data.remote.WooCommerceApiFactory
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.remote.dto.toCategory
import com.example.wooauto.data.remote.dto.toProduct
import com.example.wooauto.domain.models.Product
import com.example.wooauto.domain.models.Category
import com.example.wooauto.domain.repositories.DomainProductRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val apiService: WooCommerceApiService,
    private val settingsRepository: DomainSettingRepository,
    private val apiFactory: WooCommerceApiFactory
) : DomainProductRepository {

    private var cachedProducts: List<Product> = emptyList()
    private var cachedCategories: List<Category> = emptyList()
    private var isProductsCached = false
    private var isCategoriesCached = false

    private val _allProductsFlow = MutableStateFlow<List<Product>>(emptyList())
    private val _productsByCategoryFlow = MutableStateFlow<Map<Long, List<Product>>>(emptyMap())
    private val _searchResultsFlow = MutableStateFlow<Map<String, List<Product>>>(emptyMap())

    private suspend fun getApi(config: WooCommerceConfig? = null): WooCommerceApi {
        val actualConfig = config ?: settingsRepository.getWooCommerceConfig()
        return apiFactory.createApi(actualConfig)
    }

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

            val products = ProductMapper.mapEntitiesListToDomainList(entities)
            
            _allProductsFlow.value = getProducts()
            val updatedCategoryMap = _productsByCategoryFlow.value.toMutableMap()
            categoryId?.let { 
                updatedCategoryMap[it] = products
                _productsByCategoryFlow.value = updatedCategoryMap
            }
            
            Result.success(products)
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

    override suspend fun getProducts(): List<Product> = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "获取产品列表")
        if (!isProductsCached) {
            refreshProducts()
        }
        return@withContext cachedProducts
    }

    override suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "获取分类列表")
        if (!isCategoriesCached) {
            refreshCategories()
        }
        return@withContext cachedCategories
    }

    override suspend fun updateProduct(product: Product): Product = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "更新产品: ${product.id}")
        try {
            val api = getApi()
            val response = api.updateProduct(product.id, product.toUpdateMap())
            val updatedProduct = response.toProduct()
            
            // 更新缓存
            val index = cachedProducts.indexOfFirst { it.id == product.id }
            if (index != -1) {
                cachedProducts = cachedProducts.toMutableList().apply {
                    set(index, updatedProduct)
                }
            }
            
            return@withContext updatedProduct
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "更新产品失败", e)
            throw e
        }
    }

    override suspend fun getProductById(id: Long): Product? = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "按ID获取产品: $id")
        try {
            // 先从缓存中查找
            cachedProducts.find { it.id == id }?.let { return@withContext it }
            
            // 如果缓存中没有，从API获取
            val api = getApi()
            val response = api.getProduct(id)
            return@withContext response.toProduct()
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "获取产品失败: $id", e)
            return@withContext null
        }
    }

    override suspend fun getProductsByCategory(categoryId: Long): List<Product> = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "按分类获取产品: $categoryId")
        return@withContext getProducts().filter { product -> 
            product.categories.any { it.id == categoryId }
        }
    }

    override suspend fun searchProducts(query: String): List<Product> = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "搜索产品: $query")
        if (query.isBlank()) {
            return@withContext getProducts()
        }
        
        val normalizedQuery = query.trim().lowercase()
        return@withContext getProducts().filter { product ->
            product.name.lowercase().contains(normalizedQuery) ||
            product.sku.lowercase().contains(normalizedQuery) ||
            product.description.lowercase().contains(normalizedQuery)
        }
    }

    override suspend fun refreshProducts(): Boolean = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "刷新产品列表")
        try {
            val api = getApi()
            val products = api.getProducts().map { it.toProduct() }
            cachedProducts = products
            isProductsCached = true
            Log.d("ProductRepositoryImpl", "刷新产品成功: ${products.size}个产品")
            return@withContext true
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "刷新产品失败", e)
            return@withContext false
        }
    }

    override suspend fun refreshCategories(): Boolean = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "刷新分类列表")
        try {
            val api = getApi()
            val categories = api.getCategories().map { it.toCategory() }
            cachedCategories = categories
            isCategoriesCached = true
            Log.d("ProductRepositoryImpl", "刷新分类成功: ${categories.size}个分类")
            return@withContext true
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "刷新分类失败", e)
            return@withContext false
        }
    }

    override suspend fun testConnection(config: WooCommerceConfig?): Boolean = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "测试API连接")
        try {
            val api = getApi(config)
            // 简单地尝试获取一个分类列表，如果成功就说明连接正常
            val response = api.getCategories(perPage = 1)
            Log.d("ProductRepositoryImpl", "连接测试成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "连接测试失败", e)
            return@withContext false
        }
    }
    
    private fun Product.toUpdateMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "regular_price" to regularPrice,
            "sale_price" to salePrice,
            "description" to description,
            "short_description" to shortDescription,
            "manage_stock" to manageStock,
            "stock_quantity" to stockQuantity,
            "status" to status
        )
    }
}