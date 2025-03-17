package com.example.wooauto.data.remote.adapters

import android.util.Log
import com.example.wooauto.BuildConfig
import com.example.wooauto.data.remote.dto.OrderDto
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * OrderDto列表的类型适配器
 * 专门处理List<OrderDto>的JSON解析，防止解析失败导致整个数据集丢失
 */
class OrderDtoListTypeAdapter(
    private val orderDtoAdapter: TypeAdapter<OrderDto>
) : TypeAdapter<List<OrderDto>>() {

    override fun write(out: JsonWriter, value: List<OrderDto>?) {
        // 序列化实现（如果需要）
    }

    override fun read(reader: JsonReader): List<OrderDto> {
        val orders = mutableListOf<OrderDto>()
        var successCount = 0
        var skipCount = 0
        
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        
        try {
            reader.beginArray()
            while (reader.hasNext()) {
                try {
                    val order = orderDtoAdapter.read(reader)
                    order?.let {
                        orders.add(it)
                        successCount++
                    }
                } catch (e: Exception) {
                    // 单个订单解析失败，跳过该订单继续处理其他
                    Log.e("OrderDtoListTypeAdapter", "订单解析失败: ${e.message}", e)
                    // 尝试跳过当前不完整的对象
                    skipInvalidObject(reader)
                    skipCount++
                }
            }
            reader.endArray()
            
            // 仅在调试模式保留最终结果日志
            if (BuildConfig.DEBUG && false) { // 添加false条件使其不执行
                Log.d("OrderDtoListTypeAdapter", "订单解析完成: 成功=$successCount, 跳过=$skipCount, 总计=${orders.size}")
            }
            
            return orders
        } catch (e: Exception) {
            Log.e("OrderDtoListTypeAdapter", "批量解析订单失败: ${e.message}", e)
            return orders // 返回已成功解析的订单
        }
    }
    
    /**
     * 尝试跳过不完整或格式不正确的JSON对象
     */
    private fun skipInvalidObject(reader: JsonReader) {
        try {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
            } else {
                // 尝试跳过当前值，无论它是什么
                reader.skipValue()
            }
        } catch (e: Exception) {
            Log.e("OrderDtoListTypeAdapter", "跳过无效JSON时出错: ${e.message}", e)
        }
    }
} 