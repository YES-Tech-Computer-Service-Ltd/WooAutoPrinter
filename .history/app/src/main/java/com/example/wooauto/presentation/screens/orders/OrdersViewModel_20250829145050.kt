package com.example.wooauto.presentation.screens.orders

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asFlow
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.templates.TemplateType
import com.example.wooauto.service.BackgroundPollingService
import com.example.wooauto.service.BackgroundPollingService.Companion.ACTION_ORDERS_UPDATED
import com.example.wooauto.service.BackgroundPollingService.Companion.ACTION_NEW_ORDERS_RECEIVED
import com.example.wooauto.service.BackgroundPollingService.Companion.EXTRA_ORDER_COUNT
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.wooauto.utils.UiLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.wooauto.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: DomainOrderRepository,
    private val settingRepository: DomainSettingRepository,
    private val printerManager: PrinterManager,
    private val wooCommerceConfig: com.example.wooauto.data.local.WooCommerceConfig,
    @ApplicationContext private val context: Context,
    val licenseManager: com.example.wooauto.licensing.LicenseManager
) : ViewModel() {

    companion object {
        private const val TAG = "OrdersViewModel"
    }

    // 添加防重复调用的机制
    private var lastRefreshTime = 0L
    private var lastConfigCheckTime = 0L
    private val minRefreshInterval = 2000L // 最小刷新间隔2秒
    private val minConfigCheckInterval = 3000L // 最小配置检查间隔3秒
    private var isRefreshing = false
    private var isCheckingConfig = false

    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _selectedOrder = MutableStateFlow<Order?>(null)
    val selectedOrder: StateFlow<Order?> = _selectedOrder.asStateFlow()
    
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    
    // 当前选中的状态过滤条件
    private val _currentStatusFilter = MutableStateFlow<String?>(null)
    private val currentStatusFilter: StateFlow<String?> = _currentStatusFilter.asStateFlow()

    // 导航事件
    private val _navigationEvent = MutableStateFlow<String?>(null)
    val navigationEvent: StateFlow<String?> = _navigationEvent.asStateFlow()
    
    // 添加未读订单相关状态
    private val _unreadOrders = MutableStateFlow<List<Order>>(emptyList())
    val unreadOrders: StateFlow<List<Order>> = _unreadOrders.asStateFlow()
    
    private val _unreadOrdersCount = MutableStateFlow(0)
    val unreadOrdersCount: StateFlow<Int> = _unreadOrdersCount.asStateFlow()
    
    // 货币符号状态
    private val _currencySymbol = MutableStateFlow("C$")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    // 仅保留一个活跃的订单流收集，避免同时收集全量与筛选流造成 UI 覆盖/闪烁
    private var ordersCollectJob: Job? = null
    private var filterLoadingTimeoutJob: Job? = null

    // 空态防抖：筛选切换后短时间内不展示“未找到匹配订单”
    private val _emptyGuardActive = MutableStateFlow(false)
    val emptyGuardActive: StateFlow<Boolean> = _emptyGuardActive.asStateFlow()

    // 广播接收器
    private val ordersUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_ORDERS_UPDATED -> {
                    UiLog.d(TAG, "收到订单更新广播，刷新订单列表")
                    refreshOrders()
                }
                ACTION_NEW_ORDERS_RECEIVED -> {
                    val orderCount = intent.getIntExtra(EXTRA_ORDER_COUNT, 0)
                    UiLog.d(TAG, "收到新订单广播，新订单数量: $orderCount，刷新订单列表")
                    refreshOrders()
                }
            }
        }
    }

    init {
        UiLog.d(TAG, "OrdersViewModel 初始化")
        
        // 检查配置和注册广播
        checkConfiguration()
        registerBroadcastReceiver()
        
        // 注册接收刷新订单的广播
        registerRefreshOrdersBroadcastReceiver()
        
        // 观察订单数据
        observeOrders()
        
        // 初始化货币符号
        loadCurrencySymbol()
        
        // 监听许可证状态变化
        viewModelScope.launch {
            licenseManager.eligibilityInfo.asFlow()
                .collect { eligibilityInfo ->
                    UiLog.d(TAG, "许可证资格状态变化: $eligibilityInfo")
                    // 当许可证状态变化时，重新检查配置
                    val configResult = checkApiConfiguration()
                    UiLog.d(TAG, "许可证状态变化后重新检查配置结果: $configResult")
                }
        }
        
        // 应用启动时验证未读订单状态，然后重新加载未读订单
        viewModelScope.launch {
            try {
                Log.d(TAG, "应用启动：初始化未读订单状态")
                
                // 首先强制重置未读计数为0，防止显示错误的未读数量
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
                
                // 先清理数据库中不存在的未读订单ID
                val cleanupResult = cleanupNonExistentUnreadOrders()
                
                // 无论是否有需要清理的条目，都验证一次未读订单的有效性
                Log.d(TAG, "清理结果：移除了 $cleanupResult 个无效未读标记")
                
                // 验证未读订单有效性
                val verifyResult = verifyUnreadOrdersValid()
                if (!verifyResult) {
                    Log.d(TAG, "发现无效未读订单，重置所有未读状态")
                    resetAllUnreadStatus()
                    
                    // 延迟执行发送广播通知UI更新
                    delay(300)
                    val updateIntent = Intent(ACTION_ORDERS_UPDATED)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent)
                } else {
                    // 正常加载未读订单
                    Log.d(TAG, "未读订单验证通过，正常加载未读订单")
                    delay(300)
                    loadUnreadOrders()
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动时加载未读订单出错", e)
                // 确保未读计数为0
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 注销广播接收器
        try {
            context.unregisterReceiver(ordersUpdateReceiver)
            Log.d(TAG, "注销订单广播接收器")
        } catch (e: Exception) {
            Log.e(TAG, "注销订单广播接收器失败: ${e.message}")
        }
    }

    private fun registerBroadcastReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_ORDERS_UPDATED)
                addAction(ACTION_NEW_ORDERS_RECEIVED)
            }
            // 根据API级别使用相应的注册方法
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(ordersUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                Log.d(TAG, "使用RECEIVER_NOT_EXPORTED标志成功注册订单广播接收器(Android 13+)")
            } else {
                context.registerReceiver(ordersUpdateReceiver, filter)
                Log.d(TAG, "成功注册订单广播接收器(Android 12及以下)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册订单广播接收器失败: ${e.message}")
        }
    }

    private fun checkConfiguration() {
        // 减少防重复检查的限制，缩短间隔时间
        val currentTime = System.currentTimeMillis()
        if (isCheckingConfig || (currentTime - lastConfigCheckTime < 1000L)) { // 改为1秒间隔
            Log.d(TAG, "配置检查请求过于频繁或正在进行中，忽略本次调用")
            return
        }
        
        isCheckingConfig = true
        lastConfigCheckTime = currentTime
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "正在检查API配置")
                
                // 首先从WooCommerceConfig获取配置状态
                val configuredFromStatus = WooCommerceConfig.isConfigured.first()
                Log.d(TAG, "从状态流获取的API配置状态: $configuredFromStatus")
                
                // 初始设置为加载中
                _isLoading.value = true
                
                // 先检查是否有缓存的订单数据
                val cachedOrders = orderRepository.getCachedOrders()
                if (cachedOrders.isNotEmpty()) {
                    Log.d(TAG, "存在缓存订单数据 ${cachedOrders.size} 个，临时显示缓存数据")
                    _orders.value = cachedOrders
                    // 如果有缓存数据，提前将isLoading设为false，让用户可以查看缓存数据
                    _isLoading.value = false
                }
                
                // 直接调用改进后的checkApiConfiguration方法
                val apiConfigurationValid = checkApiConfiguration()
                Log.d(TAG, "API配置检查结果: $apiConfigurationValid")
                
                // 如果API配置有效，尝试刷新订单数据
                if (apiConfigurationValid) {
                    Log.d(TAG, "API配置有效，尝试刷新订单数据")
                    try {
                        val result = orderRepository.refreshOrders()
                        if (result.isSuccess) {
                            val orders = result.getOrNull() ?: emptyList()
                            Log.d(TAG, "成功刷新订单数据，获取到 ${orders.size} 个订单")
                            _orders.value = orders
                        } else {
                            Log.w(TAG, "刷新订单数据失败: ${result.exceptionOrNull()?.message}")
                            // 即使刷新失败，如果配置有效，也应该保持配置状态
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "刷新订单数据异常: ${e.message}")
                        // 即使刷新异常，如果配置有效，也应该保持配置状态
                    }
                } else {
                    Log.d(TAG, "API配置无效")
                }

                // 监听配置变化
                viewModelScope.launch {
                    WooCommerceConfig.isConfigured.collectLatest { configured ->
                        Log.d(TAG, "API配置状态变更: $configured")
                        _isConfigured.value = configured
                        
                        // 如果配置状态变为已配置，尝试刷新订单
                        if (configured) {
                            Log.d(TAG, "配置状态变为已配置，尝试刷新订单")
                            refreshOrders()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查配置时出错: ${e.message}", e)
                // 检查是否有缓存数据来决定配置状态
                val cachedOrders = try {
                    orderRepository.getCachedOrders()
                } catch (ex: Exception) {
                    emptyList()
                }
                
                if (cachedOrders.isNotEmpty()) {
                    Log.d(TAG, "虽然配置检查出错，但有缓存数据，设置为已配置状态")
                    _isConfigured.value = true
                } else {
                    _isConfigured.value = false
                    _errorMessage.value = "无法检查API配置: ${e.message}"
                }
                _isLoading.value = false
            } finally {
                isCheckingConfig = false
            }
        }
    }
    
    private fun observeOrders() {
        ordersCollectJob?.cancel()
        ordersCollectJob = viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "开始观察订单数据流")
                // 首先从数据库加载缓存的订单，不等待API
                val cachedOrders = orderRepository.getCachedOrders()
                if (cachedOrders.isNotEmpty()) {
                    Log.d("OrdersViewModel", "从缓存加载${cachedOrders.size}个订单")
                    _orders.value = cachedOrders
                    _isLoading.value = false
                }
                
                // 观察订单流以获取持续更新
                orderRepository.getAllOrdersFlow().collectLatest { ordersList ->
                    if (BuildConfig.DEBUG) Log.d("OrdersViewModel", "订单流更新，收到${ordersList.size}个订单")
                    _orders.value = ordersList
                    _isLoading.value = false
                }
            } catch (e: CancellationException) {
                // 这是预期的：切换筛选/数据源时会取消旧收集，避免误报错误
                UiLog.d(TAG, "订单流收集被取消（切换数据源或筛选）")
                throw e
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "观察订单时出错: ${e.message}")
                _errorMessage.value = "无法加载订单: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun refreshOrders() {
        // 减少防重复调用检查的限制，缩短间隔时间
        val currentTime = System.currentTimeMillis()
        if (isRefreshing || (currentTime - lastRefreshTime < 1000L)) { // 改为1秒间隔
            Log.d(TAG, "刷新请求过于频繁或正在进行中，忽略本次调用（间隔: ${currentTime - lastRefreshTime}ms）")
            return
        }
        
        isRefreshing = true
        lastRefreshTime = currentTime
        
        viewModelScope.launch {
            try {
                // 立即设置刷新状态，确保UI能看到转圈效果
                _refreshing.value = true
                _isLoading.value = true
                
                Log.d(TAG, "开始刷新订单数据...")
                
                // 添加最小刷新时间，确保用户能看到刷新动画（至少显示800毫秒）
                val startTime = System.currentTimeMillis()
                
                // 获取当前订单的打印状态映射，用于后续验证
                val currentPrintedMap = _orders.value.associateBy({ it.id }, { it.isPrinted })
                
                val apiConfigured = checkApiConfiguration()
                if (apiConfigured) {
                    val result = orderRepository.refreshOrders()
                    if (result.isSuccess) {
                        val refreshedOrders = result.getOrDefault(emptyList())
                        Log.d(TAG, "成功刷新订单数据，获取到 ${refreshedOrders.size} 个订单")
                        
                        // 验证打印状态
                        val refreshedPrintedMap = refreshedOrders.associateBy({ it.id }, { it.isPrinted })
                        
                        // 检查是否有任何打印状态丢失
                        val lostPrintStatus = currentPrintedMap.filter { it.value && refreshedPrintedMap[it.key] == false }
                        if (lostPrintStatus.isNotEmpty()) {
                            Log.e("OrdersViewModel", "【打印状态保护】警告：刷新后丢失了 ${lostPrintStatus.size} 个订单的打印状态")
                            Log.e("OrdersViewModel", "【打印状态保护】丢失打印状态的订单ID: ${lostPrintStatus.keys}")
                            
                            // 修复丢失的打印状态
                            val correctedOrders = refreshedOrders.map { order ->
                                if (lostPrintStatus.containsKey(order.id)) {
                                    order.copy(isPrinted = true)
                                } else {
                                    order
                                }
                            }
                            
                            // 使用修复后的订单列表
                            _orders.value = correctedOrders
                        } else {
                            _orders.value = refreshedOrders
                        }
                        
                        // 确保所有订单已正确持久化到数据库后再加载未读订单
                        delay(300) // 添加短暂延迟确保数据库操作完成
                        loadUnreadOrders() // 从数据库加载未读订单
                    } else {
                        Log.w(TAG, "刷新订单数据失败: ${result.exceptionOrNull()?.message}")
                        _errorMessage.value = result.exceptionOrNull()?.message ?: "刷新失败"
                    }
                } else {
                    Log.w(TAG, "API配置无效，无法刷新订单")
                    _errorMessage.value = "API配置无效，无法刷新订单"
                }
                
                // 确保刷新指示器至少显示800毫秒，让用户能看到刷新效果
                val elapsedTime = System.currentTimeMillis() - startTime
                val minRefreshTime = 800L
                if (elapsedTime < minRefreshTime) {
                    Log.d(TAG, "刷新完成太快，延迟 ${minRefreshTime - elapsedTime}ms 以显示刷新动画")
                    delay(minRefreshTime - elapsedTime)
                }
                
                Log.d(TAG, "订单刷新完成")
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "刷新订单时发生错误", e)
                _errorMessage.value = e.localizedMessage ?: "刷新订单失败"
            } finally {
                _isLoading.value = false
                _refreshing.value = false
                isRefreshing = false
            }
        }
    }
    
    /**
     * 检查API配置状态并返回结果
     * 与checkConfiguration不同，此方法立即返回结果而不是异步更新状态
     * @return 配置是否有效（包括API配置和许可证状态）
     */
    suspend fun checkApiConfiguration(): Boolean {
        return try {
            Log.d(TAG, "正在直接检查API配置状态")
            
            // 首先检查许可证状态
            val isLicensed = licenseManager.hasEligibility
            Log.d(TAG, "许可证状态检查结果: $isLicensed")
            
            if (!isLicensed) {
                Log.d(TAG, "许可证无效，但允许配置继续进行（试用期或验证异常）")
                // 注释掉许可证检查阻止配置，因为许可证管理器在验证异常时应该允许使用
                // _isConfigured.value = false
                // return false
            }
            
            // 使用注入的实例检查API配置
            val siteUrl = wooCommerceConfig.siteUrl.first()
            val consumerKey = wooCommerceConfig.consumerKey.first()
            val consumerSecret = wooCommerceConfig.consumerSecret.first()
            
            Log.d(TAG, "API配置信息检查: URL长度=${siteUrl.length}, Key长度=${consumerKey.length}, Secret长度=${consumerSecret.length}")
            
            // 检查配置是否有效
            val isValid = siteUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
            
            if (isValid) {
                Log.d(TAG, "API配置信息完整，开始测试连接...")
                // 测试API连接
                val connectionResult = try {
                    withContext(Dispatchers.IO) {
                        orderRepository.testConnection()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "API连接测试异常: ${e.message}")
                    false
                }
                
                if (connectionResult) {
                    Log.d(TAG, "API配置有效且连接测试成功")
                    _isConfigured.value = true
                    return true
                } else {
                    Log.d(TAG, "API配置信息完整但连接测试失败，仍然设置为已配置以允许用户查看缓存数据")
                    // 即使连接测试失败，如果配置信息完整，也应该设置为已配置
                    // 这样用户可以查看缓存的订单数据
                    _isConfigured.value = true
                    return true
                }
            } else {
                Log.d(TAG, "API配置信息不完整: siteUrl=${siteUrl.isNotBlank()}, key=${consumerKey.isNotBlank()}, secret=${consumerSecret.isNotBlank()}")
                _isConfigured.value = false
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查API配置状态发生异常: ${e.message}", e)
            // 异常时不直接设置为false，先检查是否有缓存数据
            val cachedOrders = try {
                orderRepository.getCachedOrders()
            } catch (ex: Exception) {
                emptyList()
            }
            
            if (cachedOrders.isNotEmpty()) {
                Log.d(TAG, "虽然配置检查异常，但有缓存数据，设置为已配置状态")
                _isConfigured.value = true
                return true
            } else {
                _isConfigured.value = false
                return false
            }
        }
    }
    
    /**
     * 检查配置是否有效
     */
    private suspend fun checkConfigurationValid(): Boolean {
        return try {
            // 使用注入的wooCommerceConfig实例
            val siteUrl = wooCommerceConfig.siteUrl.first()
            val consumerKey = wooCommerceConfig.consumerKey.first()
            val consumerSecret = wooCommerceConfig.consumerSecret.first()
            
            val isValid = siteUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
            if (isValid) {
                Log.d(TAG, "WooCommerce配置有效")
            } else {
                Log.w(TAG, "WooCommerce配置无效: URL=${siteUrl.isNotBlank()}, Key=${consumerKey.isNotBlank()}, Secret=${consumerSecret.isNotBlank()}")
            }
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "检查配置有效性出错: ${e.message}", e)
            false
        }
    }
    
    /**
     * 将UI中的中文状态和API中的英文状态互相映射
     * @param status 状态字符串
     * @param toEnglish 是否从中文转换到英文
     * @return 映射后的状态字符串
     */
    private fun mapStatusToChinese(status: String?, toEnglish: Boolean = false): String? {
        if (status == null || status.isEmpty()) return null // 空值时返回null，不发送status参数
        
        // 状态映射表
        val statusMap = mapOf(
            // 中文 to 英文
            "处理中" to "processing",
            "待付款" to "pending",
            "待处理" to "pending", // 添加别名
            "已完成" to "completed",
            "已取消" to "cancelled",
            "已退款" to "refunded",
            "失败" to "failed",
            "暂挂" to "on-hold",
            "保留" to "on-hold", // 添加别名
            // 英文 to 中文
            "processing" to "处理中",
            "pending" to "待付款",
            "completed" to "已完成",
            "cancelled" to "已取消",
            "refunded" to "已退款",
            "failed" to "失败",
            "on-hold" to "暂挂"
        )
        
        return if (toEnglish) {
            // 从中文转到英文，如果没有匹配则返回原值（可能是英文）
            statusMap[status] ?: status
        } else {
            // 从英文转到中文，如果没有匹配则返回原值
            statusMap[status] ?: status
        }
    }
    
    fun filterOrdersByStatus(status: String?) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "【状态筛选】开始按状态筛选: $status")
                _isLoading.value = true
                _emptyGuardActive.value = !status.isNullOrEmpty()
                UiLog.d(TAG, "Filter start -> status='$status', set isLoading=true, emptyGuardActive=${_emptyGuardActive.value}")
                
                // 保存当前状态过滤条件(中文状态)
                _currentStatusFilter.value = status
                
                if (status.isNullOrEmpty()) {
                    Log.d("OrdersViewModel", "状态为空，显示所有订单")
                    // 回到无过滤状态
                    _currentStatusFilter.value = null
                    _emptyGuardActive.value = false
                    UiLog.d(TAG, "Filter cleared -> observe all, emptyGuardActive=false")
                    observeOrders()
                } else {
                    // 将中文状态映射为英文状态用于API调用
                    val apiStatus = mapStatusToChinese(status, true)
                    Log.d("OrdersViewModel", "【状态筛选】映射后的API状态: '$apiStatus'")
                    
                    // 刷新对应状态的订单数据
                    val refreshResult = orderRepository.refreshOrders(apiStatus)
                    
                    if (refreshResult.isSuccess) {
                        Log.d("OrdersViewModel", "【状态筛选】成功刷新订单数据")
                        
                        // 获取刷新结果中的订单
                        val allRefreshedOrders = refreshResult.getOrDefault(emptyList())
                        Log.d("OrdersViewModel", "【状态筛选】刷新获取到 ${allRefreshedOrders.size} 个订单")
                        // 直接观察数据库该状态的流，避免UI再做一次状态过滤造成竞态
                        observeFilteredOrders(apiStatus ?: status)
                    } else {
                        // 处理错误情况
                        val error = refreshResult.exceptionOrNull()
                        Log.e("OrdersViewModel", "【状态筛选】刷新订单失败", error)
                        _errorMessage.value = error?.message ?: "未知错误"
                        // 出错时关闭加载
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "【状态筛选】过滤订单出错", e)
                _errorMessage.value = e.message
                _isLoading.value = false
            }
        }
    }
    
    fun getOrderDetails(orderId: Long) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在获取订单详情: $orderId")
                val order = orderRepository.getOrderById(orderId)
                _selectedOrder.value = order
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "获取订单详情时出错: ${e.message}")
                _errorMessage.value = "无法获取订单详情: ${e.message}"
            }
        }
    }
    
    fun updateOrderStatus(orderId: Long, newStatus: String) {
        // 记录旧状态
        val oldOrder = _orders.value.find { it.id == orderId }
        val oldStatus = oldOrder?.status

        // 1. 乐观更新本地状态
        _selectedOrder.value = _selectedOrder.value?.takeIf { it.id == orderId }?.copy(status = newStatus)
        _orders.value = _orders.value.map { if (it.id == orderId) it.copy(status = newStatus) else it }

        // 2. 异步请求后端
        viewModelScope.launch {
            try {
                Log.d(TAG, "乐观更新-正在更新订单状态: $orderId -> $newStatus")
                val result = orderRepository.updateOrderStatus(orderId, newStatus)
                if (result.isSuccess) {
                    Log.d(TAG, "乐观更新-成功更新订单状态")
                    _selectedOrder.value = result.getOrNull()
                    // 刷新订单，确保使用现有的状态过滤
                    refreshOrders()
                } else {
                    Log.e(TAG, "乐观更新-更新订单状态失败: ${result.exceptionOrNull()?.message}")
                    // 失败回滚
                    _selectedOrder.value = _selectedOrder.value?.takeIf { it.id == orderId }?.copy(status = oldStatus ?: newStatus)
                    _orders.value = _orders.value.map { if (it.id == orderId) it.copy(status = oldStatus ?: newStatus) else it }
                    _errorMessage.value = "订单状态更新失败: ${result.exceptionOrNull()?.message ?: ""}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "乐观更新-更新订单状态时出错: ${e.message}")
                // 失败回滚
                _selectedOrder.value = _selectedOrder.value?.takeIf { it.id == orderId }?.copy(status = oldStatus ?: newStatus)
                _orders.value = _orders.value.map { if (it.id == orderId) it.copy(status = oldStatus ?: newStatus) else it }
                _errorMessage.value = "订单状态更新失败: ${e.message ?: "未知错误"}"
            }
        }
    }
    
    fun markOrderAsPrinted(orderId: Long) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "【打印状态修复】开始标记订单为已打印: $orderId")
                
                // 先获取订单当前状态作为参考
                val originalOrder = orderRepository.getOrderById(orderId)
                Log.d("OrdersViewModel", "【打印状态修复】标记前订单状态: ID=$orderId, 已打印=${originalOrder?.isPrinted}, 选中订单打印状态=${_selectedOrder.value?.isPrinted}")
                
                // 先在本地更新选中的订单，确保UI能立即响应
                _selectedOrder.value?.let { selected ->
                    if (selected.id == orderId && !selected.isPrinted) {
                        val updatedSelected = selected.copy(isPrinted = true)
                        _selectedOrder.value = updatedSelected
                        Log.d("OrdersViewModel", "【打印状态修复】预先更新_selectedOrder状态: ${updatedSelected.isPrinted}")
                    }
                }
                
                // 调用仓库方法标记为已打印
                val success = orderRepository.markOrderAsPrinted(orderId)
                
                if (success) {
                    Log.d("OrdersViewModel", "【打印状态修复】成功调用markOrderAsPrinted方法, 开始更新UI状态")
                    
                    // 获取更新后的订单
                    val updatedOrder = orderRepository.getOrderById(orderId)
                    
                    updatedOrder?.let {
                        Log.d("OrdersViewModel", "【打印状态修复】获取到更新后的订单, ID=${it.id}, 打印状态: ${it.isPrinted}")
                        
                        // 更新选中的订单状态
                        _selectedOrder.value = it
                        Log.d("OrdersViewModel", "【打印状态修复】已更新_selectedOrder流, 当前值: ${_selectedOrder.value?.id}, 打印状态: ${_selectedOrder.value?.isPrinted}")
                        
                        // 同步更新订单列表中该订单的打印状态
                        val currentOrders = _orders.value
                        Log.d("OrdersViewModel", "【打印状态修复】当前订单列表有 ${currentOrders.size} 个订单")
                        
                        val updatedOrders = currentOrders.map { order ->
                            if (order.id == orderId) {
                                val updated = order.copy(isPrinted = true)
                                Log.d("OrdersViewModel", "【打印状态修复】更新订单列表项: #${order.number} (ID=${order.id}), 打印状态从 ${order.isPrinted} 改为 ${updated.isPrinted}")
                                updated
                            } else {
                                order
                            }
                        }
                        
                        // 设置更新后的订单列表
                        _orders.value = updatedOrders
                        Log.d("OrdersViewModel", "【打印状态修复】已更新_orders流, 列表大小: ${_orders.value.size}")
                        
                        // 验证是否更新成功
                        val printedOrder = _orders.value.find { it.id == orderId }
                        Log.d("OrdersViewModel", "【打印状态修复】验证更新: 订单 ID=$orderId 的最终打印状态: ${printedOrder?.isPrinted}")
                        
                        // 触发过滤订单重新加载，确保显示的列表与数据库一致
                        // 获取当前过滤状态
                        val currentStatus = _currentStatusFilter.value
                        if (currentStatus != null) {
                            Log.d("OrdersViewModel", "【打印状态修复】重新过滤订单列表，当前状态: $currentStatus")
                            filterOrdersByStatus(currentStatus)
                        }
                    } ?: run {
                        Log.e("OrdersViewModel", "【打印状态修复】无法获取更新后的订单, ID=$orderId")
                    }
                    
                    // 不立即刷新订单列表，避免从API获取状态覆盖本地状态
                    // refreshOrders()
                } else {
                    Log.e("OrdersViewModel", "【打印状态修复】标记订单为已打印失败, ID=$orderId")
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "【打印状态修复】标记订单为已打印过程中出错: ${e.message}", e)
                _errorMessage.value = "无法标记订单为已打印: ${e.message}"
            }
        }
    }
    
    fun clearSelectedOrder() {
        _selectedOrder.value = null
    }
    
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 获取所有有效的订单状态选项，包括中英文对应
     * 可用于在UI中显示状态选择器
     * @return 订单状态列表，包含中英文状态
     */
    fun getOrderStatusOptions(): List<Pair<String, String>> {
        return listOf(
            Pair("处理中", "processing"),
            Pair("待付款", "pending"),
            Pair("已完成", "completed"),
            Pair("已取消", "cancelled"),
            Pair("已退款", "refunded"),
            Pair("失败", "failed"),
            Pair("暂挂", "on-hold")
        )
    }

    /**
     * 打印订单（接受Order对象的重载）
     * @param order 订单对象
     * @param templateType 模板类型，如果为null则使用默认模板
     */
    fun printOrder(order: Order, templateType: TemplateType? = null) {
        printOrder(order.id, templateType)
    }

    /**
     * 打印订单（使用具体的模板ID）
     * @param orderId 订单ID
     * @param templateId 模板ID，如果为null则使用默认模板
     */
    fun printOrderWithTemplate(orderId: Long, templateId: String? = null) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "打印订单: $orderId, 模板ID: $templateId")
                
                // 获取订单信息
                val order = orderRepository.getOrderById(orderId)
                if (order == null) {
                    Log.e("OrdersViewModel", "未找到订单: $orderId")
                    return@launch
                }
                
                // 获取默认打印机配置
                val printerConfig = settingRepository.getDefaultPrinterConfig()
                if (printerConfig == null) {
                    Log.e("OrdersViewModel", "未找到默认打印机配置")
                    return@launch
                }
                
                // 设置手动打印标志
                settingRepository.setTemporaryManualPrintFlag(true)
                
                // 如果指定了模板ID，临时设置为默认模板ID
                if (templateId != null) {
                    // 将模板ID转换为TemplateType（为了向后兼容）
                    val templateType = when (templateId) {
                        "full_details" -> TemplateType.FULL_DETAILS
                        "delivery" -> TemplateType.DELIVERY
                        "kitchen" -> TemplateType.KITCHEN
                        else -> TemplateType.FULL_DETAILS // 自定义模板使用FULL_DETAILS类型
                    }
                    settingRepository.saveDefaultTemplateType(templateType)
                    
                    // 如果是自定义模板，保存模板ID
                    if (templateId.startsWith("custom_")) {
                        settingRepository.saveCustomTemplateId(templateId)
                        Log.d("OrdersViewModel", "保存自定义模板ID: $templateId")
                    } else {
                        // 如果是默认模板，清除手动打印的自定义模板ID，避免冲突
                        try {
                            // 通过保存空字符串来清除自定义模板ID
                            settingRepository.saveCustomTemplateId("")
                            Log.d("OrdersViewModel", "已清除手动打印的自定义模板ID，使用默认模板: $templateType")
                        } catch (e: Exception) {
                            Log.e("OrdersViewModel", "清除自定义模板ID失败: ${e.message}")
                        }
                    }
                    
                    Log.d("OrdersViewModel", "临时设置打印模板为: $templateType (ID: $templateId)")
                }
                
                // 打印订单
                val success = printerManager.printOrder(order, printerConfig)
                if (success) {
                    Log.d("OrdersViewModel", "【打印操作】打印订单成功: $orderId")
                    // 标记订单为已打印
                    markOrderAsPrinted(orderId)
                    
                    // 等待一下，确保数据库操作完成
                    delay(200)
                    
                    // 刷新选中的订单，确保UI显示更新
                    val updatedOrder = orderRepository.getOrderById(orderId)
                    updatedOrder?.let {
                        Log.d("OrdersViewModel", "【打印操作】更新选中订单为: ID=${it.id}, 打印状态=${it.isPrinted}")
                        _selectedOrder.value = it
                        
                        // 验证显示的打印状态
                        Log.d("OrdersViewModel", "【打印操作】验证selectedOrder状态: ${_selectedOrder.value?.isPrinted}")
                    }
                    
                    // 不刷新订单列表，避免从API获取状态覆盖本地状态
                    // refreshOrders()
                } else {
                    Log.e("OrdersViewModel", "打印订单失败: $orderId")
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "打印订单时出错: ${e.message}", e)
            }
        }
    }

    /**
     * 打印订单
     * @param orderId 订单ID
     * @param templateType 模板类型，如果为null则使用默认模板
     */
    fun printOrder(orderId: Long, templateType: TemplateType? = null) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "打印订单: $orderId, 模板类型: $templateType")
                
                // 获取订单信息
                val order = orderRepository.getOrderById(orderId)
                if (order == null) {
                    Log.e("OrdersViewModel", "未找到订单: $orderId")
                    return@launch
                }
                
                // 获取默认打印机配置
                val printerConfig = settingRepository.getDefaultPrinterConfig()
                if (printerConfig == null) {
                    Log.e("OrdersViewModel", "未找到默认打印机配置")
                    return@launch
                }
                
                // 如果指定了模板类型，临时设置为默认模板
                if (templateType != null) {
                    settingRepository.saveDefaultTemplateType(templateType)
                    Log.d("OrdersViewModel", "临时设置打印模板为: $templateType")
                }
                
                // 打印订单
                val success = printerManager.printOrder(order, printerConfig)
                if (success) {
                    Log.d("OrdersViewModel", "【打印操作】打印订单成功: $orderId")
                    // 标记订单为已打印
                    markOrderAsPrinted(orderId)
                    
                    // 等待一下，确保数据库操作完成
                    delay(200)
                    
                    // 刷新选中的订单，确保UI显示更新
                    val updatedOrder = orderRepository.getOrderById(orderId)
                    updatedOrder?.let {
                        Log.d("OrdersViewModel", "【打印操作】更新选中订单为: ID=${it.id}, 打印状态=${it.isPrinted}")
                        _selectedOrder.value = it
                        
                        // 验证显示的打印状态
                        Log.d("OrdersViewModel", "【打印操作】验证selectedOrder状态: ${_selectedOrder.value?.isPrinted}")
                    }
                    
                    // 不刷新订单列表，避免从API获取状态覆盖本地状态
                    // refreshOrders()
                } else {
                    Log.e("OrdersViewModel", "打印订单失败: $orderId")
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "打印订单时出错: ${e.message}", e)
            }
        }
    }

    /**
     * 注册接收刷新订单的广播接收器
     */
    private fun registerRefreshOrdersBroadcastReceiver() {
        viewModelScope.launch {
            try {
                // 使用已注入的context替代Application
                
                // 创建广播接收器
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        Log.d("OrdersViewModel", "【轮询通知】收到刷新订单广播，但功能已禁用以防止覆盖本地打印状态")
                        // 不再自动刷新订单，避免覆盖本地打印状态
                        // refreshOrders()
                    }
                }
                
                // 注册广播接收器
                LocalBroadcastManager.getInstance(context).registerReceiver(
                    receiver,
                    IntentFilter("com.example.wooauto.REFRESH_ORDERS")
                )
                
                Log.d("OrdersViewModel", "已注册刷新订单广播接收器（自动刷新功能已禁用）")
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "注册刷新订单广播接收器失败: ${e.message}")
            }
        }
    }

    /**
     * 清理数据库中不存在的未读订单
     * @return 清理的记录数
     */
    private suspend fun cleanupNonExistentUnreadOrders(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始清理数据库中不存在但标记为未读的订单")
            
            val orderDao = orderRepository.getOrderDao()
            
            // 获取所有未读订单ID
            val unreadIds = orderDao.getUnreadOrderIds()
            if (unreadIds.isEmpty()) {
                Log.d(TAG, "没有未读订单ID，无需清理")
                return@withContext 0
            }
            
            Log.d(TAG, "找到 ${unreadIds.size} 个未读订单ID")
            
            // 获取所有订单ID
            val allOrderIds = orderDao.getAllOrderIds()
            Log.d(TAG, "数据库中共有 ${allOrderIds.size} 个订单")
            
            // 找出不存在的订单ID
            val nonExistentIds = unreadIds.filter { it !in allOrderIds }
            
            if (nonExistentIds.isNotEmpty()) {
                Log.d(TAG, "发现 ${nonExistentIds.size} 个不存在但标记为未读的订单ID: $nonExistentIds")
                
                // 删除这些不存在的订单ID
                try {
                    orderDao.deleteOrdersByIds(nonExistentIds)
                    Log.d(TAG, "成功清理不存在的未读订单ID")
                    return@withContext nonExistentIds.size
                } catch (e: Exception) {
                    Log.e(TAG, "清理不存在的未读订单ID失败: ${e.message}")
                    return@withContext 0
                }
            } else {
                Log.d(TAG, "未发现需要清理的订单ID")
                return@withContext 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理不存在的未读订单时出错: ${e.message}")
            return@withContext 0
        }
    }
    
    // 加载未读订单
    private fun loadUnreadOrders() {
        viewModelScope.launch {
            try {
                // Log.d("OrdersViewModel", "开始从数据库加载未读订单")
                
                // 使用dao直接获取未读订单的ID列表
                val unreadOrderIds = withContext(Dispatchers.IO) {
                    try {
                        val orderDao = orderRepository.getOrderDao()
                        val ids = orderDao.getUnreadOrderIds()
                        // Log.d("OrdersViewModel", "数据库中找到 ${ids.size} 个未读订单ID: $ids")
                        ids
                    } catch (e: Exception) {
                        Log.e("OrdersViewModel", "获取未读订单ID时发生错误", e)
                        emptyList<Long>()
                    }
                }
                
                if (unreadOrderIds.isEmpty()) {
                    // 如果没有未读订单，直接清空未读订单列表和计数
                    _unreadOrders.value = emptyList()
                    _unreadOrdersCount.value = 0
                    // Log.d("OrdersViewModel", "没有未读订单，已清空未读订单列表和计数")
                    return@launch
                }
                
                // 根据未读ID列表获取完整的未读订单
                val unreadOrdersList = orderRepository.getOrdersByIds(unreadOrderIds)
                
                Log.d("OrdersViewModel", "获取到 ${unreadOrdersList.size} 个未读订单（应为 ${unreadOrderIds.size} 个）")
                
                // 如果未读订单列表数量与ID列表不一致，可能有订单已被删除
                if (unreadOrdersList.size != unreadOrderIds.size) {
                    Log.w("OrdersViewModel", "警告：未读订单数量与未读ID列表不一致")
                    
                    // 更新数据库中的未读状态，将不存在订单的ID标记为已读
                    val existingIds = unreadOrdersList.map { it.id }
                    val nonExistingIds = unreadOrderIds.filter { it !in existingIds }
                    
                    if (nonExistingIds.isNotEmpty()) {
                        Log.d("OrdersViewModel", "存在 ${nonExistingIds.size} 个ID无对应订单，将清理这些ID: $nonExistingIds")
                        
                        withContext(Dispatchers.IO) {
                            try {
                                val orderDao = orderRepository.getOrderDao()
                                // 将这些ID标记为已读，防止再次出现问题
                                orderDao.markOrdersAsRead(nonExistingIds)
                                Log.d("OrdersViewModel", "成功将不存在的订单ID标记为已读")
                            } catch (e: Exception) {
                                Log.e("OrdersViewModel", "标记不存在订单ID为已读失败: ${e.message}")
                            }
                        }
                    }
                }
                
                // 严格过滤出有效的未读订单：
                // 1. 确保订单ID有效
                // 2. 订单状态不为空
                // 3. 订单显示名称不为空
                // 4. 订单状态应为有效状态 (比如processing)
                val validUnreadOrders = unreadOrdersList.filter { order ->
                    val validId = order.id > 0
                    val validStatus = order.status.isNotEmpty()
                    val validName = order.customerName.isNotEmpty() || order.number.isNotEmpty()
                    val validOrderStatus = isValidOrderStatus(order.status)
                    
                    val isValid = validId && validStatus && validName && validOrderStatus
                    
                    if (!isValid) {
                        Log.d("OrdersViewModel", "过滤无效未读订单: ID=${order.id}, 状态=${order.status}, 名称=${order.customerName}")
                    }
                    
                    isValid
                }
                
                if (validUnreadOrders.size != unreadOrdersList.size) {
                    Log.d("OrdersViewModel", "过滤掉 ${unreadOrdersList.size - validUnreadOrders.size} 个无效未读订单")
                    
                    // 标记这些无效订单为已读
                    val invalidOrderIds = unreadOrdersList
                        .filter { order -> !validUnreadOrders.any { it.id == order.id } }
                        .map { it.id }
                    
                    if (invalidOrderIds.isNotEmpty()) {
                        Log.d("OrdersViewModel", "将 ${invalidOrderIds.size} 个无效订单标记为已读: $invalidOrderIds")
                        withContext(Dispatchers.IO) {
                            try {
                                val orderDao = orderRepository.getOrderDao()
                                orderDao.markOrdersAsRead(invalidOrderIds)
                            } catch (e: Exception) {
                                Log.e("OrdersViewModel", "标记无效订单为已读失败: ${e.message}")
                            }
                        }
                    }
                }
                
                _unreadOrders.value = validUnreadOrders
                _unreadOrdersCount.value = validUnreadOrders.size
                
                Log.d("OrdersViewModel", "未读订单加载完成，最终数量: ${_unreadOrdersCount.value}")
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "获取未读订单时发生错误", e)
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
            }
        }
    }
    
    /**
     * 检查订单状态是否有效
     */
    private fun isValidOrderStatus(status: String): Boolean {
        val validStatuses = setOf(
            "processing", "pending", "completed", "cancelled", "refunded", "failed", "on-hold",
            "处理中", "待付款", "已完成", "已取消", "已退款", "失败", "暂挂"
        )
        return status.isNotEmpty() && (validStatuses.contains(status) || status.length > 2)
    }
    
    // 标记订单为已读
    fun markOrderAsRead(orderId: Long) {
        viewModelScope.launch {
            try {
                orderRepository.markOrderAsRead(orderId)
                // 更新未读订单列表
                _unreadOrders.value = _unreadOrders.value.filter { it.id != orderId }
                _unreadOrdersCount.value = _unreadOrders.value.size
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "标记订单已读时发生错误", e)
                _errorMessage.value = e.localizedMessage ?: "标记订单已读失败"
            }
        }
    }
    
    // 标记所有订单为已读
    fun markAllOrdersAsRead() {
        viewModelScope.launch {
            try {
                orderRepository.markAllOrdersAsRead()
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "标记所有订单已读时发生错误", e)
                _errorMessage.value = e.localizedMessage ?: "标记所有订单已读失败"
            }
        }
    }
    
    /**
     * 刷新未读订单列表
     * 专门用于未读订单对话框打开时调用
     */
    fun refreshUnreadOrders() {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "手动刷新未读订单列表")
                loadUnreadOrders()
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "刷新未读订单时发生错误", e)
            }
        }
    }

    /**
     * 强制重置所有订单为已读状态
     */
    private suspend fun resetAllUnreadStatus() {
        withContext(Dispatchers.IO) {
            try {
                val orderDao = orderRepository.getOrderDao()
                Log.d(TAG, "开始将所有订单标记为已读")
                
                // 获取所有未读订单ID
                val unreadIds = orderDao.getUnreadOrderIds()
                
                if (unreadIds.isNotEmpty()) {
                    Log.d(TAG, "发现 ${unreadIds.size} 个未读订单ID，将全部标记为已读: $unreadIds")
                    orderDao.markAllOrdersAsRead()
                    Log.d(TAG, "成功将所有订单标记为已读")
                } else {
                    Log.d(TAG, "没有未读订单，无需重置")
                }
                
                // 重置内存中的状态
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
            } catch (e: Exception) {
                Log.e(TAG, "重置所有订单为已读状态时出错: ${e.message}")
            }
        }
    }

    /**
     * 验证未读订单的有效性
     * @return 如果所有未读订单都有效返回true，否则返回false
     */
    private suspend fun verifyUnreadOrdersValid(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始验证未读订单有效性")
            
            val orderDao = orderRepository.getOrderDao()
            
            // 获取所有未读订单ID
            val unreadIds = orderDao.getUnreadOrderIds()
            if (unreadIds.isEmpty()) {
                Log.d(TAG, "没有未读订单ID，无需验证")
                return@withContext true
            }
            
            Log.d(TAG, "找到 ${unreadIds.size} 个未读订单ID: $unreadIds")
            
            // 获取这些ID对应的订单
            val unreadOrders = orderRepository.getOrdersByIds(unreadIds)
            
            // 如果未读订单数量与未读ID数量不匹配，表示有无效的未读订单
            if (unreadOrders.size != unreadIds.size) {
                Log.w(TAG, "未读订单数量 (${unreadOrders.size}) 与未读ID数量 (${unreadIds.size}) 不匹配")
                return@withContext false
            }
            
            // 计算30天前的时间戳，用于过滤过老的订单
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -30)  // 30天前的时间
            val thirtyDaysAgo = calendar.timeInMillis
            
            // 检查所有未读订单是否有效
            val invalidOrders = unreadOrders.filter { order ->
                // 检查订单是否无效或者是否太老
                !isValidOrder(order) || order.dateCreated.time < thirtyDaysAgo
            }
            
            if (invalidOrders.isNotEmpty()) {
                Log.w(TAG, "发现 ${invalidOrders.size} 个无效的未读订单")
                invalidOrders.forEach { order ->
                    val reason = if (!isValidOrder(order)) "无效订单" else "订单过期(超过30天)"
                    Log.d(TAG, "无效未读订单: ID=${order.id}, 状态=${order.status}, 名称=${order.customerName}, 原因=$reason")
                }
                
                // 将这些无效订单标记为已读
                val invalidOrderIds = invalidOrders.map { it.id }
                if (invalidOrderIds.isNotEmpty()) {
                    Log.d(TAG, "将 ${invalidOrderIds.size} 个无效订单标记为已读: $invalidOrderIds")
                    orderDao.markOrdersAsRead(invalidOrderIds)
                }
                
                return@withContext false
            }
            
            Log.d(TAG, "所有未读订单验证通过")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "验证未读订单有效性时出错: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * 判断订单是否有效
     */
    private fun isValidOrder(order: Order): Boolean {
        val validId = order.id > 0
        val validStatus = order.status.isNotEmpty()
        val validName = order.customerName.isNotEmpty() || order.number.isNotEmpty()
        val validOrderStatus = isValidOrderStatus(order.status)
        
        return validId && validStatus && validName && validOrderStatus
    }

    /**
     * 观察指定状态的订单
     * @param status 订单状态，可以是中文或英文
     */
    private fun observeFilteredOrders(status: String) {
        ordersCollectJob?.cancel()
        ordersCollectJob = viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "【本地过滤】开始观察状态为 '$status' 的订单")
                // 启动一个短超时：避免首次收集到空数据时立刻显示空态，给数据库刷新留出时间
                filterLoadingTimeoutJob?.cancel()
                filterLoadingTimeoutJob = viewModelScope.launch {
                    try {
                        kotlinx.coroutines.delay(1500)
                    } finally {
                        // 超时后如果仍为空，则允许显示空态
                        _isLoading.value = false
                        _emptyGuardActive.value = false
                        UiLog.d(TAG, "Filter timeout -> set isLoading=false, emptyGuardActive=false (may render empty state)")
                    }
                }
                
                orderRepository.getOrdersByStatusFlow(status)
                    .collect { filteredOrders ->
                        Log.d("OrdersViewModel", "【本地过滤】获取到 ${filteredOrders.size} 个状态为 '$status' 的订单")
                        UiLog.d(TAG, "Flow emit -> status='$status', size=${filteredOrders.size}, isLoading=${_isLoading.value}, emptyGuardActive=${_emptyGuardActive.value}")
                        
                        if (filteredOrders.isEmpty()) {
                            Log.w("OrdersViewModel", "【本地过滤】未找到状态为 '$status' 的订单")
                            // 但不显示错误，避免用户体验不佳
                        }
                        
                        // 更新UI
                        _orders.value = filteredOrders
                        if (filteredOrders.isNotEmpty()) {
                            // 一旦拿到非空结果，立即结束Loading并取消超时任务
                            filterLoadingTimeoutJob?.cancel()
                            _isLoading.value = false
                            _emptyGuardActive.value = false
                            UiLog.d(TAG, "Flow non-empty -> set isLoading=false, emptyGuardActive=false")
                        }
                    }
            } catch (e: CancellationException) {
                // 切换筛选/数据源导致的正常取消，不提示错误
                UiLog.d(TAG, "本地过滤收集被取消（切换筛选）")
                throw e
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "【本地过滤】过滤订单出错", e)
                _errorMessage.value = e.message
                _isLoading.value = false
            }
        }
    }

    // 导航到许可证设置页面
    fun navigateToLicenseSettings() {
        _navigationEvent.value = "license_settings"
    }
    
    // 清除导航事件
    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }
    
    // 加载货币符号
    private fun loadCurrencySymbol() {
        viewModelScope.launch {
            try {
                settingRepository.getCurrencySymbolFlow()
                    .collectLatest { symbol ->
                        _currencySymbol.value = symbol.ifEmpty { "C$" }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "加载货币符号失败: ${e.message}")
                _currencySymbol.value = "C$" // 默认值
            }
        }
    }
} 