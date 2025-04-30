pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // 用于 DantSu/ESCPOS-ThermalPrinter-Android 依赖
        maven { url = uri("https://www.star-m.jp/repo/maven/") } // 添加Star Micronics的Maven仓库
    }
}


rootProject.name = "WooAuto"
include(":app")