package com.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 设置实体类
 * 用于本地数据库存储应用设置信息
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val type: String,
    val description: String?
) 