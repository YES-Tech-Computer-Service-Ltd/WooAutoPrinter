package com.example.wooauto.data.templates

import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.OrderPrintTemplate
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
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        
        // 使用runBlocking获取商店信息
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        val storeAddress = runBlocking { settingRepository.getStoreAddressFlow().first() }
        val storePhone = runBlocking { settingRepository.getStorePhoneFlow().first() }
        
        val sb = StringBuilder()
        
        // 标题 (总是打印)
        sb.append("[C]<b>${storeName}</b>\n\n")
        
        // 店铺信息 (根据配置决定是否打印)
        if (config.printStoreInfo) {
            if (storeAddress.isNotEmpty()) {
                sb.append("[C]${storeAddress}\n")
            }
            if (storePhone.isNotEmpty()) {
                sb.append("[C]电话: ${storePhone}\n")
            }
        }
        sb.append("[C]--------------------------------\n")
        
        // 订单信息 (总是打印)
        sb.append("[L]<b>订单号:</b> ${order.number}\n")
        sb.append("[L]<b>日期:</b> ${dateFormat.format(order.dateCreated)}\n")
        sb.append("[L]<b>打印时间:</b> ${currentTime}\n")
        
        // 客户信息 (根据配置决定是否打印)
        if (config.printCustomerInfo && (order.customerName.isNotEmpty() || order.contactInfo.isNotEmpty() || order.billingInfo.isNotEmpty())) {
            sb.append("[C]--------------------------------\n")
            sb.append("[L]<b>客户信息</b>\n")
            
            if (order.customerName.isNotEmpty()) {
                sb.append("[L]姓名: ${order.customerName}\n")
            }
            
            if (order.contactInfo.isNotEmpty()) {
                sb.append("[L]联系方式: ${order.contactInfo}\n")
            }
            
            // 地址信息
            if (order.billingInfo.isNotEmpty()) {
                sb.append("[L]地址: ${order.billingInfo}\n")
            }
        }
        
        // 订单项目 (根据配置决定是否打印详情)
        sb.append("[C]--------------------------------\n")
        sb.append("[L]<b>订单项目</b>\n")
        sb.append("[C]--------------------------------\n")
        
        // 表头
        sb.append("[L]<b>商品名称</b>[R]<b>数量 x 单价</b>\n")
        
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
        
        // 订单合计 (总是打印)
        sb.append("[L]<b>总计:</b>[R]${order.total}\n")
        
        // 支付方式 (总是打印)
        sb.append("[L]<b>支付方式:</b>[R]${order.paymentMethod}\n")
        
        // 订单备注 (根据配置决定是否打印)
        if (config.printOrderNotes && order.notes.isNotEmpty()) {
            sb.append("[C]--------------------------------\n")
            sb.append("[L]<b>订单备注:</b>\n")
            sb.append("[L]${order.notes}\n")
        }
        
        // 页脚 (根据配置决定是否打印)
        if (config.printFooter) {
            sb.append("[C]--------------------------------\n")
            sb.append("[C]感谢您的惠顾!\n")
        }
        
        // 添加额外的空行，方便撕纸
        sb.append("\n\n\n\n")
        
        return sb.toString()
    }
    
    override fun generateTestPrintContent(config: PrinterConfig): String {
        Log.d(TAG, "生成测试打印内容")
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        
        // 使用runBlocking获取商店名称
        val storeName = runBlocking { settingRepository.getStoreNameFlow().first() }
        
        val sb = StringBuilder()
        
        // 标题
        sb.append("[C]<b>${storeName}</b>\n\n")
        sb.append("[C]<b>打印机测试页</b>\n")
        sb.append("[C]--------------------------------\n")
        
        // 打印机信息
        sb.append("[L]<b>打印机名称:</b> ${config.name}\n")
        sb.append("[L]<b>打印机地址:</b> ${config.address}\n")
        sb.append("[L]<b>纸张宽度:</b> ${config.paperWidth}mm\n")
        sb.append("[L]<b>测试时间:</b> ${currentTime}\n")
        
        // 字体测试
        sb.append("[C]--------------------------------\n")
        sb.append("[C]<b>字体测试</b>\n")
        sb.append("[L]普通字体\n")
        sb.append("[L]<b>粗体字体</b>\n")
        sb.append("[L]<i>斜体字体</i>\n")
        sb.append("[L]<u>下划线字体</u>\n")
        sb.append("[L]<b><i>粗斜体</i></b>\n")
        
        // 对齐测试
        sb.append("[C]--------------------------------\n")
        sb.append("[C]<b>对齐测试</b>\n")
        sb.append("[L]左对齐文本\n")
        sb.append("[C]居中对齐文本\n")
        sb.append("[R]右对齐文本\n")
        
        // 中文测试
        sb.append("[C]--------------------------------\n")
        sb.append("[C]<b>中文测试</b>\n")
        sb.append("[L]这是中文打印测试\n")
        sb.append("[L]支持中文字符和标点符号\n")
        sb.append("[L]确保中文显示正常\n")
        
        // 数字和符号测试
        sb.append("[C]--------------------------------\n")
        sb.append("[C]<b>数字和符号测试</b>\n")
        sb.append("[L]0123456789\n")
        sb.append("[L]!@#$%^&*()_+-=[]{}|;':\",./<>?\n")
        
        // 页脚
        sb.append("[C]--------------------------------\n")
        sb.append("[C]测试打印完成\n")
        sb.append("[C]如果您能看到此内容\n")
        sb.append("[C]说明打印机工作正常\n")
        
        // 添加额外的空行，方便撕纸
        sb.append("\n\n\n\n")
        
        return sb.toString()
    }
    
    // 辅助方法：格式化价格
    private fun formatPrice(price: Double): String {
        // 使用runBlocking获取货币符号
        val currencySymbol = runBlocking { settingRepository.getCurrencySymbolFlow().first() }
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 