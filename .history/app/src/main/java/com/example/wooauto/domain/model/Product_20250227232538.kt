package com.example.wooauto.domain.model

data class Product(
    val id: Long,
    val name: String,
    val permalink: String,
    val description: String,
    val shortDescription: String,
    val sku: String,
    val price: String,
    val regularPrice: String,
    val salePrice: String,
    val onSale: Boolean,
    val stockStatus: String,
    val stockQuantity: Int,
    val manageStock: Boolean,
    val categories: List<Category>,
    val images: List<String>,
    val status: String
) 