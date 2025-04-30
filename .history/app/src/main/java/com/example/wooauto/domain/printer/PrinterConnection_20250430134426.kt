package com.example.wooauto.domain.printer

import com.example.wooauto.domain.models.PrinterConfig

/**
 * 打印机连接接口
 * 用于抽象不同类型打印机的连接方式
 */
interface PrinterConnection {
    /**
     * 建立打印机连接
     * @return 是否成功连接
     */
    suspend fun connect(): Boolean
    
    /**
     * 断开打印机连接
     */
    suspend fun disconnect()
    
    /**
     * 检查打印机连接状态
     * @return 是否已连接
     */
    fun isConnected(): Boolean
    
    /**
     * 发送数据到打印机
     * @param data 要发送的数据
     * @return 是否成功发送
     */
    suspend fun write(data: ByteArray): Boolean
    
    /**
     * 读取打印机状态
     * @return 打印机状态
     */
    suspend fun readStatus(): PrinterStatus
    
    /**
     * 发送打印内容
     * @param content 打印内容
     * @return 是否成功打印
     */
    suspend fun print(content: String): Boolean
    
    /**
     * 执行切纸
     * @param partial 是否部分切纸
     * @return 是否成功执行
     */
    suspend fun cutPaper(partial: Boolean = true): Boolean
    
    /**
     * 走纸
     * @param lines 走纸行数
     * @return 是否成功执行
     */
    suspend fun feedPaper(lines: Int): Boolean
    
    /**
     * 初始化打印机
     * @return 是否成功执行
     */
    suspend fun initialize(): Boolean
    
    /**
     * 发送心跳命令，保持连接活跃
     * @return 是否成功执行
     */
    suspend fun sendHeartbeat(): Boolean
    
    /**
     * 获取打印机配置
     * @return 打印机配置
     */
    fun getConfig(): PrinterConfig
    
    /**
     * 测试打印机连接
     * @return 测试结果
     */
    suspend fun testConnection(): Boolean
} 