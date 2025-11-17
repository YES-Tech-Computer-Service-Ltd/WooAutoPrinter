package com.example.wooauto.data.printer.star

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.PrinterConfig

/**
 * Star 打印机蓝牙驱动（独立于现有 ESC/POS 流程）。
 * - 通过反射调用 Star SDK（避免强编译期依赖）
 * - 未集成 SDK 时，所有操作将优雅失败并记录日志
 */
class StarPrinterDriver(
    private val context: Context
) {
    companion object {
        private const val TAG = "StarPrinterDriver"
        private const val STAR_IO_PORT_CLASS = "com.starmicronics.stario.StarIOPort"
        private const val GET_PORT_METHOD = "getPort"
        private const val RELEASE_PORT_METHOD = "releasePort"
        private const val RETREIVE_STATUS_METHOD = "retreiveStatus" // Star SDK 的旧拼写接口
        private const val WRITE_PORT_METHOD = "writePort"
    }

    private var starPort: Any? = null
    private var connectedAddress: String? = null

    private fun isSdkAvailable(): Boolean {
        return try {
            Class.forName(STAR_IO_PORT_CLASS)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 通过 Star SDK 搜索蓝牙设备，返回 MAC 地址集合
     */
    fun searchBluetoothMacs(): Set<String> {
        if (!isSdkAvailable()) return emptySet()
        return try {
            val ioClazz = Class.forName(STAR_IO_PORT_CLASS)
            // 优先尝试带 Context 的重载
            val results: Any? = try {
                val m = ioClazz.getMethod("searchPrinter", String::class.java, Context::class.java)
                m.invoke(null, "BT:", context)
            } catch (_: NoSuchMethodException) {
                // 退化到无 Context 重载
                val m2 = ioClazz.getMethod("searchPrinter", String::class.java)
                m2.invoke(null, "BT:")
            }
            if (results == null) return emptySet()
            val list = (results as? Iterable<*>) ?: return emptySet()
            val macs = mutableSetOf<String>()
            for (pi in list) {
                try {
                    // PortInfo 常见方法：getMacAddress() / getPortName()
                    val mac = try {
                        val gm = pi!!.javaClass.getMethod("getMacAddress")
                        gm.invoke(pi) as? String
                    } catch (_: NoSuchMethodException) {
                        // 解析 portName: "BT:xx:xx:xx:xx:xx:xx"
                        val gp = pi!!.javaClass.getMethod("getPortName")
                        val portName = gp.invoke(pi) as? String
                        if (portName != null && portName.startsWith("BT:", ignoreCase = true) && portName.length >= 3 + 17) {
                            portName.substring(3, 3 + 17)
                        } else null
                    }
                    if (!mac.isNullOrBlank()) {
                        macs += mac.uppercase()
                    }
                } catch (_: Throwable) {
                }
            }
            macs
        } catch (e: Throwable) {
            Log.w(TAG, "Star 搜索蓝牙设备失败: ${e.message}")
            emptySet()
        }
    }

    /**
     * 连接 Star 打印机
     */
    suspend fun connect(device: BluetoothDevice, config: PrinterConfig): Boolean {
        if (!isSdkAvailable()) {
            Log.w(TAG, "Star SDK 未集成，无法使用 Star 打印流程")
            return false
        }
        return try {
            val clazz = Class.forName(STAR_IO_PORT_CLASS)
            val getPort = clazz.getMethod(
                GET_PORT_METHOD,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Context::class.java
            )
            val portName = "BT:${device.address}"
            // portSettings 可按需设置：如 "Portable", "escpos", "mini" 等，先留空以获取默认
            starPort = getPort.invoke(null, portName, "", 10000, context)
            connectedAddress = device.address
            Log.d(TAG, "Star 打印机连接成功: $portName")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "连接 Star 打印机失败: ${e.message}", e)
            starPort = null
            connectedAddress = null
            false
        }
    }

    fun isConnected(address: String): Boolean {
        return starPort != null && connectedAddress == address
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        if (!isSdkAvailable()) return
        try {
            val port = starPort ?: return
            val clazz = Class.forName(STAR_IO_PORT_CLASS)
            val release = clazz.getMethod(RELEASE_PORT_METHOD, Object::class.java)
            release.invoke(null, port)
        } catch (e: Throwable) {
            Log.w(TAG, "释放 Star 端口失败: ${e.message}", e)
        } finally {
            starPort = null
            connectedAddress = null
        }
    }

    /**
     * 简单连通性测试：尝试获取一次状态
     */
    suspend fun testConnection(): Boolean {
        if (!isSdkAvailable()) return false
        val port = starPort ?: return false
        return try {
            val clazz = port.javaClass
            // retreiveStatus() 无参方法，若能执行不抛异常，视为连通
            val method = clazz.getMethod(RETREIVE_STATUS_METHOD)
            method.invoke(port)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Star 连通性测试失败: ${e.message}")
            false
        }
    }

    /**
     * 打印订单（最小实现：将模板文本转为纯文本发送）
     * 注意：真实项目建议使用 Star 官方命令构建器以获得最佳排版与功能。
     */
    suspend fun printOrder(order: Order, config: PrinterConfig, formattedContent: String): Boolean {
        if (!isSdkAvailable()) return false
        val port = starPort ?: return false
        return try {
            val plain = toPlainText(formattedContent)
            val data = (plain + "\r\n").toByteArray(charset("UTF-8"))
            val write = port.javaClass.getMethod(WRITE_PORT_METHOD, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val written = write.invoke(port, data, 0, data.size) as? Int ?: -1
            written > 0
        } catch (e: Throwable) {
            Log.e(TAG, "Star 打印失败: ${e.message}", e)
            false
        }
    }

    /**
     * 打印测试页（简单文本）
     */
    suspend fun printTest(): Boolean {
        if (!isSdkAvailable()) return false
        val port = starPort ?: return false
        return try {
            val text = "STAR TEST PRINT\r\n----------------\r\nOK\r\n"
            val data = text.toByteArray(charset("UTF-8"))
            val write = port.javaClass.getMethod(WRITE_PORT_METHOD, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val written = write.invoke(port, data, 0, data.size) as? Int ?: -1
            written > 0
        } catch (e: Throwable) {
            Log.e(TAG, "Star 测试打印失败: ${e.message}", e)
            false
        }
    }

    /**
     * 将 ESC/POS 风格标签文本转为纯文本（基本可读）
     */
    private fun toPlainText(formatted: String): String {
        // 去掉常见对齐/加粗/条码等标签，保留文本行
        val noTags = formatted
            .replace(Regex("\\[(L|C|R|B|CODE|BARCODE|QR|CUT|LF|FS|GS|ESC|D)\\][^\\n]*"), "")
            .replace(Regex("<[^>]*>"), "") // 简单去 HTML-like 标签
        // 规整空行
        return noTags.lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\\n{3,}"), "\n\n")
            .ifBlank { " " }
    }
}


