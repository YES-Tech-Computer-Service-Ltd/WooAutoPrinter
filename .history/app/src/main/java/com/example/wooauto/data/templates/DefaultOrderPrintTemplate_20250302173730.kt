package com.example.wooauto.data.templates

import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.OrderPrintTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
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
        
        val storeName = settingRepository.getStoreName() ?: "我的商店"
        val storeAddress = settingRepository.getStoreAddress() ?: ""
        val storePhone = settingRepository.getStorePhone() ?: ""
        
        val sb = StringBuilder()
        
        // 标题
        sb.append("[C]<b>${storeName}</b>\n\n")
        
        // 店铺信息
        if (storeAddress.isNotEmpty()) {
            sb.append("[C]${storeAddress}\n")
        }
        if (storePhone.isNotEmpty()) {
            sb.append("[C]电话: ${storePhone}\n")
        }
        sb.append("[C]--------------------------------\n")
        
        // 订单信息
        sb.append("[L]<b>订单号:</b> ${order.number}\n")
        sb.append("[L]<b>日期:</b> ${dateFormat.format(order.dateCreated)}\n")
        sb.append("[L]<b>打印时间:</b> ${currentTime}\n")
        
        // 客户信息
        sb.append("[C]--------------------------------\n")
        sb.append("[L]<b>客户信息</b>\n")
        sb.append("[L]姓名: ${order.billingFirstName} ${order.billingLastName}\n")
        
        if (order.billingPhone.isNotEmpty()) {
            sb.append("[L]电话: ${order.billingPhone}\n")
        }
        
        // 地址信息
        if (order.billingAddress1.isNotEmpty() || order.billingAddress2.isNotEmpty()) {
            val address = listOfNotNull(
                order.billingAddress1,
                order.billingAddress2,
                order.billingCity,
                order.billingState,
                order.billingPostcode,
                order.billingCountry
            ).filter { it.isNotEmpty() }.joinToString(", ")
            
            sb.append("[L]地址: ${address}\n")
        }
        
        // 订单项目
        sb.append("[C]--------------------------------\n")
        sb.append("[L]<b>订单项目</b>\n")
        sb.append("[C]--------------------------------\n")
        
        // 表头
        sb.append("[L]<b>商品名称</b>[R]<b>数量 x 单价</b>\n")
        
        // 商品列表
        order.items.forEach { item ->
            // 商品名称可能很长，需要处理换行
            val name = item.name
            val price = formatPrice(item.price)
            
            sb.append("[L]${name}[R]${item.quantity} x ${price}\n")
            
            // 如果有商品变体信息，显示变体
            if (item.variationId > 0 && !item.variation.isNullOrEmpty()) {
                sb.append("[L]  ${item.variation}\n")
            }
            
            // 如果有元数据，显示元数据
            item.meta.forEach { meta ->
                sb.append("[L]  ${meta.key}: ${meta.value}\n")
            }
        }
        
        sb.append("[C]--------------------------------\n")
        
        // 订单合计
        sb.append("[L]<b>小计:</b>[R]${formatPrice(order.subtotal)}\n")
        
        // 显示折扣
        if (order.discountTotal > 0) {
            sb.append("[L]<b>折扣:</b>[R]-${formatPrice(order.discountTotal)}\n")
        }
        
        // 显示运费
        if (order.shippingTotal > 0) {
            sb.append("[L]<b>运费:</b>[R]${formatPrice(order.shippingTotal)}\n")
        }
        
        // 显示税费
        if (order.totalTax > 0) {
            sb.append("[L]<b>税费:</b>[R]${formatPrice(order.totalTax)}\n")
        }
        
        // 总计
        sb.append("[L]<b>总计:</b>[R]${formatPrice(order.total)}\n")
        
        // 支付方式
        sb.append("[L]<b>支付方式:</b>[R]${order.paymentMethodTitle}\n")
        
        // 配送方式
        if (order.shippingMethodTitle.isNotEmpty()) {
            sb.append("[L]<b>配送方式:</b>[R]${order.shippingMethodTitle}\n")
        }
        
        // 订单备注
        if (order.customerNote.isNotEmpty()) {
            sb.append("[C]--------------------------------\n")
            sb.append("[L]<b>订单备注:</b>\n")
            sb.append("[L]${order.customerNote}\n")
        }
        
        // 页脚
        sb.append("[C]--------------------------------\n")
        sb.append("[C]感谢您的惠顾!\n")
        
        // 添加额外的空行，方便撕纸
        sb.append("\n\n\n\n")
        
        return sb.toString()
    }
    
    override fun generateTestPrintContent(config: PrinterConfig): String {
        Log.d(TAG, "生成测试打印内容")
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
        
        val storeName = settingRepository.getStoreName() ?: "我的商店"
        
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
        val currencySymbol = settingRepository.getCurrencySymbol() ?: "¥"
        return "$currencySymbol${String.format("%.2f", price)}"
    }
} 