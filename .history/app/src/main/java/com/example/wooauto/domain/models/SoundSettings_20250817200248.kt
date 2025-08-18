package com.example.wooauto.domain.models

/**
 * 声音设置数据类
 * 存储应用的声音相关设置
 */
data class SoundSettings(
    // 通知音量 (0-1000)
    val notificationVolume: Int = 50,   // 默认轻级别
    
    // 提示音类型
    val soundType: String = SOUND_TYPE_DEFAULT,
    
    // 是否启用声音
    val soundEnabled: Boolean = true,
    
    // 自定义音效文件路径
    val customSoundUri: String = "",
    // 接单持续提示：开启后新订单到达会持续响铃，直到用户点击接受订单
    val keepRingingUntilAccept: Boolean = false
) {
    companion object {
        // 提示音类型常量 - 全部使用系统声音，兼容安卓7
        const val SOUND_TYPE_DEFAULT = "default"           // 系统默认通知音
        const val SOUND_TYPE_ALARM = "system_alarm"        // 系统闹钟声音 - 最响亮
        const val SOUND_TYPE_RINGTONE = "system_ringtone"  // 系统电话铃声 - 较响亮
        const val SOUND_TYPE_EVENT = "system_event"        // 系统事件声音
        const val SOUND_TYPE_EMAIL = "system_email"        // 系统邮件声音
        const val SOUND_TYPE_CUSTOM = "custom"             // 自定义音频文件
        
        // 音量级别定义 - 7个级别对应不同的音量百分比
        const val VOLUME_LEVEL_MUTE = 0      // 静音 (0%)
        const val VOLUME_LEVEL_VERY_SOFT = 25   // 很轻 (25%)
        const val VOLUME_LEVEL_SOFT = 50        // 轻 (50%)
        const val VOLUME_LEVEL_MEDIUM = 100     // 中等 (100%)
        const val VOLUME_LEVEL_LOUD = 300       // 响亮 (300%)
        const val VOLUME_LEVEL_VERY_LOUD = 700  // 很响 (700%)
        const val VOLUME_LEVEL_EXTREME = 1000   // 极响 (1000%)
        
        // 获取所有可用的提示音类型
        fun getAllSoundTypes(): List<String> {
            return listOf(
                SOUND_TYPE_DEFAULT,
                SOUND_TYPE_ALARM,
                SOUND_TYPE_RINGTONE,
                SOUND_TYPE_EVENT,
                SOUND_TYPE_EMAIL,
                SOUND_TYPE_CUSTOM
            )
        }
        
        // 获取所有音量级别
        fun getAllVolumeLevels(): List<Int> {
            return listOf(
                VOLUME_LEVEL_MUTE,
                VOLUME_LEVEL_VERY_SOFT,
                VOLUME_LEVEL_SOFT,
                VOLUME_LEVEL_MEDIUM,
                VOLUME_LEVEL_LOUD,
                VOLUME_LEVEL_VERY_LOUD,
                VOLUME_LEVEL_EXTREME
            )
        }
        
        // 获取音量级别的显示名称
        fun getVolumeLevelDisplayName(level: Int): String {
            return when (level) {
                VOLUME_LEVEL_MUTE -> "静音"
                VOLUME_LEVEL_VERY_SOFT -> "很轻"
                VOLUME_LEVEL_SOFT -> "轻"
                VOLUME_LEVEL_MEDIUM -> "中等"
                VOLUME_LEVEL_LOUD -> "响亮"
                VOLUME_LEVEL_VERY_LOUD -> "很响"
                VOLUME_LEVEL_EXTREME -> "极响"
                else -> "中等"
            }
        }
        
        // 根据百分比获取最接近的音量级别
        fun getClosestVolumeLevel(percentage: Int): Int {
            val levels = getAllVolumeLevels()
            return levels.minByOrNull { kotlin.math.abs(it - percentage) } ?: VOLUME_LEVEL_MEDIUM
        }
        
        // 获取声音类型的显示名称
        fun getSoundTypeDisplayName(type: String): String {
            return when (type) {
                SOUND_TYPE_DEFAULT -> "系统默认"
                SOUND_TYPE_ALARM -> "系统闹钟"
                SOUND_TYPE_RINGTONE -> "系统铃声"
                SOUND_TYPE_EVENT -> "系统事件"
                SOUND_TYPE_EMAIL -> "系统邮件"
                SOUND_TYPE_CUSTOM -> "自定义音频"
                else -> "系统默认"
            }
        }
    }
} 