package com.example.wooauto.domain.printer

/**
 * 打印机品牌信息
 * 定义了支持的打印机品牌和对应的命令语言
 */
enum class PrinterBrand(
    val displayName: String,
    val commandLanguage: String,
    val modelKeywords: String,
    listOf: List<String>
) {
    EPSON(
        "epson",
        "爱普生(EPSON)",
        "ESC/POS",
        listOf("epson", "爱普生")
    ),
    BIXOLON(
        "bixolon",
        "佳博(BIXOLON)",
        "BIXOLON",
        listOf("bixolon", "佳博", "srp", "stp")
    ),
    SPRT(
        "sprt",
        "思普瑞特(SPRT)",
        "ESC/POS",
        listOf("sprt", "思普瑞特")
    ),
    CITIZEN(
        "citizen",
        "西铁城(CITIZEN)",
        "ESC/POS",
        listOf("citizen", "西铁城", "ct")
    ),
    UNKNOWN(
        "unknown",
        "未知品牌",
        "ESC/POS", // 默认使用最常见的ESC/POS命令集
        listOf()
    );

    companion object {
        /**
         * 根据打印机名称识别品牌
         * @param printerName 打印机名称
         * @return 识别的打印机品牌
         */
        fun identifyBrand(printerName: String): PrinterBrand {
            val lowerName = printerName.lowercase()
            
            // 遍历所有品牌(除了UNKNOWN)
            for (brand in values()) {
                if (brand == UNKNOWN) continue
                
                // 检查品牌关键词
                for (keyword in brand.modelKeywords) {
                    if (lowerName.contains(keyword.lowercase())) {
                        return brand
                    }
                }
            }
            
            return UNKNOWN
        }
    }
} 