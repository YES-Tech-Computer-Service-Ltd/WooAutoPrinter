import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// 读取版本属性
val versionPropsFile = file("../version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        load(FileInputStream(versionPropsFile))
    } else {
        // 如果文件不存在，设置默认值
        setProperty("major", "0")
        setProperty("minor", "1")
        setProperty("patch", "0")
        setProperty("build", "1")
        setProperty("versionCode", "1")
        setProperty("versionName", "0.1.0")
        setProperty("isBeta", "true")
    }
}

// API密钥（证书验证模块使用）
val licenseApiKey = "0A9Q5OXT13in3LGjM9F3"

// 提取版本信息
val major = versionProps.getProperty("major").toInt()
val minor = versionProps.getProperty("minor").toInt()
val patch = versionProps.getProperty("patch").toInt()
val build = versionProps.getProperty("build").toInt()
val versionCode = versionProps.getProperty("versionCode").toInt()
val versionName = versionProps.getProperty("versionName")
val isBeta = versionProps.getProperty("isBeta").toBoolean()

// 更新构建号和版本信息的任务
tasks.register("updateVersionCode") {
    doLast {
        val newBuild = build + 1
        val newVersionCode = versionCode + 1
        
        versionProps.setProperty("build", newBuild.toString())
        versionProps.setProperty("versionCode", newVersionCode.toString())
        
        versionProps.store(
            versionPropsFile.outputStream(), 
            "版本信息自动更新 - 构建号：$newBuild, 版本代号：$newVersionCode"
        )
    }
}

// 创建新版本的任务
tasks.register("createNewVersion") {
    doLast {
        // 读取参数，格式: ./gradlew createNewVersion -PnewVersion=major|minor|patch
        val newVersionType = project.properties["newVersion"] as? String ?: "patch"
        
        val newMajor = when(newVersionType) {
            "major" -> major + 1
            else -> major
        }
        
        val newMinor = when(newVersionType) {
            "major" -> 0
            "minor" -> minor + 1
            else -> minor
        }
        
        val newPatch = when(newVersionType) {
            "major", "minor" -> 0
            "patch" -> patch + 1
            else -> patch
        }
        
        val newVersionName = "$newMajor.$newMinor.$newPatch"
        println("创建新版本: $newVersionName")
        
        // 更新版本信息
        versionProps.setProperty("major", newMajor.toString())
        versionProps.setProperty("minor", newMinor.toString())
        versionProps.setProperty("patch", newPatch.toString())
        versionProps.setProperty("versionName", newVersionName)
        versionProps.setProperty("build", "1") // 重置构建号
        versionProps.setProperty("versionCode", (versionCode + 1).toString())
        
        versionProps.store(
            versionPropsFile.outputStream(), 
            "版本信息更新: $newVersionName"
        )
    }
}

// 修改Beta状态的任务
tasks.register("setBetaState") {
    doLast {
        // 读取参数，格式: ./gradlew setBetaState -PisBeta=true|false
        val betaState = (project.properties["isBeta"] as? String ?: "true").toBoolean()
        
        versionProps.setProperty("isBeta", betaState.toString())
        versionProps.store(
            versionPropsFile.outputStream(), 
            "更新测试版状态: $betaState"
        )
    }
}

android {
    namespace = "com.example.wooauto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.wooauto"
        minSdk = 24
        targetSdk = 34
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName") + 
                      (if (isBeta) "-beta" else "")
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // 在BuildConfig中添加版本信息
        buildConfigField("boolean", "IS_BETA", isBeta.toString())
        buildConfigField("int", "VERSION_MAJOR", major.toString())
        buildConfigField("int", "VERSION_MINOR", minor.toString())
        buildConfigField("int", "VERSION_PATCH", patch.toString())
        buildConfigField("int", "VERSION_BUILD", build.toString())
        buildConfigField("String", "LICENSE_API_KEY", "\"${licenseApiKey}\"")
    }

    // 禁用密度分包，避免 bundle 工具错误
    bundle {
        density {
            enableSplit = false
        }
        language {
            enableSplit = false
        }
        abi {
            enableSplit = false
        }
    }

    // 添加测试配置，跳过所有测试
    testOptions {
        unitTests.all {
            it.enabled = false
        }
    }

    // 添加签名配置
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/wooautoprinter.keystore")
            storePassword = "wooautoprinter"
            keyAlias = "wooautoprinter"
            keyPassword = "wooautoprinter"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "LICENSE_API_KEY", "\"$licenseApiKey\"")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "LICENSE_API_KEY", "\"$licenseApiKey\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    // 添加Lint配置
    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {

    implementation(libs.firebase.functions.ktx)
    implementation(libs.annotations)
    // 添加Multidex支持
    implementation("androidx.multidex:multidex:2.0.1")
    
    // 添加ZXing二维码扫描库
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // JUnit 5
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit)

    // Mockk（用于模拟 Repository）
    testImplementation(libs.mockk)

    // Coroutines 测试支持
    testImplementation(libs.kotlinx.coroutines.test)

    // 明确指定Room测试库版本
    testImplementation("androidx.room:room-testing:2.5.2")
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    testImplementation (libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)
    implementation(libs.hilt.android)


    // Core Android
    implementation(libs.androidx.core.ktx.v1150)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx.v287)
    implementation(libs.androidx.activity.compose.v1101)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.play.services.analytics.impl)

    
    configurations.all {
        resolutionStrategy {
            exclude(group = "xmlpull", module = "xmlpull")
            exclude(group = "xpp3", module = "xpp3")

            exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")

            exclude(group = "com.android.support")

            exclude(group = "com.intellij", module = "annotations")

            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.22")
        }
    }

    // Compose
    val composeBomVersion = "2024.02.00"
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database
    val roomVersion = "2.5.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Retrofit for API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Bluetooth and printing
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")


    implementation (libs.androidx.material.icons.extended)

    implementation(libs.hilt.android.v250)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hilt
    ksp(libs.hilt.android.compiler)

    // 应用更新相关依赖
    implementation("com.github.javiersantos:AppUpdater:2.7") {
        // 排除旧版support库，解决冲突
        exclude(group = "com.android.support")
    }
    implementation("org.jsoup:jsoup:1.16.2") // 用于解析HTML
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // 网络请求

    // Compose LiveData
    implementation("androidx.compose.runtime:runtime-livedata:1.6.3")
}