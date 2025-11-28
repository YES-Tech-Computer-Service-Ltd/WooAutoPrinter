package com.example.wooauto.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun StoreInfoDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, address: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AppPopupDialog(
        title = "完善店铺信息",
        onConfirm = {
            if (name.isBlank()) {
                isError = true
            } else {
                onConfirm(name, address, phone)
            }
        },
        confirmButtonText = "保存",
        dismissButtonText = "稍后设置",
        onDismiss = onDismiss
    ) {
        Text("检测到您的店铺信息尚未设置。为了打印小票的完整性，请填写以下信息：")
        
        OutlinedTextField(
            value = name,
            onValueChange = { 
                name = it
                isError = false
            },
            label = { Text("店铺名称 (必填)") },
            singleLine = true,
            isError = isError,
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("店铺地址") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("联系电话") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        if (isError) {
            Text(
                text = "店铺名称不能为空",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

