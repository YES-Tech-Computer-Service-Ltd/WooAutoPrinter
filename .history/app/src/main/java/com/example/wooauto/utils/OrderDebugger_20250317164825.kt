/**
 * 调试订单费用行数据转换过程
 * 帮助解决订单费用显示问题
 */
fun debugOrderFeeConversion(order: Order?) {
    Log.d(TAG, "======= 订单费用行转换调试 =======")
    if (order == null) {
        Log.e(TAG, "订单为null，无法分析费用行")
        return
    }
    
    // 基本订单信息
    Log.d(TAG, "订单ID: ${order.id}, 订单号: ${order.number}, 状态: ${order.status}")
    Log.d(TAG, "订单总金额: ${order.total}, 小计: ${order.subtotal}, 税费: ${order.totalTax}")
    
    // WooFood信息
    Log.d(TAG, "WooFood信息:")
    order.woofoodInfo?.let { info ->
        Log.d(TAG, "  订单类型: ${if (info.isDelivery) "外卖订单" else "自取订单"}")
        Log.d(TAG, "  配送费: ${info.deliveryFee ?: "无"}")
        Log.d(TAG, "  小费: ${info.tip ?: "无"}")
    } ?: Log.d(TAG, "  无WooFood信息")
    
    // 费用行信息
    Log.d(TAG, "费用行信息 (${order.feeLines.size}项):")
    if (order.feeLines.isEmpty()) {
        Log.d(TAG, "  没有费用行 - 这是问题所在!")
    } else {
        order.feeLines.forEachIndexed { index, feeLine ->
            Log.d(TAG, "  ${index+1}. name='${feeLine.name}', total='${feeLine.total}'")
        }
    }
    
    // 建议修复步骤
    Log.d(TAG, "建议修复方案:")
    if (order.feeLines.isEmpty()) {
        Log.d(TAG, "  1. 确保在OrderMapper.mapEntityToDomain方法中直接添加费用行")
        Log.d(TAG, "  2. 不要依赖parseFeeLines方法，而是根据WooFoodInfo直接构建费用行")
        Log.d(TAG, "  3. 检查外卖/自取的判断逻辑是否正确")
    }
    
    // 核对费用行是否与WooFoodInfo一致
    order.woofoodInfo?.let { info ->
        var hasMatchingDeliveryFee = false
        var hasMatchingTip = false
        
        if (info.isDelivery) {
            val deliveryFee = info.deliveryFee ?: "0.00"
            hasMatchingDeliveryFee = order.feeLines.any { 
                (it.name.contains("配送", ignoreCase = true) || 
                 it.name.contains("外卖", ignoreCase = true) ||
                 it.name.contains("delivery", ignoreCase = true) ||
                 it.name.contains("shipping", ignoreCase = true)) && 
                it.total == deliveryFee
            }
            
            if (!hasMatchingDeliveryFee) {
                Log.d(TAG, "  ❌ 未找到与WooFoodInfo中配送费(${deliveryFee})匹配的费用行")
            } else {
                Log.d(TAG, "  ✓ 找到匹配WooFoodInfo配送费的费用行")
            }
        }
        
        val tip = info.tip ?: "0.00"
        hasMatchingTip = order.feeLines.any { 
            (it.name.contains("小费", ignoreCase = true) || 
             it.name.contains("tip", ignoreCase = true) ||
             it.name.contains("gratuity", ignoreCase = true) ||
             it.name.contains("appreciation", ignoreCase = true)) && 
            it.total == tip
        }
        
        if (!hasMatchingTip) {
            Log.d(TAG, "  ❌ 未找到与WooFoodInfo中小费(${tip})匹配的费用行")
        } else {
            Log.d(TAG, "  ✓ 找到匹配WooFoodInfo小费的费用行")
        }
    }
    
    Log.d(TAG, "======= 费用行调试结束 =======")
} 