package com.example.wooauto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Store Entity
 * Represents a WooCommerce store configuration.
 */
@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Store Basic Info
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    
    // API Configuration
    val siteUrl: String,
    val consumerKey: String,
    val consumerSecret: String,
    
    // Status
    val isActive: Boolean = true, // If the store is enabled for monitoring
    val isDefault: Boolean = false // Used to identify the primary store
)

