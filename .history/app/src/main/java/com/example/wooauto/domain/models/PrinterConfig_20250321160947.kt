package com.example.wooauto.domain.models

import com.example.wooauto.domain.printer.PrinterBrand
import java.io.Serializable
import java.util.UUID

/**
 * 打印机配置数据类
 * 存储打印机相关的所有配置信息
 */
data class PrinterConfig(
    // 打印机唯一标识
    val id: String = UUID.randomUUID().toString(),
    
    // 打印机名称
    val name: String,
    
    // 打印机地址（蓝牙MAC地址或IP地址）
    val address: String,
    
    // 打印机类型
    val type: String = PRINTER_TYPE_BLUETOOTH,
    
    // 纸张宽度（毫米）
    val paperWidth: Int = PAPER_WIDTH_57MM,
    
    // 打印机品牌
    val brand: PrinterBrand = PrinterBrand.UNKNOWN,
    
    // 是否为默认打印机
    val isDefault: Boolean = false,
    
    // 是否启用自动打印
    val isAutoPrint: Boolean = false,
    
    // 打印份数
    val printCopies: Int = 1,
    
    // 字体大小
    val fontSize: Int = FONT_SIZE_NORMAL,
    
    // 打印浓度
    val printDensity: Int = PRINT_DENSITY_NORMAL,
    
    // 打印速度
    val printSpeed: Int = PRINT_SPEED_NORMAL,
    
    // 是否自动切纸
    val autoCut: Boolean = false,
    
    // 是否打印店铺信息
    val printStoreInfo: Boolean = true,
    
    // 是否打印客户信息
    val printCustomerInfo: Boolean = true,
    
    // 是否打印商品详情
    val printItemDetails: Boolean = true,
    
    // 是否打印订单备注
    val printOrderNotes: Boolean = true,
    
    // 是否打印页脚
    val printFooter: Boolean = true,
    
    // 自定义页眉
    val customHeader: String = "",
    
    // 自定义页脚
    val customFooter: String = ""
) : Serializable {
    companion object {
        // 打印机类型
        const val PRINTER_TYPE_BLUETOOTH = 0
        const val PRINTER_TYPE_WIFI = 1
        const val PRINTER_TYPE_USB = 2
        
        // 纸张宽度
        const val PAPER_WIDTH_57MM = 58 // 58mm 打印机，有效打印宽度为50mm
        const val PAPER_WIDTH_80MM = 80 // 80mm 打印机，有效打印宽度为72mm
        
        // 字体大小
        const val FONT_SIZE_SMALL = 0
        const val FONT_SIZE_NORMAL = 1
        const val FONT_SIZE_LARGE = 2
        
        // 打印浓度
        const val PRINT_DENSITY_LIGHT = 0
        const val PRINT_DENSITY_NORMAL = 1
        const val PRINT_DENSITY_DARK = 2
        
        // 打印速度
        const val PRINT_SPEED_SLOW = 0
        const val PRINT_SPEED_NORMAL = 1
        const val PRINT_SPEED_FAST = 2
        
        // 默认打印机配置键
        const val KEY_DEFAULT_PRINTER = "default_printer_config"
        
        // 打印机连接状态键
        const val KEY_PRINTER_CONNECTED = "printer_connected"
    }
    
    /**
     * 判断打印机配置是否有效
     */
    fun isValid(): Boolean {
        return when (type) {
            PRINTER_TYPE_BLUETOOTH -> address.isNotBlank()
            PRINTER_TYPE_WIFI -> address.isNotBlank()
            PRINTER_TYPE_USB -> address.isNotBlank()
            else -> false
        }
    }
    
    /**
     * 获取打印机显示名称
     */
    fun getDisplayName(): String {
        return if (name.isNotEmpty()) name else address
    }
}