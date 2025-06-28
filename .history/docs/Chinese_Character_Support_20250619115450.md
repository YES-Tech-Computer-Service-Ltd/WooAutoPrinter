# 中文字符编码支持

## 概述

本项目已集成中文字符编码支持，确保打印机能够正确打印包含中文字符的小票。系统会自动检测文本中的中文字符，并使用适当的编码格式（GBK或UTF-8）发送给打印机。

## 功能特性

### 1. 自动字符编码检测
- 自动检测文本是否包含中文字符
- 支持基本汉字、扩展汉字、兼容汉字等Unicode范围
- 智能选择编码格式：GBK（中文）或UTF-8（英文）

### 2. 智能文本换行
- 考虑中文字符宽度（中文字符占用2个英文字符宽度）
- 自动调整每行字符数
- 支持中英文混合文本的智能换行

### 3. 完整的编码支持
- 支持商品名称中的中文字符
- 支持商品选项中的中文字符
- 支持订单备注中的中文字符
- 支持店铺信息中的中文字符

## 技术实现

### 核心类和方法

#### 1. BluetoothPrinterManager
```kotlin
// 中文字符检测
private fun containsChineseCharacters(text: String): Boolean

// 字符编码转换
private fun convertTextToPrinterEncoding(text: String): ByteArray

// 支持中文的格式化文本处理
private fun processFormattedLineWithChineseSupport(line: String, outputStream: ByteArrayOutputStream)
```

#### 2. ThermalPrinterFormatter
```kotlin
// 智能字符数计算（考虑中文宽度）
fun getCharsPerLine(paperWidth: Int, text: String = ""): Int

// 支持中文的多行文本格式化
fun formatMultilineTextWithChineseSupport(text: String, paperWidth: Int, alignment: Char = 'L'): String

// 字符编码转换
fun convertTextToPrinterEncoding(text: String): ByteArray
```

### 编码检测范围

系统检测以下Unicode范围的中文字符：
- 基本汉字：0x4E00-0x9FFF
- 扩展A区：0x3400-0x4DBF
- 扩展B区：0x20000-0x2A6DF
- 扩展C区：0x2A700-0x2B73F
- 扩展D区：0x2B740-0x2B81F
- 扩展E区：0x2B820-0x2CEAF
- 兼容汉字：0xF900-0xFAFF
- 兼容扩展：0x2F800-0x2FA1F

## 使用示例

### 1. 基本使用
```kotlin
// 系统会自动检测并处理中文字符
val order = Order(
    items = listOf(
        OrderItem(name = "咖喱鸡 Chicken Curry", ...),
        OrderItem(name = "红烧牛肉 Beef Stew", ...)
    ),
    notes = "请少放辣椒，谢谢！\nPlease less spicy, thank you!"
)

// 打印时会自动使用GBK编码
printerManager.printOrder(order, config)
```

### 2. 测试中文字符编码
```kotlin
// 运行编码测试
ChineseEncodingTest.runAllTests()
```

## 测试数据

### 测试订单包含的中文字符
- 商品名称：咖喱鸡、红烧牛肉、宫保鸡丁、酸辣汤等
- 商品选项：辣度、配菜、温度、冰块等
- 订单备注：包含中英文混合的测试说明

### 测试内容示例
```
58MM打印机测试订单 - 所有功能正常
58MM printer test order - all functions normal

商品：咖喱鸡 Chicken Curry
选项：辣度 Spice Level - 中辣 Medium
      米饭 Rice - 茉莉香米 Jasmine
```

## 兼容性

### 支持的打印机
- 支持GBK编码的热敏打印机
- ESC/POS指令集打印机
- 常见品牌：EPSON、STAR、BIXOLON、SPRT、CITIZEN等

### 纸张宽度适配
- 58mm打印机：自动调整字符数以适应中文字符
- 80mm打印机：充分利用宽度显示中文字符
- 智能换行：根据字符类型自动调整换行位置

## 故障排除

### 1. 中文字符显示乱码
- 确认打印机支持GBK编码
- 检查打印机字体是否包含中文字符
- 验证编码转换是否正常工作

### 2. 文本换行异常
- 检查字符宽度计算是否正确
- 验证中文字符检测是否准确
- 确认打印机字符数设置是否合适

### 3. 编码转换失败
- 检查系统是否支持GBK编码
- 验证字符编码库是否正确加载
- 查看日志中的编码转换错误信息

## 日志调试

启用详细日志以调试编码问题：
```kotlin
// 查看编码检测日志
Log.d(TAG, "检测到中文字符，使用GBK编码: $text")

// 查看编码转换日志
Log.d(TAG, "字符编码转换失败，使用UTF-8作为后备: ${e.message}")

// 查看格式化日志
Log.d(TAG, "智能文本换行，考虑中文字符宽度")
```

## 更新日志

### v1.0.0
- 初始中文字符编码支持
- 自动编码检测和转换
- 智能文本换行
- 完整的测试数据

### 未来计划
- 支持更多中文字体
- 优化字符宽度计算
- 增加更多编码格式支持
- 改进错误处理机制 