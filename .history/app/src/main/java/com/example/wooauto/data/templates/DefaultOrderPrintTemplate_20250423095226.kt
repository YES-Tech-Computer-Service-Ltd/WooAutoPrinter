package com.example.wooauto.data.templates

import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
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
    private val settingRepository: DomainSettingRepository
) : OrderPrintTemplate {

    private val TAG = "OrderPrintTemplate"
    
    override fun generateOrderPrintContent(order: Order, config: PrinterConfig): String {
        Log.d(TAG, "生成订单打印内容: ${order.number}")
        
        // 获取当前的模板类型
        val templateType = runBlocking { 
            settingRepository.getDefaultTemplateType() ?: TemplateType.FULL_DETAILS 
        }
        
        // 根据不同模板类型生成内容
        return when (templateType) {
            TemplateType.FULL_DETAILS -> generateFullDetailsTemplate(order, config)
            TemplateType.DELIVERY -> generateDeliveryTemplate(order, config)
            TemplateType.KITCHEN -> generateKitchenTemplate(order, config)
        }
    }
    
    /**
     * 生成完整订单详情模板 - 包含所有信息
     */
    private fun generateFullDetailsTemplate(order: Order, config: PrinterConfig): String {
        // 获取商店信息
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
        val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
        
        val paperWidth = config.paperWidth
        val formatter = ThermalPrinterFormatter
        val sb = StringBuilder()
        
        // 标题 (总是打印)
        sb.append(formatter.formatTitle(storeName, paperWidth))
        sb.append(formatter.addEmptyLines(1))
        
        // 店铺信息
        if (config.printStoreInfo) {
            if (storeAddress.isNotEmpty()) {
                sb.append(formatter.formatCenteredText(storeAddress, paperWidth))
            }
            if (storePhone.isNotEmpty()) {
                sb.append(formatter.formatCenteredText("Tel: $storePhone", paperWidth))
            }
        }
        sb.append(formatter.formatDivider(paperWidth))
        
        // 订单信息
        sb.append(formatter.formatLabelValue("Order #", order.number, paperWidth))
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(order.dateCreated)
        sb.append(formatter.formatLabelValue("Date", formattedDate, paperWidth))
        
        val currentTime = formatter.formatDateTime(Date())
        sb.append(formatter.formatLabelValue("Print Time", currentTime, paperWidth))
        
        // 客户信息
        if (config.printCustomerInfo && (order.customerName.isNotEmpty() || order.contactInfo.isNotEmpty() || order.billingInfo.isNotEmpty())) {
            sb.append(formatter.formatDivider(paperWidth))
            sb.append(formatter.formatLeftText(formatter.formatBold("Customer Information"), paperWidth))
            
            if (order.customerName.isNotEmpty()) {
                sb.append(formatter.formatLabelValue("Name", order.customerName, paperWidth))
            }
            
            if (order.contactInfo.isNotEmpty()) {
                sb.append(formatter.formatLabelValue("Contact", order.contactInfo, paperWidth))
            }
            
            // 地址信息
            if (order.billingInfo.isNotEmpty()) {
                sb.append(formatter.formatLabelValue("Address", order.billingInfo, paperWidth))
            }
        }
        
        // 配送信息
        if (order.woofoodInfo != null) {
            sb.append(formatter.formatDivider(paperWidth))
            sb.append(formatter.formatLeftText(formatter.formatBold("Delivery Info"), paperWidth))
            
            // 订单类型 (配送或取餐)
            sb.append(formatter.formatLabelValue("Order Type", 
                if (order.woofoodInfo.isDelivery) "Delivery" else "Takeaway", paperWidth))
            
            // 如果是配送订单，添加配送地址
            if (order.woofoodInfo.isDelivery) {
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
        }
        
        // 订单项目
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLeftText(formatter.formatBold("Order Items"), paperWidth))
        sb.append(formatter.formatDivider(paperWidth))
        
        // 表头
        sb.append(formatter.formatLeftRightText(
            formatter.formatBold("Item"), 
            formatter.formatBold("Qty x Price"), 
            paperWidth
        ))
        
        // 商品列表
        order.items.forEach { item ->
            val name = item.name
            val price = formatPrice(item.price.toDouble())
            
            // 根据打印机宽度自动调整格式
            sb.append(formatter.formatItemPriceLine(name, item.quantity.toInt(), price, paperWidth))
            
            // 如果配置为打印商品详情，则打印商品选项
            if (config.printItemDetails && item.options.isNotEmpty()) {
                item.options.forEach { option ->
                    sb.append(formatter.formatIndentedText("- ${option.name}: ${option.value}", 1, paperWidth))
                }
            }
        }
        
        sb.append(formatter.formatDivider(paperWidth))
        
        // 支付信息
        // 先显示小计
        if (order.subtotal.isNotEmpty()) {
            sb.append(formatter.formatLeftRightText("subtotal:", order.subtotal, paperWidth))
        }
        
        // 显示税费明细
        order.taxLines.forEach { taxLine ->
            // 根据税费类型显示GST或PST
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
        sb.append(formatter.formatLeftRightText("payment method:", order.paymentMethod, paperWidth))
        
        // 订单备注
        if (config.printOrderNotes && order.notes.isNotEmpty()) {
            sb.append(formatter.formatDivider(paperWidth))
            sb.append(formatter.formatLeftText(formatter.formatBold("Order Notes:"), paperWidth))
            sb.append(formatter.formatMultilineText(order.notes, paperWidth))
        }
        
        // 页脚
        if (config.printFooter) {
            sb.append(formatter.formatFooter("Thank you for your order!", paperWidth))
        } else {
            sb.append(formatter.addEmptyLines(3))
        }
        
        return sb.toString()
    }
    
    /**
     * 生成配送订单模板 - 重点突出配送信息和菜品信息
     */
    private fun generateDeliveryTemplate(order: Order, config: PrinterConfig): String {
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        val paperWidth = config.paperWidth
        val formatter = ThermalPrinterFormatter
        val sb = StringBuilder()
        
        // 标题
        sb.append(formatter.formatTitle(storeName, paperWidth))
        sb.append(formatter.formatTitle("DELIVERY RECEIPT", paperWidth))
        sb.append(formatter.addEmptyLines(1))
        
        // 订单基本信息
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLabelValue("Order #", order.number, paperWidth))
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(order.dateCreated)
        sb.append(formatter.formatLabelValue("Date", formattedDate, paperWidth))
        
        // 客户和配送信息 (配送单特别突出这部分)
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLeftText(formatter.formatBold("DELIVERY INFORMATION"), paperWidth))
        
        // 客户名称
        if (order.customerName.isNotEmpty()) {
            sb.append(formatter.formatLabelValue("Customer", order.customerName, paperWidth))
        }
        
        // 联系方式 (配送单必须包含)
        if (order.contactInfo.isNotEmpty()) {
            sb.append(formatter.formatLabelValue("Contact", order.contactInfo, paperWidth))
        }
        
        // 配送地址 (配送单重点突出)
        if (order.woofoodInfo != null && order.woofoodInfo.isDelivery) {
            order.woofoodInfo.deliveryAddress?.let {
                sb.append(formatter.formatLabelValue("Address", it, paperWidth))
            }
        } else if (order.billingInfo.isNotEmpty()) {
            sb.append(formatter.formatLabelValue("Address", order.billingInfo, paperWidth))
        }
        
        // 订单类型
        if (order.woofoodInfo != null) {
            sb.append(formatter.formatLabelValue(
                "Order Type", 
                if (order.woofoodInfo.isDelivery) "Delivery" else "Takeaway", 
                paperWidth
            ))
        }
        
        // 订单项目
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLeftText(formatter.formatBold("ORDER ITEMS"), paperWidth))
        sb.append(formatter.formatDivider(paperWidth))
        
        // 商品列表
        order.items.forEach { item ->
            sb.append(formatter.formatLeftText(
                formatter.formatBold("${item.name} x ${item.quantity}"), 
                paperWidth
            ))
            
            // 配送单应该包含商品选项信息
            if (item.options.isNotEmpty()) {
                item.options.forEach { option ->
                    sb.append(formatter.formatIndentedText("- ${option.name}: ${option.value}", 1, paperWidth))
                }
            }
        }
        
        sb.append(formatter.formatDivider(paperWidth))
        
        // 支付信息
        // 先显示小计
        if (order.subtotal.isNotEmpty()) {
            sb.append(formatter.formatLeftRightText("subtotal:", order.subtotal, paperWidth))
        }
        
        // 显示税费明细
        order.taxLines.forEach { taxLine ->
            // 根据税费类型显示GST或PST
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
        
        sb.append(formatter.formatLeftRightText("Total:", order.total, paperWidth))
        sb.append(formatter.formatLeftRightText("paymentMethod:", order.paymentMethod, paperWidth))
        
        // 订单备注 (配送说明很重要)
        if (order.notes.isNotEmpty()) {
            sb.append(formatter.formatDivider(paperWidth))
            sb.append(formatter.formatLeftText(formatter.formatBold("DELIVERY NOTES:"), paperWidth))
            sb.append(formatter.formatMultilineText(order.notes, paperWidth))
        }
        
        // 添加空行便于撕纸
        sb.append(formatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    /**
     * 生成厨房订单模板 - 仅包含菜品信息和下单时间
     */
    private fun generateKitchenTemplate(order: Order, config: PrinterConfig): String {
        val paperWidth = config.paperWidth
        val formatter = ThermalPrinterFormatter
        val sb = StringBuilder()
        
        // 标题 - 厨房模式下更加简洁
        sb.append(formatter.formatTitle("KITCHEN ORDER", paperWidth))
        sb.append(formatter.formatDivider(paperWidth))
        
        // 订单基本信息 - 仅保留必要信息
        sb.append(formatter.formatLabelValue("Order #", order.number, paperWidth))
        
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        sb.append(formatter.formatLabelValue(
            "Time", 
            timeFormat.format(order.dateCreated), 
            paperWidth
        ))
        
        // 订单类型
        if (order.woofoodInfo != null) {
            sb.append(formatter.formatLabelValue(
                "Type", 
                if (order.woofoodInfo.isDelivery) "Delivery" else "Takeaway", 
                paperWidth
            ))
        }
        
        // 商品列表 - 厨房模板重点突出这部分
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatCenteredText(formatter.formatBold("ITEMS TO PREPARE"), paperWidth))
        sb.append(formatter.formatDivider(paperWidth))
        
        // 商品列表 - 更大字体、更清晰布局
        order.items.forEach { item ->
            sb.append(formatter.formatLeftText(
                formatter.formatBold("${item.quantity} x ${item.name}"), 
                paperWidth
            ))
            
            // 厨房单必须包含商品选项信息
            if (item.options.isNotEmpty()) {
                item.options.forEach { option ->
                    sb.append(formatter.formatIndentedText("${option.name}: ${option.value}", 1, paperWidth))
                }
            }
            sb.append(formatter.addEmptyLines(1)) // 每个商品之间添加额外的空行，便于厨房识别
        }
        
        // 订单备注 - 厨房需要特别注意的事项
        if (order.notes.isNotEmpty()) {
            sb.append(formatter.formatDivider(paperWidth))
            sb.append(formatter.formatLeftText(formatter.formatBold("SPECIAL INSTRUCTIONS:"), paperWidth))
            sb.append(formatter.formatMultilineText(order.notes, paperWidth))
        }
        
        // 打印时间
        sb.append(formatter.formatDivider(paperWidth))
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sb.append(formatter.formatRightText(
            "Printed: ${dateFormat.format(Date())}", 
            paperWidth
        ))
        
        // 添加额外的空行，方便撕纸
        sb.append(formatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    override fun generateTestPrintContent(config: PrinterConfig): String {
        val paperWidth = config.paperWidth
        val formatter = ThermalPrinterFormatter
        val sb = StringBuilder()
        
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        
        // 标题
        sb.append(formatter.formatTitle(storeName, paperWidth))
        sb.append(formatter.formatTitle("PRINTER DIAGNOSTIC TEST", paperWidth))
        sb.append(formatter.addEmptyLines(1))
        
        // 打印机信息
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLabelValue("Printer Name", config.name, paperWidth))
        sb.append(formatter.formatLabelValue("Printer Address", config.address, paperWidth))
        sb.append(formatter.formatLabelValue("Printer Brand", config.brand.displayName, paperWidth))
        
        // 根据不同的打印宽度显示不同的信息
        val paperWidthInfo = when (paperWidth) {
            PrinterConfig.PAPER_WIDTH_57MM -> "57mm"
            PrinterConfig.PAPER_WIDTH_80MM -> "80mm"
            else -> "${paperWidth}mm"
        }
        
        sb.append(formatter.formatLabelValue("Paper Width", paperWidthInfo, paperWidth))
        sb.append(formatter.formatLabelValue("Auto Cut", if(config.autoCut) "Enabled" else "Disabled", paperWidth))
        
        // 打印过程诊断信息
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatCenteredText(formatter.formatBold("PRINTING WORKFLOW"), paperWidth))
        sb.append(formatter.formatDivider(paperWidth))
        
        // 从连接到切纸的每个步骤
        sb.append(formatter.formatLeftText("1. PRINTER CONNECTION", paperWidth))
        sb.append(formatter.formatIndentedText("- Initialize printer (ESC @)", 1, paperWidth))
        sb.append(formatter.formatIndentedText("- Clear buffer (CAN)", 1, paperWidth))
        
        sb.append(formatter.formatLeftText("2. BEFORE PRINTING", paperWidth))
        sb.append(formatter.formatIndentedText("- Clear any pending commands", 1, paperWidth))
        sb.append(formatter.formatIndentedText("- Reset print head position", 1, paperWidth))
        sb.append(formatter.formatIndentedText("- Clear buffer again", 1, paperWidth))
        
        sb.append(formatter.formatLeftText("3. PRINTING CONTENT", paperWidth))
        sb.append(formatter.formatIndentedText("- Send content in chunks", 1, paperWidth))
        sb.append(formatter.formatIndentedText("- Flush buffer after each chunk", 1, paperWidth))
        sb.append(formatter.formatIndentedText("- Wait for printing to complete", 1, paperWidth))
        
        sb.append(formatter.formatLeftText("4. AFTER PRINTING", paperWidth))
        sb.append(formatter.formatIndentedText("- Flush all buffers", 1, paperWidth))
        sb.append(formatter.formatIndentedText("- Feed paper (10 lines)", 1, paperWidth))
        
        sb.append(formatter.formatLeftText("5. PAPER CUTTING SEQUENCE", paperWidth))
        sb.append(formatter.formatIndentedText("- Clear buffer before cutting", 1, paperWidth))
        sb.append(formatter.formatIndentedText("- Send cutting command (GS V 1)", 1, paperWidth))
        sb.append(formatter.formatIndentedText("- Wait for cutting to complete", 1, paperWidth))
        
        // 切纸命令摘要
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatCenteredText(formatter.formatBold("CUT COMMAND DETAILS"), paperWidth))
        sb.append(formatter.formatLeftText("Primary Cut Cmd: GS V 1 (0x1D 0x56 0x01)", paperWidth))
        sb.append(formatter.formatLeftText("Feed Before Cut: Yes (10 lines)", paperWidth))
        sb.append(formatter.formatLeftText("Cut Mode: Partial cut", paperWidth))
        
        // 如果打印测试成功，纸应该在这里被切断
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatCenteredText(formatter.formatBold("PAPER SHOULD BE CUT HERE"), paperWidth))
        sb.append(formatter.formatDivider(paperWidth))
        
        // 通用打印测试
        sb.append(formatter.formatLeftText("This is left aligned text", paperWidth))
        sb.append(formatter.formatCenteredText("This is center aligned", paperWidth))
        sb.append(formatter.formatRightText("This is right aligned", paperWidth))
        
        // 订单格式示例
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatLeftText(formatter.formatBold("ORDER FORMAT EXAMPLE:"), paperWidth))
        sb.append(formatter.formatItemPriceLine("Ginger Beef", 2, "38.00", paperWidth))
        sb.append(formatter.formatItemPriceLine("Spicy Tofu", 1, "28.00", paperWidth))
        sb.append(formatter.formatLeftRightText("Total:", "66.00", paperWidth))
        
        // 结尾
        sb.append(formatter.formatDivider(paperWidth))
        sb.append(formatter.formatCenteredText("TEST COMPLETED", paperWidth))
        sb.append(formatter.formatCenteredText("If paper is not cut after this line,", paperWidth))
        sb.append(formatter.formatCenteredText("there is a problem with the cutter", paperWidth))
        sb.append(formatter.addEmptyLines(3))
        
        return sb.toString()
    }
    
    // 辅助方法：格式化价格
    private fun formatPrice(price: Double): String {
        // 使用runBlocking获取货币符号
        val currencySymbol = runBlocking { settingRepository.getCurrencySymbolFlow().first() }
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 