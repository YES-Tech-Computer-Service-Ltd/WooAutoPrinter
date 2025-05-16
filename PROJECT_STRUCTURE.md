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
  - `onCreate()` - Application startup entry point, initializes all core components
  - `initializeMetadataProcessors()` - Initializes metadata processing system
  - `initializeOrderNotificationManager()` - Initializes order notification manager
  - `initializeLicenseManager()` - Initializes license management system
  - `checkConfigAndStartService()` - Checks configuration and starts background service
  - `startBackgroundPollingService()` - Starts background polling service
  - `startLicenseMonitoring()` - Starts license status monitoring

### 2. Presentation Layer
- `/presentation` - Contains all UI-related code
  - `/components` - Reusable Compose UI components
  - `/screens` - Various screens in the application
  - `/theme` - Application theme definitions
  - `MainActivity.kt` - Main activity entry point
  - `WooAutoApp.kt` - Main Compose application entry point
    - `getContent()` - Provides the main interface content of the application
    - `NavHost` - Manages the main navigation structure of the application
    - `BottomNavigation` - Bottom navigation bar implementation
  
  - `/screens` - Various screens in the application
    - `HomeScreen.kt` - Home screen
      - `HomeScreen()` - Main page Composable function
      - `OrdersList()` - Orders list component
      - `StatisticsSection()` - Statistics information section
    - `OrdersScreen.kt` - Order management screen
      - `OrdersScreen()` - Order management main interface
      - `OrderFilters()` - Order filtering component
      - `OrderDetails()` - Order details display
    - `PrinterScreen.kt` - Printer management screen
      - `PrinterScreen()` - Printer settings main interface
      - `PrinterConnectionStatus()` - Printer connection status
      - `PrinterTestFunctions()` - Printer test functions
    - `SettingsScreen.kt` - Settings screen
      - `SettingsScreen()` - Settings main interface
      - `ApiSettings()` - API settings component
      - `PrinterSettings()` - Printer settings component
      - `NotificationSettings()` - Notification settings component
      - `GeneralSettings()` - General settings component
    - `ScanScreen.kt` - Scanning screen
      - `ScanScreen()` - Scanning main interface
      - `QrScanner()` - QR code scanning component
  
  - `/theme` - Application theme definitions
    - `Theme.kt` - Defines application colors and themes
    - `Color.kt` - Color definitions
    - `Type.kt` - Typography definitions
    - `Shape.kt` - Shape definitions

### 3. Domain Layer
- `/domain` - Contains business logic and entities
  - `/models` - Business entities
    - `Order.kt` - Order entity model
    - `Customer.kt` - Customer entity model
    - `Product.kt` - Product entity model
    - `PrinterConfig.kt` - Printer configuration model
    - `PrintTemplate.kt` - Print template model
  - `/repositories` - Repository interface definitions
    - `DomainOrderRepository.kt` - Order repository interface
    - `DomainCustomerRepository.kt` - Customer repository interface
    - `DomainPrinterRepository.kt` - Printer repository interface
    - `DomainTemplateRepository.kt` - Template repository interface
  - `/usecases` - Use case definitions, implementing business logic
    - `GetOrdersUseCase.kt` - Get orders use case
    - `ProcessOrderUseCase.kt` - Process order use case
    - `PrintOrderUseCase.kt` - Print order use case
    - `UpdateOrderStatusUseCase.kt` - Update order status use case
    - `ScanQrCodeUseCase.kt` - Scan QR code use case
  - `/printer` - Printing-related domain logic
    - `PrinterManager.kt` - Printer management interface
    - `PrintJob.kt` - Print job definition
    - `PrinterConnection.kt` - Printer connection interface
  - `/templates` - Printing template domain logic
    - `TemplateProcessor.kt` - Template processor interface
    - `TemplateEngine.kt` - Template engine interface
    - `TemplateVariable.kt` - Template variable definition

### 4. Data Layer
- `/data` - Data-related code
  - `/local` - Local data sources (Room database)
    - `WooCommerceConfig.kt` - WooCommerce configuration storage
    - `OrderDatabase.kt` - Order database definition
    - `OrderDao.kt` - Order data access object
    - `CustomerDao.kt` - Customer data access object
    - `PrinterConfigDao.kt` - Printer configuration data access object
    - `TemplateDao.kt` - Template data access object
  - `/remote` - Remote data sources (Network APIs)
    - `WooCommerceApi.kt` - WooCommerce API interface
    - `OrderApiService.kt` - Order API service
    - `CustomerApiService.kt` - Customer API service
    - `/metadata` - Metadata processing related
      - `MetadataProcessor.kt` - Metadata processor interface
      - `MetadataProcessorFactory.kt` - Metadata processor factory
      - `MetadataProcessorRegistry.kt` - Metadata processor registry
  - `/repository` - Repository implementations
    - `OrderRepositoryImpl.kt` - Order repository implementation
    - `CustomerRepositoryImpl.kt` - Customer repository implementation
    - `PrinterRepositoryImpl.kt` - Printer repository implementation
    - `TemplateRepositoryImpl.kt` - Template repository implementation
  - `/mappers` - Data model mappers
    - `OrderMapper.kt` - Order data mapper
    - `CustomerMapper.kt` - Customer data mapper
    - `ProductMapper.kt` - Product data mapper
  - `/printer` - Printer-related data processing
    - `EscPosPrinter.kt` - ESC/POS printer implementation
    - `BluetoothPrinterConnection.kt` - Bluetooth printer connection implementation
    - `UsbPrinterConnection.kt` - USB printer connection implementation
    - `NetworkPrinterConnection.kt` - Network printer connection implementation
  - `/templates` - Print template data processing
    - `TemplateProcessorImpl.kt` - Template processor implementation
    - `TemplateEngineImpl.kt` - Template engine implementation
    - `TemplateVariableExtractor.kt` - Template variable extractor

### 5. Utilities
- `/utils` - Common utility classes and extension functions
  - `OrderNotificationManager.kt` - Order notification manager
  - `LocaleHelper.kt` - Localization helper tool
  - `LocaleManager.kt` - Language manager
  - `DateTimeUtils.kt` - Date and time utility class
  - `PrinterUtils.kt` - Printer utility class
  - `CurrencyFormatter.kt` - Currency formatting tool
  - `QrCodeGenerator.kt` - QR code generation tool
  - `NetworkUtils.kt` - Network utility class
  - `PermissionUtils.kt` - Permission utility class

### 6. Dependency Injection
- `/di` - Hilt dependency injection modules
  - `AppModule.kt` - Application-level dependencies
  - `NetworkModule.kt` - Network-related dependencies
  - `DatabaseModule.kt` - Database-related dependencies
  - `RepositoryModule.kt` - Repository bindings
  - `PrinterModule.kt` - Printer-related dependencies
  - `ApiModule.kt` - API-related dependencies
  - `DataStoreModule.kt` - DataStore related dependencies
  - `UseCaseModule.kt` - Use case injection module

### 7. Navigation
- `/navigation` - Application navigation components
  - `NavigationItem.kt` - Navigation definition with icons and localization
  - `Screen.kt` - Screen route definition
  - `NavGraph.kt` - Navigation graph definition

### 8. Services
- `/service` - Background services
  - `BackgroundPollingService.kt` - Background polling service
    - `onStartCommand()` - Service startup entry point
    - `startPolling()` - Start polling process
    - `processNewOrders()` - Process new orders
    - `handleOrderProcessing()` - Handle order printing logic
  - `PrinterService.kt` - Printer service
    - `printOrder()` - Print order function
    - `connectToPrinter()` - Connect to printer
    - `getPrinterStatus()` - Get printer status

### 9. Licensing
- `/licensing` - License-related code
  - `LicenseManager.kt` - License manager
  - `LicenseVerificationManager.kt` - License verification manager
  - `LicenseStatus.kt` - License status enumeration

### 10. Updater
- `/updater` - Application update related code
  - `AppUpdateManager.kt` - Application update manager
  - `UpdateChecker.kt` - Update checker

## Project Configuration
- `app/build.gradle.kts` - Application-level build configuration
- `build.gradle.kts` - Project-level build configuration
- `settings.gradle.kts` - Gradle settings
- `version.properties` - Version properties configuration
- `api-keys.properties` - API key configuration

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