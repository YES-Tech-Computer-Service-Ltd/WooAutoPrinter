package com.example.wooauto.domain.model

data class Category(
    val id: Long,
    val name: String,
    val slug: String,
    val parent: Long,
    val description: String,
    val count: Int
) 