package com.example.wooauto.data.remote.adapters

import android.util.Log
import com.example.wooauto.data.remote.dto.OrderDto
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * OrderDto列表的类型适配器
 * 专门处理OrderDto列表的JSON解析，确保即使部分订单解析失败，也能返回可用的订单列表
 */
class OrderDtoListTypeAdapter : TypeAdapter<List<OrderDto>>() {
    
    private val orderDtoAdapter = OrderDtoTypeAdapter()
    
    override fun write(out: JsonWriter, value: List<OrderDto>?) {
        // 实现序列化逻辑（如果需要）
        if (value == null) {
            out.nullValue()
            return
        }
        
        out.beginArray()
        for (order in value) {
            orderDtoAdapter.write(out, order)
        }
        out.endArray()
    }

    override fun read(reader: JsonReader): List<OrderDto> {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        
        val orders = mutableListOf<OrderDto>()
        try {
            reader.beginArray()
            
            var skipCount = 0
            var successCount = 0
            
            while (reader.hasNext()) {
                try {
                    val order = orderDtoAdapter.read(reader)
                    if (order != null) {
                        orders.add(order)
                        successCount++
                    }
                } catch (e: Exception) {
                    skipCount++
                    Log.e("OrderDtoListTypeAdapter", "跳过无法解析的订单: ${e.message}", e)
                    
                    // 跳过当前损坏的JSON对象
                    try {
                        // 如果当前在对象内部，需要读到对象结束
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            reader.skipValue()
                        } else if (reader.peek() != JsonToken.END_ARRAY) {
                            // 如果不是数组结束，但又不在对象开始位置，可能解析器状态异常
                            // 尝试跳过直到找到下一个对象开始或数组结束
                            while (reader.peek() != JsonToken.BEGIN_OBJECT && 
                                   reader.peek() != JsonToken.END_ARRAY && 
                                   reader.hasNext()) {
                                reader.skipValue()
                            }
                        }
                    } catch (skipException: Exception) {
                        Log.e("OrderDtoListTypeAdapter", "尝试跳过损坏订单时出错: ${skipException.message}")
                        // 最后的尝试：如果所有其他方法都失败，尝试跳到数组末尾
                        try {
                            while (reader.peek() != JsonToken.END_ARRAY && reader.hasNext()) {
                                reader.skipValue()
                            }
                        } catch (e: Exception) {
                            // 如果跳过失败，只能中断解析返回已解析的内容
                            Log.e("OrderDtoListTypeAdapter", "无法恢复解析，返回已解析的 ${orders.size} 个订单")
                            break
                        }
                    }
                }
            }
            
            reader.endArray()
            
            // 记录解析统计信息
            Log.d("OrderDtoListTypeAdapter", "订单解析完成: 成功=${successCount}, 跳过=${skipCount}, 总计=${orders.size}")
            
        } catch (e: Exception) {
            Log.e("OrderDtoListTypeAdapter", "解析订单列表时出错: ${e.message}", e)
            // 即使出错，也返回已解析成功的订单
        }
        
        return orders
    }
} 