# WooAutoPrinter 项目结构

## 项目简介
WooAutoPrinter 是一个基于 Kotlin 和 Jetpack Compose 开发的 Android 应用，主要用于自动处理和打印 WooCommerce 订单。项目采用了现代 Android 开发架构和技术栈。

## 技术栈
- **语言**：Kotlin
- **UI框架**：Jetpack Compose
- **架构模式**：MVVM + Clean Architecture
- **依赖注入**：Hilt
- **数据存储**：Room Database
- **网络请求**：Retrofit
- **并发处理**：Kotlin Coroutines
- **后台任务**：WorkManager
- **导航**：Navigation Compose
- **打印功能**：ESCPOS-ThermalPrinter-Android
- **二维码扫描**：ZXing

## 项目结构
项目遵循清洁架构(Clean Architecture)原则，分为以下几个主要层次：

### 1. 应用层(Application Layer)
- `WooAutoApplication.kt` - 应用入口点，负责初始化应用级组件如 Hilt

### 2. 表示层(Presentation Layer)
- `/presentation` - 包含所有UI相关代码
  - `/components` - 可复用的 Compose UI 组件
  - `/navigation` - 应用导航设置
  - `/screens` - 应用中的各个屏幕
  - `/theme` - 应用主题定义
  - `MainActivity.kt` - 主活动入口
  - `WooAutoApp.kt` - 主Compose应用入口点

### 3. 领域层(Domain Layer)
- `/domain` - 包含业务逻辑和实体
  - `/models` - 业务实体
  - `/repositories` - 仓库接口定义
  - `/usecases` - 用例定义，实现业务逻辑
  - `/printer` - 打印相关的领域逻辑
  - `/templates` - 打印模板领域逻辑

### 4. 数据层(Data Layer)
- `/data` - 数据相关代码
  - `/local` - 本地数据源（Room数据库）
  - `/remote` - 远程数据源（网络API）
  - `/repositories` - 仓库实现
  - `/mappers` - 数据模型映射器
  - `/printer` - 打印机相关数据处理
  - `/templates` - 打印模板数据处理
  - `/di` - 数据层依赖注入

### 5. 工具类
- `/utils` - 通用工具类和扩展函数

### 6. 依赖注入
- `/di` - Hilt 依赖注入模块

### 7. 导航
- `/navigation` - 应用导航组件

### 8. 服务
- `/service` - 后台服务

## 项目配置
- `app/build.gradle.kts` - 应用级构建配置
- `build.gradle.kts` - 项目级构建配置
- `settings.gradle.kts` - Gradle设置

## 资源文件
- `app/src/main/res` - 包含应用资源（布局、字符串、样式等）
- `app/src/main/assets` - 包含应用静态资源

## 权限
应用需要以下权限：
- 网络访问
- 蓝牙访问（用于打印机连接）
- 存储访问（可选，取决于功能）

## 构建与部署
项目使用 Gradle 构建工具，可以通过 Android Studio 或命令行方式构建和部署。 