package com.example.wooauto.data.local.converters

import androidx.room.TypeConverter
import com.example.wooauto.data.local.entities.CategoryEntity
import com.example.wooauto.data.local.entities.OrderItemEntity
import com.example.wooauto.data.local.entities.OrderLineItemEntity
import com.example.wooauto.data.local.entities.ProductAttributeEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * Room数据库类型转换器
 * 用于转换复杂类型和Room支持的基本类型之间的转换
 */
class RoomConverters {
    private val gson = Gson()

    // Date转换
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    // 通用的字符串列表转换器
    @TypeConverter
    fun listToJson(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun jsonToList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    // Long列表转换
    @TypeConverter
    fun longListToJson(value: List<Long>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun jsonToLongList(value: String): List<Long> {
        if (value.isBlank()) return emptyList()
        val listType = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, listType)
    }

    // CategoryEntity列表转换
    @TypeConverter
    fun categoryListToJson(value: List<CategoryEntity>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun jsonToCategoryList(value: String): List<CategoryEntity> {
        if (value.isBlank()) return emptyList()
        val type = object : TypeToken<List<CategoryEntity>>() {}.type
        return gson.fromJson(value, type)
    }

    // ProductAttributeEntity列表转换
    @TypeConverter
    fun productAttributeListToJson(value: List<ProductAttributeEntity>?): String? {
        return if (value == null) null else gson.toJson(value)
    }
    
    @TypeConverter
    fun jsonToProductAttributeList(value: String?): List<ProductAttributeEntity>? {
        if (value == null) return null
        val type = object : TypeToken<List<ProductAttributeEntity>>() {}.type
        return gson.fromJson(value, type)
    }

    // OrderItemEntity列表转换
    @TypeConverter
    fun orderItemListToJson(value: List<OrderItemEntity>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun jsonToOrderItemList(value: String): List<OrderItemEntity> {
        if (value.isBlank()) return emptyList()
        val type = object : TypeToken<List<OrderItemEntity>>() {}.type
        return gson.fromJson(value, type)
    }

    // OrderLineItemEntity列表转换
    @TypeConverter
    fun orderLineItemListToJson(value: List<OrderLineItemEntity>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun jsonToOrderLineItemList(value: String): List<OrderLineItemEntity> {
        if (value.isBlank()) return emptyList()
        val type = object : TypeToken<List<OrderLineItemEntity>>() {}.type
        return gson.fromJson(value, type)
    }
    
    // WooFoodInfoEntity转换
    @TypeConverter
    fun wooFoodInfoToJson(value: WooFoodInfoEntity?): String? {
        return if (value == null) null else gson.toJson(value)
    }
    
    @TypeConverter
    fun jsonToWooFoodInfo(value: String?): WooFoodInfoEntity? {
        if (value == null || value.isBlank()) return null
        return gson.fromJson(value, WooFoodInfoEntity::class.java)
    }
    
    // FeeLineEntity列表转换
    @TypeConverter
    fun feeLineListToJson(value: List<FeeLineEntity>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun jsonToFeeLineList(value: String): List<FeeLineEntity> {
        if (value.isBlank()) return emptyList()
        val type = object : TypeToken<List<FeeLineEntity>>() {}.type
        return gson.fromJson(value, type)
    }
    
    // TaxLineEntity列表转换
    @TypeConverter
    fun taxLineListToJson(value: List<TaxLineEntity>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun jsonToTaxLineList(value: String): List<TaxLineEntity> {
        if (value.isBlank()) return emptyList()
        val type = object : TypeToken<List<TaxLineEntity>>() {}.type
        return gson.fromJson(value, type)
    }
} 