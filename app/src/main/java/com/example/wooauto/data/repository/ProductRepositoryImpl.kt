package com.example.wooauto.data.repository

import android.util.Log
import com.example.wooauto.data.local.dao.ProductDao
import com.example.wooauto.data.local.entities.ProductEntity
import com.example.wooauto.data.mappers.ProductMapper
import com.example.wooauto.data.remote.api.WooCommerceApiService
import com.example.wooauto.data.remote.WooCommerceApi
import com.example.wooauto.data.remote.WooCommerceApiFactory
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.remote.dto.CategoryDto
import com.example.wooauto.data.remote.dto.ProductDto
import com.example.wooauto.data.remote.dto.toCategory
import com.example.wooauto.data.remote.dto.toProduct
import com.example.wooauto.data.remote.impl.WooCommerceApiImpl
import com.example.wooauto.domain.models.Product
import com.example.wooauto.domain.models.Category
import com.example.wooauto.domain.repositories.DomainProductRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.example.wooauto.data.remote.models.ProductResponse
import com.example.wooauto.data.remote.models.CategoryResponse
import com.example.wooauto.data.remote.models.ImageResponse
import com.example.wooauto.data.remote.models.AttributeResponse
import com.example.wooauto.data.remote.ApiError

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

    override suspend fun getProductById(productId: Long): Product? = withContext(Dispatchers.IO) {
        try {
            // 首先尝试从本地获取
            val localProduct = productDao.getProductById(productId)?.let {
                ProductMapper.mapEntityToDomain(it)
            }
            
            if (localProduct != null) {
                return@withContext localProduct
            }
            
            // 如果本地没有，尝试从远程获取
            val remoteProduct = try {
                val response = apiService.getProduct(productId)
                ProductMapper.mapResponseToDomain(response)
            } catch (e: Exception) {
                Log.e("ProductRepositoryImpl", "从API获取产品失败", e)
                null
            }
            
            // 如果远程获取成功，保存到本地
            if (remoteProduct != null) {
                productDao.insertProduct(ProductMapper.mapDomainToEntity(remoteProduct))
            }
            
            return@withContext remoteProduct
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "获取产品详情失败", e)
            null
        }
    }

    override suspend fun refreshProducts(categoryId: Long?): Result<List<Product>> = withContext(Dispatchers.IO) {
        try {
            Log.d("ProductRepositoryImpl", "开始刷新产品数据，分类ID: ${categoryId ?: "全部"}")
            
            // 获取配置
            val config = settingsRepository.getWooCommerceConfig()
            Log.d("ProductRepositoryImpl", "当前API配置: ${config.toString()}")
            
            // 检查配置是否有效
            if (!config.isValid()) {
                Log.e("ProductRepositoryImpl", "API配置无效，某些必要参数为空")
                return@withContext Result.failure(Exception("API配置无效，请检查设置"))
            }

            // 使用getApi方法获取API实例，确保使用最新的配置
            val api = getApi(config)
            
            Log.d("ProductRepositoryImpl", "调用API获取产品列表")
            
            try {
                // 先获取所有产品
                val allProducts = api.getProducts(1, 100)
                Log.d("ProductRepositoryImpl", "成功获取所有产品列表，共 ${allProducts.size} 个产品")
                
                // 转换所有产品
                val allDomainProducts = allProducts.map { it.toProduct() }
                
                // 更新全局缓存
                cachedProducts = allDomainProducts
                isProductsCached = true
                
                // 更新数据库 - 无论是否有分类过滤，都保存所有产品
                val entities = allDomainProducts.map { ProductMapper.mapDomainToEntity(it) }
                productDao.deleteAllProducts() // 清除旧数据
                productDao.insertProducts(entities) // 插入新数据
                
                // 更新内存中的数据流
                _allProductsFlow.value = allDomainProducts
                
                // 如果有分类ID，过滤产品并返回
                val filteredProducts = if (categoryId != null) {
                    val filtered = allDomainProducts.filter { product -> 
                        product.categories.any { it.id == categoryId }
                    }
                    Log.d("ProductRepositoryImpl", "按分类ID $categoryId 过滤后得到 ${filtered.size} 个产品")
                    
                    // 更新分类产品缓存
                    val currentMap = _productsByCategoryFlow.value.toMutableMap()
                    currentMap[categoryId] = filtered
                    _productsByCategoryFlow.value = currentMap
                    
                    filtered
                } else {
                    allDomainProducts
                }
                
                return@withContext Result.success(filteredProducts)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val error = when (e) {
                    is ApiError -> e
                    else -> ApiError.fromException(e)
                }
                
                Log.e("ProductRepositoryImpl", "获取产品列表失败: ${error.code} - ${error.message}", e)
                return@withContext Result.failure(error)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val error = when (e) {
                is ApiError -> e
                else -> ApiError.fromException(e)
            }
            
            Log.e("ProductRepositoryImpl", "刷新产品失败: ${error.code} - ${error.message}", e)
            return@withContext Result.failure(error)
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
            Log.d("ProductRepositoryImpl", "获取所有分类")
            
            // 获取配置
            val config = settingsRepository.getWooCommerceConfig()
            
            // 检查配置是否有效
            if (!config.isValid()) {
                Log.e("ProductRepositoryImpl", "API配置无效，某些必要参数为空")
                return emptyList()
            }

            // 使用getApi方法获取API实例，确保使用最新的配置
            val api = getApi(config)
            
            // 获取分类列表
            val categories = api.getCategories(1, 100)
            Log.d("ProductRepositoryImpl", "成功获取分类列表，共 ${categories.size} 个分类")
            
            categories.map { it.id to it.name }
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "获取分类列表时出错: ${e.message}", e)
            // 检查是否是认证错误 (401)
            if (e.message?.contains("401") == true) {
                Log.e("ProductRepositoryImpl", "API认证错误 (401)，请检查consumer key和secret是否正确")
            }
            emptyList()
        }
    }

    override suspend fun updateProduct(product: Product): Result<Product> = withContext(Dispatchers.IO) {
        try {
            // 首先更新本地数据库
            productDao.updateProduct(ProductMapper.mapDomainToEntity(product))
            
            // 获取配置
            val config = settingsRepository.getWooCommerceConfig()
            
            // 检查配置是否有效
            if (!config.isValid()) {
                Log.e("ProductRepositoryImpl", "API配置无效，某些必要参数为空")
                return@withContext Result.failure(Exception("API配置无效，请检查设置"))
            }

            // 使用getApi方法获取API实例，确保使用最新的配置
            val api = getApi(config)
            
            // 然后更新远程
            val updates = mutableMapOf<String, Any>()

            updates["name"] = product.name

            // 为简单起见，这里 product.description 在领域模型中是非空的 并且总是被发送（即使是空字符串）
            updates["description"] = product.description 

            updates["regular_price"] = product.regularPrice

            if (product.salePrice.isNotEmpty()) {
                updates["sale_price"] = product.salePrice
            }
            
            updates["stock_status"] = product.stockStatus
            updates["manage_stock"] = false // 固定为 false

            val response = api.updateProduct(product.id, updates)
            val updatedProduct = response.toProduct()
            val entity = ProductMapper.mapDomainToEntity(updatedProduct)
            productDao.updateProduct(entity)
            
            Log.d("ProductRepositoryImpl", "成功更新产品: ${product.id} - ${product.name}")
            Result.success(updatedProduct)
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "更新产品失败", e)
            Result.failure(e)
        }
    }

    override suspend fun updateProductStock(productId: Long, newStockQuantity: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("ProductRepositoryImpl", "更新产品库存: ID=$productId, 新库存=$newStockQuantity")
            
            // 获取配置
            val config = settingsRepository.getWooCommerceConfig()
            
            // 检查配置是否有效
            if (!config.isValid()) {
                Log.e("ProductRepositoryImpl", "API配置无效，某些必要参数为空")
                return@withContext Result.failure(Exception("API配置无效，请检查设置"))
            }

            // 使用getApi方法获取API实例，确保使用最新的配置
            val api = getApi(config)
            
            val updates = mapOf<String, Any>(
                "manage_stock" to (newStockQuantity != null),
                "stock_quantity" to (newStockQuantity ?: 0),
                "stock_status" to if (newStockQuantity != null && newStockQuantity > 0) "instock" else "outofstock"
            )
            
            // 更新远程
            val response = api.updateProduct(productId, updates)
            val updatedProduct = response.toProduct()
            
            // 更新本地
            val localProduct = productDao.getProductById(productId)
            if (localProduct != null) {
                val updatedEntity = localProduct.copy(
                    stockQuantity = newStockQuantity,
                    stockStatus = if (newStockQuantity != null && newStockQuantity > 0) "instock" else "outofstock"
                )
                productDao.updateProduct(updatedEntity)
            } else {
                // 如果本地没有，保存远程获取的产品
                productDao.insertProduct(ProductMapper.mapDomainToEntity(updatedProduct))
            }
            
            Log.d("ProductRepositoryImpl", "成功更新产品库存: ID=$productId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "更新产品库存失败", e)
            Result.failure(e)
        }
    }

    override suspend fun updateProductPrices(
        productId: Long,
        regularPrice: String,
        salePrice: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("ProductRepositoryImpl", "更新产品价格: ID=$productId, 原价=$regularPrice, 促销价=$salePrice")
            
            // 获取配置
            val config = settingsRepository.getWooCommerceConfig()
            
            // 检查配置是否有效
            if (!config.isValid()) {
                Log.e("ProductRepositoryImpl", "API配置无效，某些必要参数为空")
                return@withContext Result.failure(Exception("API配置无效，请检查设置"))
            }

            // 使用getApi方法获取API实例，确保使用最新的配置
            val api = getApi(config)
            
            val updates = mapOf<String, Any>(
                "regular_price" to regularPrice,
                "sale_price" to salePrice
            )

            val response = api.updateProduct(productId, updates)
            val updatedProduct = response.toProduct()
            val entity = ProductMapper.mapDomainToEntity(updatedProduct)
            productDao.updateProduct(entity)
            
            Log.d("ProductRepositoryImpl", "成功更新产品价格: ID=$productId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "更新产品价格失败", e)
            Result.failure(e)
        }
    }

    override suspend fun getProducts(): List<Product> = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "获取产品列表")
        if (!isProductsCached) {
            refreshProducts(null)
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

    override suspend fun refreshCategories(): Boolean {
        return try {
            Log.d("ProductRepositoryImpl", "开始刷新分类数据")
            
            // 获取配置
            val config = settingsRepository.getWooCommerceConfig()
            
            // 检查配置是否有效
            if (!config.isValid()) {
                Log.e("ProductRepositoryImpl", "API配置无效，某些必要参数为空")
                return false
            }

            // 使用getApi方法获取API实例，确保使用最新的配置
            val api = getApi(config)
            
            Log.d("ProductRepositoryImpl", "调用API获取分类列表")
            // 获取CategoryDto列表并转换为Category列表
            try {
                val dtoList = api.getCategories(1, 100)
                Log.d("ProductRepositoryImpl", "成功获取分类列表，共 ${dtoList.size} 个分类")
                val categories = dtoList.map { it.toCategory() }
                
                // 更新缓存
                cachedCategories = categories
                isCategoriesCached = true
                
                true
            } catch (e: Exception) {
                val error = when (e) {
                    is ApiError -> e
                    else -> ApiError.fromException(e)
                }
                
                Log.e("ProductRepositoryImpl", "获取分类列表失败: ${error.code} - ${error.message}", e)
                false
            }
        } catch (e: Exception) {
            val error = when (e) {
                is ApiError -> e
                else -> ApiError.fromException(e)
            }
            
            Log.e("ProductRepositoryImpl", "刷新分类失败: ${error.code} - ${error.message}", e)
            false
        }
    }

    // 添加getApi方法
    private suspend fun getApi(config: WooCommerceConfig? = null): WooCommerceApi {
        val actualConfig = config ?: settingsRepository.getWooCommerceConfig()
        return apiFactory.createApi(actualConfig)
    }
    
    override suspend fun testConnection(config: WooCommerceConfig): Boolean = withContext(Dispatchers.IO) {
        Log.d("ProductRepositoryImpl", "测试API连接: siteUrl=${config.siteUrl}")
        Log.d("ProductRepositoryImpl", "Consumer Key: ${config.consumerKey.take(5)}..., Secret: ${config.consumerSecret.take(5)}...")
        
        try {
            // 使用getApi方法获取API实例
            val api = getApi(config)
            
            // 尝试获取产品列表
            val products = api.getProducts(1, 1)
            Log.d("ProductRepositoryImpl", "API连接测试成功，获取到${products.size}个产品")
            true
        } catch (e: Exception) {
            Log.e("ProductRepositoryImpl", "API连接测试失败: ${e.message}", e)
            // 检查是否具有明确的错误消息
            if (e.message?.contains("401") == true) {
                Log.e("ProductRepositoryImpl", "API认证错误 (401)，请检查consumer key和secret是否正确")
            } else if (e.message?.contains("404") == true) {
                Log.e("ProductRepositoryImpl", "API端点未找到 (404)，请检查URL是否正确")
            }
            false
        }
    }

    override suspend fun clearCache() {
        Log.d("ProductRepositoryImpl", "清除所有缓存")
        withContext(Dispatchers.IO) {
            cachedProducts = emptyList()
            cachedCategories = emptyList()
            isProductsCached = false
            isCategoriesCached = false
        }
    }
    
    // 清除产品缓存的别名方法
    suspend fun clearProductCache() {
        Log.d("ProductRepositoryImpl", "清除产品缓存")
        clearCache()
    }

    // 扩展函数，将Product转换为更新API所需的Map
    private fun Product.toUpdateMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "description" to description,
            "regular_price" to regularPrice,
            "sale_price" to salePrice,
            "stock_quantity" to (stockQuantity ?: 0),
            "stock_status" to stockStatus,
            "manage_stock" to (stockQuantity != null)
        )
    }

    // 添加从ProductDto到ProductResponse的转换方法
    private fun ProductDto.toProductResponse(): ProductResponse {
        return ProductResponse(
            id = id,
            name = name,
            description = description,
            price = price,
            regularPrice = regularPrice,
            salePrice = salePrice,
            stockStatus = stockStatus,
            stockQuantity = stockQuantity,
            categories = categories?.map { 
                CategoryResponse(
                    id = it.id,
                    name = it.name,
                    slug = it.slug
                )
            } ?: emptyList(),
            images = images.map { 
                ImageResponse(
                    id = it.id ?: 0,
                    src = it.src ?: "",
                    name = "",
                    alt = null
                )
            },
            attributes = null,
            variations = null
        )
    }

    override suspend fun refreshProducts(): Boolean {
        return try {
            val result = refreshProducts(null)
            result.isSuccess
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("ProductRepositoryImpl", "刷新产品失败", e)
            false
        }
    }
}