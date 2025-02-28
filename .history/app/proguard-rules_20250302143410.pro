# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 保留注解处理器相关类
-keep class androidx.room.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.hilt.** { *; }
-keep class dagger.hilt.** { *; }

# 保留Kotlin相关类
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# 保留反射使用的类
-keepattributes *Annotation*, InnerClasses
-keepattributes SourceFile, LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# 保留Retrofit和OkHttp相关类
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 保留Gson相关类
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 应用特定的保留规则
-keep class com.example.wooauto.data.remote.dto.** { *; }
-keep class com.example.wooauto.domain.models.** { *; }