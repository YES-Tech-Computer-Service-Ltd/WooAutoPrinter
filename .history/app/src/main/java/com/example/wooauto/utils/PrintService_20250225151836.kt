package com.example.wooauto.utils

import android.content.Context
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.example.wooauto.data.api.models.LineItem
import com.example.wooauto.data.api.models.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.dantsu.escposprinter.EscPosCharsetEncoding

class PrintService(private val context: Context) {

    private val TAG = "PrintService"
    private val prefsManager = PreferencesManager(context)

    // Enum for printer types
    enum class PrinterType {
        BLUETOOTH,
        NETWORK
    }

    // Enum for paper sizes
    enum class PaperSize(val width: Int) {
        SIZE_57MM(384),    // 58mm printer is actually 384 dots
        SIZE_80MM(576),    // 80mm printer is actually 576 dots
        SIZE_LETTER(832)   // A more standard size
    }

    // Data class to store printer information
    data class PrinterInfo(
        val id: String,
        val name: String,
        val type: PrinterType,
        val address: String,
        val port: Int = 9100,
        val model: String,
        val paperSize: PaperSize = PaperSize.SIZE_80MM,
        val isDefault: Boolean = false,
        val isBackup: Boolean = false,
        val autoPrint: Boolean = false,
        val copies: Int = 1
    )

    // Font size constants for different paper widths
    private object FontSize {
        const val SMALL = "[C]<font size='small'>"
        const val NORMAL = "[C]<font size='normal'>"
        const val MEDIUM = "[C]<font size='medium'>"
        const val LARGE = "[C]<font size='large'>"
        const val WIDE = "[C]<font size='wide'>"
    }

    // Print an order
    suspend fun printOrder(order: Order): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get default printer
                val printerInfo = getDefaultPrinter() ?: return@withContext false

                // Prepare print content based on template
                val content = generateOrderPrintContent(order, printerInfo.paperSize)

                // Print based on printer type
                val success = when (printerInfo.type) {
                    PrinterType.BLUETOOTH -> printToBluetoothPrinter(printerInfo, content)
                    PrinterType.NETWORK -> printToNetworkPrinter(printerInfo, content)
                }

                // If printing failed and we have a backup printer, try that
                if (!success) {
                    val backupPrinter = getBackupPrinter()
                    if (backupPrinter != null) {
                        Log.d(TAG, "Trying backup printer: ${backupPrinter.name}")
                        return@withContext when (backupPrinter.type) {
                            PrinterType.BLUETOOTH -> printToBluetoothPrinter(backupPrinter, content)
                            PrinterType.NETWORK -> printToNetworkPrinter(backupPrinter, content)
                        }
                    }
                }

                return@withContext success
            } catch (e: Exception) {
                Log.e(TAG, "Error printing order", e)
                return@withContext false
            }
        }
    }

    // Print to a Bluetooth printer
    private fun printToBluetoothPrinter(printerInfo: PrinterInfo, content: String): Boolean {
        try {
            // Find Bluetooth printer
            val printer = BluetoothPrintersConnections.selectFirstPaired()
                ?: return false

            // Create EscPos printer with correct paper size
            val escPosPrinter = EscPosPrinter(
                printer,
                203, // 203 DPI is standard
                printerInfo.paperSize.width.toFloat() / 203f, // Width in inches
                32, // Max paper feed
                charset("UTF-8")
            )

            // Print content
            escPosPrinter.printFormattedText(content)
            escPosPrinter.disconnectPrinter()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error printing to Bluetooth printer", e)
            return false
        }
    }

    // Print to a network printer
    private fun printToNetworkPrinter(printerInfo: PrinterInfo, content: String): Boolean {
        try {
            // Connect to the network printer
            val connection = TcpConnection(printerInfo.address, printerInfo.port, 5000)

            // Create EscPos printer with correct paper size
            val escPosPrinter = EscPosPrinter(
                connection,
                203, // 203 DPI is standard
                printerInfo.paperSize.width.toFloat() / 203f, // Width in inches
                32, // Max paper feed
                charset("UTF-8"))

            // Print content
            escPosPrinter.printFormattedText(content)
            escPosPrinter.disconnectPrinter()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error printing to Network printer", e)
            return false
        }
    }

    // Generate print content based on template and paper size
    private fun generateOrderPrintContent(order: Order, paperSize: PaperSize): String {
        val stringBuilder = StringBuilder()

        // Use different font sizes based on paper size
        val titleSize = when (paperSize) {
            PaperSize.SIZE_57MM -> FontSize.MEDIUM
            else -> FontSize.LARGE
        }

        val normalSize = when (paperSize) {
            PaperSize.SIZE_57MM -> FontSize.SMALL
            else -> FontSize.NORMAL
        }

        // Add store logo or name at the top
        stringBuilder.append("${titleSize}WOOAUTO ORDER</font>\n\n")

        // Add order info
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(order.dateCreated)

        stringBuilder.append("${normalSize}Order #: ${order.number}</font>\n")
        stringBuilder.append("${normalSize}Date: $formattedDate</font>\n")
        stringBuilder.append("${normalSize}Status: ${order.status.uppercase()}</font>\n\n")

        // Add customer info
        stringBuilder.append("${titleSize}CUSTOMER DETAILS</font>\n")
        stringBuilder.append("${normalSize}Name: ${order.billing.getFullName()}</font>\n")
        stringBuilder.append("${normalSize}Phone: ${order.billing.phone}</font>\n")
        if (order.billing.email.isNotEmpty()) {
            stringBuilder.append("${normalSize}Email: ${order.billing.email}</font>\n")
        }
        stringBuilder.append("\n")

        // Add shipping address if available
        stringBuilder.append("${titleSize}SHIPPING ADDRESS</font>\n")
        stringBuilder.append("${normalSize}${order.shipping.getFullName()}</font>\n")
        stringBuilder.append("${normalSize}${order.shipping.address1}</font>\n")
        if (!order.shipping.address2.isNullOrEmpty()) {
            stringBuilder.append("${normalSize}${order.shipping.address2}</font>\n")
        }
        stringBuilder.append("${normalSize}${order.shipping.city}, ${order.shipping.state} ${order.shipping.postcode}</font>\n")
        stringBuilder.append("${normalSize}${order.shipping.country}</font>\n\n")

        // Add order items
        stringBuilder.append("${titleSize}ORDER ITEMS</font>\n")

        // Add header
        when (paperSize) {
            PaperSize.SIZE_57MM -> {
                stringBuilder.append("[L]<font size='small'>Item[R]Qty x Price</font>\n")
                stringBuilder.append("[L]<font size='small'>---------------------</font>\n")
            }
            else -> {
                stringBuilder.append("[L]<font size='normal'>Item[R]Qty x Price = Total</font>\n")
                stringBuilder.append("[L]<font size='normal'>------------------------------</font>\n")
            }
        }

        // Add items
        for (item in order.lineItems) {
            when (paperSize) {
                PaperSize.SIZE_57MM -> {
                    stringBuilder.append("[L]<font size='small'>${item.name}</font>\n")
                    stringBuilder.append("[R]<font size='small'>${item.quantity} x ${item.subtotal}</font>\n")
                }
                else -> {
                    stringBuilder.append("[L]<font size='normal'>${item.name}[R]${item.quantity} x ${item.subtotal} = ${item.total}</font>\n")
                }
            }

            // Add item meta data if available
            item.metaData?.forEach { meta ->
                if (meta.key != "_") {
                    stringBuilder.append("[L]<font size='small'>  ${meta.key}: ${meta.value}</font>\n")
                }
            }
        }

        // Add totals
        stringBuilder.append("\n${normalSize}TOTAL: ${order.total}</font>\n")
        stringBuilder.append("${normalSize}Payment Method: ${order.paymentMethodTitle}</font>\n\n")

        // Add footer
        stringBuilder.append("${normalSize}Thank you for your order!</font>\n")
        stringBuilder.append("${FontSize.SMALL}Printed by WooAuto</font>\n")

        return stringBuilder.toString()
    }

    // Placeholder methods for printer management - in a real app this would use DataStore
    private suspend fun getDefaultPrinter(): PrinterInfo? {
        // In a real app, retrieve this from DataStore
        // This is just a placeholder
        return PrinterInfo(
            id = "1",
            name = "Default Printer",
            type = PrinterType.BLUETOOTH,
            address = "00:11:22:33:44:55",
            model = "Generic ESC/POS",
            paperSize = PaperSize.SIZE_80MM,
            isDefault = true
        )
    }

    private suspend fun getBackupPrinter(): PrinterInfo? {
        // In a real app, retrieve this from DataStore
        // This is just a placeholder
        return null
    }

    // Add a new printer to DataStore
    suspend fun addPrinter(printerInfo: PrinterInfo) {
        // In a real app, store this in DataStore
        // This is just a placeholder
        Log.d(TAG, "Added printer: ${printerInfo.name}")
    }

    // Get all printers from DataStore
    suspend fun getAllPrinters(): List<PrinterInfo> {
        // In a real app, retrieve this from DataStore
        // This is just a placeholder
        return listOf()
    }

    // Set a printer as default
    suspend fun setDefaultPrinter(printerId: String) {
        // In a real app, update this in DataStore
        // This is just a placeholder
        Log.d(TAG, "Set default printer: $printerId")
    }

    // Set a printer as backup
    suspend fun setBackupPrinter(printerId: String) {
        // In a real app, update this in DataStore
        // This is just a placeholder
        Log.d(TAG, "Set backup printer: $printerId")
    }

    // Test a printer connection
    suspend fun testPrinterConnection(printerInfo: PrinterInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val testContent = "[C]<font size='medium'>Test Print</font>\n" +
                        "[C]<font size='normal'>WooAuto App</font>\n" +
                        "[C]<font size='normal'>Printer Test Successful</font>\n" +
                        "[C]<font size='small'>Current Time: ${Date()}</font>\n"

                val success = when (printerInfo.type) {
                    PrinterType.BLUETOOTH -> printToBluetoothPrinter(printerInfo, testContent)
                    PrinterType.NETWORK -> printToNetworkPrinter(printerInfo, testContent)
                }

                return@withContext success
            } catch (e: Exception) {
                Log.e(TAG, "Error testing printer connection", e)
                return@withContext false
            }
        }
    }
}