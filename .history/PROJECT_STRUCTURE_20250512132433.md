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

### 2. Presentation Layer
- `/presentation` - Contains all UI-related code
  - `/components` - Reusable Compose UI components
  - `/navigation` - Application navigation setup
  - `/screens` - Various screens in the application
  - `/theme` - Application theme definitions
  - `MainActivity.kt` - Main activity entry point
  - `WooAutoApp.kt` - Main Compose application entry point

### 3. Domain Layer
- `/domain` - Contains business logic and entities
  - `/models` - Business entities
  - `/repositories` - Repository interface definitions
  - `/usecases` - Use case definitions, implementing business logic
  - `/printer` - Printing-related domain logic
  - `/templates` - Printing template domain logic

### 4. Data Layer
- `/data` - Data-related code
  - `/local` - Local data sources (Room database)
  - `/remote` - Remote data sources (Network APIs)
  - `/repositories` - Repository implementations
  - `/mappers` - Data model mappers
  - `/printer` - Printer-related data processing
  - `/templates` - Print template data processing
  - `/di` - Data layer dependency injection

### 5. Utilities
- `/utils` - Common utility classes and extension functions

### 6. Dependency Injection
- `/di` - Hilt dependency injection modules

### 7. Navigation
- `/navigation` - Application navigation components

### 8. Services
- `/service` - Background services

## Project Configuration
- `app/build.gradle.kts` - Application-level build configuration
- `build.gradle.kts` - Project-level build configuration
- `settings.gradle.kts` - Gradle settings

## Resource Files
- `app/src/main/res` - Contains application resources (layouts, strings, styles, etc.)
- `app/src/main/assets` - Contains application static assets

## Permissions
The application requires the following permissions:
- Network access
- Bluetooth access (for printer connectivity)
- Storage access (optional, depending on functionality)

## Build and Deployment
The project uses the Gradle build tool and can be built and deployed through Android Studio or via command line. 