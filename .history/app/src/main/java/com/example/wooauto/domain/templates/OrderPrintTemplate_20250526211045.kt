package com.example.wooauto.domain.templates

import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.PrinterConfig

/**
 * 订单打印模板接口
 * 负责生成订单打印内容
 */
interface OrderPrintTemplate {
    /**
     * 生成订单打印内容
     * @param order 订单信息
     * @param config 打印机配置
     * @return 格式化后的打印内容
     */
    fun generateOrderPrintContent(order: Order, config: PrinterConfig): String
    
    /**
     * 生成测试打印内容
     * @param config 打印机配置
     * @return 格式化后的测试打印内容
     */
    fun generateTestPrintContent(config: PrinterConfig): String
    
    /**
     * 创建测试用的订单对象
     * @param config 打印机配置
     * @return 包含测试数据的订单对象
     */
    fun createTestOrder(config: PrinterConfig): Order
} 