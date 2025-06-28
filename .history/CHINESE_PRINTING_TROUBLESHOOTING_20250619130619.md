# 中文打印故障排除指南

## 问题描述
打印订单时，中文字符无法正确显示，显示为乱码或空白。

## 可能的原因

### 1. 编码问题
- 打印机不支持GB18030/GBK编码
- 编码转换失败
- 打印机固件不支持中文字符集

### 2. 打印库问题
- EscPosPrinter库内部使用UTF-8编码
- 绕过了我们自定义的编码转换

### 3. 打印机硬件问题
- 打印机字库不支持中文
- 打印机内存不足

## 解决方案

### 方案1：使用新的直接发送方法（已实现）

我们已经修改了打印流程，现在使用`sendFormattedTextDirectly`方法直接发送GB18030编码的数据到打印机，绕过了EscPosPrinter库的内部编码处理。

### 方案2：测试和诊断

#### 1. 运行简单中文测试
```kotlin
// 在BluetoothPrinterManager中调用
val success = printerManager.testSimpleChinese(config)
```

#### 2. 运行完整GB18030测试
```kotlin
// 在BluetoothPrinterManager中调用
val success = printerManager.testGB18030Encoding(config)
```

#### 3. 调试编码转换
```kotlin
// 调试特定文本的编码转换
printerManager.debugEncodingConversion("你好世界")
```

### 方案3：检查日志

在Logcat中搜索以下标签查看详细日志：
- `BluetoothPrinterManager` - 查看打印流程和编码转换
- `ChineseEncodingTest` - 查看编码测试结果

### 方案4：打印机设置检查

1. **检查打印机编码设置**
   - 确保打印机设置为GB18030或GBK编码
   - 某些打印机需要在打印机菜单中手动设置编码

2. **检查打印机字库**
   - 确保打印机安装了中文字库
   - 某些打印机需要下载中文字库

3. **检查打印机内存**
   - 确保打印机有足够内存处理中文字符

## 测试步骤

### 步骤1：基础测试
```kotlin
// 测试简单的中文文本
printerManager.testSimpleChinese(config)
```

### 步骤2：编码调试
```kotlin
// 调试编码转换过程
printerManager.debugEncodingConversion("测试中文")
```

### 步骤3：完整测试
```kotlin
// 运行完整的中文打印测试
printerManager.testGB18030Encoding(config)
```

### 步骤4：订单打印测试
```kotlin
// 测试实际订单打印
val success = printerManager.printOrder(order, config)
```

## 常见问题

### Q1: 中文显示为方块或问号
**原因**: 打印机不支持该编码或字库缺失
**解决**: 
- 检查打印机编码设置
- 尝试不同的编码（GBK、UTF-8）
- 检查打印机字库

### Q2: 中文完全不显示
**原因**: 编码转换失败或发送失败
**解决**:
- 查看日志中的编码转换信息
- 检查打印机连接状态
- 尝试使用`debugEncodingConversion`方法

### Q3: 部分中文显示正常，部分显示乱码
**原因**: 混合编码或字符集不完整
**解决**:
- 确保所有文本使用相同编码
- 检查特殊字符的编码支持

## 日志分析

### 正常日志示例
```
BluetoothPrinterManager: 原始文本: '你好世界'
BluetoothPrinterManager: 包含中文字符: true
BluetoothPrinterManager: GB18030编码: 8 字节
BluetoothPrinterManager: 成功发送格式化文本，字节数: 15
```

### 错误日志示例
```
BluetoothPrinterManager: GB18030编码失败: Unsupported charset: GB18030
BluetoothPrinterManager: GBK编码也失败，使用UTF-8编码: Unsupported charset: GBK
BluetoothPrinterManager: 发送格式化文本失败: Connection reset
```

## 打印机兼容性

### 支持中文的打印机品牌
- **EPSON**: 通常支持GBK编码
- **STAR**: 支持多种编码，包括GB18030
- **Citizen**: 支持GBK和UTF-8
- **Bixolon**: 支持多种中文编码

### 编码支持优先级
1. **GB18030** - 最完整的中文字符集
2. **GBK** - 兼容性好，支持大部分常用汉字
3. **UTF-8** - 通用编码，但某些打印机可能不支持

## 联系支持

如果问题仍然存在，请提供以下信息：
1. 打印机品牌和型号
2. 完整的错误日志
3. 测试结果截图
4. 打印机固件版本（如果知道）

## 更新日志

### v1.1.0
- 修复了EscPosPrinter库绕过编码转换的问题
- 添加了`sendFormattedTextDirectly`方法
- 添加了编码调试功能
- 更新了所有测试方法使用新的发送方式 