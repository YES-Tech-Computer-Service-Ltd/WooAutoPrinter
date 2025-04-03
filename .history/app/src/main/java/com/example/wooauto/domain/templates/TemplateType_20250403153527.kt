package com.example.wooauto.domain.templates

/**
 * 打印模板类型枚举
 * 定义了不同类型的打印模板
 */
enum class TemplateType {
    /**
     * 完整订单详情 - 包含所有订单信息
     */
    FULL_DETAILS,
    
    /**
     * 配送信息 - 包含配送相关内容和菜品信息
     */
    DELIVERY,
    
    /**
     * 厨房订单 - 仅包含菜品和下单时间
     */
    KITCHEN
} 