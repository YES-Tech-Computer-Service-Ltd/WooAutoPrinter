package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.FeeLine
import com.example.wooauto.data.local.entities.OrderEntity
import com.example.wooauto.data.remote.models.OrderResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/**
 * 订单调试工具类
 */
object OrderDebugger {
    
    private const val TAG = "OrderDebugger"
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    
    /**
     * 详细打印订单信息到logcat，用于调试费用显示问题
     * 这个函数没有任何副作用，只是打印信息
     */
    fun debugPrintOrderDetails(order: Order?) {
        if (order == null) {
            Log.e(TAG, "订单为null，无法打印详情")
            return
        }
        
        Log.d(TAG, "==================== 订单调试信息 ====================")
        Log.d(TAG, "订单ID: ${order.id}, 订单号: ${order.number}, 状态: ${order.status}")
        Log.d(TAG, "订单总金额: ${order.total}, 小计: ${order.subtotal}, 税费: ${order.totalTax}")
        
        // WooFood信息
        order.woofoodInfo?.let { info ->
            Log.d(TAG, "------ WooFood信息 ------")
            Log.d(TAG, "订单类型: ${if (info.isDelivery) "外卖订单" else "自取订单"}")
            Log.d(TAG, "方式: ${info.orderMethod ?: "未指定"}")
            Log.d(TAG, "时间: ${info.deliveryTime ?: "未指定"}")
            Log.d(TAG, "配送费: ${info.deliveryFee ?: "0.00"}")
            Log.d(TAG, "小费: ${info.tip ?: "0.00"}")
            if (info.isDelivery) {
                Log.d(TAG, "配送地址: ${info.deliveryAddress ?: "未指定"}")
            }
        } ?: Log.d(TAG, "没有WooFood信息")
        
        // 费用行信息
        Log.d(TAG, "------ 费用行信息 (${order.feeLines.size}项) ------")
        if (order.feeLines.isEmpty()) {
            Log.d(TAG, "没有费用行")
        } else {
            order.feeLines.forEachIndexed { index, feeLine ->
                Log.d(TAG, "费用项 #${index + 1}: 名称='${feeLine.name}', 金额='${feeLine.total}', 税='${feeLine.totalTax}'")
                // 检查这是否是配送费或小费
                val isDeliveryFee = feeLine.name.contains("配送费", ignoreCase = true) || 
                               feeLine.name.contains("外卖费", ignoreCase = true) ||
                               feeLine.name.contains("shipping", ignoreCase = true) || 
                               feeLine.name.contains("delivery", ignoreCase = true)
                
                val isTip = feeLine.name.contains("小费", ignoreCase = true) || 
                       feeLine.name.contains("tip", ignoreCase = true) || 
                       feeLine.name.contains("gratuity", ignoreCase = true) ||
                       feeLine.name.contains("appreciation", ignoreCase = true)
                
                if (isDeliveryFee) {
                    Log.d(TAG, "  -> 这是配送费")
                }
                if (isTip) {
                    Log.d(TAG, "  -> 这是小费")
                }
            }
        }
        
        // 订单备注
        if (order.notes.isNotEmpty()) {
            Log.d(TAG, "------ 订单备注 ------")
            // 分行显示以便更清晰查看
            order.notes.split("\n").forEach { line ->
                Log.d(TAG, "备注: $line")
            }
        }
        
        // 商品详情
        Log.d(TAG, "------ 商品信息 (${order.items.size}项) ------")
        order.items.forEachIndexed { index, item ->
            Log.d(TAG, "商品 #${index + 1}: ${item.name}, 数量: ${item.quantity}, 单价: ${item.price}, 总价: ${item.total}")
        }
        
        Log.d(TAG, "==================== 调试信息结束 ====================")
    }
    
    /**
     * 增强版订单调试 - 打印领域模型和数据库实体详情
     * 输出订单在领域层的信息以及从API获取的原始数据
     * 这个函数没有任何副作用，只是打印信息
     */
    fun debugPrintDetailedOrderInfo(order: Order?, entity: OrderEntity?) {
        Log.d(TAG, "================================")
        Log.d(TAG, "======= 增强订单调试输出 =======")
        Log.d(TAG, "================================")
        
        // 先打印基本的领域模型信息
        if (order != null) {
            Log.d(TAG, "【领域模型】订单ID: ${order.id}, 订单号: ${order.number}")
            Log.d(TAG, "【领域模型】WooFood信息:")
            order.woofoodInfo?.let { info ->
                Log.d(TAG, "  - isDelivery = ${info.isDelivery}")
                Log.d(TAG, "  - orderMethod = ${info.orderMethod}")
                Log.d(TAG, "  - deliveryTime = ${info.deliveryTime}")
                Log.d(TAG, "  - deliveryFee = ${info.deliveryFee}")
                Log.d(TAG, "  - tip = ${info.tip}")
                Log.d(TAG, "  - deliveryAddress = ${info.deliveryAddress}")
            } ?: Log.d(TAG, "  - WooFood信息为null")
            
            Log.d(TAG, "【领域模型】费用行列表 (${order.feeLines.size}项):")
            order.feeLines.forEachIndexed { index, feeLine ->
                Log.d(TAG, "  ${index+1}. name='${feeLine.name}', total='${feeLine.total}'")
            }
        } else {
            Log.d(TAG, "【领域模型】订单为null")
        }
        
        // 打印数据库实体信息
        if (entity != null) {
            Log.d(TAG, "【数据库实体】订单ID: ${entity.id}, 订单号: ${entity.number}")
            
            // 备注可能包含重要信息(如元数据)
            Log.d(TAG, "【数据库实体】备注/元数据内容:")
            entity.customerNote.split("\n").forEach { line ->
                if (line.isNotEmpty()) {
                    Log.d(TAG, "  $line")
                }
            }
            
            // 解析备注中的提示词
            val pickupKeywords = listOf("pickup", "pick up", "collected", "collection", "takeaway", "take away", "take-away", "自取", "取餐")
            val deliveryKeywords = listOf("delivery", "deliver", "shipping", "ship", "外卖", "配送")
            
            Log.d(TAG, "【订单类型分析】订单备注关键词检查:")
            val noteLC = entity.customerNote.lowercase()
            
            pickupKeywords.forEach { keyword ->
                if (noteLC.contains(keyword)) {
                    Log.d(TAG, "  ✓ 找到自取关键词: '$keyword'")
                }
            }
            
            deliveryKeywords.forEach { keyword ->
                if (noteLC.contains(keyword)) {
                    Log.d(TAG, "  ✓ 找到外卖关键词: '$keyword'")
                }
            }
            
            // 检查ASAP关键词（通常意味着自取）
            if (noteLC.contains("asap")) {
                Log.d(TAG, "  ✓ 找到'asap'关键词（通常表示自取）")
            }
            
            // 检查exwfood_order_method元数据
            val methodRegex = "exwfood_order_method[：:]*\\s*([\\w]+)".toRegex(RegexOption.IGNORE_CASE)
            val methodMatch = methodRegex.find(entity.customerNote)
            if (methodMatch != null && methodMatch.groupValues.size > 1) {
                val method = methodMatch.groupValues[1].trim().lowercase()
                Log.d(TAG, "  ✓ 找到元数据订单方式: '$method'")
            }
            
            // 检查fee_lines元数据
            val feeLineRegex = "(?:小费|配送费|外卖费|运费|Tip|gratuity|Show Your Appreciation|Shipping fee|Delivery fee)[：:]*\\s*\\$?([0-9.]+)".toRegex(RegexOption.IGNORE_CASE)
            val feeMatches = feeLineRegex.findAll(entity.customerNote).toList()
            for (match in feeMatches) {
                if (match.groupValues.size > 1) {
                    val feeType = match.groupValues[0]
                    val feeAmount = match.groupValues[1]
                    Log.d(TAG, "  ✓ 找到费用项: '$feeType' = $feeAmount")
                }
            }
            
            // 分析订单地址
            Log.d(TAG, "【地址分析】:")
            Log.d(TAG, "  - 账单地址: ${entity.billingAddress}")
            Log.d(TAG, "  - 配送地址: ${entity.shippingAddress}")
            val addressesSame = entity.billingAddress == entity.shippingAddress
            Log.d(TAG, "  - 两个地址${if (addressesSame) "相同" else "不同"}")
            
            if (entity.shippingAddress.isBlank()) {
                Log.d(TAG, "  ✓ 配送地址为空（通常表示自取订单）")
            }
        } else {
            Log.d(TAG, "【数据库实体】实体为null")
        }
        
        Log.d(TAG, "================================")
        Log.d(TAG, "======= 增强调试输出结束 =======")
        Log.d(TAG, "================================")
    }
    
    /**
     * 高级API调试 - 打印API原始响应数据
     * 用于分析API返回的原始数据结构，特别是fee_lines和metadata
     * 这个函数没有任何副作用，只是打印信息
     */
    fun debugPrintApiResponse(response: OrderResponse?) {
        Log.d(TAG, "==================================")
        Log.d(TAG, "======= API响应调试输出 =======")
        Log.d(TAG, "==================================")
        
        if (response == null) {
            Log.e(TAG, "API响应为null")
            return
        }
        
        Log.d(TAG, "API响应订单ID: ${response.id}, 订单号: ${response.number}")
        Log.d(TAG, "订单状态: ${response.status}")
        Log.d(TAG, "客户备注: ${response.customerNote ?: "无"}")
        
        // 打印元数据
        Log.d(TAG, "【元数据 meta_data】(${response.metaData.size}项):")
        response.metaData.forEach { meta ->
            Log.d(TAG, "  - ${meta.key}: ${meta.value}")
        }
        
        // 重点：打印fee_lines
        Log.d(TAG, "【费用行 fee_lines】(${response.feeLines?.size ?: 0}项):")
        response.feeLines?.forEach { fee ->
            Log.d(TAG, "  - ID: ${fee.id}, 名称: '${fee.name}', 金额: ${fee.total}")
            
            // 检查关键词
            val isTip = fee.name.contains("tip", ignoreCase = true) || 
                      fee.name.contains("小费", ignoreCase = true) || 
                      fee.name.contains("gratuity", ignoreCase = true) ||
                      fee.name.contains("appreciation", ignoreCase = true)
            
            val isDeliveryFee = fee.name.contains("shipping", ignoreCase = true) || 
                              fee.name.contains("delivery", ignoreCase = true) ||
                              fee.name.contains("配送", ignoreCase = true) || 
                              fee.name.contains("外卖", ignoreCase = true)
            
            if (isTip) {
                Log.d(TAG, "    ✓ 这是小费")
            }
            
            if (isDeliveryFee) {
                Log.d(TAG, "    ✓ 这是配送费")
            }
        } ?: Log.d(TAG, "  无费用行")
        
        // 打印地址信息
        Log.d(TAG, "【配送地址】:")
        Log.d(TAG, "  - 名: ${response.shipping.firstName}")
        Log.d(TAG, "  - 姓: ${response.shipping.lastName}")
        Log.d(TAG, "  - 地址1: ${response.shipping.address1}")
        Log.d(TAG, "  - 城市: ${response.shipping.city}")
        Log.d(TAG, "  - 邮编: ${response.shipping.postcode}")
        
        val hasShippingAddress = response.shipping.firstName.isNotBlank() || 
                               response.shipping.lastName.isNotBlank() || 
                               response.shipping.address1.isNotBlank()
        
        if (!hasShippingAddress) {
            Log.d(TAG, "  ✓ 配送地址为空（通常表示自取订单）")
        }
        
        // 打印tip和deliveryFee辅助属性的值
        Log.d(TAG, "【辅助提取属性】:")
        Log.d(TAG, "  - response.tip = ${response.tip ?: "null"}")
        Log.d(TAG, "  - response.deliveryFee = ${response.deliveryFee ?: "null"}")
        
        Log.d(TAG, "==================================")
        Log.d(TAG, "======= API调试输出结束 =======")
        Log.d(TAG, "==================================")
    }
    
    /**
     * 分析订单类型判断逻辑
     * 仅供调试使用，打印判断订单类型的关键信息
     */
    fun analyzeOrderTypeDetection(order: Order?, customerNote: String? = null) {
        Log.d(TAG, "======= 订单类型判断分析 =======")
        
        if (order == null) {
            Log.e(TAG, "订单为null，无法分析")
            return
        }
        
        val note = customerNote ?: order.notes
        val noteLC = note.lowercase()
        
        // 分析顾客备注中的关键词
        Log.d(TAG, "订单ID: ${order.id}, 订单号: ${order.number}")
        Log.d(TAG, "当前WooFood信息: isDelivery=${order.woofoodInfo?.isDelivery ?: "null"}")
        Log.d(TAG, "订单备注: \"$note\"")
        
        // 检查自取关键词
        val pickupKeywords = listOf("pickup", "pick up", "collected", "collection", "takeaway", "take away", "take-away", "自取", "取餐")
        var foundPickupKeyword = false
        
        pickupKeywords.forEach { keyword ->
            if (noteLC.contains(keyword)) {
                Log.d(TAG, "✓ 包含自取关键词: '$keyword'")
                foundPickupKeyword = true
            }
        }
        
        if (!foundPickupKeyword) {
            Log.d(TAG, "✗ 未找到任何自取关键词")
        }
        
        // 检查外卖关键词
        val deliveryKeywords = listOf("delivery", "deliver", "shipping", "ship", "外卖", "配送")
        var foundDeliveryKeyword = false
        
        deliveryKeywords.forEach { keyword ->
            if (noteLC.contains(keyword)) {
                Log.d(TAG, "✓ 包含外卖关键词: '$keyword'")
                foundDeliveryKeyword = true
            }
        }
        
        if (!foundDeliveryKeyword) {
            Log.d(TAG, "✗ 未找到任何外卖关键词")
        }
        
        // 检查ASAP关键词（通常意味着自取）
        if (noteLC.contains("asap")) {
            Log.d(TAG, "✓ 包含'asap'关键词（通常表示自取）")
        }
        
        // 冲突分析
        if (foundPickupKeyword && foundDeliveryKeyword) {
            Log.d(TAG, "⚠️ 订单备注同时包含自取和外卖关键词，可能导致判断错误")
        }
        
        // 根据当前的判断逻辑给出建议
        val shouldBePickup = foundPickupKeyword && !foundDeliveryKeyword
        val shouldBeDelivery = foundDeliveryKeyword && !foundPickupKeyword
        val isAmbiguous = (foundPickupKeyword && foundDeliveryKeyword) || (!foundPickupKeyword && !foundDeliveryKeyword)
        
        Log.d(TAG, "-----------------------------------")
        
        if (order.woofoodInfo?.isDelivery == true) {
            if (shouldBePickup) {
                Log.d(TAG, "⚠️ 判断错误: 该订单被错误地标记为外卖订单，但备注中包含自取关键词")
            } else if (isAmbiguous && noteLC.contains("asap")) {
                Log.d(TAG, "⚠️ 可能判断错误: 备注中包含'asap'，通常表示自取，但订单被标为外卖")
            }
        } else if (order.woofoodInfo?.isDelivery == false) {
            if (shouldBeDelivery) {
                Log.d(TAG, "⚠️ 判断错误: 该订单被错误地标记为自取订单，但备注中包含外卖关键词")
            }
        }
        
        // 检查配送费和小费逻辑
        var hasDeliveryFee = false
        var hasTip = false
        
        order.feeLines.forEach { feeLine ->
            val feeName = feeLine.name.lowercase()
            if (feeName.contains("配送") || feeName.contains("外卖") || 
                feeName.contains("shipping") || feeName.contains("delivery")) {
                hasDeliveryFee = true
                Log.d(TAG, "✓ 费用行中包含配送费: ${feeLine.name}=${feeLine.total}")
            }
            
            if (feeName.contains("小费") || feeName.contains("tip") || 
                feeName.contains("gratuity") || feeName.contains("appreciation")) {
                hasTip = true
                Log.d(TAG, "✓ 费用行中包含小费: ${feeLine.name}=${feeLine.total}")
            }
        }
        
        if (!hasDeliveryFee && order.woofoodInfo?.isDelivery == true) {
            Log.d(TAG, "⚠️ 外卖订单缺少配送费行项目")
        }
        
        if (!hasTip) {
            Log.d(TAG, "⚠️ 订单缺少小费行项目")
        }
        
        Log.d(TAG, "======= 分析结束 =======")
    }
} 