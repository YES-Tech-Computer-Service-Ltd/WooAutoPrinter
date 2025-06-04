package com.example.wooauto.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * 电源管理工具类
 * 用于管理设备省电模式、电池优化等设置
 */
object PowerManagementUtils {
    private const val TAG = "PowerManagementUtils"

    /**
     * 检查应用是否被加入电池优化白名单
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "检查电池优化状态失败: ${e.message}")
            false
        }
    }

    /**
     * 请求用户将应用加入电池优化白名单
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun requestIgnoreBatteryOptimizations(context: Context): Intent? {
        return try {
            if (!isIgnoringBatteryOptimizations(context)) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Log.d(TAG, "应用已在电池优化白名单中")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建电池优化请求Intent失败: ${e.message}")
            null
        }
    }

    /**
     * 检查设备是否开启了省电模式
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun isPowerSaveMode(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isPowerSaveMode
        } catch (e: Exception) {
            Log.e(TAG, "检查省电模式状态失败: ${e.message}")
            false
        }
    }

    /**
     * 检查设备是否开启了数据保护模式（Android 7+）
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun isDataSaverMode(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            
            when (connectivityManager.restrictBackgroundStatus) {
                android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> {
                    Log.w(TAG, "数据保护模式已开启，可能影响后台网络访问")
                    true
                }
                android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> {
                    Log.d(TAG, "应用已被加入数据保护白名单")
                    false
                }
                android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> {
                    Log.d(TAG, "数据保护模式未开启")
                    false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查数据保护模式状态失败: ${e.message}")
            false
        }
    }

    /**
     * 获取设备省电相关设置的建议
     */
    fun getPowerSavingRecommendations(context: Context): List<String> {
        val recommendations = mutableListOf<String>()

        try {
            val androidVersion = Build.VERSION.SDK_INT
            Log.d(TAG, "为Android $androidVersion 生成省电优化建议")

            // 检查电池优化（Android 6.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!isIgnoringBatteryOptimizations(context)) {
                    recommendations.add("建议将WooAuto加入电池优化白名单，避免系统限制后台运行")
                    
                    // 针对不同Android版本的具体指导
                    when {
                        androidVersion >= Build.VERSION_CODES.Q -> { // Android 10+
                            recommendations.add("Android 10+系统：在设置 > 应用和通知 > 特殊应用权限 > 电池优化中设置")
                        }
                        androidVersion >= Build.VERSION_CODES.P -> { // Android 9
                            recommendations.add("Android 9系统：在设置 > 应用 > 特殊访问权限 > 电池优化中设置")
                        }
                        androidVersion >= Build.VERSION_CODES.N -> { // Android 7-8
                            recommendations.add("Android 7-8系统：在设置 > 电池 > 电池优化中将WooAuto设为'不优化'")
                        }
                        else -> { // Android 6
                            recommendations.add("Android 6系统：在设置 > 电池 > 菜单 > 电池优化中设置")
                        }
                    }
                }
            }

            // 检查省电模式（Android 5.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (isPowerSaveMode(context)) {
                    when {
                        androidVersion >= Build.VERSION_CODES.P -> { // Android 9+
                            recommendations.add("设备当前处于省电模式，可能影响应用后台运行。建议在设置 > 电池 > 自适应电池中调整")
                        }
                        androidVersion >= Build.VERSION_CODES.N -> { // Android 7-8
                            recommendations.add("设备当前处于省电模式，建议在设置 > 电池 > 省电模式中关闭或将WooAuto设为例外")
                        }
                        else -> { // Android 5-6
                            recommendations.add("设备当前处于省电模式，可能影响应用后台运行和网络连接")
                        }
                    }
                }
            }

            // 检查数据保护模式（Android 7.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (isDataSaverMode(context)) {
                    recommendations.add("数据保护模式已开启，建议在设置 > 网络和互联网 > 数据使用量中将WooAuto设为不受限制")
                }
            }

            // Android版本特定的建议
            when {
                androidVersion >= Build.VERSION_CODES.Q -> { // Android 10+
                    recommendations.add("Android 10+建议：检查后台应用刷新设置，确保WooAuto可以后台刷新")
                }
                androidVersion >= Build.VERSION_CODES.P -> { // Android 9
                    recommendations.add("Android 9建议：在设置 > 应用 > WooAuto > 电池中设置为'不受限制'")
                    recommendations.add("检查自适应电池功能，确保WooAuto不被智能限制")
                }
                androidVersion >= Build.VERSION_CODES.O -> { // Android 8.0-8.1
                    recommendations.add("Android 8建议：由于后台服务限制，请保持应用在前台或允许后台活动")
                    recommendations.add("在设置 > 应用 > WooAuto > 电池中关闭后台活动限制")
                }
                androidVersion >= Build.VERSION_CODES.N -> { // Android 7.0-7.1
                    recommendations.add("Android 7建议：检查Doze模式设置，将WooAuto添加到白名单")
                    recommendations.add("在设置 > 电池优化中将WooAuto设为'不优化'")
                }
                else -> { // Android 6及以下
                    recommendations.add("较老Android版本：主要注意电池优化和自启动权限")
                }
            }

            // 厂商特定建议
            if (isAggressivePowerManagementDevice()) {
                val manufacturer = Build.MANUFACTURER
                recommendations.add("检测到${manufacturer}设备，该品牌通常有激进的省电策略")
                recommendations.add("建议查看厂商特定的电源管理设置指南")
                
                when {
                    manufacturer.contains("xiaomi", ignoreCase = true) -> {
                        recommendations.add("MIUI系统：请在安全中心中设置WooAuto为自启动应用")
                    }
                    manufacturer.contains("huawei", ignoreCase = true) || 
                    manufacturer.contains("honor", ignoreCase = true) -> {
                        recommendations.add("EMUI/Magic UI：请在手机管家中设置应用启动管理")
                    }
                    manufacturer.contains("oppo", ignoreCase = true) -> {
                        recommendations.add("ColorOS：请在设置 > 电池 > 应用耗电管理中调整")
                    }
                    manufacturer.contains("vivo", ignoreCase = true) -> {
                        recommendations.add("OriginOS：请在i管家中设置后台应用管理")
                    }
                }
            }

            // 通用建议
            recommendations.add("建议在系统设置中关闭应用的自动休眠功能")
            recommendations.add("如果使用第三方省电应用，请将WooAuto加入白名单")

        } catch (e: Exception) {
            Log.e(TAG, "获取省电建议失败: ${e.message}")
            recommendations.add("检查设备电源管理设置失败，建议手动检查相关设置")
            recommendations.add("可以尝试在设置中搜索'电池优化'或'后台应用管理'")
        }

        return recommendations
    }

    /**
     * 检查是否为已知的激进省电策略厂商
     */
    fun isAggressivePowerManagementDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val aggressiveManufacturers = listOf(
            "xiaomi", "huawei", "honor", "oppo", "vivo", "oneplus", "samsung"
        )
        
        return aggressiveManufacturers.any { manufacturer.contains(it) }
    }

    /**
     * 获取厂商特定的省电设置指引
     */
    fun getManufacturerSpecificGuidance(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") -> {
                "MIUI系统建议：\n" +
                "1. 进入设置 > 应用管理 > WooAuto > 电池与性能\n" +
                "2. 关闭省电策略，选择'无限制'\n" +
                "3. 开启'锁屏显示'和'后台弹出界面'\n" +
                "4. 在安全中心中设置为自启动应用"
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                "EMUI/Magic UI系统建议：\n" +
                "1. 进入设置 > 应用 > 应用启动管理\n" +
                "2. 找到WooAuto，设置为手动管理\n" +
                "3. 开启'自动启动'、'关联启动'和'后台活动'\n" +
                "4. 在电池优化中将WooAuto设为不优化"
            }
            manufacturer.contains("oppo") -> {
                "ColorOS系统建议：\n" +
                "1. 进入设置 > 电池 > 应用耗电管理\n" +
                "2. 找到WooAuto，设置为'允许后台运行'\n" +
                "3. 在权限隐私中设置自启动权限\n" +
                "4. 关闭智能省电模式"
            }
            manufacturer.contains("vivo") -> {
                "OriginOS/Funtouch OS系统建议：\n" +
                "1. 进入设置 > 电池 > 后台应用管理\n" +
                "2. 将WooAuto设置为'允许'\n" +
                "3. 在应用管理中开启自启动权限\n" +
                "4. 关闭超级省电模式"
            }
            manufacturer.contains("samsung") -> {
                "One UI系统建议：\n" +
                "1. 进入设置 > 应用 > WooAuto > 电池\n" +
                "2. 选择'不优化'电池使用\n" +
                "3. 在设备维护中将WooAuto添加到未监视应用\n" +
                "4. 关闭自适应电池功能"
            }
            else -> {
                "通用建议：\n" +
                "1. 在系统设置中关闭应用的电池优化\n" +
                "2. 设置应用为自启动\n" +
                "3. 允许应用后台运行\n" +
                "4. 关闭省电模式或将应用加入白名单"
            }
        }
    }
} 