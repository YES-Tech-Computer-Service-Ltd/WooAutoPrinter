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
    
    override fun generateOrderPrintContent(order: Order, config: PrinterConfig, templateId: String?): String {
        Log.d(TAG, "生成订单打印内容: ${order.number}, 指定模板ID: $templateId")
        
        if (templateId.isNullOrEmpty()) {
            // 如果没有指定模板ID，使用默认逻辑
            return generateOrderPrintContent(order, config)
        }
        
        // 直接使用指定的模板ID
        val templateType: TemplateType = when {
            templateId == "full_details" -> TemplateType.FULL_DETAILS
            templateId == "delivery" -> TemplateType.DELIVERY
            templateId == "kitchen" -> TemplateType.KITCHEN
            templateId.startsWith("custom_") -> TemplateType.FULL_DETAILS
            else -> TemplateType.FULL_DETAILS
        }
        
        // 获取模板配置
        val templateConfig = runBlocking { 
            templateConfigRepository.getOrCreateConfig(templateId, templateType)
        }
        
        Log.d(TAG, "使用指定模板: $templateId, 模板类型: $templateType")
        
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
                // 名称按样式渲染
                val nameLine = ThermalPrinterFormatter.formatStyledLine(
                    text = storeName,
                    paperWidth = paperWidth,
                    bold = templateConfig.styleStoreNameBold,
                    large = templateConfig.styleStoreNameLarge,
                    alignment = 'C'
                )
                sb.append(nameLine)
                hasContent = true
            }
        }
        
        if (templateConfig.showStoreAddress) {
            val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
            if (storeAddress.isNotEmpty()) {
                val addr = ThermalPrinterFormatter.formatStyledLine(
                    text = storeAddress,
                    paperWidth = paperWidth,
                    bold = templateConfig.styleStoreAddressBold,
                    large = templateConfig.styleStoreAddressLarge,
                    alignment = 'C'
                )
                sb.append(addr)
                hasContent = true
            }
        }
        
        if (templateConfig.showStorePhone) {
            val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
            if (storePhone.isNotEmpty()) {
                val phone = ThermalPrinterFormatter.formatStyledLine(
                    text = "Tel: $storePhone",
                    paperWidth = paperWidth,
                    bold = templateConfig.styleStorePhoneBold,
                    large = templateConfig.styleStorePhoneLarge,
                    alignment = 'C'
                )
                sb.append(phone)
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
            val label = "Order #"
            val value = order.number
            val styled = ThermalPrinterFormatter.formatStyledLine(
                text = "$label: $value",
                paperWidth = paperWidth,
                bold = templateConfig.styleOrderNumberBold,
                large = templateConfig.styleOrderNumberLarge,
                alignment = 'L'
            )
            sb.append(styled)
        }
        
        if (templateConfig.showOrderDate) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(order.dateCreated)
            val styledDate = ThermalPrinterFormatter.formatStyledLine(
                text = "Date: $formattedDate",
                paperWidth = paperWidth,
                bold = templateConfig.styleOrderDateBold,
                large = templateConfig.styleOrderDateLarge,
                alignment = 'L'
            )
            sb.append(styledDate)
            
            if (showPrintTime) {
                val currentTime = ThermalPrinterFormatter.formatDateTime(Date())
                val styledTime = ThermalPrinterFormatter.formatStyledLine(
                    text = "Print Time: $currentTime",
                    paperWidth = paperWidth,
                    bold = templateConfig.styleOrderDateBold,
                    large = templateConfig.styleOrderDateLarge,
                    alignment = 'L'
                )
                sb.append(styledTime)
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
            val line = ThermalPrinterFormatter.formatStyledLine(
                text = "Customer: ${order.customerName}",
                paperWidth = paperWidth,
                bold = templateConfig.styleCustomerNameBold,
                large = templateConfig.styleCustomerNameLarge,
                alignment = 'L'
            )
            sb.append(line)
            hasContent = true
        }
        
        if (templateConfig.showCustomerPhone && !showMinimal && order.contactInfo.isNotEmpty()) {
            val line = ThermalPrinterFormatter.formatStyledLine(
                text = "Contact: ${order.contactInfo}",
                paperWidth = paperWidth,
                bold = templateConfig.styleCustomerPhoneBold,
                large = templateConfig.styleCustomerPhoneLarge,
                alignment = 'L'
            )
            sb.append(line)
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
        
        // 订单类型 (配送或取餐)
        val isDelivery = order.woofoodInfo?.isDelivery ?: false
        sb.append(ThermalPrinterFormatter.formatLabelValue("Order Type", 
            if (isDelivery) "Delivery" else "Takeaway", paperWidth))
        
        if (!showOnlyOrderType && isDelivery) {
            // 优先使用WooFood插件的配送地址，如果为空则使用billing地址作为备选
            val deliveryAddress = order.woofoodInfo?.deliveryAddress?.takeIf { it.isNotBlank() } 
                                 ?: order.billingInfo.takeIf { it.isNotBlank() }
            
            deliveryAddress?.let { address ->
                sb.append(ThermalPrinterFormatter.formatLabelValue("Delivery Address", address, paperWidth))
            }
            
            order.woofoodInfo?.deliveryFee?.let {
                sb.append(ThermalPrinterFormatter.formatLabelValue("Delivery Fee", it, paperWidth))
            }
            
            order.woofoodInfo?.tip?.let {
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
            val left = if (templateConfig.styleItemNameBold) ThermalPrinterFormatter.formatBold("Item") else "Item"
            val rightBase = "Qty x Price"
            val right = if (templateConfig.styleQtyPriceBold) ThermalPrinterFormatter.formatBold(rightBase) else rightBase
            sb.append(ThermalPrinterFormatter.formatLeftRightText(left, right, paperWidth))
        }
        
        // 商品列表
        order.items.forEach { item ->
            val name = item.name
            
            if (!kitchenStyle && templateConfig.showItemPrices) {
                val price = item.price
                // 价格行右侧使用是否加粗/放大
                val qtyPrice = "${item.quantity} x $price"
                val leftName = if (templateConfig.styleItemNameLarge || templateConfig.styleItemNameBold) {
                    // 使用中文大字体方案 + 可选加粗
                    val enlarged = ThermalPrinterFormatter.formatChineseLargeFont(name, paperWidth)
                    if (templateConfig.styleItemNameBold) "<b>$enlarged</b>" else enlarged
                } else name
                val rightStyled = buildString {
                    if (templateConfig.styleQtyPriceLarge) append("<h><w>")
                    if (templateConfig.styleQtyPriceBold) append("<b>")
                    append(qtyPrice)
                    if (templateConfig.styleQtyPriceBold) append("</b>")
                    if (templateConfig.styleQtyPriceLarge) append("</w></h>")
                }
                sb.append(ThermalPrinterFormatter.formatLeftRightText(leftName, rightStyled, paperWidth))
            } else {
                // 厨房模式或不显示价格时，整行按样式输出
                val line = ThermalPrinterFormatter.formatStyledLine(
                    text = "${item.quantity} x $name",
                    paperWidth = paperWidth,
                    bold = templateConfig.styleItemNameBold,
                    large = templateConfig.styleItemNameLarge,
                    alignment = 'L'
                )
                sb.append(line)
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
        val left = if (templateConfig.stylePaymentInfoBold) ThermalPrinterFormatter.formatBold("payment method:") else "payment method:"
        val right = if (templateConfig.stylePaymentInfoLarge || templateConfig.stylePaymentInfoBold) {
            buildString {
                if (templateConfig.stylePaymentInfoLarge) append("<h><w>")
                if (templateConfig.stylePaymentInfoBold) append("<b>")
                append(order.paymentMethod)
                if (templateConfig.stylePaymentInfoBold) append("</b>")
                if (templateConfig.stylePaymentInfoLarge) append("</w></h>")
            }
        } else order.paymentMethod
        sb.append(ThermalPrinterFormatter.formatLeftRightText(left, right, paperWidth))
        
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
     * 创建中文测试订单 - 使用中文字符测试GB18030编码
     */
    override fun createChineseTestOrder(config: PrinterConfig): Order {
        val paperWidth = config.paperWidth
        
        return when (paperWidth) {
            PrinterConfig.PAPER_WIDTH_57MM -> create58mmChineseTestOrder()
            PrinterConfig.PAPER_WIDTH_80MM -> create80mmChineseTestOrder()
            else -> createDefaultChineseTestOrder()
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
            customerName = "Test Customer",
            contactInfo = "555-TEST-003",
            billingInfo = "Test Billing Address",
            paymentMethod = "Credit Card",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 301,
                    name = "Test Item A",
                    quantity = 2,
                    price = "$25.00",
                    subtotal = "$50.00",
                    total = "$50.00",
                    image = "",
                    options = listOf()
                ),
                OrderItem(
                    id = 2,
                    productId = 302,
                    name = "Test Item B",
                    quantity = 1,
                    price = "$15.00",
                    subtotal = "$15.00",
                    total = "$15.00",
                    image = "",
                    options = listOf()
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "Default printer test order for compatibility",
            woofoodInfo = WooFoodInfo(
                orderMethod = "Takeaway",
                deliveryTime = null,
                deliveryAddress = null,
                deliveryFee = null,
                tip = null,
                isDelivery = false
            ),
            subtotal = "$60.00",
            totalTax = "$5.00",
            discountTotal = "0.00",
            taxLines = listOf(
                TaxLine(1, "Tax", 8.3, "$5.00")
            )
        )
    }
    
    /**
     * 创建58mm中文测试订单
     */
    private fun create58mmChineseTestOrder(): Order {
        val currentTime = Date()
        return Order(
            id = 888001L,
            number = "中文测试-58MM-001",
            status = "processing",
            dateCreated = currentTime,
            total = "￥125.50",
            customerName = "张三",
            contactInfo = "138-8888-8888",
            billingInfo = "北京市朝阳区建国路1号",
            paymentMethod = "微信支付",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 101,
                    name = "宫保鸡丁",
                    quantity = 1,
                    price = "￥38.00",
                    subtotal = "￥38.00",
                    total = "￥38.00",
                    image = "",
                    options = listOf(
                        ItemOption("辣度", "中辣"),
                        ItemOption("米饭", "香米")
                    )
                ),
                OrderItem(
                    id = 2,
                    productId = 102,
                    name = "麻婆豆腐",
                    quantity = 1,
                    price = "￥28.00",
                    subtotal = "￥28.00",
                    total = "￥28.00",
                    image = "",
                    options = listOf(
                        ItemOption("辣度", "微辣")
                    )
                ),
                OrderItem(
                    id = 3,
                    productId = 103,
                    name = "酸辣汤",
                    quantity = 2,
                    price = "￥18.00",
                    subtotal = "￥36.00",
                    total = "￥36.00",
                    image = "",
                    options = emptyList()
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "中文测试订单 - 请确保中文字符正确显示",
            woofoodInfo = WooFoodInfo(
                orderMethod = "外卖",
                deliveryTime = "60分钟内",
                deliveryAddress = "北京市朝阳区建国路1号",
                deliveryFee = "￥8.00",
                tip = "￥5.00",
                isDelivery = true
            ),
            subtotal = "￥102.00",
            totalTax = "￥10.50",
            discountTotal = "0.00",
            taxLines = listOf(
                TaxLine(1, "增值税", 10.3, "￥10.50")
            )
        )
    }
    
    /**
     * 创建80mm中文测试订单
     */
    private fun create80mmChineseTestOrder(): Order {
        val currentTime = Date()
        return Order(
            id = 888002L,
            number = "中文测试-80MM-002",
            status = "processing",
            dateCreated = currentTime,
            total = "￥198.88",
            customerName = "李明华",
            contactInfo = "186-1234-5678",
            billingInfo = "上海市浦东新区陆家嘴金融贸易区世纪大道100号",
            paymentMethod = "支付宝",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 201,
                    name = "北京烤鸭套餐",
                    quantity = 1,
                    price = "￥89.00",
                    subtotal = "￥89.00",
                    total = "￥89.00",
                    image = "",
                    options = listOf(
                        ItemOption("配菜", "黄瓜丝，葱丝"),
                        ItemOption("饼皮", "薄饼"),
                        ItemOption("酱料", "甜面酱")
                    )
                ),
                OrderItem(
                    id = 2,
                    productId = 202,
                    name = "四川麻辣火锅",
                    quantity = 1,
                    price = "￥68.00",
                    subtotal = "￥68.00",
                    total = "￥68.00",
                    image = "",
                    options = listOf(
                        ItemOption("辣度", "特辣"),
                        ItemOption("锅底", "牛油锅底"),
                        ItemOption("配菜", "毛肚，鸭血，豆腐")
                    )
                ),
                OrderItem(
                    id = 3,
                    productId = 203,
                    name = "广式点心拼盘",
                    quantity = 1,
                    price = "￥45.00",
                    subtotal = "￥45.00",
                    total = "￥45.00",
                    image = "",
                    options = listOf(
                        ItemOption("种类", "虾饺，烧卖，叉烧包")
                    )
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "中文测试订单 - 包含各种中文字符：简体，繁體，符号￥€$，数字１２３４５６７８９０",
            woofoodInfo = WooFoodInfo(
                orderMethod = "外卖配送",
                deliveryTime = "45分钟内送达",
                deliveryAddress = "上海市浦东新区陆家嘴金融贸易区世纪大道100号2楼201室",
                deliveryFee = "￥12.00",
                tip = "￥8.88",
                isDelivery = true
            ),
            subtotal = "￥202.00",
            totalTax = "￥16.88",
            discountTotal = "￥20.00",
            taxLines = listOf(
                TaxLine(1, "增值税", 8.36, "￥16.88")
            )
        )
    }
    
    /**
     * 创建默认中文测试订单
     */
    private fun createDefaultChineseTestOrder(): Order {
        val currentTime = Date()
        return Order(
            id = 888003L,
            number = "中文测试-DEFAULT-003",
            status = "processing",
            dateCreated = currentTime,
            total = "￥88.88",
            customerName = "王小明",
            contactInfo = "138-0000-1111",
            billingInfo = "深圳市南山区科技园南区",
            paymentMethod = "现金支付",
            items = listOf(
                OrderItem(
                    id = 1,
                    productId = 301,
                    name = "中文测试菜品A",
                    quantity = 1,
                    price = "￥39.00",
                    subtotal = "￥39.00",
                    total = "￥39.00",
                    image = "",
                    options = listOf(
                        ItemOption("测试选项", "中文值")
                    )
                ),
                OrderItem(
                    id = 2,
                    productId = 302,
                    name = "中文测试菜品B",
                    quantity = 2,
                    price = "￥19.88",
                    subtotal = "￥39.76",
                    total = "￥39.76",
                    image = "",
                    options = emptyList()
                )
            ),
            isPrinted = false,
            notificationShown = false,
            notes = "默认中文测试订单 - 测试GB18030编码支持",
            woofoodInfo = WooFoodInfo(
                orderMethod = "自取",
                deliveryTime = null,
                deliveryAddress = null,
                deliveryFee = null,
                tip = null,
                isDelivery = false
            ),
            subtotal = "￥78.76",
            totalTax = "￥10.12",
            discountTotal = "0.00",
            taxLines = listOf(
                TaxLine(1, "税费", 12.8, "￥10.12")
            )
        )
    }
    
    // 辅助方法：格式化价格
    private fun formatPrice(price: Double): String {
        val currencySymbol = runBlocking { settingRepository.getCurrencySymbolFlow().first() }
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 