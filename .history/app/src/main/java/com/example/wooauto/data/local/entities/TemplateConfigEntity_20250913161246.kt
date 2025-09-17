package com.example.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.wooauto.domain.models.TemplateConfig
import com.example.wooauto.domain.templates.TemplateType

/**
 * 模板配置实体类
 * 用于在Room数据库中存储模板配置信息
 */
@Entity(tableName = "template_configs")
data class TemplateConfigEntity(
    @PrimaryKey
    val templateId: String,
    
    val templateType: String, // 存储TemplateType的名称
    val templateName: String,
    
    // 商店信息
    val showStoreInfo: Boolean,
    val showStoreName: Boolean,
    val showStoreAddress: Boolean,
    val showStorePhone: Boolean,
    val styleStoreInfoBold: Boolean,
    val styleStoreInfoLarge: Boolean,
    
    // 订单基本信息  
    val showOrderInfo: Boolean,
    val showOrderNumber: Boolean,
    val showOrderDate: Boolean,
    val styleOrderInfoBold: Boolean,
    val styleOrderInfoLarge: Boolean,
    
    // 客户信息
    val showCustomerInfo: Boolean,
    val showCustomerName: Boolean,
    val showCustomerPhone: Boolean,
    val showDeliveryInfo: Boolean,
    val styleCustomerInfoBold: Boolean,
    val styleCustomerInfoLarge: Boolean,
    
    // 订单内容
    val showOrderContent: Boolean,
    val showItemDetails: Boolean,
    val showItemPrices: Boolean,
    val showOrderNotes: Boolean,
    val showTotals: Boolean,
    val styleOrderContentBold: Boolean,
    val styleOrderContentLarge: Boolean,
    
    // 支付信息
    val showPaymentInfo: Boolean,
    val stylePaymentInfoBold: Boolean,
    val stylePaymentInfoLarge: Boolean,
    
    // 页脚
    val showFooter: Boolean,
    val styleFooterBold: Boolean,
    val styleFooterLarge: Boolean,
    
    // 自定义页脚文本
    val footerText: String,
    
    val createdAt: Long,
    val updatedAt: Long
) {
    /**
     * 转换为领域模型
     */
    fun toDomainModel(): TemplateConfig {
        return TemplateConfig(
            templateId = templateId,
            templateType = TemplateType.valueOf(templateType),
            templateName = templateName,
            showStoreInfo = showStoreInfo,
            showStoreName = showStoreName,
            showStoreAddress = showStoreAddress,
            showStorePhone = showStorePhone,
            styleStoreInfoBold = styleStoreInfoBold,
            styleStoreInfoLarge = styleStoreInfoLarge,
            showOrderInfo = showOrderInfo,
            showOrderNumber = showOrderNumber,
            showOrderDate = showOrderDate,
            styleOrderInfoBold = styleOrderInfoBold,
            styleOrderInfoLarge = styleOrderInfoLarge,
            showCustomerInfo = showCustomerInfo,
            showCustomerName = showCustomerName,
            showCustomerPhone = showCustomerPhone,
            showDeliveryInfo = showDeliveryInfo,
            styleCustomerInfoBold = styleCustomerInfoBold,
            styleCustomerInfoLarge = styleCustomerInfoLarge,
            showOrderContent = showOrderContent,
            showItemDetails = showItemDetails,
            showItemPrices = showItemPrices,
            showOrderNotes = showOrderNotes,
            showTotals = showTotals,
            styleOrderContentBold = styleOrderContentBold,
            styleOrderContentLarge = styleOrderContentLarge,
            showPaymentInfo = showPaymentInfo,
            stylePaymentInfoBold = stylePaymentInfoBold,
            stylePaymentInfoLarge = stylePaymentInfoLarge,
            showFooter = showFooter,
            styleFooterBold = styleFooterBold,
            styleFooterLarge = styleFooterLarge,
            footerText = footerText,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromDomainModel(config: TemplateConfig): TemplateConfigEntity {
            return TemplateConfigEntity(
                templateId = config.templateId,
                templateType = config.templateType.name,
                templateName = config.templateName,
                showStoreInfo = config.showStoreInfo,
                showStoreName = config.showStoreName,
                showStoreAddress = config.showStoreAddress,
                showStorePhone = config.showStorePhone,
                showOrderInfo = config.showOrderInfo,
                showOrderNumber = config.showOrderNumber,
                showOrderDate = config.showOrderDate,
                showCustomerInfo = config.showCustomerInfo,
                showCustomerName = config.showCustomerName,
                showCustomerPhone = config.showCustomerPhone,
                showDeliveryInfo = config.showDeliveryInfo,
                showOrderContent = config.showOrderContent,
                showItemDetails = config.showItemDetails,
                showItemPrices = config.showItemPrices,
                showOrderNotes = config.showOrderNotes,
                showTotals = config.showTotals,
                showPaymentInfo = config.showPaymentInfo,
                showFooter = config.showFooter,
                footerText = config.footerText,
                createdAt = config.createdAt,
                updatedAt = config.updatedAt
            )
        }
    }
} 