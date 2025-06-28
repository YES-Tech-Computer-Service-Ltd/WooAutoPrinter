package com.example.wooauto.utils

import android.util.Log

/**
 * 中文字符编码测试工具
 * 用于验证中文字符编码功能是否正常工作
 */
object ChineseEncodingTest {
    private const val TAG = "ChineseEncodingTest"
    
    /**
     * 测试中文字符检测功能
     */
    fun testChineseCharacterDetection() {
        val testCases = listOf(
            "Hello World" to false,
            "你好世界" to true,
            "Hello 世界" to true,
            "Test123" to false,
            "测试文本" to true,
            "Mixed 中英文 Text" to true,
            "" to false
        )
        
        Log.d(TAG, "开始测试中文字符检测功能")
        
        testCases.forEach { (text, expected) ->
            val hasChinese = text.any { char ->
                char.code in 0x4E00..0x9FFF || // 基本汉字
                char.code in 0x3400..0x4DBF || // 扩展A区
                char.code in 0x20000..0x2A6DF || // 扩展B区
                char.code in 0x2A700..0x2B73F || // 扩展C区
                char.code in 0x2B740..0x2B81F || // 扩展D区
                char.code in 0x2B820..0x2CEAF || // 扩展E区
                char.code in 0xF900..0xFAFF || // 兼容汉字
                char.code in 0x2F800..0x2FA1F // 兼容扩展
            }
            
            val testResult = if (hasChinese == expected) "✓" else "✗"
            Log.d(TAG, "$testResult 文本: '$text' - 检测结果: $hasChinese, 期望: $expected")
        }
        
        Log.d(TAG, "中文字符检测功能测试完成")
    }
    
    /**
     * 测试GB18030编码转换功能
     */
    fun testGB18030Encoding() {
        val testCases = listOf(
            "Hello World",
            "你好世界",
            "Mixed 中英文 Text",
            "测试文本 Test Text",
            "Special chars: !@#$%^&*()",
            "Numbers: 1234567890",
            "Emojis: 😀🎉🎊",
            "中文标点：，。！？；：""''（）【】"
        )
        
        Log.d(TAG, "开始测试GB18030编码转换功能")
        
        testCases.forEach { text ->
            try {
                // 测试GB18030编码
                val gb18030Bytes = text.toByteArray(charset("GB18030"))
                Log.d(TAG, "✓ GB18030编码: '$text' - 字节数: ${gb18030Bytes.size}")
                
                // 测试GBK编码（作为对比）
                val gbkBytes = text.toByteArray(charset("GBK"))
                Log.d(TAG, "✓ GBK编码: '$text' - 字节数: ${gbkBytes.size}")
                
                // 测试UTF-8编码（作为对比）
                val utf8Bytes = text.toByteArray(Charsets.UTF_8)
                Log.d(TAG, "✓ UTF-8编码: '$text' - 字节数: ${utf8Bytes.size}")
                
            } catch (e: Exception) {
                Log.e(TAG, "✗ 编码转换失败: '$text' - ${e.message}")
            }
        }
        
        Log.d(TAG, "GB18030编码转换功能测试完成")
    }
    
    /**
     * 测试字符编码转换功能
     */
    fun testEncodingConversion() {
        val testCases = listOf(
            "Hello World",
            "你好世界",
            "Mixed 中英文 Text",
            "测试文本 Test Text",
            "Special chars: !@#$%^&*()",
            "Numbers: 1234567890",
            "Emojis: 😀🎉🎊"
        )
        
        Log.d(TAG, "开始测试字符编码转换功能")
        
        testCases.forEach { text ->
            try {
                // 使用新的编码转换方法
                val result = convertTextToGB18030(text)
                Log.d(TAG, "✓ 文本: '$text' - 编码转换成功，字节数: ${result.size}")
            } catch (e: Exception) {
                Log.e(TAG, "✗ 文本: '$text' - 编码转换失败: ${e.message}")
            }
        }
        
        Log.d(TAG, "字符编码转换功能测试完成")
    }
    
    /**
     * 测试多行文本格式化功能
     */
    fun testMultilineTextFormatting() {
        val testTexts = listOf(
            "这是一个很长的中文文本，需要测试自动换行功能是否正常工作。This is a long text that needs to test automatic line wrapping.",
            "Short text",
            "中英文混合文本 Mixed Chinese and English Text",
            "Multiple\nLines\nText\n多行\n文本"
        )
        
        Log.d(TAG, "开始测试多行文本格式化功能")
        
        testTexts.forEach { text ->
            try {
                val result = ThermalPrinterFormatter.formatMultilineText(text, 80)
                Log.d(TAG, "✓ 文本格式化成功: '$text'")
                Log.d(TAG, "格式化结果:\n$result")
            } catch (e: Exception) {
                Log.e(TAG, "✗ 文本格式化失败: '$text' - ${e.message}")
            }
        }
        
        Log.d(TAG, "多行文本格式化功能测试完成")
    }
    
    /**
     * 将文本转换为GB18030编码（模拟BluetoothPrinterManager中的方法）
     */
    private fun convertTextToGB18030(text: String): ByteArray {
        return try {
            // 优先尝试GB18030编码
            text.toByteArray(charset("GB18030"))
        } catch (e: Exception) {
            Log.w(TAG, "GB18030编码失败，尝试GBK编码: ${e.message}")
            try {
                // 如果GB18030失败，回退到GBK编码
                text.toByteArray(charset("GBK"))
            } catch (e2: Exception) {
                Log.w(TAG, "GBK编码也失败，使用UTF-8编码: ${e2.message}")
                // 最后回退到UTF-8编码
                text.toByteArray(Charsets.UTF_8)
            }
        }
    }
    
    /**
     * 运行所有测试
     */
    fun runAllTests() {
        Log.d(TAG, "========== 开始中文字符编码功能测试 ==========")
        
        testChineseCharacterDetection()
        testGB18030Encoding()
        testEncodingConversion()
        testMultilineTextFormatting()
        
        Log.d(TAG, "========== 中文字符编码功能测试完成 ==========")
    }
} 