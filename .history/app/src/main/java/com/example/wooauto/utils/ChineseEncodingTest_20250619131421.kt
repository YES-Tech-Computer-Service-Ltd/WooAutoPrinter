package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.data.printer.BluetoothPrinterManager
import com.example.wooauto.domain.models.PrinterConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 中文编码测试工具类
 * 用于测试打印机的中文显示功能
 */
class ChineseEncodingTest(
    private val printerManager: BluetoothPrinterManager,
    private val scope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "ChineseEncodingTest"
    }
    
    /**
     * 运行所有测试
     * @param config 打印机配置
     */
    fun runAllTests(config: PrinterConfig) {
        Log.d(TAG, "开始运行所有中文编码测试")
        
        scope.launch(Dispatchers.IO) {
            try {
                // 1. 最简单连接测试
                Log.d(TAG, "=== 测试1: 最简单连接测试 ===")
                val test1Result = printerManager.testMinimalConnection(config)
                Log.d(TAG, "最简单连接测试结果: ${if (test1Result) "成功" else "失败"}")
                
                Thread.sleep(2000)
                
                // 2. 绕过所有处理的测试
                Log.d(TAG, "=== 测试2: 绕过所有处理的测试 ===")
                val test2Result = printerManager.testBypassAllProcessing(config)
                Log.d(TAG, "绕过所有处理的测试结果: ${if (test2Result) "成功" else "失败"}")
                
                Thread.sleep(2000)
                
                // 3. 基本连接测试
                Log.d(TAG, "=== 测试3: 基本连接测试 ===")
                val test3Result = printerManager.testBasicConnection(config)
                Log.d(TAG, "基本连接测试结果: ${if (test3Result) "成功" else "失败"}")
                
                Thread.sleep(2000)
                
                // 4. 原始库测试
                Log.d(TAG, "=== 测试4: 原始库测试 ===")
                val test4Result = printerManager.testWithOriginalLibrary(config)
                Log.d(TAG, "原始库测试结果: ${if (test4Result) "成功" else "失败"}")
                
                Thread.sleep(2000)
                
                // 5. GB18030编码测试
                Log.d(TAG, "=== 测试5: GB18030编码测试 ===")
                val test5Result = printerManager.testGB18030Encoding(config)
                Log.d(TAG, "GB18030编码测试结果: ${if (test5Result) "成功" else "失败"}")
                
                Thread.sleep(2000)
                
                // 6. 简单中文测试
                Log.d(TAG, "=== 测试6: 简单中文测试 ===")
                val test6Result = printerManager.testSimpleChinese(config)
                Log.d(TAG, "简单中文测试结果: ${if (test6Result) "成功" else "失败"}")
                
                Log.d(TAG, "所有测试完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "测试过程中发生异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 运行单个测试
     * @param config 打印机配置
     * @param testType 测试类型
     */
    fun runSingleTest(config: PrinterConfig, testType: String) {
        Log.d(TAG, "开始运行单个测试: $testType")
        
        scope.launch(Dispatchers.IO) {
            try {
                val result = when (testType) {
                    "minimal" -> printerManager.testMinimalConnection(config)
                    "bypass" -> printerManager.testBypassAllProcessing(config)
                    "basic" -> printerManager.testBasicConnection(config)
                    "original" -> printerManager.testWithOriginalLibrary(config)
                    "gb18030" -> printerManager.testGB18030Encoding(config)
                    "simple" -> printerManager.testSimpleChinese(config)
                    else -> {
                        Log.e(TAG, "未知的测试类型: $testType")
                        false
                    }
                }
                
                Log.d(TAG, "测试 $testType 结果: ${if (result) "成功" else "失败"}")
                
            } catch (e: Exception) {
                Log.e(TAG, "测试 $testType 异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 调试编码转换
     * @param text 要调试的文本
     */
    fun debugEncoding(text: String) {
        Log.d(TAG, "开始调试编码转换")
        printerManager.debugEncodingConversion(text)
    }
} 