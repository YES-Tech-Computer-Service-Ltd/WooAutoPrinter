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
        // 提示音类型常量
        const val SOUND_TYPE_DEFAULT = "default" // 默认 - 使用系统通知音效
        const val SOUND_TYPE_BELL = "bell"
        const val SOUND_TYPE_CASH = "cash_register"
        const val SOUND_TYPE_ALERT = "alert"
        const val SOUND_TYPE_CHIME = "chime"
        
        // 系统铃声类型 - 使用安卓内置的更响亮的铃声
        const val SOUND_TYPE_SYSTEM_ALARM = "system_alarm"     // 系统闹钟声音 - 非常响亮
        const val SOUND_TYPE_SYSTEM_RINGTONE = "system_ringtone" // 系统电话铃声 - 比通知声更响亮
        
        // 获取所有可用的提示音类型
        fun getAllSoundTypes(): List<String> {
            return listOf(
                SOUND_TYPE_DEFAULT,
                SOUND_TYPE_BELL,
                SOUND_TYPE_CASH,
                SOUND_TYPE_ALERT,
                SOUND_TYPE_CHIME,
                SOUND_TYPE_SYSTEM_ALARM,
                SOUND_TYPE_SYSTEM_RINGTONE
            )
        }
        
        // 获取声音类型的显示名称 - 返回空字符串，让调用者决定如何展示
        fun getSoundTypeDisplayName(type: String): String {
            return when (type) {
                SOUND_TYPE_DEFAULT -> ""
                SOUND_TYPE_BELL -> ""
                SOUND_TYPE_CASH -> ""
                SOUND_TYPE_ALERT -> ""
                SOUND_TYPE_CHIME -> ""
                SOUND_TYPE_SYSTEM_ALARM -> ""
                SOUND_TYPE_SYSTEM_RINGTONE -> ""
                else -> ""
            }
        }
    }
} 