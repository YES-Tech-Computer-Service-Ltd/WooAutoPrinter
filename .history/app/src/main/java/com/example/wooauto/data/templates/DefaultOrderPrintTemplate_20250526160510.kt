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
        
        // 获取当前的模板类型
        val templateType = runBlocking { 
            settingRepository.getDefaultTemplateType() ?: TemplateType.FULL_DETAILS 
        }
        
        Log.d(TAG, "使用模板类型: $templateType")
        
        // 获取模板配置ID
        val templateId = when (templateType) {
            TemplateType.FULL_DETAILS -> "full_details"
            TemplateType.DELIVERY -> "delivery" 
            TemplateType.KITCHEN -> "kitchen"
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
        val formatter = ThermalPrinterFormatter
        val sb = StringBuilder()
        
        // 组合各个模块
        sb.append(generateHeader(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateStoreInfo(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateOrderInfo(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateCustomerInfo(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateDeliveryInfo(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateItemDetails(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateTotals(order, config, templateConfig, paperWidth, formatter))
        sb.append(generatePaymentInfo(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateOrderNotes(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateFooter(order, config, templateConfig, paperWidth, formatter))
        
        return sb.toString()
    }
    
    /**
     * 生成配送订单模板 - 重点突出配送信息和菜品信息
     */
    private fun generateDeliveryTemplate(order: Order, config: PrinterConfig, templateConfig: TemplateConfig): String {
        val paperWidth = config.paperWidth
        val formatter = ThermalPrinterFormatter
        val sb = StringBuilder()
        
        // 配送模板特殊标题
        if (templateConfig.showStoreInfo) {
            val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
            sb.append(formatter.formatTitle(storeName, paperWidth))
        }
        sb.append(formatter.formatTitle("DELIVERY RECEIPT", paperWidth))
        sb.append(formatter.addEmptyLines(1))
        
        // 组合各个模块
        sb.append(generateOrderInfo(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateCustomerInfo(order, config, templateConfig, paperWidth, formatter, "DELIVERY INFORMATION"))
        sb.append(generateDeliveryInfo(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateItemDetails(order, config, templateConfig, paperWidth, formatter, "ORDER ITEMS"))
        sb.append(generateTotals(order, config, templateConfig, paperWidth, formatter))
        sb.append(generatePaymentInfo(order, config, templateConfig, paperWidth, formatter))
        sb.append(generateOrderNotes(order, config, templateConfig, paperWidth, formatter, "DELIVERY NOTES:"))
        
        // 添加空行便于撕纸
        sb.append(formatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    /**
     * 生成厨房订单模板 - 仅包含菜品信息和下单时间
     */
    private fun generateKitchenTemplate(order: Order, config: PrinterConfig, templateConfig: TemplateConfig): String {
        val paperWidth = config.paperWidth
        val formatter = ThermalPrinterFormatter
        val sb = StringBuilder()
        
        // 厨房模板特殊标题
        if (templateConfig.showStoreInfo) {
            val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
            sb.append(formatter.formatTitle(storeName, paperWidth))
        }
        sb.append(formatter.formatTitle("KITCHEN ORDER", paperWidth))
        sb.append(formatter.formatDivider(paperWidth))
        
        // 组合各个模块
        sb.append(generateOrderInfo(order, config, templateConfig, paperWidth, formatter, showPrintTime = false))
        sb.append(generateDeliveryInfo(order, config, templateConfig, paperWidth, formatter, showOnlyOrderType = true))
        sb.append(generateCustomerInfo(order, config, templateConfig, paperWidth, formatter, showMinimal = true))
        sb.append(generateItemDetails(order, config, templateConfig, paperWidth, formatter, "ITEMS TO PREPARE", kitchenStyle = true))
        sb.append(generateOrderNotes(order, config, templateConfig, paperWidth, formatter, "SPECIAL INSTRUCTIONS:"))
        
        // 打印时间
        sb.append(formatter.formatDivider(paperWidth))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sb.append(formatter.formatRightText("Printed: ${dateFormat.format(Date())}", paperWidth))
        
        // 添加额外的空行，方便撕纸
        sb.append(formatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    // ==================== 功能模块方法 ====================
    
    /**
     * 生成页眉（商店名称）
     */
    private fun generateHeader(order: Order, config: PrinterConfig, templateConfig: TemplateConfig, 
                              paperWidth: Int, formatter: ThermalPrinterFormatter): String {
        val sb = StringBuilder()
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        
        // 标题 (总是打印)
        sb.append(formatter.formatTitle(storeName, paperWidth))
        sb.append(formatter.addEmptyLines(1))
        
        return sb.toString()
    }
    
    /**
     * 生成商店信息（地址、电话）
     */
    private fun generateStoreInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                 paperWidth: Int, formatter: ThermalPrinterFormatter): String {
        if (!templateConfig.showStoreInfo) return ""
        
        val sb = StringBuilder()
        val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
        val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
        
        if (storeAddress.isNotEmpty()) {
            sb.append(formatter.formatCenteredText(storeAddress, paperWidth))
        }
        if (storePhone.isNotEmpty()) {
            sb.append(formatter.formatCenteredText("Tel: $storePhone", paperWidth))
        }
        
        return sb.toString()
    }
    
    /**
     * 生成订单信息（订单号、日期、打印时间）
     */
    private fun generateOrderInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                 paperWidth: Int, formatter: ThermalPrinterFormatter, showPrintTime: Boolean = true): String {
        val sb = StringBuilder()
        sb.append(formatter.formatDivider(paperWidth))
        
        if (templateConfig.showOrderNumber) {
            sb.append(formatter.formatLabelValue("Order #", order.number, paperWidth))
        }
        
        if (templateConfig.showOrderDate) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(order.dateCreated)
            sb.append(formatter.formatLabelValue("Date", formattedDate, paperWidth))
            
            if (showPrintTime) {
                val currentTime = formatter.formatDateTime(Date())
                sb.append(formatter.formatLabelValue("Print Time", currentTime, paperWidth))
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 生成客户信息
     */
    private fun generateCustomerInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                    paperWidth: Int, formatter: ThermalPrinterFormatter, 
                                    sectionTitle: String = "Customer Information", showMinimal: Boolean = false): String {
        if (!templateConfig.showCustomerInfo) return ""
        if (order.customerName.isEmpty() && order.contactInfo.isEmpty() && order.billingInfo.isEmpty()) return ""
        
        val sb = StringBuilder()
        sb.append(formatter.formatDivider(paperWidth))
        
        if (!showMinimal) {
            sb.append(formatter.formatLeftText(formatter.formatBold(sectionTitle), paperWidth))
        }
        
        if (order.customerName.isNotEmpty()) {
            sb.append(formatter.formatLabelValue("Customer", order.customerName, paperWidth))
        }
        
        if (!showMinimal && order.contactInfo.isNotEmpty()) {
            sb.append(formatter.formatLabelValue("Contact", order.contactInfo, paperWidth))
        }
        
        if (!showMinimal && order.billingInfo.isNotEmpty()) {
            sb.append(formatter.formatLabelValue("Address", order.billingInfo, paperWidth))
        }
        
        return sb.toString()
    }
    
    /**
     * 生成配送信息
     */
    private fun generateDeliveryInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                    paperWidth: Int, formatter: ThermalPrinterFormatter, showOnlyOrderType: Boolean = false): String {
        if (!templateConfig.showDeliveryInfo || order.woofoodInfo == null) return ""
        
        val sb = StringBuilder()
        
        if (!showOnlyOrderType) {
            sb.append(formatter.formatDivider(paperWidth))
            sb.append(formatter.formatLeftText(formatter.formatBold("Delivery Info"), paperWidth))
        }
        
        // 订单类型 (配送或取餐)
        sb.append(formatter.formatLabelValue("Order Type", 
            if (order.woofoodInfo.isDelivery) "Delivery" else "Takeaway", paperWidth))
        
        if (!showOnlyOrderType && order.woofoodInfo.isDelivery) {
            order.woofoodInfo.deliveryAddress?.let {
                sb.append(formatter.formatLabelValue("Delivery Address", it, paperWidth))
            }
            
            order.woofoodInfo.deliveryFee?.let {
                sb.append(formatter.formatLabelValue("Delivery Fee", it, paperWidth))
            }
            
            order.woofoodInfo.tip?.let {
                sb.append(formatter.formatLabelValue("Tip", it, paperWidth))
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 生成商品明细
     */
    private fun generateItemDetails(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                   paperWidth: Int, formatter: ThermalPrinterFormatter, 
                                   sectionTitle: String = "Order Items", kitchenStyle: Boolean = false): String {
        if (!templateConfig.showItemDetails) return ""
        
        val sb = StringBuilder()
        sb.append(formatter.formatDivider(paperWidth))
        
        if (kitchenStyle) {
            sb.append(formatter.formatCenteredText(formatter.formatBold(sectionTitle), paperWidth))
        } else {
            sb.append(formatter.formatLeftText(formatter.formatBold(sectionTitle), paperWidth))
        }
        
        sb.append(formatter.formatDivider(paperWidth))
        
        // 表头（非厨房模式且显示价格时）
        if (!kitchenStyle && templateConfig.showItemPrices) {
            sb.append(formatter.formatLeftRightText(
                formatter.formatBold("Item"), 
                formatter.formatBold("Qty x Price"), 
                paperWidth
            ))
        }
        
        // 商品列表
        order.items.forEach { item ->
            val name = item.name
            
            if (!kitchenStyle && templateConfig.showItemPrices) {
                val price = formatPrice(item.price.toDouble())
                sb.append(formatter.formatItemPriceLine(name, item.quantity.toInt(), price, paperWidth))
            } else {
                sb.append(formatter.formatLeftText(
                    formatter.formatBold("${item.quantity} x $name"), 
                    paperWidth
                ))
            }
            
            // 商品选项
            if (item.options.isNotEmpty()) {
                item.options.forEach { option ->
                    sb.append(formatter.formatIndentedText(
                        if (kitchenStyle) "${option.name}: ${option.value}" else "- ${option.name}: ${option.value}", 
                        1, paperWidth))
                }
            }
            
            // 厨房模式下每个商品之间添加额外空行
            if (kitchenStyle) {
                sb.append(formatter.addEmptyLines(1))
            }
        }
        
        sb.append(formatter.formatDivider(paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成总计信息
     */
    private fun generateTotals(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                              paperWidth: Int, formatter: ThermalPrinterFormatter): String {
        if (!templateConfig.showTotals) return ""
        
        val sb = StringBuilder()
        
        // 先显示小计
        if (order.subtotal.isNotEmpty()) {
            sb.append(formatter.formatLeftRightText("subtotal:", order.subtotal, paperWidth))
        }
        
        // 显示税费明细
        order.taxLines.forEach { taxLine ->
            val taxLabel = when {
                taxLine.label.contains("GST", ignoreCase = true) -> "GST:"
                taxLine.label.contains("PST", ignoreCase = true) -> "PST:"
                else -> "${taxLine.label} (${taxLine.ratePercent}%):"
            }
            sb.append(formatter.formatLeftRightText(taxLabel, taxLine.taxTotal, paperWidth))
        }
        
        // 如果没有税费明细但有总税费
        if (order.taxLines.isEmpty() && order.totalTax.isNotEmpty()) {
            sb.append(formatter.formatLeftRightText("tax:", order.totalTax, paperWidth))
        }
        
        // 显示外卖费用
        if (order.woofoodInfo?.deliveryFee != null && order.woofoodInfo.deliveryFee.isNotEmpty()) {
            sb.append(formatter.formatLeftRightText("delivery fee:", order.woofoodInfo.deliveryFee, paperWidth))
        }
        
        // 显示小费
        if (order.woofoodInfo?.tip != null && order.woofoodInfo.tip.isNotEmpty()) {
            sb.append(formatter.formatLeftRightText("tips:", order.woofoodInfo.tip, paperWidth))
        }
        
        // 显示折扣
        if (order.discountTotal.isNotEmpty() && order.discountTotal != "0.00") {
            sb.append(formatter.formatLeftRightText("discount:", "-${order.discountTotal}", paperWidth))
        }
        
        sb.append(formatter.formatLeftRightText("total:", order.total, paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成支付信息
     */
    private fun generatePaymentInfo(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                   paperWidth: Int, formatter: ThermalPrinterFormatter): String {
        if (!templateConfig.showPaymentInfo) return ""
        
        val sb = StringBuilder()
        sb.append(formatter.formatLeftRightText("payment method:", order.paymentMethod, paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成订单备注
     */
    private fun generateOrderNotes(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                                  paperWidth: Int, formatter: ThermalPrinterFormatter, 
                                  sectionTitle: String = "Order Notes:"): String {
        if (!templateConfig.showOrderNotes || order.notes.isEmpty()) return ""
        
        val sb = StringBuilder()
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLeftText(formatter.formatBold(sectionTitle), paperWidth))
        sb.append(formatter.formatMultilineText(order.notes, paperWidth))
        
        return sb.toString()
    }
    
    /**
     * 生成页脚
     */
    private fun generateFooter(order: Order, config: PrinterConfig, templateConfig: TemplateConfig,
                              paperWidth: Int, formatter: ThermalPrinterFormatter): String {
        val sb = StringBuilder()
        
        if (templateConfig.showFooter) {
            sb.append(formatter.formatFooter("Thank you for your order!", paperWidth))
        } else {
            sb.append(formatter.addEmptyLines(3))
        }
        
        return sb.toString()
    }
    
    // ==================== 其他方法 ====================
    
    override fun generateTestPrintContent(config: PrinterConfig): String {
        val paperWidth = config.paperWidth
        val formatter = ThermalPrinterFormatter
        val sb = StringBuilder()
        
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        
        // 标题
        sb.append(formatter.formatTitle(storeName, paperWidth))
        sb.append(formatter.formatTitle("printing test", paperWidth))
        sb.append(formatter.addEmptyLines(1))
        
        // 打印机信息
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLabelValue("printer name", config.name, paperWidth))
        sb.append(formatter.formatLabelValue("printer address", config.address, paperWidth))
        sb.append(formatter.formatLabelValue("printer brand", config.brand.displayName, paperWidth))
        
        // 根据不同的打印宽度显示不同的信息
        val paperWidthInfo = when (paperWidth) {
            PrinterConfig.PAPER_WIDTH_57MM -> "57mm"
            PrinterConfig.PAPER_WIDTH_80MM -> "80mm"
            else -> "${paperWidth}mm"
        }
        
        sb.append(formatter.formatLabelValue("paper width", paperWidthInfo, paperWidth))
        
        // 测试各种格式
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLeftText("left check", paperWidth))
        sb.append(formatter.formatCenteredText("middle check", paperWidth))
        sb.append(formatter.formatRightText("right check", paperWidth))
        
        // 测试商品格式
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatItemPriceLine("Ginger Beef", 2, "38.00", paperWidth))
        sb.append(formatter.formatItemPriceLine("Spicy Tofu", 1, "28.00", paperWidth))
        sb.append(formatter.formatLeftRightText("total:", "66.00", paperWidth))
        
        // 结尾
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatCenteredText("printing test finished", paperWidth))
        sb.append(formatter.addEmptyLines(1))
        
        return sb.toString()
    }
    
    // 辅助方法：格式化价格
    private fun formatPrice(price: Double): String {
        val currencySymbol = runBlocking { settingRepository.getCurrencySymbolFlow().first() }
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 