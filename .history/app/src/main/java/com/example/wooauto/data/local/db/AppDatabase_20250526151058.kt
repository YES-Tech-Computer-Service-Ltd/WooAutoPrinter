package com.example.wooauto.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.wooauto.data.local.converters.RoomConverters
import com.example.wooauto.data.local.dao.OrderDao
import com.example.wooauto.data.local.dao.ProductDao
import com.example.wooauto.data.local.dao.SettingDao
import com.example.wooauto.data.local.dao.TemplateConfigDao
import com.example.wooauto.data.local.entities.OrderEntity
import com.example.wooauto.data.local.entities.ProductEntity
import com.example.wooauto.data.local.entities.SettingEntity
import com.example.wooauto.data.local.entities.TemplateConfigEntity

/**
 * Room数据库定义类
 * 版本历史：
 * - V1: 初始版本
 * - V2: 更新实体类结构，适应新的数据模型
 * - V3: 添加woofoodInfo、feeLines和taxLines字段支持配送费和小费显示
 * - V4: 添加isRead字段，支持订单已读/未读状态
 */
@Database(
    entities = [OrderEntity::class, ProductEntity::class, SettingEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao
    abstract fun settingDao(): SettingDao
} 