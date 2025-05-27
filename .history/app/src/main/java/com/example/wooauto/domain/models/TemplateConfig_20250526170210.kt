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
    
    // 是否显示商店信息（主控制选项）
    val showStoreInfo: Boolean = true,
    
    // 商店信息细分选项
    val showStoreName: Boolean = true,
    val showStoreAddress: Boolean = true, 
    val showStorePhone: Boolean = true,
    
    // 是否显示订单编号
    val showOrderNumber: Boolean = true,
    
    // 是否显示客户信息
    val showCustomerInfo: Boolean = true,
    
    // 是否显示订单日期时间
    val showOrderDate: Boolean = true,
    
    // 是否显示配送信息
    val showDeliveryInfo: Boolean = false,
    
    // 是否显示支付信息
    val showPaymentInfo: Boolean = true,
    
    // 是否显示商品详情
    val showItemDetails: Boolean = true,
    
    // 是否显示商品价格
    val showItemPrices: Boolean = true,
    
    // 是否显示订单备注
    val showOrderNotes: Boolean = true,
    
    // 是否显示订单合计
    val showTotals: Boolean = true,
    
    // 是否显示页脚
    val showFooter: Boolean = true,
    
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
        if (showOrderNumber) count++
        if (showCustomerInfo) count++
        if (showOrderDate) count++
        if (showDeliveryInfo) count++
        if (showPaymentInfo) count++
        if (showItemDetails) count++
        if (showOrderNotes) count++
        if (showTotals) count++
        if (showFooter) count++
        return count
    }
    
    /**
     * 检查商店信息子选项的状态
     * 如果所有子选项都被选中，返回true
     * 如果部分选中，返回null（表示中间状态）
     * 如果都未选中，返回false
     */
    fun getStoreInfoState(): Boolean? {
        val storeSubOptions = listOf(showStoreName, showStoreAddress, showStorePhone)
        return when {
            storeSubOptions.all { it } -> true
            storeSubOptions.none { it } -> false
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
                showOrderNumber = true,
                showCustomerInfo = true,
                showOrderDate = true,
                showDeliveryInfo = false, // 完整详情默认不显示配送信息
                showPaymentInfo = true,
                showItemDetails = true,
                showItemPrices = true,
                showOrderNotes = true,
                showTotals = true,
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
                showOrderNumber = true,
                showCustomerInfo = true,
                showOrderDate = true,
                showDeliveryInfo = true, // 配送模板重点显示配送信息
                showPaymentInfo = true,
                showItemDetails = true,
                showItemPrices = true,
                showOrderNotes = true,
                showTotals = true,
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
                showOrderNumber = true,
                showCustomerInfo = false, // 厨房不需要客户信息
                showOrderDate = true,
                showDeliveryInfo = false, // 厨房不需要配送信息
                showPaymentInfo = false, // 厨房不需要支付信息
                showItemDetails = true,
                showItemPrices = false, // 厨房不需要价格信息
                showOrderNotes = true, // 厨房需要看到制作备注
                showTotals = false, // 厨房不需要价格合计
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
               (showStoreInfo || showOrderNumber || showCustomerInfo || 
                showOrderDate || showDeliveryInfo || showPaymentInfo || 
                showItemDetails || showOrderNotes || showTotals || showFooter)
    }
} 