package com.wooauto.data.mappers

import com.wooauto.data.local.entities.SettingEntity
import com.wooauto.domain.models.Setting

/**
 * 设置数据映射器
 * 负责在不同层的设置模型之间进行转换
 */
object SettingMapper {

    /**
     * 将本地数据库实体转换为领域模型
     * @param entity 数据库设置实体
     * @return 领域设置模型
     */
    fun mapEntityToDomain(entity: SettingEntity): Setting {
        return Setting(
            key = entity.key,
            value = entity.value,
            type = entity.type,
            description = entity.description
        )
    }

    /**
     * 将领域模型转换为本地数据库实体
     * @param domain 领域设置模型
     * @return 数据库设置实体
     */
    fun mapDomainToEntity(domain: Setting): SettingEntity {
        return SettingEntity(
            key = domain.key,
            value = domain.value,
            type = domain.type,
            description = domain.description
        )
    }

    /**
     * 创建字符串类型的设置实体
     * @param key 设置键
     * @param value 设置值
     * @param description 可选的描述
     * @return 设置实体
     */
    fun createStringSettingEntity(
        key: String,
        value: String,
        description: String? = null
    ): SettingEntity {
        return SettingEntity(
            key = key,
            value = value,
            type = "string",
            description = description
        )
    }

    /**
     * 创建布尔类型的设置实体
     * @param key 设置键
     * @param value 设置值
     * @param description 可选的描述
     * @return 设置实体
     */
    fun createBooleanSettingEntity(
        key: String,
        value: Boolean,
        description: String? = null
    ): SettingEntity {
        return SettingEntity(
            key = key,
            value = value.toString(),
            type = "boolean",
            description = description
        )
    }

    /**
     * 创建整数类型的设置实体
     * @param key 设置键
     * @param value 设置值
     * @param description 可选的描述
     * @return 设置实体
     */
    fun createIntSettingEntity(
        key: String,
        value: Int,
        description: String? = null
    ): SettingEntity {
        return SettingEntity(
            key = key,
            value = value.toString(),
            type = "int",
            description = description
        )
    }
}