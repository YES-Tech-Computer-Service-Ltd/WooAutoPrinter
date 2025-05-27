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
 * - V5: 添加模板配置表，支持打印模板自定义配置
 * - V6: 模板配置表添加商店信息细分字段（showStoreName、showStoreAddress、showStorePhone）
 * - V7: 重新设计模板配置字段结构，按功能分组（订单信息、客户信息、订单内容、支付信息）
 */
@Database(
    entities = [OrderEntity::class, ProductEntity::class, SettingEntity::class, TemplateConfigEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao
    abstract fun settingDao(): SettingDao
    abstract fun templateConfigDao(): TemplateConfigDao
} 