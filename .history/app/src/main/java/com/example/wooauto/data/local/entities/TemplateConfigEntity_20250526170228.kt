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
    val showStoreInfo: Boolean,
    val showStoreName: Boolean,
    val showStoreAddress: Boolean,
    val showStorePhone: Boolean,
    val showOrderNumber: Boolean,
    val showCustomerInfo: Boolean,
    val showOrderDate: Boolean,
    val showDeliveryInfo: Boolean,
    val showPaymentInfo: Boolean,
    val showItemDetails: Boolean,
    val showItemPrices: Boolean,
    val showOrderNotes: Boolean,
    val showTotals: Boolean,
    val showFooter: Boolean,
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
            showOrderNumber = showOrderNumber,
            showCustomerInfo = showCustomerInfo,
            showOrderDate = showOrderDate,
            showDeliveryInfo = showDeliveryInfo,
            showPaymentInfo = showPaymentInfo,
            showItemDetails = showItemDetails,
            showItemPrices = showItemPrices,
            showOrderNotes = showOrderNotes,
            showTotals = showTotals,
            showFooter = showFooter,
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
                showOrderNumber = config.showOrderNumber,
                showCustomerInfo = config.showCustomerInfo,
                showOrderDate = config.showOrderDate,
                showDeliveryInfo = config.showDeliveryInfo,
                showPaymentInfo = config.showPaymentInfo,
                showItemDetails = config.showItemDetails,
                showItemPrices = config.showItemPrices,
                showOrderNotes = config.showOrderNotes,
                showTotals = config.showTotals,
                showFooter = config.showFooter,
                createdAt = config.createdAt,
                updatedAt = config.updatedAt
            )
        }
    }
} 