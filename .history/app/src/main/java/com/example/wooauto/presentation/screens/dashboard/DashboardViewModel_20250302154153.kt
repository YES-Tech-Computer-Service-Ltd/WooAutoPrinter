package com.example.wooauto.presentation.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.domain.models.AutomationTask
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainProductRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.Exception
import javax.inject.Inject

/**
 * 仪表盘页面的ViewModel
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val orderRepository: DomainOrderRepository,
    private val productRepository: DomainProductRepository,
    private val settingsRepository: DomainSettingRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 统计数据
    data class StatItem(val title: String, val value: String, val type: String)
    private val _statsData = MutableStateFlow<List<StatItem>>(emptyList())
    val statsData: StateFlow<List<StatItem>> = _statsData.asStateFlow()

    // 自动化任务
    private val _automationTasks = MutableStateFlow<List<AutomationTask>>(emptyList())
    val automationTasks: StateFlow<List<AutomationTask>> = _automationTasks.asStateFlow()

    init {
        Log.d("DashboardViewModel", "初始化")
        // 加载默认数据
        loadDemoData()
    }

    /**
     * 刷新仪表盘数据
     */
    fun refreshData() {
        Log.d("DashboardViewModel", "刷新仪表盘数据")
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 获取订单数量
                val orders = orderRepository.getOrders()
                
                // 获取产品数量
                val products = productRepository.getProducts()
                
                // 获取自动任务（Demo 数据）
                val tasks = loadAutomationTasks()
                
                // 更新状态
                _statsData.value = listOf(
                    StatItem("订单总数", orders.size.toString(), "orders"),
                    StatItem("产品总数", products.size.toString(), "products"),
                    StatItem("待处理订单", orders.count { it.status == "processing" }.toString(), "orders"),
                    StatItem("系统设置", "配置", "settings")
                )
                
                _automationTasks.value = tasks
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "刷新数据失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 切换自动化任务状态
     */
    fun toggleTaskStatus(taskId: String) {
        Log.d("DashboardViewModel", "切换任务状态: $taskId")
        _automationTasks.value = _automationTasks.value.map { 
            if (it.id == taskId) it.copy(isActive = !it.isActive) else it 
        }
    }

    /**
     * 加载测试数据
     */
    private fun loadDemoData() {
        _statsData.value = listOf(
            StatItem("订单总数", "0", "orders"),
            StatItem("产品总数", "0", "products"),
            StatItem("待处理订单", "0", "orders"),
            StatItem("系统设置", "配置", "settings")
        )
        
        _automationTasks.value = loadAutomationTasks()
    }

    /**
     * 加载自动化任务（演示数据）
     */
    private fun loadAutomationTasks(): List<AutomationTask> {
        return listOf(
            AutomationTask(
                id = "task1",
                name = "自动接单",
                description = "新订单自动接收处理",
                isActive = true
            ),
            AutomationTask(
                id = "task2",
                name = "订单打印",
                description = "新订单自动打印小票",
                isActive = false
            ),
            AutomationTask(
                id = "task3",
                name = "库存提醒",
                description = "库存不足自动提醒",
                isActive = true
            ),
            AutomationTask(
                id = "task4",
                name = "定时备份",
                description = "每日自动备份数据",
                isActive = false
            )
        )
    }
} 