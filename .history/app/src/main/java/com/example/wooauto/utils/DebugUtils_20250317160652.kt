package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.FeeLine

/**
 * 订单调试工具类
 */
object OrderDebugger {
    
    private const val TAG = "OrderDebugger"
    
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
} 