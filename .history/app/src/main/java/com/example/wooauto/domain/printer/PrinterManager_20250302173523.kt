package com.example.wooauto.domain.printer

import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.PrinterConfig
import kotlinx.coroutines.flow.Flow

/**
 * 打印机管理器接口
 * 定义了打印机连接和打印操作的方法
 */
interface PrinterManager {
    /**
     * 连接打印机
     * @param config 打印机配置
     * @return 连接结果，成功返回true
     */
    suspend fun connect(config: PrinterConfig): Boolean
    
    /**
     * 断开打印机连接
     * @param config 打印机配置
     */
    suspend fun disconnect(config: PrinterConfig)
    
    /**
     * 获取打印机状态
     * @param config 打印机配置
     * @return 打印机状态
     */
    suspend fun getPrinterStatus(config: PrinterConfig): PrinterStatus
    
    /**
     * 获取打印机状态流
     * @param config 打印机配置
     * @return 打印机状态流
     */
    fun getPrinterStatusFlow(config: PrinterConfig): Flow<PrinterStatus>
    
    /**
     * 扫描可用打印机
     * @param type 打印机类型
     * @return 可用打印机列表
     */
    suspend fun scanPrinters(type: String): List<PrinterDevice>
    
    /**
     * 打印订单
     * @param order 订单
     * @param config 打印机配置
     * @return 打印结果，成功返回true
     */
    suspend fun printOrder(order: Order, config: PrinterConfig): Boolean
    
    /**
     * 执行测试打印
     * @param config 打印机配置
     * @return 打印结果，成功返回true
     */
    suspend fun printTest(config: PrinterConfig): Boolean
    
    /**
     * 自动打印新订单
     * @param order 订单
     * @return 打印结果，成功返回true
     */
    suspend fun autoPrintNewOrder(order: Order): Boolean
}

/**
 * 打印机状态
 */
enum class PrinterStatus {
    DISCONNECTED,   // 未连接
    CONNECTING,     // 连接中
    CONNECTED,      // 已连接
    ERROR,          // 连接错误
    PAPER_OUT,      // 缺纸
    OVERHEATED,     // 过热
    LOW_BATTERY     // 电量低
}

/**
 * 打印机设备
 */
data class PrinterDevice(
    val name: String,       // 设备名称
    val address: String,    // 设备地址(MAC地址或IP地址)
    val type: String,       // 设备类型
    val isConnected: Boolean = false // 是否已连接
) 