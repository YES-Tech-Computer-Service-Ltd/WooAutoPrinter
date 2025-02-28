package com.example.wooauto.domain.models

/**
 * 自动化任务领域模型
 */
data class AutomationTask(
    val id: String,
    val name: String,
    val description: String,
    val isActive: Boolean
) 