package com.example.wooauto.data.templates

import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.OrderPrintTemplate
import com.example.wooauto.domain.templates.TemplateType
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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        
        // 使用runBlocking获取商店信息
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
        val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
        
        val sb = StringBuilder()
        
        // 标题 (总是打印)
        sb.append("[C]<b>${storeName}</b>\n\n")
        
        // 店铺信息
        if (config.printStoreInfo) {
            if (storeAddress.isNotEmpty()) {
                sb.append("[C]${storeAddress}\n")
            }
            if (storePhone.isNotEmpty()) {
                sb.append("[C]Tel: ${storePhone}\n")
            }
        }
        sb.append("[C]--------------------------------\n")
        
        // 订单信息
        sb.append("[L]<b>Order #:</b> ${order.number}\n")
        sb.append("[L]<b>Date:</b> ${dateFormat.format(order.dateCreated)}\n")
        sb.append("[L]<b>Print Time:</b> ${currentTime}\n")
        
        // 客户信息
        if (config.printCustomerInfo && (order.customerName.isNotEmpty() || order.contactInfo.isNotEmpty() || order.billingInfo.isNotEmpty())) {
            sb.append("[C]--------------------------------\n")
            sb.append("[L]<b>Customer Information</b>\n")
            
            if (order.customerName.isNotEmpty()) {
                sb.append("[L]Name: ${order.customerName}\n")
            }
            
            if (order.contactInfo.isNotEmpty()) {
                sb.append("[L]Contact: ${order.contactInfo}\n")
            }
            
            // 地址信息
            if (order.billingInfo.isNotEmpty()) {
                sb.append("[L]Address: ${order.billingInfo}\n")
            }
        }
        
        // 配送信息
        if (order.woofoodInfo != null) {
            sb.append("[C]--------------------------------\n")
            sb.append("[L]<b>Delivery Info</b>\n")
            
            // 订单类型 (配送或取餐)
            sb.append("[L]Order Type: ${if (order.woofoodInfo.isDelivery) "Delivery" else "Takeaway"}\n")
            
            // 如果是配送订单，添加配送地址
            if (order.woofoodInfo.isDelivery) {
                order.woofoodInfo.deliveryAddress?.let {
                    sb.append("[L]Delivery Address: ${it}\n")
                }
                
                order.woofoodInfo.deliveryFee?.let {
                    sb.append("[L]Delivery Fee: ${it}\n")
                }
                
                order.woofoodInfo.tip?.let {
                    sb.append("[L]Tip: ${it}\n")
                }
            }
        }
        
        // 订单项目
        sb.append("[C]--------------------------------\n")
        sb.append("[L]<b>Order Items</b>\n")
        sb.append("[C]--------------------------------\n")
        
        // 表头
        sb.append("[L]<b>Item</b>[R]<b>Qty x Price</b>\n")
        
        // 商品列表
        order.items.forEach { item ->
            // 商品名称可能很长，需要处理换行
            val name = item.name
            val price = formatPrice(item.price.toDouble())
            
            sb.append("[L]${name}[R]${item.quantity} x ${price}\n")
            
            // 如果配置为打印商品详情，则打印商品选项
            if (config.printItemDetails && item.options.isNotEmpty()) {
                item.options.forEach { option ->
                    sb.append("[L]  - ${option.name}: ${option.value}\n")
                }
            }
        }
        
        sb.append("[C]--------------------------------\n")
        
        // 订单合计
        sb.append("[L]<b>Total:</b>[R]${order.total}\n")
        
        // 支付方式
        sb.append("[L]<b>Payment Method:</b>[R]${order.paymentMethod}\n")
        
        // 订单备注
        if (config.printOrderNotes && order.notes.isNotEmpty()) {
            sb.append("[C]--------------------------------\n")
            sb.append("[L]<b>Order Notes:</b>\n")
            sb.append("[L]${order.notes}\n")
        }
        
        // 页脚
        if (config.printFooter) {
            sb.append("[C]--------------------------------\n")
            sb.append("[C]Thank you for your order!\n")
        }
        
        // 添加额外的空行，方便撕纸
        sb.append("\n\n\n\n")
        
        return sb.toString()
    }
    
    /**
     * 生成配送订单模板 - 重点突出配送信息和菜品信息
     */
    private fun generateDeliveryTemplate(order: Order, config: PrinterConfig): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        
        val sb = StringBuilder()
        
        // 标题
        sb.append("[C]<b>${storeName}</b>\n")
        sb.append("[C]<b>DELIVERY RECEIPT</b>\n\n")
        
        // 订单基本信息
        sb.append("[C]--------------------------------\n")
        sb.append("[L]<b>Order #:</b> ${order.number}\n")
        sb.append("[L]<b>Date:</b> ${dateFormat.format(order.dateCreated)}\n")
        
        // 客户和配送信息 (配送单特别突出这部分)
        sb.append("[C]--------------------------------\n")
        sb.append("[L]<b>DELIVERY INFORMATION</b>\n")
        
        // 客户名称
        if (order.customerName.isNotEmpty()) {
            sb.append("[L]<b>Customer:</b> ${order.customerName}\n")
        }
        
        // 联系方式 (配送单必须包含)
        if (order.contactInfo.isNotEmpty()) {
            sb.append("[L]<b>Contact:</b> ${order.contactInfo}\n")
        }
        
        // 配送地址 (配送单重点突出)
        if (order.woofoodInfo != null && order.woofoodInfo.isDelivery) {
            order.woofoodInfo.deliveryAddress?.let {
                sb.append("[L]<b>Address:</b> ${it}\n")
            }
        } else if (order.billingInfo.isNotEmpty()) {
            sb.append("[L]<b>Address:</b> ${order.billingInfo}\n")
        }
        
        // 订单类型
        if (order.woofoodInfo != null) {
            sb.append("[L]<b>Order Type:</b> ${if (order.woofoodInfo.isDelivery) "Delivery" else "Takeaway"}\n")
        }
        
        // 订单项目
        sb.append("[C]--------------------------------\n")
        sb.append("[L]<b>ORDER ITEMS</b>\n")
        sb.append("[C]--------------------------------\n")
        
        // 商品列表
        order.items.forEach { item ->
            sb.append("[L]<b>${item.name} x ${item.quantity}</b>\n")
            
            // 配送单应该包含商品选项信息
            if (item.options.isNotEmpty()) {
                item.options.forEach { option ->
                    sb.append("[L]  - ${option.name}: ${option.value}\n")
                }
            }
        }
        
        sb.append("[C]--------------------------------\n")
        
        // 支付信息
        sb.append("[L]<b>Total:</b>[R]${order.total}\n")
        sb.append("[L]<b>Payment:</b>[R]${order.paymentMethod}\n")
        
        // 订单备注 (配送说明很重要)
        if (order.notes.isNotEmpty()) {
            sb.append("[C]--------------------------------\n")
            sb.append("[L]<b>DELIVERY NOTES:</b>\n")
            sb.append("[L]${order.notes}\n")
        }
        
        // 添加额外的空行，方便撕纸
        sb.append("\n\n\n\n")
        
        return sb.toString()
    }
    
    /**
     * 生成厨房订单模板 - 仅包含菜品信息和下单时间
     */
    private fun generateKitchenTemplate(order: Order, config: PrinterConfig): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        
        val sb = StringBuilder()
        
        // 标题 - 厨房模式下更加简洁
        sb.append("[C]<b>KITCHEN ORDER</b>\n")
        sb.append("[C]--------------------------------\n")
        
        // 订单基本信息 - 仅保留必要信息
        sb.append("[L]<b>Order #:</b> ${order.number}\n")
        sb.append("[L]<b>Time:</b> ${timeFormat.format(order.dateCreated)}\n")
        
        // 订单类型
        if (order.woofoodInfo != null) {
            sb.append("[L]<b>Type:</b> ${if (order.woofoodInfo.isDelivery) "Delivery" else "Takeaway"}\n")
        }
        
        // 商品列表 - 厨房模板重点突出这部分
        sb.append("[C]--------------------------------\n")
        sb.append("[C]<b>ITEMS TO PREPARE</b>\n")
        sb.append("[C]--------------------------------\n")
        
        // 商品列表 - 更大字体、更清晰布局
        order.items.forEach { item ->
            sb.append("[L]<b>${item.quantity} x ${item.name}</b>\n")
            
            // 厨房单必须包含商品选项信息
            if (item.options.isNotEmpty()) {
                item.options.forEach { option ->
                    sb.append("[L]   ${option.name}: ${option.value}\n")
                }
            }
            sb.append("[L]\n") // 每个商品之间添加额外的空行，便于厨房识别
        }
        
        // 订单备注 - 厨房需要特别注意的事项
        if (order.notes.isNotEmpty()) {
            sb.append("[C]--------------------------------\n")
            sb.append("[L]<b>SPECIAL INSTRUCTIONS:</b>\n")
            sb.append("[L]${order.notes}\n")
        }
        
        // 打印时间
        sb.append("[C]--------------------------------\n")
        sb.append("[R]Printed: ${dateFormat.format(Date())}\n")
        
        // 添加额外的空行，方便撕纸
        sb.append("\n\n\n\n")
        
        return sb.toString()
    }
    
    override fun generateTestPrintContent(config: PrinterConfig): String {
        Log.d(TAG, "生成测试打印内容")
        
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentTime = dateFormat.format(Date())
            
            // 使用runBlocking获取商店名称，并提供默认值以防读取失败
            val storeName = try {
                runBlocking { settingRepository.getStoreNameFlow().first() }
            } catch (e: Exception) {
                Log.e(TAG, "获取商店名称失败: ${e.message}")
                "TEST STORE" // 提供默认值
            }
            
            val sb = StringBuilder()
            
            // 标题 - 保持简单
            sb.append("[C]<b>${storeName}</b>\n")
            sb.append("[C]<b>PRINTER TEST PAGE</b>\n")
            sb.append("[C]------------------------\n")
            
            // 打印机信息 - 使用简单格式
            sb.append("[L]<b>Printer:</b> ${config.name}\n")
            sb.append("[L]<b>Address:</b> ${config.address}\n")
            sb.append("[L]<b>Paper:</b> ${config.paperWidth}mm\n")
            sb.append("[L]<b>Time:</b> ${currentTime}\n")
            
            // 字体测试 - 保持基本格式
            sb.append("[C]------------------------\n")
            sb.append("[L]Normal Text\n")
            sb.append("[L]<b>Bold Text</b>\n")
            
            // 对齐测试 - 使用ASCII兼容字符
            sb.append("[C]------------------------\n")
            sb.append("[L]Left Aligned\n")
            sb.append("[C]Center Aligned\n")
            sb.append("[R]Right Aligned\n")
            
            // 页脚
            sb.append("[C]------------------------\n")
            sb.append("[C]Test Complete\n")
            sb.append("[C]Printer Working Normally\n")
            
            // 添加额外的空行，方便撕纸
            sb.append("\n\n\n")
            
            return sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "生成测试打印内容异常: ${e.message}", e)
        }
        
        // 如果发生错误，返回一个简单的默认内容
        return """
            [C]<b>PRINTER TEST</b>
            [C]------------
            [L]Simple Printer Test
            [C]Test Complete
            
            
        """.trimIndent()
    }
    
    // 辅助方法：格式化价格
    private fun formatPrice(price: Double): String {
        // 使用runBlocking获取货币符号
        val currencySymbol = runBlocking { settingRepository.getCurrencySymbolFlow().first() }
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 