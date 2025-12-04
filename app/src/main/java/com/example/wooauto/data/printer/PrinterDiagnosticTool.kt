package com.example.wooauto.data.printer

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.dantsu.escposprinter.connection.DeviceConnection
import com.example.wooauto.domain.models.PrinterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 打印机诊断工具类
 * 
 * 用于绕过标准库封装，直接操作底层 BluetoothSocket 进行双向通信测试。
 * 主要解决标准 ESC/POS 库无法主动获取打印机状态的问题。
 * 
 * 注意：此类使用了反射机制访问私有字段，必须配合 ProGuard 规则防止混淆。
 */
object PrinterDiagnosticTool {
    private const val TAG = "PrinterDiagnostic"

    /**
     * 尝试从 Dantsu 的 Connection 对象中提取底层 Socket
     * 公开方法，供其他模块复用反射逻辑
     */
    fun getSocket(connection: DeviceConnection?): BluetoothSocket? {
        if (connection == null) return null
        
        try {
            var clazz: Class<*>? = connection.javaClass
            var field: java.lang.reflect.Field? = null
            
            // 向上查找字段
            while (clazz != null && field == null) {
                try {
                    // 优先尝试 socket (Dantsu库常用名)
                    field = clazz.getDeclaredField("socket")
                } catch (e: NoSuchFieldException) {
                    try {
                        // 尝试 bluetoothSocket (备用名)
                        field = clazz.getDeclaredField("bluetoothSocket")
                    } catch (e2: NoSuchFieldException) {
                        clazz = clazz.superclass
                    }
                }
            }

            if (field != null) {
                field.isAccessible = true
                return field.get(connection) as? BluetoothSocket
            }
        } catch (e: Exception) {
            Log.e(TAG, "反射获取 Socket 失败: ${e.message}")
        }
        return null
    }

    /**
     * 发送指令并等待接收数据
     * 公开方法，供其他模块复用双向通信逻辑
     * @return 接收到的字节数组，如果超时或失败则返回 null
     */
    suspend fun sendAndReceiveRaw(
        inputStream: InputStream,
        outputStream: OutputStream,
        command: ByteArray,
        timeoutMs: Long = 1500
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // 清理缓冲区
            val available = inputStream.available()
            if (available > 0) {
                inputStream.skip(available.toLong())
            }

            outputStream.write(command)
            outputStream.flush()
            
            val endTime = System.currentTimeMillis() + timeoutMs
            
            while (System.currentTimeMillis() < endTime) {
                if (inputStream.available() > 0) {
                    val buffer = ByteArray(1024)
                    val len = inputStream.read(buffer)
                    if (len > 0) {
                        return@withContext buffer.copyOf(len)
                    }
                }
                delay(50)
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "通信异常: ${e.message}")
            return@withContext null
        }
    }

    /**
     * 执行完整的双向通信诊断
     * @param config 打印机配置
     * @param connection 当前连接对象
     * @param isConnectedChecker 一个函数，用于检查当前库认为的连接状态
     */
    suspend fun runFullDiagnostics(
        config: PrinterConfig, 
        connection: DeviceConnection?,
        isConnectedChecker: () -> Boolean
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("【连接状态测试报告】\n")
        sb.append("目标设备: ${config.name} (${config.address})\n")
        sb.append("时间: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("--------------------------------\n")

        try {
            // 1. 基础连接对象检查
            if (connection == null) {
                sb.append("❌ 错误: 连接对象为空 (Connection is null)\n")
                return@withContext sb.toString()
            }

            if (!isConnectedChecker()) {
                 sb.append("❌ 错误: 底层连接已断开 (isConnected = false)\n")
                 return@withContext sb.toString()
            }
            sb.append("✅ 底层连接: 活跃\n")

            // 2. 尝试获取 Socket
            sb.append("正在获取底层输入流...\n")
            val socket = getSocket(connection)
            
            if (socket == null) {
                sb.append("⚠️ 警告: 无法通过反射获取 Socket 对象\n")
                sb.append("   可能原因: 混淆导致字段改名，或库版本变更\n")
                sb.append("   -> 尝试执行盲发测试 (单向)...\n")
                try {
                    connection.write(byteArrayOf(0x1B, 0x40)) // ESC @ 初始化
                    sb.append("✅ 发送初始化命令成功 (单向)\n")
                } catch (e: Exception) {
                    sb.append("❌ 发送命令失败: ${e.message}\n")
                }
                return@withContext sb.toString()
            } else {
                sb.append("✅ 获取 Socket 对象成功\n")
            }

            if (!socket.isConnected) {
                sb.append("❌ Socket.isConnected() 返回 false\n")
                return@withContext sb.toString()
            }

            val inputStream = socket.inputStream
            val outputStream = socket.outputStream
            
            // 3. 交互测试 1: 打印机状态 (DLE EOT 1)
            // 10 04 01
            sb.append("\n>> 发送指令: 10 04 01 (查询状态)\n")
            val response1 = sendAndReceiveRaw(inputStream, outputStream, byteArrayOf(0x10, 0x04, 0x01))
            if (response1 != null) {
                val hex = response1.joinToString(" ") { "%02X".format(it) }
                sb.append("<< 收到响应: $hex\n")
            } else {
                sb.append("<< 无响应 (超时)\n")
            }

            // 4. 交互测试 2: 离线状态 (DLE EOT 2)
            // 10 04 02
            sb.append("\n>> 发送指令: 10 04 02 (离线查询)\n")
            val response2 = sendAndReceiveRaw(inputStream, outputStream, byteArrayOf(0x10, 0x04, 0x02), 1000)
            if (response2 != null) {
                val hex = response2.joinToString(" ") { "%02X".format(it) }
                sb.append("<< 收到响应: $hex\n")
            } else {
                sb.append("<< 无响应 (超时)\n")
            }
            
            sb.append("\n--------------------------------\n")
            sb.append("结论: 测试完成。\n")
            sb.append("如果看到 '<< 收到响应'，说明双向通信正常。\n")
            sb.append("如果全无响应，说明打印机不支持状态回传，或只连接了 TX 线。")

        } catch (e: Exception) {
            sb.append("\n❌ 测试过程发生严重异常: ${e.message}\n")
            Log.e(TAG, "Diagnostics failed", e)
        }
        
        return@withContext sb.toString()
    }
}
