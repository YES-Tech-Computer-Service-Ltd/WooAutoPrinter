package com.example.wooauto.data.api.requests

import com.google.gson.annotations.SerializedName

data class OrderUpdateRequest(
    @SerializedName("status")
    val status: String
)