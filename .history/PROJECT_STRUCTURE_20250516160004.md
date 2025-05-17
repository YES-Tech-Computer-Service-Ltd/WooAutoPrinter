# WooAutoPrinter Project Structure

## Project Overview
WooAutoPrinter is an Android application developed with Kotlin and Jetpack Compose, primarily designed for automatically processing and printing WooCommerce orders. The project utilizes modern Android development architecture and technology stack.

## Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture Pattern**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt
- **Data Storage**: Room Database
- **Network Requests**: Retrofit
- **Concurrency**: Kotlin Coroutines
- **Background Tasks**: WorkManager
- **Navigation**: Navigation Compose
- **Printing Functionality**: ESCPOS-ThermalPrinter-Android
- **QR Code Scanning**: ZXing

## Project Structure
The project follows Clean Architecture principles and is organized into the following main layers:

### 1. Application Layer
- `WooAutoApplication.kt` - Application entry point, responsible for initializing application-level components such as Hilt
  - `onCreate()` - 应用启动入口，初始化所有核心组件
  - `initializeMetadataProcessors()` - 初始化元数据处理系统
  - `initializeOrderNotificationManager()` - 初始化订单通知管理器
  - `initializeLicenseManager()` - 初始化证书管理系统
  - `checkConfigAndStartService()` - 检查配置并启动后台服务
  - `startBackgroundPollingService()` - 启动后台轮询服务
  - `startLicenseMonitoring()` - 启动证书状态监控

### 2. Presentation Layer
- `/presentation` - Contains all UI-related code
  - `/components` - Reusable Compose UI components
  - `/screens` - Various screens in the application
  - `/theme` - Application theme definitions
  - `MainActivity.kt` - Main activity entry point
  - `WooAutoApp.kt` - Main Compose application entry point
    - `getContent()` - 提供应用主界面内容
    - `NavHost` - 管理应用的主导航结构
    - `BottomNavigation` - 底部导航栏实现
  
  - `/screens` - 应用中的各个屏幕
    - `HomeScreen.kt` - 首页屏幕
      - `HomeScreen()` - 主页面Composable函数
      - `OrdersList()` - 订单列表组件
      - `StatisticsSection()` - 统计信息部分
    - `OrdersScreen.kt` - 订单管理屏幕
      - `OrdersScreen()` - 订单管理主界面
      - `OrderFilters()` - 订单筛选组件
      - `OrderDetails()` - 订单详情展示
    - `PrinterScreen.kt` - 打印机管理屏幕
      - `PrinterScreen()` - 打印机设置主界面
      - `PrinterConnectionStatus()` - 打印机连接状态
      - `PrinterTestFunctions()` - 打印机测试功能
    - `SettingsScreen.kt` - 设置屏幕
      - `SettingsScreen()` - 设置主界面
      - `ApiSettings()` - API设置组件
      - `PrinterSettings()` - 打印机设置组件
      - `NotificationSettings()` - 通知设置组件
      - `GeneralSettings()` - 通用设置组件
    - `ScanScreen.kt` - 扫描屏幕
      - `ScanScreen()` - 扫描主界面
      - `QrScanner()` - 二维码扫描组件
  
  - `/theme` - 应用主题定义
    - `Theme.kt` - 定义应用颜色和主题
    - `Color.kt` - 颜色定义
    - `Type.kt` - 字体排版定义
    - `Shape.kt` - 形状定义

### 3. Domain Layer
- `/domain` - Contains business logic and entities
  - `/models` - Business entities
    - `Order.kt` - 订单实体模型
    - `Customer.kt` - 客户实体模型
    - `Product.kt` - 产品实体模型
    - `PrinterConfig.kt` - 打印机配置模型
    - `PrintTemplate.kt` - 打印模板模型
  - `/repositories` - Repository interface definitions
    - `DomainOrderRepository.kt` - 订单仓库接口
    - `DomainCustomerRepository.kt` - 客户仓库接口
    - `DomainPrinterRepository.kt` - 打印机仓库接口
    - `DomainTemplateRepository.kt` - 模板仓库接口
  - `/usecases` - Use case definitions, implementing business logic
    - `GetOrdersUseCase.kt` - 获取订单用例
    - `ProcessOrderUseCase.kt` - 处理订单用例
    - `PrintOrderUseCase.kt` - 打印订单用例
    - `UpdateOrderStatusUseCase.kt` - 更新订单状态用例
    - `ScanQrCodeUseCase.kt` - 扫描二维码用例
  - `/printer` - Printing-related domain logic
    - `PrinterManager.kt` - 打印机管理接口
    - `PrintJob.kt` - 打印任务定义
    - `PrinterConnection.kt` - 打印机连接接口
  - `/templates` - Printing template domain logic
    - `TemplateProcessor.kt` - 模板处理器接口
    - `TemplateEngine.kt` - 模板引擎接口
    - `TemplateVariable.kt` - 模板变量定义

### 4. Data Layer
- `/data` - Data-related code
  - `/local` - Local data sources (Room database)
    - `WooCommerceConfig.kt` - WooCommerce配置存储
    - `OrderDatabase.kt` - 订单数据库定义
    - `OrderDao.kt` - 订单数据访问对象
    - `CustomerDao.kt` - 客户数据访问对象
    - `PrinterConfigDao.kt` - 打印机配置数据访问对象
    - `TemplateDao.kt` - 模板数据访问对象
  - `/remote` - Remote data sources (Network APIs)
    - `WooCommerceApi.kt` - WooCommerce API接口
    - `OrderApiService.kt` - 订单API服务
    - `CustomerApiService.kt` - 客户API服务
    - `/metadata` - 元数据处理相关
      - `MetadataProcessor.kt` - 元数据处理器接口
      - `MetadataProcessorFactory.kt` - 元数据处理器工厂
      - `MetadataProcessorRegistry.kt` - 元数据处理器注册表
  - `/repository` - Repository implementations
    - `OrderRepositoryImpl.kt` - 订单仓库实现
    - `CustomerRepositoryImpl.kt` - 客户仓库实现
    - `PrinterRepositoryImpl.kt` - 打印机仓库实现
    - `TemplateRepositoryImpl.kt` - 模板仓库实现
  - `/mappers` - Data model mappers
    - `OrderMapper.kt` - 订单数据映射器
    - `CustomerMapper.kt` - 客户数据映射器
    - `ProductMapper.kt` - 产品数据映射器
  - `/printer` - Printer-related data processing
    - `EscPosPrinter.kt` - ESC/POS打印机实现
    - `BluetoothPrinterConnection.kt` - 蓝牙打印机连接实现
    - `UsbPrinterConnection.kt` - USB打印机连接实现
    - `NetworkPrinterConnection.kt` - 网络打印机连接实现
  - `/templates` - Print template data processing
    - `TemplateProcessorImpl.kt` - 模板处理器实现
    - `TemplateEngineImpl.kt` - 模板引擎实现
    - `TemplateVariableExtractor.kt` - 模板变量提取器

### 5. Utilities
- `/utils` - Common utility classes and extension functions
  - `OrderNotificationManager.kt` - 订单通知管理器
  - `LocaleHelper.kt` - 本地化辅助工具
  - `LocaleManager.kt` - 语言管理器
  - `DateTimeUtils.kt` - 日期时间工具类
  - `PrinterUtils.kt` - 打印机工具类
  - `CurrencyFormatter.kt` - 货币格式化工具
  - `QrCodeGenerator.kt` - 二维码生成工具
  - `NetworkUtils.kt` - 网络工具类
  - `PermissionUtils.kt` - 权限工具类

### 6. Dependency Injection
- `/di` - Hilt dependency injection modules
  - `AppModule.kt` - Application-level dependencies
  - `NetworkModule.kt` - Network-related dependencies
  - `DatabaseModule.kt` - Database-related dependencies
  - `RepositoryModule.kt` - Repository bindings
  - `PrinterModule.kt` - Printer-related dependencies
  - `ApiModule.kt` - API-related dependencies
  - `DataStoreModule.kt` - DataStore related dependencies
  - `UseCaseModule.kt` - 用例注入模块

### 7. Navigation
- `/navigation` - Application navigation components
  - `NavigationItem.kt` - Navigation definition with icons and localization
  - `Screen.kt` - 屏幕路由定义
  - `NavGraph.kt` - 导航图定义

### 8. Services
- `/service` - Background services
  - `BackgroundPollingService.kt` - 后台轮询服务
    - `onStartCommand()` - 服务启动入口
    - `startPolling()` - 开始轮询流程
    - `processNewOrders()` - 处理新订单
    - `handleOrderProcessing()` - 处理订单打印逻辑
  - `PrinterService.kt` - 打印机服务
    - `printOrder()` - 打印订单函数
    - `connectToPrinter()` - 连接打印机
    - `getPrinterStatus()` - 获取打印机状态

### 9. Licensing
- `/licensing` - 证书相关代码
  - `LicenseManager.kt` - 证书管理器
  - `LicenseVerificationManager.kt` - 证书验证管理器
  - `LicenseStatus.kt` - 证书状态枚举

### 10. Updater
- `/updater` - 应用更新相关代码
  - `AppUpdateManager.kt` - 应用更新管理器
  - `UpdateChecker.kt` - 更新检查器

## Project Configuration
- `app/build.gradle.kts` - Application-level build configuration
- `build.gradle.kts` - Project-level build configuration
- `settings.gradle.kts` - Gradle settings
- `version.properties` - 版本属性配置
- `api-keys.properties` - API密钥配置

## Resource Files
- `app/src/main/res` - Contains application resources (layouts, strings, styles, etc.)
- `app/src/main/assets` - Contains application static assets

## Permissions
The application requires the following permissions:
- Network access
- Bluetooth access (for printer connectivity)
- Storage access (optional, depending on functionality)
- Notification permission
- Camera permission
- Location permission (for Bluetooth scanning, required for Android S and above)

## Build and Deployment
The project uses the Gradle build tool and can be built and deployed through Android Studio or via command line. 

## Feature Flow
1. **Order Processing Flow**:
   - Background service periodically polls WooCommerce API for new orders
   - New orders are notified through the notification system
   - User can manually trigger print or set automatic print
   - Order status is automatically updated after printing

2. **Printer Connection Flow**:
   - Supports Bluetooth, USB, and network printers
   - Automatic reconnection mechanism
   - Printer status monitoring
   - Print task queue management

3. **Template Processing Flow**:
   - User can customize print templates
   - Template engine handles variable substitution
   - Supports multiple print formats and styles

4. **Certificate Verification Flow**:
   - Application starts by verifying certificates
   - Regularly checks certificate validity
   - Restricts certain features if certificates are invalid 