package com.example.wooauto.data.templates

import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.models.TemplateConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.repositories.DomainTemplateConfigRepository
import com.example.wooauto.domain.templates.OrderPrintTemplate
import com.example.wooauto.domain.templates.TemplateType
import com.example.wooauto.utils.ThermalPrinterFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultOrderPrintTemplate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: DomainSettingRepository,
    private val templateConfigRepository: DomainTemplateConfigRepository
) : OrderPrintTemplate {

    private val TAG = "OrderPrintTemplate"
    
    override fun generateOrderPrintContent(order: Order, config: PrinterConfig): String {
        Log.d(TAG, "生成订单打印内容: ${order.number}")
        
        // 检查是否为手动打印（会自动清除标志）
        val isManualPrint = runBlocking { 
            settingRepository.getAndClearTemporaryManualPrintFlag()
        }
        
        val customTemplateId = if (isManualPrint) {
            // 手动打印：使用手动打印模板ID
            runBlocking { 
                settingRepository.getCurrentCustomTemplateId()
            }?.takeIf { it.isNotEmpty() }
        } else {
            // 自动打印：使用自动打印模板ID
            runBlocking { 
                settingRepository.getDefaultAutoPrintTemplateId()
            }?.takeIf { it.isNotEmpty() }
        }
        
        Log.d(TAG, "打印模式: ${if (isManualPrint) "手动打印" else "自动打印"}, 使用模板ID: $customTemplateId")
        
        val templateId: String
        val templateType: TemplateType
        
        if (customTemplateId != null && customTemplateId.startsWith("custom_")) {
            // 使用自定义模板
            templateId = customTemplateId
            templateType = TemplateType.FULL_DETAILS // 自定义模板都使用FULL_DETAILS类型
            Log.d(TAG, "使用自定义模板: $customTemplateId")
        } else {
            // 使用默认模板
            templateType = runBlocking { 
            settingRepository.getDefaultTemplateType() ?: TemplateType.FULL_DETAILS 
            }
            
            templateId = when (templateType) {
                TemplateType.FULL_DETAILS -> "full_details"
                TemplateType.DELIVERY -> "delivery" 
                TemplateType.KITCHEN -> "kitchen"
            }
            Log.d(TAG, "使用默认模板类型: $templateType")
        }
        
        // 获取模板配置
        val templateConfig = runBlocking { 
            templateConfigRepository.getOrCreateConfig(templateId, templateType)
        }
        
        Log.d(TAG, "获取到模板配置: ${templateConfig.templateId}, 模板类型: ${templateConfig.templateType}")
        
        // 根据不同模板类型生成内容
        return when (templateType) {
            TemplateType.FULL_DETAILS -> generateFullDetailsTemplate(order, config, templateConfig)
            TemplateType.DELIVERY -> generateDeliveryTemplate(order, config, templateConfig)
            TemplateType.KITCHEN -> generateKitchenTemplate(order, config, templateConfig)
        }
    }
    
    /**
     * 生成完整订单详情模板 - 包含所有信息
     */
    private fun generateFullDetailsTemplate(order: Order, config: PrinterConfig, templateConfig: TemplateConfig): String {
        val paperWidth = config.paperWidth
        val sb = StringBuilder()
        
        // 组合各个模块 - 使用商店信息生成方法，避免重复
        sb.append(generateStoreInfo(order, config, templateConfig, paperWidth))
        sb.append(generateOrderInfo(order, config, templateConfig, paperWidth))
        sb.append(generateCustomerInfo(order, config, templateConfig, paperWidth))
        sb.append(generateDeliveryInfo(order, config, templateConfig, paperWidth))
        sb.append(generateItemDetails(order, config, templateConfig, paperWidth))
        sb.append(generateTotals(order, config, templateConfig, paperWidth))
        sb.append(generatePaymentInfo(order, config, templateConfig, paperWidth))
        sb.append(generateOrderNotes(order, config, templateConfig, paperWidth))
        sb.append(generateFooter(order, config, templateConfig, paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成配送订单模板 - 重点突出配送信息和菜品信息
     */
    private fun generateDeliveryTemplate(order: Order, config: PrinterConfig, templateConfig: TemplateConfig): String {
        val paperWidth = config.paperWidth
        val sb = StringBuilder()
        
        // 配送模板特殊标题
        if (templateConfig.showStoreInfo) {
            val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
            if (storeName.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatTitle(storeName, paperWidth))
            }
        }
        sb.append(ThermalPrinterFormatter.formatTitle("DELIVERY RECEIPT", paperWidth))
        sb.append(ThermalPrinterFormatter.addEmptyLines(1))
        
        // 组合各个模块
        sb.append(generateOrderInfo(order, config, templateConfig, paperWidth))
        sb.append(generateCustomerInfo(order, config, templateConfig, paperWidth, "DELIVERY INFORMATION"))
        sb.append(generateDeliveryInfo(order, config, templateConfig, paperWidth))
        sb.append(generateItemDetails(order, config, templateConfig, paperWidth, "ORDER ITEMS"))
        sb.append(generateTotals(order, config, templateConfig, paperWidth))
        sb.append(generatePaymentInfo(order, config, templateConfig, paperWidth))
        sb.append(generateOrderNotes(order, config, templateConfig, paperWidth, "DELIVERY NOTES:"))
        
        // 添加空行便于撕纸
        sb.append(ThermalPrinterFormatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    /**
     * 生成厨房订单模板 - 仅包含菜品信息和下单时间
     */
    private fun generateKitchenTemplate(order: Order, config: PrinterConfig, templateConfig: TemplateConfig): String {
        val paperWidth = config.paperWidth
        val sb = StringBuilder()
        
        // 厨房模板特殊标题
        if (templateConfig.showStoreInfo) {
            val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
            if (storeName.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatTitle(storeName, paperWidth))
            }
        }
        sb.append(ThermalPrinterFormatter.formatTitle("KITCHEN ORDER", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        // 组合各个模块
        sb.append(generateOrderInfo(order, config, templateConfig, paperWidth, showPrintTime = false))
        sb.append(generateDeliveryInfo(order, config, templateConfig, paperWidth, showOnlyOrderType = true))
        sb.append(generateCustomerInfo(order, config, templateConfig, paperWidth, showMinimal = true))
        sb.append(generateItemDetails(order, config, templateConfig, paperWidth, "ITEMS TO PREPARE", kitchenStyle = true))
        sb.append(generateOrderNotes(order, config, templateConfig, paperWidth, "SPECIAL INSTRUCTIONS:"))
        
        // 打印时间
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sb.append(ThermalPrinterFormatter.formatRightText("Printed: ${dateFormat.format(Date())}", paperWidth))
        
        // 添加额外的空行，方便撕纸
        sb.append(ThermalPrinterFormatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    // ==================== 功能模块方法 ====================
    
    /**
     * 生成页眉（商店名称）
     */
    private fun generateHeader(order: Order, config: PrinterConfig, templateConfig: TemplateConfig, 
                              paperWidth: Int): String {
        val sb = StringBuilder()
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        
        // 只有当商店名称不为空时才打印标题
        if (storeName.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatTitle(storeName, paperWidth))
            sb.append(ThermalPrinterFormatter.addEmptyLines(1))
        }
        
        return sb.toString()
    }
    
    /**
     * 生成商店信息（商店名称、地址、电话）
     */
    private fun generateStoreInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                 paperWidth: Int): String {
        // 检查主控制选项
        if (!templateConfig.showStoreInfo) return ""
        
        val sb = StringBuilder()
        var hasContent = false
        
        // 根据细分选项显示对应的商店信息
        if (templateConfig.showStoreName) {
            val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
            if (storeName.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText(storeName, paperWidth))
                hasContent = true
            }
        }
        
        if (templateConfig.showStoreAddress) {
            val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
            if (storeAddress.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText(storeAddress, paperWidth))
                hasContent = true
            }
        }
        
        if (templateConfig.showStorePhone) {
            val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
            if (storePhone.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText("Tel: $storePhone", paperWidth))
                hasContent = true
            }
        }
        
        // 只有当有实际内容时才返回，避免打印空白区域
        return if (hasContent) sb.toString() else ""
    }
    
    /**
     * 生成订单信息（订单号、日期、打印时间）
     */
    private fun generateOrderInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                 paperWidth: Int, showPrintTime: Boolean = true): String {
        if (!templateConfig.showOrderInfo) return ""
        
        val sb = StringBuilder()
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        if (templateConfig.showOrderNumber) {
            sb.append(ThermalPrinterFormatter.formatLabelValue("Order #", order.number, paperWidth))
        }
        
        if (templateConfig.showOrderDate) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(order.dateCreated)
            sb.append(ThermalPrinterFormatter.formatLabelValue("Date", formattedDate, paperWidth))
            
            if (showPrintTime) {
                val currentTime = ThermalPrinterFormatter.formatDateTime(Date())
                sb.append(ThermalPrinterFormatter.formatLabelValue("Print Time", currentTime, paperWidth))
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 生成客户信息
     */
    private fun generateCustomerInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                    paperWidth: Int, 
                                    sectionTitle: String = "Customer Information", showMinimal: Boolean = false): String {
        if (!templateConfig.showCustomerInfo) return ""
        
        val sb = StringBuilder()
        var hasContent = false
        
        if (!showMinimal) {
            sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
            sb.append(ThermalPrinterFormatter.formatLeftText(ThermalPrinterFormatter.formatBold(sectionTitle), paperWidth))
        }
        
        if (templateConfig.showCustomerName && order.customerName.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatLabelValue("Customer", order.customerName, paperWidth))
            hasContent = true
        }
        
        if (templateConfig.showCustomerPhone && !showMinimal && order.contactInfo.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatLabelValue("Contact", order.contactInfo, paperWidth))
            hasContent = true
        }
        
        // 如果没有任何内容，返回空字符串
        return if (hasContent || showMinimal) sb.toString() else ""
    }
    
    /**
     * 生成配送信息
     */
    private fun generateDeliveryInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                    paperWidth: Int, showOnlyOrderType: Boolean = false): String {
        if (!templateConfig.showDeliveryInfo || order.woofoodInfo == null) return ""
        
        val sb = StringBuilder()
        
        if (!showOnlyOrderType) {
            sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
            sb.append(ThermalPrinterFormatter.formatLeftText(ThermalPrinterFormatter.formatBold("Delivery Info"), paperWidth))
        }
        
        // 订单类型 (配送或取餐)
        sb.append(ThermalPrinterFormatter.formatLabelValue("Order Type", 
            if (order.woofoodInfo.isDelivery) "Delivery" else "Takeaway", paperWidth))
        
        if (!showOnlyOrderType && order.woofoodInfo.isDelivery) {
            order.woofoodInfo.deliveryAddress?.let {
                sb.append(ThermalPrinterFormatter.formatLabelValue("Delivery Address", it, paperWidth))
            }
            
            order.woofoodInfo.deliveryFee?.let {
                sb.append(ThermalPrinterFormatter.formatLabelValue("Delivery Fee", it, paperWidth))
            }
            
            order.woofoodInfo.tip?.let {
                sb.append(ThermalPrinterFormatter.formatLabelValue("Tip", it, paperWidth))
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 生成商品明细
     */
    private fun generateItemDetails(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                   paperWidth: Int, 
                                   sectionTitle: String = "Order Items", kitchenStyle: Boolean = false): String {
        if (!templateConfig.showOrderContent || !templateConfig.showItemDetails) return ""
        
        val sb = StringBuilder()
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        if (kitchenStyle) {
            sb.append(ThermalPrinterFormatter.formatCenteredText(ThermalPrinterFormatter.formatBold(sectionTitle), paperWidth))
        } else {
            sb.append(ThermalPrinterFormatter.formatLeftText(ThermalPrinterFormatter.formatBold(sectionTitle), paperWidth))
        }
        
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        // 表头（非厨房模式且显示价格时）
        if (!kitchenStyle && templateConfig.showItemPrices) {
            sb.append(ThermalPrinterFormatter.formatLeftRightText(
                ThermalPrinterFormatter.formatBold("Item"), 
                ThermalPrinterFormatter.formatBold("Qty x Price"), 
                paperWidth
            ))
        }
        
        // 商品列表
        order.items.forEach { item ->
            val name = item.name
            
            if (!kitchenStyle && templateConfig.showItemPrices) {
                val price = formatPrice(item.price.toDouble())
                sb.append(ThermalPrinterFormatter.formatItemPriceLine(name, item.quantity.toInt(), price, paperWidth))
            } else {
                sb.append(ThermalPrinterFormatter.formatLeftText(
                    ThermalPrinterFormatter.formatBold("${item.quantity} x $name"), 
                paperWidth
            ))
            }
            
            // 商品选项
            if (item.options.isNotEmpty()) {
                item.options.forEach { option ->
                    sb.append(ThermalPrinterFormatter.formatIndentedText(
                        if (kitchenStyle) "${option.name}: ${option.value}" else "- ${option.name}: ${option.value}", 
                        1, paperWidth))
                }
            }
            
            // 厨房模式下每个商品之间添加额外空行
            if (kitchenStyle) {
                sb.append(ThermalPrinterFormatter.addEmptyLines(1))
            }
        }
        
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成总计信息
     */
    private fun generateTotals(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                              paperWidth: Int): String {
        if (!templateConfig.showOrderContent || !templateConfig.showTotals) return ""
        
        val sb = StringBuilder()
        
        // 先显示小计
        if (order.subtotal.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatLeftRightText("subtotal:", order.subtotal, paperWidth))
        }
        
        // 显示税费明细
        order.taxLines.forEach { taxLine ->
            val taxLabel = when {
                taxLine.label.contains("GST", ignoreCase = true) -> "GST:"
                taxLine.label.contains("PST", ignoreCase = true) -> "PST:"
                else -> "${taxLine.label} (${taxLine.ratePercent}%):"
            }
            sb.append(ThermalPrinterFormatter.formatLeftRightText(taxLabel, taxLine.taxTotal, paperWidth))
        }
        
        // 如果没有税费明细但有总税费
        if (order.taxLines.isEmpty() && order.totalTax.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatLeftRightText("tax:", order.totalTax, paperWidth))
        }
        
        // 显示外卖费用
        if (order.woofoodInfo?.deliveryFee != null && order.woofoodInfo.deliveryFee.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatLeftRightText("delivery fee:", order.woofoodInfo.deliveryFee, paperWidth))
        }
        
        // 显示小费
        if (order.woofoodInfo?.tip != null && order.woofoodInfo.tip.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatLeftRightText("tips:", order.woofoodInfo.tip, paperWidth))
        }
        
        // 显示折扣
        if (order.discountTotal.isNotEmpty() && order.discountTotal != "0.00") {
            sb.append(ThermalPrinterFormatter.formatLeftRightText("discount:", "-${order.discountTotal}", paperWidth))
        }
        
        sb.append(ThermalPrinterFormatter.formatLeftRightText("total:", order.total, paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成支付信息
     */
    private fun generatePaymentInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                   paperWidth: Int): String {
        if (!templateConfig.showPaymentInfo) return ""
        
        val sb = StringBuilder()
        sb.append(ThermalPrinterFormatter.formatLeftRightText("payment method:", order.paymentMethod, paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成订单备注
     */
    private fun generateOrderNotes(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                  paperWidth: Int,
                                  sectionTitle: String = "Order Notes:"): String {
        if (!templateConfig.showOrderContent || !templateConfig.showOrderNotes || order.notes.isEmpty()) return ""
        
        val sb = StringBuilder()
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText(ThermalPrinterFormatter.formatBold(sectionTitle), paperWidth))
        sb.append(ThermalPrinterFormatter.formatMultilineText(order.notes, paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成页脚
     */
    private fun generateFooter(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                              paperWidth: Int): String {
        val sb = StringBuilder()
        
        if (templateConfig.showFooter && templateConfig.footerText.isNotBlank()) {
            sb.append(ThermalPrinterFormatter.formatFooter(templateConfig.footerText, paperWidth))
        } else {
            sb.append(ThermalPrinterFormatter.addEmptyLines(3))
        }
        
        return sb.toString()
    }
    
    // ==================== 其他方法 ====================
    
    override fun generateTestPrintContent(config: PrinterConfig): String {
        val paperWidth = config.paperWidth
        return when (paperWidth) {
            PrinterConfig.PAPER_WIDTH_57MM -> generate58mmTestContent(config)
            PrinterConfig.PAPER_WIDTH_80MM -> generate80mmTestContent(config)
            else -> generateDefaultTestContent(config)
        }
    }
    
    /**
     * 生成58mm纸张专用测试内容
     */
    private fun generate58mmTestContent(config: PrinterConfig): String {
        val paperWidth = config.paperWidth
        val sb = StringBuilder()
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
        val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
        
        // 58mm纸张标题
        if (storeName.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatTitle(storeName, paperWidth))
        }
        sb.append(ThermalPrinterFormatter.formatTitle("58MM PRINTER TEST", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("Receipt Printing Test", paperWidth))
        sb.append(ThermalPrinterFormatter.addEmptyLines(1))
        
        // 时间戳
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sb.append(ThermalPrinterFormatter.formatCenteredText("Test Time: ${dateFormat.format(Date())}", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        // 打印机信息 (58mm简化版)
        sb.append(ThermalPrinterFormatter.formatLeftText("Printer Information:", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Name", config.name, paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Address", config.address.takeLast(8), paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Brand", config.brand.displayName, paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Paper", "58mm (32 chars/line)", paperWidth))
        
        // 商店信息测试
        if (storeName.isNotEmpty() || storeAddress.isNotEmpty() || storePhone.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
            sb.append(ThermalPrinterFormatter.formatLeftText("Store Info Test:", paperWidth))
            if (storeName.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText(storeName, paperWidth))
            }
            if (storeAddress.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText(storeAddress, paperWidth))
            }
            if (storePhone.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText("Tel: $storePhone", paperWidth))
            }
        }
        
        // 对齐测试
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Alignment Test:", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("< Left Aligned Text", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("* Center Aligned *", paperWidth))
        sb.append(ThermalPrinterFormatter.formatRightText("Right Aligned Text >", paperWidth))
        
        // 58mm商品列表测试 (紧凑格式)
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("Item List Test", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        // 58mm适配的商品格式
        sb.append(ThermalPrinterFormatter.formatLeftText("2x Chicken Curry", paperWidth))
        sb.append(ThermalPrinterFormatter.formatRightText("$38.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("1x Beef Stew", paperWidth))
        sb.append(ThermalPrinterFormatter.formatRightText("$28.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("3x Rice Bowl", paperWidth))
        sb.append(ThermalPrinterFormatter.formatRightText("$9.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        // 总计测试
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Subtotal:", "$75.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Tax:", "$7.50", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Total:", "$82.50", paperWidth))
        
        // 字符测试
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Character Set Test:", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("English: Test Print OK", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Numbers: 0123456789", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Symbols: !@#$%^&*()", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Special: []{}|\\<>/?", paperWidth))
        
        // 58mm结尾
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("58MM Test Complete", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("Test Completed", paperWidth))
        
        if (config.autoCut) {
            sb.append(ThermalPrinterFormatter.formatCenteredText("Auto Cut Enabled", paperWidth))
        } else {
            sb.append(ThermalPrinterFormatter.formatCenteredText("Manual Tear", paperWidth))
        }
        
        sb.append(ThermalPrinterFormatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    /**
     * 生成80mm纸张专用测试内容
     */
    private fun generate80mmTestContent(config: PrinterConfig): String {
        val paperWidth = config.paperWidth
        val sb = StringBuilder()
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
        val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
        
        // 80mm纸张标题
        if (storeName.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatTitle(storeName, paperWidth))
        }
        sb.append(ThermalPrinterFormatter.formatTitle("80MM THERMAL PRINTER TEST", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("80MM Thermal Printer Test", paperWidth))
        sb.append(ThermalPrinterFormatter.addEmptyLines(1))
        
        // 详细时间戳
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sb.append(ThermalPrinterFormatter.formatCenteredText("Test Execution Time: ${dateFormat.format(Date())}", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        // 详细打印机信息
        sb.append(ThermalPrinterFormatter.formatCenteredText("Printer Configuration", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Printer Name", config.name, paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Bluetooth Address", config.address, paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Printer Brand", config.brand.displayName, paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Paper Width", "80mm (42 chars/line)", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Auto Cut", if (config.autoCut) "Enabled" else "Disabled", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Auto Print", if (config.isAutoPrint) "Enabled" else "Disabled", paperWidth))
        
        // 商店信息完整测试
        if (storeName.isNotEmpty() || storeAddress.isNotEmpty() || storePhone.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
            sb.append(ThermalPrinterFormatter.formatCenteredText("Store Information Test", paperWidth))
            sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
            if (storeName.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText(storeName, paperWidth))
            } else {
                sb.append(ThermalPrinterFormatter.formatCenteredText("[Please configure store name in settings]", paperWidth))
            }
            if (storeAddress.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText(storeAddress, paperWidth))
            } else {
                sb.append(ThermalPrinterFormatter.formatCenteredText("[Please configure store address in settings]", paperWidth))
            }
            if (storePhone.isNotEmpty()) {
                sb.append(ThermalPrinterFormatter.formatCenteredText("Contact: $storePhone", paperWidth))
            } else {
                sb.append(ThermalPrinterFormatter.formatCenteredText("[Please configure contact phone in settings]", paperWidth))
            }
        }
        
        // 对齐和格式测试
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("Text Alignment and Format Test", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("<<< Left Aligned Text Test (LEFT ALIGNMENT)", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("*** Center Aligned Text Test (CENTER ALIGNMENT) ***", paperWidth))
        sb.append(ThermalPrinterFormatter.formatRightText("Right Aligned Text Test (RIGHT ALIGNMENT) >>>", paperWidth))
        
        // 80mm完整商品列表测试
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("Complete Order Format Test", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Order Number: WO2024030601", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Order Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Customer Name: John Smith", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Contact Phone: 555-***-8888", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        // 80mm详细商品列表
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Item Name", "Qty x Price", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatItemPriceLine("Braised Pork Set", 2, "45.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("  Side dishes: Rice, Vegetables, Soup", paperWidth))
        sb.append(ThermalPrinterFormatter.formatItemPriceLine("Kung Pao Chicken", 1, "32.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("  Spice level: Medium, Less oil", paperWidth))
        sb.append(ThermalPrinterFormatter.formatItemPriceLine("Hot & Sour Soup", 2, "12.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatItemPriceLine("Cola (Large)", 3, "8.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        
        // 详细费用计算
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Item Subtotal:", "$146.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Packaging Fee:", "$3.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Delivery Fee:", "$5.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("GST (5%):", "$7.70", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("PST (7%):", "$10.78", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Tip:", "$15.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Discount:", "-$10.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Order Total:", "$177.48", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Payment Method:", "Credit Card", paperWidth))
        
        // 字符编码和特殊字符测试
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("Character Encoding Test", paperWidth))
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("English: This is English character test", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("French: Ceci est un test francais", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Spanish: Esta es una prueba espanola", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("German: Dies ist ein deutscher Test", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Numbers: 0123456789", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Symbols: !@#$%^&*()_+-=[]{}|;':\",./<>?", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Currency: $ USD, EUR, GBP, CAD", paperWidth))
        
        // 备注测试
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Order Notes:", paperWidth))
        sb.append(ThermalPrinterFormatter.formatMultilineText("Please deliver at the specified time.\nIf there are any issues, please contact in advance.\nThank you for your cooperation!", paperWidth))
        
        // 80mm结尾信息
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("80MM Thermal Printer Test Complete", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("80MM Thermal Printer Test Completed", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("If the above content displays correctly,", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("the printer is working properly", paperWidth))
        
        if (config.autoCut) {
            sb.append(ThermalPrinterFormatter.formatCenteredText("Auto cut function enabled, will auto cut", paperWidth))
        } else {
            sb.append(ThermalPrinterFormatter.formatCenteredText("Auto cut function disabled, manual tear", paperWidth))
        }
        
        sb.append(ThermalPrinterFormatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    /**
     * 生成默认测试内容（兼容其他纸张宽度）
     */
    private fun generateDefaultTestContent(config: PrinterConfig): String {
        val paperWidth = config.paperWidth
        val sb = StringBuilder()
        
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        
        // 标题
        if (storeName.isNotEmpty()) {
            sb.append(ThermalPrinterFormatter.formatTitle(storeName, paperWidth))
        }
        sb.append(ThermalPrinterFormatter.formatTitle("PRINTER TEST", paperWidth))
        sb.append(ThermalPrinterFormatter.addEmptyLines(1))
        
        // 基本信息
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Printer Name", config.name, paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Bluetooth Address", config.address, paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Printer Brand", config.brand.displayName, paperWidth))
        sb.append(ThermalPrinterFormatter.formatLabelValue("Paper Width", "${paperWidth}mm", paperWidth))
        
        // 对齐测试
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftText("Left Alignment Test", paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("Center Alignment Test", paperWidth))
        sb.append(ThermalPrinterFormatter.formatRightText("Right Alignment Test", paperWidth))
        
        // 简单商品测试
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatItemPriceLine("Test Item A", 2, "25.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatItemPriceLine("Test Item B", 1, "15.00", paperWidth))
        sb.append(ThermalPrinterFormatter.formatLeftRightText("Total:", "$65.00", paperWidth))
        
        // 结尾
        sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
        sb.append(ThermalPrinterFormatter.formatCenteredText("Test Complete", paperWidth))
        
        // 添加走纸和切纸命令
        sb.append(ThermalPrinterFormatter.addEmptyLines(3))
        sb.append(addTestPrintCutCommand(config))
        
        return sb.toString()
    }
    
    /**
     * 为测试打印添加走纸和切纸命令
     */
    private fun addTestPrintCutCommand(config: PrinterConfig): String {
        val sb = StringBuilder()
        
        // 8个换行符用于走纸
        sb.append("\n\n\n\n\n\n\n\n")

        // 安全的切纸命令格式
        if (config.autoCut) {
            sb.append("${27.toChar()}${64.toChar()}")     // ESC @ - 初始化
            sb.append("${29.toChar()}${86.toChar()}${1.toChar()}")  // GS V 1 - 切纸
        }

        // 确保命令执行的换行
        sb.append("\n\n")
        
        return sb.toString()
    }
    
    // 辅助方法：格式化价格
    private fun formatPrice(price: Double): String {
        val currencySymbol = runBlocking { settingRepository.getCurrencySymbolFlow().first() }
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 