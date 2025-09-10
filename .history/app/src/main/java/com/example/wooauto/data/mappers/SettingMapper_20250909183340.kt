package com.example.wooauto.data.mappers

import com.example.wooauto.data.local.entities.SettingEntity
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.models.Setting
import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * 设置数据映射器
 * 负责在不同层的设置模型之间进行转换
 */
object SettingMapper {

    private val gson = Gson()

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

    /**
     * 将打印机配置转换为JSON字符串
     * @param config 打印机配置
     * @return JSON字符串
     */
    fun printerConfigToJson(config: PrinterConfig): String {
        return gson.toJson(config)
    }
    
    /**
     * 将JSON字符串转换为打印机配置
     * @param json JSON字符串
     * @return 打印机配置
     */
    fun jsonToPrinterConfig(json: String): PrinterConfig {
        return try {
            gson.fromJson(json, PrinterConfig::class.java)
        } catch (e: Exception) {
            try {
                val jsonObj = JsonParser.parseString(json).asJsonObject
                // 将旧版本可能包含的品牌值统一替换为 UNKNOWN，避免枚举不匹配
                jsonObj.addProperty("brand", "UNKNOWN")
                gson.fromJson(jsonObj, PrinterConfig::class.java)
            } catch (e2: Exception) {
                // 二次解析仍失败，则抛出原异常以便上层处理
                throw e
            }
        }
    }
    
    /**
     * 创建打印机配置的设置实体
     * @param key 设置键
     * @param config 打印机配置
     * @return 设置实体
     */
    fun createPrinterConfigSettingEntity(key: String, config: PrinterConfig): SettingEntity {
        return SettingEntity(
            key = key,
            value = printerConfigToJson(config),
            type = "printer_config",
            description = "打印机配置: ${config.getDisplayName()}"
        )
    }
}