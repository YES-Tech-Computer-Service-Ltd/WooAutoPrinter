package com.example.wooauto.data.templates

import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.domain.models.ItemOption
import com.example.wooauto.domain.models.TaxLine
import com.example.wooauto.domain.models.WooFoodInfo
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
                sb.append(ThermalPrinterFormatter.formatStoreName(storeName, paperWidth))
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
                sb.append(ThermalPrinterFormatter.formatStoreName(storeName, paperWidth))
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
            sb.append(ThermalPrinterFormatter.formatStoreName(storeName, paperWidth))
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
                // 使用专门的店铺名称格式化方法（大号字体，加粗）
                sb.append(ThermalPrinterFormatter.formatStoreName(storeName, paperWidth))
                hasContent = true
            }
        }
        
        if (templateConfig.showStoreAddress) {
            val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
            if (storeAddress.isNotEmpty()) {
                // 使用专门的店铺地址格式化方法
                sb.append(ThermalPrinterFormatter.formatStoreAddress(storeAddress, paperWidth))
                hasContent = true
            }
        }
        
        if (templateConfig.showStorePhone) {
            val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
            if (storePhone.isNotEmpty()) {
                // 使用专门的店铺电话格式化方法
                sb.append(ThermalPrinterFormatter.formatStorePhone(storePhone, paperWidth))
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
        if (!templateConfig.showDeliveryInfo) return ""
        
        val sb = StringBuilder()
        
        if (!showOnlyOrderType) {
            sb.append(ThermalPrinterFormatter.formatDivider(paperWidth))
            sb.append(ThermalPrinterFormatter.formatLeftText(ThermalPrinterFormatter.formatBold("Delivery Info"), paperWidth))
        }
        
        // 判断订单类型和配送地址
        val isDelivery = order.woofoodInfo?.isDelivery ?: false
        val deliveryAddress = order.getEffectiveShippingAddress()
        
        // 订单类型 (配送或取餐)
        sb.append(ThermalPrinterFormatter.formatLabelValue("Order Type", 
            if (isDelivery) "Delivery" else "Takeaway", paperWidth))
        
        // 显示配送地址（对于配送订单，或者当有不同于账单地址的配送地址时）
        if (!showOnlyOrderType) {
            if (isDelivery || (deliveryAddress.isNotBlank() && deliveryAddress != order.billingInfo)) {
                sb.append(ThermalPrinterFormatter.formatLabelValue("Delivery Address", deliveryAddress, paperWidth))
            }
            
            // 显示WooFood相关费用（如果有）
            order.woofoodInfo?.deliveryFee?.let {
                if (it.isNotBlank() && it != "0.00") {
                    sb.append(ThermalPrinterFormatter.formatLabelValue("Delivery Fee", it, paperWidth))
                }
            }
            
            order.woofoodInfo?.tip?.let {
                if (it.isNotBlank() && it != "0.00") {
                    sb.append(ThermalPrinterFormatter.formatLabelValue("Tip", it, paperWidth))
                }
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
                val price = item.price
                sb.append(ThermalPrinterFormatter.formatItemPriceLine(name, item.quantity, price, paperWidth))
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
            PrinterConfig.PAPER_WIDTH_57MM -> create57mmTestOrder()
            PrinterConfig.PAPER_WIDTH_80MM -> create80mmTestOrder()
            else -> createDefaultTestOrder()
        }
    }
    
    /**
     * 创建57mm测试订单
     */
    private fun create57mmTestOrder(): Order {
        val currentTime = Date()
        return Order(
            id = 999001L,
            number = "TEST-57MM-001",
            status = "processing",
            dateCreated = currentTime,
            total = "$42.50",
            customerName = "Jane Doe",
            contactInfo = "555-***-9999",
            billingInfo = "456 Home Street, Apartment 2B, Residential Area, CA 90210",
            shippingAddress = "456 Home Street, Apartment 2B, Residential Area, CA 90210",
            paymentMethod = "Cash",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 101,
                    name = "Coffee Latte",
                    quantity = 2,
                    price = "5.50",
                    subtotal = "11.00",
                    total = "11.00",
                    image = ""
                ),
                OrderItem(
                    id = 2,
                    productId = 102,
                    name = "Croissant",
                    quantity = 1,
                    price = "3.25",
                    subtotal = "3.25",
                    total = "3.25",
                    image = ""
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "No sugar, extra foam",
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
            shippingAddress = "789 Delivery Street, Suite 456, Delivery Zone, ST 54321",
            paymentMethod = "Credit Card",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 201,
                    name = "Premium Business Lunch Set",
                    quantity = 2,
                    price = "28.99",
                    subtotal = "57.98",
                    total = "57.98",
                    image = "",
                    options = listOf(
                        OrderItemOption("Main Course", "Grilled Salmon"),
                        OrderItemOption("Side Dish", "Roasted Vegetables"),
                        OrderItemOption("Drink", "Fresh Orange Juice")
                    )
                ),
                OrderItem(
                    id = 2,
                    productId = 202,
                    name = "Gourmet Coffee Selection",
                    quantity = 3,
                    price = "12.50",
                    subtotal = "37.50",
                    total = "37.50",
                    image = "",
                    options = listOf(
                        OrderItemOption("Type", "Ethiopian Single Origin"),
                        OrderItemOption("Size", "Large"),
                        OrderItemOption("Preparation", "French Press")
                    )
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "Please prepare everything fresh. Customer has dietary restrictions - no nuts or shellfish.",
            woofoodInfo = WooFoodInfo(
                orderMethod = "Delivery",
                deliveryTime = "12:30 PM",
                deliveryAddress = "789 Delivery Street, Suite 456, Delivery Zone, ST 54321",
                deliveryFee = "$5.99",
                tip = "$15.00",
                isDelivery = true
            ),
            subtotal = "$95.48",
            totalTax = "$9.55",
            discountTotal = "5.00",
            feeLines = listOf(
                FeeLine(1, "Delivery Fee", "$5.99", "$0.60"),
                FeeLine(2, "Service Charge", "$2.00", "$0.20"),
                FeeLine(3, "Tip", "$15.00", "$0.00")
            ),
            taxLines = listOf(
                TaxLine(1, "GST", 10.0, "$9.55")
            )
        )
    }
    
    /**
     * 创建默认测试订单
     */
    private fun createDefaultTestOrder(): Order {
        val currentTime = Date()
        return Order(
            id = 999000L,
            number = "TEST-DEFAULT-000",
            status = "processing",
            dateCreated = currentTime,
            total = "$25.99",
            customerName = "Test User",
            contactInfo = "555-***-0000",
            billingInfo = "Test Billing Address, Test City, TS 12345",
            shippingAddress = "Test Shipping Address, Test City, TS 12345",
            paymentMethod = "Test Payment",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 100,
                    name = "Test Product",
                    quantity = 1,
                    price = "$25.99",
                    subtotal = "$25.99",
                    total = "$25.99",
                    image = ""
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "This is a test order for printer functionality verification.",
            woofoodInfo = WooFoodInfo(
                orderMethod = "Test",
                deliveryTime = null,
                deliveryAddress = "Test Shipping Address, Test City, TS 12345",
                deliveryFee = null,
                tip = null,
                isDelivery = false
            ),
            subtotal = "$25.99",
            totalTax = "$0.00",
            discountTotal = "0.00"
        )
    }
    
    // 辅助方法：格式化价格
    private fun formatPrice(price: Double): String {
        val currencySymbol = runBlocking { settingRepository.getCurrencySymbolFlow().first() }
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 