/**
 * 统一执行切纸命令
 * 根据当前打印机状态和型号，发送合适的切纸命令
 * @param forceCut 是否强制切纸
 */
fun executeUnifiedPaperCut(forceCut: Boolean = false) {
    Timber.d("executeUnifiedPaperCut")
    try {
        if (currentPrinter == null || !isConnected()) {
            Timber.e("切纸失败：打印机未连接")
            return
        }

        // 确保打印缓冲区中的数据已被发送
        printerOutputStream?.flush()
        
        // 使用ESC/POS切纸命令
        // 0x1D, 0x56, 66, 0x00 是完全切纸的命令
        // 0x1D, 0x56, 65, 0x00 是部分切纸的命令
        val cutCommand = byteArrayOf(0x1D, 0x56, 66, 0x00)
        
        Timber.d("正在发送切纸命令...")
        printerOutputStream?.write(cutCommand)
        printerOutputStream?.flush()
        
        // 短暂延迟确保命令被执行
        sleepTime(50)
        
        Timber.d("切纸命令已发送")
    } catch (e: Exception) {
        Timber.e(e, "切纸过程中出现异常")
    }
} 