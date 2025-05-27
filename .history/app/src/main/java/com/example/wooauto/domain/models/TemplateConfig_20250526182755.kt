package com.example.wooauto.domain.models

import com.example.wooauto.domain.templates.TemplateType
import java.io.Serializable

/**
 * 模板配置数据类
 * 定义了打印模板中各个元素的显示配置
 */
data class TemplateConfig(
    // 模板ID，用于标识不同的模板
    val templateId: String,
    
    // 模板类型
    val templateType: TemplateType,
    
    // 模板名称
    val templateName: String = "",
    
    // === 商店信息 ===
    // 是否显示商店信息（主控制选项）
    val showStoreInfo: Boolean = true,
    // 商店信息细分选项
    val showStoreName: Boolean = true,
    val showStoreAddress: Boolean = true, 
    val showStorePhone: Boolean = true,
    
    // === 订单基本信息 ===
    // 是否显示订单信息（主控制选项）
    val showOrderInfo: Boolean = true,
    // 订单信息细分选项
    val showOrderNumber: Boolean = true,
    val showOrderDate: Boolean = true,
    
    // === 客户信息 ===
    // 是否显示客户信息（主控制选项）
    val showCustomerInfo: Boolean = true,
    // 客户信息细分选项
    val showCustomerName: Boolean = true,
    val showCustomerPhone: Boolean = true,
    val showDeliveryInfo: Boolean = false,
    
    // === 订单内容信息 ===
    // 是否显示订单内容（主控制选项）
    val showOrderContent: Boolean = true,
    // 订单内容细分选项
    val showItemDetails: Boolean = true,
    val showItemPrices: Boolean = true,
    val showOrderNotes: Boolean = true,
    val showTotals: Boolean = true,
    
    // === 支付信息 ===
    // 是否显示支付信息
    val showPaymentInfo: Boolean = true,
    
    // === 页脚 ===
    // 是否显示页脚
    val showFooter: Boolean = true,
    
    // 自定义页脚文本
    val footerText: String = "Thank you for your order!",
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 最后修改时间
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable {
    
    /**
     * 计算启用的字段数量
     */
    fun getEnabledFieldCount(): Int {
        var count = 0
        if (showStoreInfo) count++
        if (showOrderInfo) count++
        if (showCustomerInfo) count++
        if (showOrderContent) count++
        if (showPaymentInfo) count++
        if (showFooter) count++
        return count
    }
    
    /**
     * 检查商店信息子选项的状态
     */
    fun getStoreInfoState(): Boolean? {
        val storeSubOptions = listOf(showStoreName, showStoreAddress, showStorePhone)
        return when {
            storeSubOptions.all { it } -> true
            storeSubOptions.none { it } -> false
            else -> null // 部分选中
        }
    }
    
    /**
     * 检查订单信息子选项的状态
     */
    fun getOrderInfoState(): Boolean? {
        val orderSubOptions = listOf(showOrderNumber, showOrderDate)
        return when {
            orderSubOptions.all { it } -> true
            orderSubOptions.none { it } -> false
            else -> null // 部分选中
        }
    }
    
    /**
     * 检查客户信息子选项的状态
     */
    fun getCustomerInfoState(): Boolean? {
        val customerSubOptions = listOf(showCustomerName, showCustomerPhone, showDeliveryInfo)
        return when {
            customerSubOptions.all { it } -> true
            customerSubOptions.none { it } -> false
            else -> null // 部分选中
        }
    }
    
    /**
     * 检查订单内容子选项的状态
     */
    fun getOrderContentState(): Boolean? {
        val contentSubOptions = listOf(showItemDetails, showItemPrices, showOrderNotes, showTotals)
        return when {
            contentSubOptions.all { it } -> true
            contentSubOptions.none { it } -> false
            else -> null // 部分选中
        }
    }
    
    companion object {
        /**
         * 根据模板类型创建默认配置
         * @param templateType 模板类型
         * @param templateId 模板ID
         * @return 默认的模板配置
         */
        fun createDefaultConfig(
            templateType: TemplateType,
            templateId: String = templateType.name.lowercase()
        ): TemplateConfig {
            return when (templateType) {
                TemplateType.FULL_DETAILS -> createFullDetailsConfig(templateId)
                TemplateType.DELIVERY -> createDeliveryConfig(templateId)
                TemplateType.KITCHEN -> createKitchenConfig(templateId)
            }
        }
        
        /**
         * 创建完整详情模板的默认配置
         */
        private fun createFullDetailsConfig(templateId: String): TemplateConfig {
            return TemplateConfig(
                templateId = templateId,
                templateType = TemplateType.FULL_DETAILS,
                templateName = "Full Order Details",
                showStoreInfo = true,
                showStoreName = true,
                showStoreAddress = true,
                showStorePhone = true,
                showOrderInfo = true,
                showOrderNumber = true,
                showOrderDate = true,
                showCustomerInfo = true,
                showCustomerName = true,
                showCustomerPhone = true,
                showDeliveryInfo = false, // 完整详情默认不显示配送信息
                showOrderContent = true,
                showItemDetails = true,
                showItemPrices = true,
                showOrderNotes = true,
                showTotals = true,
                showPaymentInfo = true,
                showFooter = true
            )
        }
        
        /**
         * 创建配送模板的默认配置
         */
        private fun createDeliveryConfig(templateId: String): TemplateConfig {
            return TemplateConfig(
                templateId = templateId,
                templateType = TemplateType.DELIVERY,
                templateName = "Delivery Receipt",
                showStoreInfo = true,
                showStoreName = true,
                showStoreAddress = true,
                showStorePhone = true,
                showOrderInfo = true,
                showOrderNumber = true,
                showOrderDate = true,
                showCustomerInfo = true,
                showCustomerName = true,
                showCustomerPhone = true,
                showDeliveryInfo = true, // 配送模板重点显示配送信息
                showOrderContent = true,
                showItemDetails = true,
                showItemPrices = true,
                showOrderNotes = true,
                showTotals = true,
                showPaymentInfo = true,
                showFooter = true
            )
        }
        
        /**
         * 创建厨房模板的默认配置
         */
        private fun createKitchenConfig(templateId: String): TemplateConfig {
            return TemplateConfig(
                templateId = templateId,
                templateType = TemplateType.KITCHEN,
                templateName = "Kitchen Order",
                showStoreInfo = false, // 厨房不需要商店信息
                showStoreName = false,
                showStoreAddress = false,
                showStorePhone = false,
                showOrderInfo = true,
                showOrderNumber = true,
                showOrderDate = true,
                showCustomerInfo = false, // 厨房不需要详细客户信息
                showCustomerName = false,
                showCustomerPhone = false,
                showDeliveryInfo = false,
                showOrderContent = true,
                showItemDetails = true,
                showItemPrices = false, // 厨房不需要价格信息
                showOrderNotes = true, // 厨房需要看到制作备注
                showTotals = false, // 厨房不需要价格合计
                showPaymentInfo = false, // 厨房不需要支付信息
                showFooter = false // 厨房不需要页脚
            )
        }
        
        /**
         * 所有预设模板的列表
         */
        val PRESET_TEMPLATES = listOf(
            "full_details",
            "delivery", 
            "kitchen"
        )
    }
    
    /**
     * 创建该配置的副本并更新时间戳
     */
    fun copyWithUpdatedTimestamp(): TemplateConfig {
        return this.copy(updatedAt = System.currentTimeMillis())
    }
    
    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return templateId.isNotBlank() && 
               templateName.isNotBlank() &&
               (showStoreInfo || showOrderInfo || showCustomerInfo || 
                showOrderContent || showPaymentInfo || showFooter)
    }
} 