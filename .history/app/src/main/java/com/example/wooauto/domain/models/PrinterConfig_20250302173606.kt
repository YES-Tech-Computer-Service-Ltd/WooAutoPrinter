package com.example.wooauto.domain.models

import java.io.Serializable

/**
 * 打印机配置数据类
 * 存储打印机相关的所有配置信息
 */
data class PrinterConfig(
    val id: String = "",                   // 打印机唯一标识符
    val name: String = "",                 // 打印机自定义名称
    val type: String = PRINTER_TYPE_BLUETOOTH, // 打印机类型
    val address: String = "",              // 蓝牙MAC地址或IP地址
    val port: Int = 9100,                  // 网络打印机端口号
    val model: String = "",                // 打印机型号
    val isDefault: Boolean = false,        // 是否为默认打印机
    val isBackup: Boolean = false,         // 是否为备用打印机
    val isAutoPrint: Boolean = false,      // 是否自动打印新订单
    val printCopies: Int = 1,              // 打印份数
    val templateId: String = TEMPLATE_STANDARD, // 打印模板ID
    val paperWidth: Int = PAPER_WIDTH_57MM // 纸张宽度
) : Serializable {
    companion object {
        // 打印机类型常量
        const val PRINTER_TYPE_BLUETOOTH = "bluetooth"
        const val PRINTER_TYPE_NETWORK = "network"
        
        // 打印模板常量
        const val TEMPLATE_SIMPLE = "simple"
        const val TEMPLATE_STANDARD = "standard"
        const val TEMPLATE_DETAILED = "detailed"
        
        // 纸张宽度常量
        const val PAPER_WIDTH_57MM = 57
        const val PAPER_WIDTH_80MM = 80
    }
    
    /**
     * 判断打印机配置是否有效
     */
    fun isValid(): Boolean {
        return when (type) {
            PRINTER_TYPE_BLUETOOTH -> address.isNotBlank()
            PRINTER_TYPE_NETWORK -> address.isNotBlank() && port > 0
            else -> false
        }
    }
    
    /**
     * 获取打印机显示名称
     */
    fun getDisplayName(): String {
        return if (name.isNotBlank()) name else address
    }
}