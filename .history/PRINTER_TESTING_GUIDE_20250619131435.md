# 打印机测试指南

## 问题诊断

如果修改后什么都打印不出来，请按以下步骤进行测试：

## 测试步骤

### 1. 最简单连接测试
```kotlin
// 在ViewModel或Activity中调用
printerManager.testMinimalConnection(printerConfig)
```
这个测试直接发送ASCII文本，不进行任何编码转换或格式化处理。

### 2. 绕过所有处理的测试
```kotlin
printerManager.testBypassAllProcessing(printerConfig)
```
这个测试直接发送UTF-8编码的原始内容，绕过所有复杂的处理逻辑。

### 3. 基本连接测试
```kotlin
printerManager.testBasicConnection(printerConfig)
```
这个测试使用我们的GB18030编码转换，但绕过格式化处理。

### 4. 原始库测试
```kotlin
printerManager.testWithOriginalLibrary(printerConfig)
```
这个测试使用原始的EscPosPrinter库，用于对比测试。

### 5. GB18030编码测试
```kotlin
printerManager.testGB18030Encoding(printerConfig)
```
这个测试使用我们的完整格式化处理流程。

### 6. 简单中文测试
```kotlin
printerManager.testSimpleChinese(printerConfig)
```
这个测试专门针对中文显示。

## 使用测试工具类

```kotlin
// 创建测试工具实例
val testTool = ChineseEncodingTest(printerManager, viewModelScope)

// 运行所有测试
testTool.runAllTests(printerConfig)

// 运行单个测试
testTool.runSingleTest(printerConfig, "minimal")
testTool.runSingleTest(printerConfig, "bypass")
testTool.runSingleTest(printerConfig, "basic")
testTool.runSingleTest(printerConfig, "original")
testTool.runSingleTest(printerConfig, "gb18030")
testTool.runSingleTest(printerConfig, "simple")

// 调试编码转换
testTool.debugEncoding("你好世界")
```

## 可能的问题和解决方案

### 问题1: 所有测试都失败
- **原因**: 打印机连接问题
- **解决**: 检查蓝牙连接状态，重新连接打印机

### 问题2: 最简单测试成功，其他失败
- **原因**: 编码转换问题
- **解决**: 检查GB18030编码支持，可能需要使用UTF-8

### 问题3: 原始库测试成功，自定义方法失败
- **原因**: 我们的格式化处理有问题
- **解决**: 检查`processFormattedLine`方法，可能需要简化处理逻辑

### 问题4: 英文正常，中文乱码
- **原因**: 编码不匹配
- **解决**: 尝试不同的编码方式（UTF-8、GBK、GB18030）

## 日志分析

查看Logcat中的日志，重点关注：
- `BluetoothPrinterManager` 标签的日志
- `ChineseEncodingTest` 标签的日志
- 错误信息和异常堆栈

## 建议的测试顺序

1. 先运行 `testMinimalConnection` - 确认基本连接
2. 再运行 `testBypassAllProcessing` - 确认编码问题
3. 然后运行 `testWithOriginalLibrary` - 对比原始库
4. 最后运行其他测试 - 逐步定位问题

## 快速修复建议

如果所有测试都失败，可以临时回退到使用原始库：

```kotlin
// 在printContent方法中，临时使用原始库
private suspend fun printContent(content: String, config: PrinterConfig): Boolean {
    return try {
        if (!ensurePrinterConnected(config)) {
            return false
        }
        
        // 临时使用原始库
        currentPrinter?.printFormattedText(content)
        return true
        
    } catch (e: Exception) {
        Log.e(TAG, "打印失败: ${e.message}", e)
        return false
    }
}
``` 