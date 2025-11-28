package com.example.wooauto.domain.models

data class Store(
    val id: Long = 0,
    val name: String,
    val siteUrl: String,
    val consumerKey: String,
    val consumerSecret: String,
    val address: String? = null,
    val phone: String? = null,
    val isActive: Boolean = true,
    val isDefault: Boolean = false
)

