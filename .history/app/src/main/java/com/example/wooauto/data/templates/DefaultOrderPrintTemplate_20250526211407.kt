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
        // 不再直接生成测试内容，而是创建测试订单对象
        // 这个方法保留是为了接口兼容性，但实际测试打印会使用createTestOrder
        return "[L]Test print will use order printing logic"
    }
    
    /**
     * 创建测试用的订单对象，包含完整的测试数据
     */
    override fun createTestOrder(config: PrinterConfig): Order {
        val paperWidth = config.paperWidth
        
        // 根据纸张宽度创建不同的测试数据
        return when (paperWidth) {
            PrinterConfig.PAPER_WIDTH_57MM -> create58mmTestOrder()
            PrinterConfig.PAPER_WIDTH_80MM -> create80mmTestOrder()
            else -> createDefaultTestOrder()
        }
    }
    

    
    /**
     * 创建58mm测试订单
     */
    private fun create58mmTestOrder(): Order {
        val currentTime = Date()
        return Order(
            id = 999001L,
            number = "TEST-58MM-001",
            status = "processing",
            dateCreated = currentTime,
            total = "$82.50",
            customerName = "Test Customer",
            contactInfo = "555-TEST-001",
            billingInfo = "Test Billing Address",
            paymentMethod = "Credit Card",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 101,
                    name = "Chicken Curry",
                    quantity = 2,
                    price = "$19.00",
                    subtotal = "$38.00",
                    total = "$38.00",
                    image = "",
                    options = listOf(
                        ItemOption("Spice Level", "Medium"),
                        ItemOption("Rice", "Jasmine")
                    )
                ),
                OrderItem(
                    id = 2,
                    productId = 102,
                    name = "Beef Stew",
                    quantity = 1,
                    price = "$28.00",
                    subtotal = "$28.00",
                    total = "$28.00",
                    image = "",
                    options = listOf()
                ),
                OrderItem(
                    id = 3,
                    productId = 103,
                    name = "Rice Bowl",
                    quantity = 3,
                    price = "$3.00",
                    subtotal = "$9.00",
                    total = "$9.00",
                    image = "",
                    options = listOf()
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "58MM printer test order - all functions normal",
            woofoodInfo = WooFoodInfo(
                orderMethod = "Takeaway",
                deliveryTime = null,
                deliveryAddress = null,
                deliveryFee = null,
                tip = null,
                isDelivery = false
            ),
            subtotal = "$75.00",
            totalTax = "$7.50",
            discountTotal = "0.00",
            taxLines = listOf(
                TaxLine(1, "GST", 5.0, "$7.50")
            )
        )
    }
    
    /**
     * 创建80mm测试订单
     */
    private fun create80mmTestOrder(): Order {
        val currentTime = Date()
        return Order(
            id = 999002L,
            number = "TEST-80MM-002",
            status = "processing",
            dateCreated = currentTime,
            total = "$177.48",
            customerName = "John Smith",
            contactInfo = "555-***-8888",
            billingInfo = "123 Business Avenue, Unit 100, Business District, ST 12345",
            paymentMethod = "Credit Card",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 201,
                    name = "Braised Pork Set",
                    quantity = 2,
                    price = "$45.00",
                    subtotal = "$90.00",
                    total = "$90.00",
                    image = "",
                    options = listOf(
                        ItemOption("Side dishes", "Rice, Vegetables, Soup"),
                        ItemOption("Spice Level", "Mild")
                    )
                ),
                OrderItem(
                    id = 2,
                    productId = 202,
                    name = "Kung Pao Chicken",
                    quantity = 1,
                    price = "$32.00",
                    subtotal = "$32.00",
                    total = "$32.00",
                    image = "",
                    options = listOf(
                        ItemOption("Spice level", "Medium"),
                        ItemOption("Oil", "Less oil")
                    )
                ),
                OrderItem(
                    id = 3,
                    productId = 203,
                    name = "Hot & Sour Soup",
                    quantity = 2,
                    price = "$12.00",
                    subtotal = "$24.00",
                    total = "$24.00",
                    image = "",
                    options = listOf()
                ),
                OrderItem(
                    id = 4,
                    productId = 204,
                    name = "Cola (Large)",
                    quantity = 3,
                    price = "$8.00",
                    subtotal = "$24.00",
                    total = "$24.00",
                    image = "",
                    options = listOf(
                        ItemOption("Temperature", "Cold"),
                        ItemOption("Ice", "Normal")
                    )
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "80MM thermal printer comprehensive test order.\nAll printing functions including alignment, formatting, and cutting are being tested.\nIf this prints correctly, your printer is working properly.",
            woofoodInfo = WooFoodInfo(
                orderMethod = "Delivery",
                deliveryTime = "45-60 minutes",
                deliveryAddress = "123 Business Avenue, Unit 100, Business District, ST 12345",
                deliveryFee = "$5.00",
                tip = "$15.00",
                isDelivery = true
            ),
            subtotal = "$146.00",
            totalTax = "$18.48",
            discountTotal = "10.00",
            taxLines = listOf(
                TaxLine(1, "GST", 5.0, "$7.70"),
                TaxLine(2, "PST", 7.0, "$10.78")
            )
        )
    }
    
    /**
     * 创建默认测试订单
     */
    private fun createDefaultTestOrder(): Order {
        val currentTime = Date()
        return Order(
            id = 999003L,
            number = "TEST-DEFAULT-003",
            status = "processing",
            dateCreated = currentTime,
            total = "$65.00",
            subtotal = "$60.00",
            totalTax = "$5.00",
            discountTotal = "0.00",
            paymentMethod = "Credit Card",
            customerName = "Test Customer",
            contactInfo = "555-TEST-003",
            notes = "Default printer test order for compatibility",
            isPrinted = false,
            items = listOf(
                Order.OrderItem(
                    id = 1,
                    name = "Test Item A",
                    quantity = 2,
                    price = 25.00,
                    options = listOf()
                ),
                Order.OrderItem(
                    id = 2,
                    name = "Test Item B",
                    quantity = 1,
                    price = 15.00,
                    options = listOf()
                )
            ),
            taxLines = listOf(
                Order.TaxLine("Tax", "8.3", "$5.00")
            ),
            woofoodInfo = Order.WoofoodInfo(
                isDelivery = false,
                deliveryAddress = null,
                deliveryFee = null,
                tip = null
            )
        )
    }
    
    // 辅助方法：格式化价格
    private fun formatPrice(price: Double): String {
        val currencySymbol = runBlocking { settingRepository.getCurrencySymbolFlow().first() }
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 