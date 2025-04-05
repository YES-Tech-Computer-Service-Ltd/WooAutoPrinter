package com.example.wooauto.domain.models

/**
 * 声音设置数据类
 * 存储应用的声音相关设置
 */
data class SoundSettings(
    // 通知音量 (0-100)
    val notificationVolume: Int = 70,
    
    // 提示音类型
    val soundType: String = SOUND_TYPE_DEFAULT,
    
    // 是否启用声音
    val soundEnabled: Boolean = true
) {
    companion object {
        // 提示音类型常量 - 全部使用系统声音，兼容安卓7
        const val SOUND_TYPE_DEFAULT = "default"           // 系统默认通知音
        const val SOUND_TYPE_ALARM = "system_alarm"        // 系统闹钟声音 - 最响亮
        const val SOUND_TYPE_RINGTONE = "system_ringtone"  // 系统电话铃声 - 较响亮
        const val SOUND_TYPE_EVENT = "system_event"        // 系统事件声音
        const val SOUND_TYPE_EMAIL = "system_email"        // 系统邮件声音
        
        // 获取所有可用的提示音类型
        fun getAllSoundTypes(): List<String> {
            return listOf(
                SOUND_TYPE_DEFAULT,
                SOUND_TYPE_ALARM,
                SOUND_TYPE_RINGTONE,
                SOUND_TYPE_EVENT,
                SOUND_TYPE_EMAIL
            )
        }
        
        // 获取声音类型的显示名称
        fun getSoundTypeDisplayName(type: String): String {
            return when (type) {
                SOUND_TYPE_DEFAULT -> ""
                SOUND_TYPE_ALARM -> ""
                SOUND_TYPE_RINGTONE -> ""
                SOUND_TYPE_EVENT -> ""
                SOUND_TYPE_EMAIL -> ""
                else -> ""
            }
        }
    }
} 