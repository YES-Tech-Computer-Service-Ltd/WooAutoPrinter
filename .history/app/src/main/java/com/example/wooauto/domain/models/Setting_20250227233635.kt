package com.example.wooauto.domain.models

/**
 * 设置领域模型
 * 表示应用中的各种设置选项
 */
data class Setting(
    val key: String,
    val value: String,
    val type: String,
    val description: String?
)