// Create a new file: com.example.woauto.utils.WooFoodHelper.kt

package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.data.api.models.LineItem
import com.example.wooauto.data.api.models.MetaData

/**
 * Helper class for parsing WooFood plugin data
 */
object WooFoodHelper {
    private const val TAG = "WooFoodHelper"

    /**
     * Parse WooFood options from a line item's metadata
     */
    fun getFormattedOptions(lineItem: LineItem): List<String> {
        val options = mutableListOf<String>()

        lineItem.metaData?.forEach { meta ->
            if (meta.key == "_exceptions") {
                try {
                    val rawValue = meta.value.toString()
                    if (rawValue.contains("value=")) {
                        // Extract the chosen option value
                        val valueStart = rawValue.indexOf("value=") + 6
                        val valueEnd = rawValue.indexOf(",", valueStart).takeIf { it > 0 } ?: rawValue.length
                        val optionValue = rawValue.substring(valueStart, valueEnd).trim()

                        options.add("Option: $optionValue")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WooFood options", e)
                }
            }
        }

        return options
    }

    /**
     * Clean up the product name by removing the "or X" part and adding the selected option
     */
    fun getCleanProductName(lineItem: LineItem): String {
        var name = lineItem.name

        // Check if this is an item with options
        if (name.contains(" or ") && lineItem.metaData != null) {
            val baseName = name.split(" or ").first().trim()
            val selectedOption = getSelectedOption(lineItem)

            if (selectedOption.isNotEmpty()) {
                name = "$baseName - $selectedOption"
            } else {
                name = baseName
            }
        }

        return name
    }

    /**
     * Get the selected option from metadata
     */
    private fun getSelectedOption(lineItem: LineItem): String {
        lineItem.metaData?.forEach { meta ->
            if (meta.key == "_exceptions") {
                try {
                    val rawValue = meta.value.toString()
                    if (rawValue.contains("value=")) {
                        val valueStart = rawValue.indexOf("value=") + 6
                        val valueEnd = rawValue.indexOf(",", valueStart).takeIf { it > 0 } ?: rawValue.length
                        return rawValue.substring(valueStart, valueEnd).trim()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting selected option", e)
                }
            }
        }

        return ""
    }
}