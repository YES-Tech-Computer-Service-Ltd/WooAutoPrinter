# WooAutoPrinter Project Structure

## 1. Core Application Logic
- `app/src/main/java/com/example/wooauto/WooAutoApplication.kt` - **App Entry Point**:
    - **Responsibilities**:
        - **Initialization Hub**: Coordinates app startup via `InitializationManager`.
        - **Service Management**: Starts `BackgroundPollingService` after validating config and license.
        - **Dependency Injection**: Triggers Hilt dependency graph generation (`@HiltAndroidApp`).
        - **Global Configuration**: Sets up `WorkManager`, `ImageLoader` (Coil), and `LocaleManager`.
    - **Key Methods**:
        - `onCreate()`: Entry point. Launches coroutines for parallel initialization (Metadata, Notifications, License).
        - `checkConfigAndStartService()`: Validates API keys/URL before starting the background poller.
        - `startLicenseMonitoring()`: Periodic license validation loop (every 1 hour).

- `app/src/main/java/com/example/wooauto/MainActivity.kt` - **UI Entry Point**:
    - **Responsibilities**:
        - **UI Container**: Hosts the Compose `NavHost` and sets up the theme (`WooAutoTheme`).
        - **Permission Handler**: Manages critical permissions (Bluetooth, Camera, Notification) using `ActivityResultContracts`.
        - **Global Dialogs**: Renders overlay dialogs for "New Order" (`NewOrderPopup`) and "System Errors" (`ErrorDetailsDialog`).
        - **Screen Management**: Handles "Keep Screen On" logic and immersive mode (hiding status bars).
    - **Key Methods**:
        - `onCreate()`: Initializes Splash screen, requests permissions, and sets Compose content.
        - `onNewOrderReceived()`: Callback from `OrderNotificationManager`. Triggers the full-screen `NewOrderPopup`.
        - `MainAppContent()`: Top-level Composable that monitors `GlobalErrorManager` state to show error dialogs.

## 2. Presentation Layer (UI & ViewModels)
- `app/src/main/java/com/example/wooauto/presentation/WooAutoApp.kt` - Root composable with `NavHost` and `BottomNavigation`.
- **Screens**:
    - `presentation/screens/orders/`
        - `OrdersScreen.kt` - Main order list UI with filtering and tabs.
        - `OrdersViewModel.kt` - **Core**: Manages order list state (`_orders`), status filtering (`filterOrdersByStatus`), and manual print triggers (`printOrderWithTemplate`).
            - **State**: `orders` (List<Order>), `unreadOrdersCount`, `currentStatusFilter`.
            - **Actions**: `refreshOrders` (poll API), `updateOrderStatus` (optimistic update), `batchStartProcessing` (bulk action).
        - `OrderDetailDialog.kt` - Detailed view of a single order.
    - `presentation/screens/settings/`
        - `SettingsScreen.kt` - Main settings dashboard.
        - `SettingsViewModel.kt` - **Core**: Central configuration hub.
            - **Printer**: `connectPrinter`, `scanPrinters`, `testPrint`, `testKeepAliveFeed`.
            - **API**: `saveSettings` (validates & persists WooCommerce keys), `testConnection`.
            - **System**: `setAppLanguage`, `updateKeepScreenOn`, `checkAppUpdate`.
        - `PrinterSettings/PrinterSettingsScreen.kt` - Bluetooth device scanning and printer configuration.
        - `ApiSettings.kt` - WooCommerce URL/Key input form.
    - `presentation/screens/products/`
        - `ProductsScreen.kt` - Product list with category filtering.
        - `ProductsViewModel.kt` - Product data fetching and sync logic.
- **Navigation**:
    - `presentation/navigation/AppNavConfig.kt` - Navigation route definitions.
    - `presentation/navigation/Screen.kt` - Sealed classes for type-safe navigation args.
- **Theme**:
    - `presentation/theme/Theme.kt`, `Color.kt`, `Type.kt` - Jetpack Compose styling.

## 3. Domain Layer (Business Rules)
- **Models**:
    - `domain/models/Order.kt` - **Core Entity**: Represents a WooCommerce order.
        - Contains: `items` (List), `woofoodInfo` (delivery/pickup details), `feeLines` (tips/surcharges), `taxLines`.
        - Key Flags: `isPrinted`, `isRead`, `status` (processing/completed/etc).
    - `domain/models/PrinterConfig.kt` - **Configuration Entity**: Stores printer settings.
        - Fields: `address` (MAC/IP), `paperWidth` (58/80mm), `autoCut` (Boolean), `encoding` (GBK/UTF-8).
    - `domain/models/TemplateConfig.kt` - **Layout Configuration**: Defines which sections (Header, Items, Footer) to print.

- **Printer Logic**:
    - `domain/printer/PrinterManager.kt` - **Interface**: The "Bible" for printer operations. Hides Bluetooth/USB details from UI.
        - Core Methods: `connect()`, `printOrder()`, `feedPaperMinimal()` (Keep-Alive), `queryRealtimeStatus()`.
    - `domain/printer/PrinterStatus.kt` - **Enum**: `CONNECTED`, `DISCONNECTED`, `CONNECTING`, `ERROR`.

- **Templates**:
    - `domain/templates/OrderPrintTemplate.kt` - **Interface**: Logic for formatting `Order` objects into printable strings (ESC/POS commands or plain text).
        - Method: `generateOrderPrintContent(order, config)` -> Returns formatted string.

- **Repositories (Interfaces)**:
    - `domain/repositories/DomainOrderRepository.kt` - Contract for fetching orders (API + Local DB).
    - `domain/repositories/DomainPrinterRepository.kt` - Contract for saving printer configs.
    - `domain/repositories/DomainSettingRepository.kt` - Contract for app-wide settings (API keys, sound, auto-print).

## 4. Data Layer (Implementation)
- **Local Storage (Room)**:
    - `data/local/db/AppDatabase.kt` - **Database Entry Point**: Manages Room database versioning and migrations.
    - `data/local/dao/OrderDao.kt` - **Order Data Access**:
        - `getAllOrders()`: Returns `Flow<List<OrderEntity>>` for real-time UI updates.
        - `insertOrder()`: Uses `OnConflictStrategy.REPLACE` for upsert behavior.
        - `getUnreadOrderIds()`: Efficient query for notification badges.
    - `data/local/entities/OrderEntity.kt` - **Table Schema**: Defines the `orders` table columns and type converters.
    - `data/local/WooCommerceConfig.kt` - **DataStore Preference**: Stores simple key-value pairs (site URL, consumer key) using Jetpack DataStore.

- **Remote (Network)**:
    - `data/remote/WooCommerceApi.kt` - **Retrofit Interface**: Defines HTTP endpoints (`GET /orders`, `POST /orders/{id}`).
    - `data/remote/impl/WooCommerceApiImpl.kt` - **Network Core**:
        - **OAuth 1.0a**: Implements request signing via `addAuthParams`.
        - **Error Handling**: Translates HTTP 4xx/5xx into `ApiError` exceptions.
        - **Retry Logic**: Implements exponential backoff for failed requests.
        - **JSON Parsing**: Uses Gson with custom `TypeAdapter` for complex WooCommerce DTOs.
    - `data/remote/dto/OrderDto.kt` - **Data Transfer Object**: Maps raw JSON response to Kotlin objects.

- **Printer Implementation**:
    - `data/printer/BluetoothPrinterManager.kt` - **The Heavy Lifter**:
        - **Connection State Machine**: Manages `CONNECTED` / `CONNECTING` / `DISCONNECTED` states.
        - **Command Generation**:
            - `printContent()`: Main method. Handles charset (GBK vs UTF-8), chunking, and ESC/POS formatting.
            - `feedPaperMinimal()`: Keep-alive logic.
        - **Resilience**: Includes `ensurePrinterConnected` (auto-reconnect) and `handleConnectionError` (broken pipe recovery).
    - `data/printer/star/StarPrinterDriver.kt` - **Vendor Specific**: Translates generic print commands into Star Line Mode commands.
    - `data/printer/detect/VendorDetector.kt` - **Heuristics**: Guesses printer brand based on MAC OUI to select the right driver.

- **Repositories (Impl)**:
    - `data/repository/OrderRepositoryImpl.kt` - **SSOT (Single Source of Truth)**:
        - Fetches from Network -> Saves to DB -> UI observes DB.
        - Never lets UI talk to Network directly.
    - `data/repository/SettingsRepositoryImpl.kt` - Bridges DataStore and UI.

## 5. Background Services
- `service/BackgroundPollingService.kt` - **Core Service**: Runs indefinitely. Manages:
    - **Order Fetching**: Polls WooCommerce `GET /orders` (via `DomainOrderRepository`).
    - **Auto-Printing**: Triggers `printOrder` logic if auto-print is enabled.
    - **Keep-Alive Task**: Sends `feedPaperMinimal` every X hours to prevent printer sleep.
    - **Network Heartbeat**: Monitors `ConnectivityManager` and holds `WifiLock`/`WakeLock` to ensure reliability.
- `service/SystemPollingManager.kt` - Orchestrates various polling jobs.

## 6. Utilities & Helpers
- `utils/ThermalPrinterFormatter.kt` - **Print Layout Engine**:
    - Translates pseudo-HTML tags (`<h>`, `<b>`, `<w>`) into ESC/POS commands.
    - Handles text wrapping, column alignment, and Chinese character width (2 bytes vs 1 byte).
- `utils/GlobalErrorManager.kt` - Centralized error reporting (triggers UI dialogs for network/printer errors).
- `utils/SoundManager.kt` - Audio feedback (notification sounds).
- `utils/LocaleManager.kt` - App language switching logic (restarts Activities).
- `di/` - Hilt modules (`AppModule.kt`, `PrinterModule.kt`, etc.) providing dependency injection.

## 7. Updater & Licensing
- `updater/WordPressUpdater.kt` - Checks for app updates from WP endpoint.
- `licensing/LicenseManager.kt` - **Gatekeeper**:
    - **Validation Logic**: Checks trial token (`TrialTokenManager`) or paid license key.
    - **Status Management**: Exposes `eligibilityInfo` LiveData to UI.
    - **Fallback**: Implements "Default Allow" policy (soft fail) for network errors.
