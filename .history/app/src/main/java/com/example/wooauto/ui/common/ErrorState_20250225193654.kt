package com.example.wooauto.ui.common

data class ErrorState(
    val message: String,
    val throwable: Throwable? = null
) 