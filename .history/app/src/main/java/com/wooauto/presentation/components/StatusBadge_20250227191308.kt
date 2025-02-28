package com.wooauto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 状态徽章组件
 * 用于显示订单状态，不同状态显示不同颜色
 *
 * @param status 状态文本
 */
@Composable
fun StatusBadge(
    status: String
) {
    val (backgroundColor, textColor) = when (status.lowercase()) {
        "pending" -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))    // 橙色系
        "processing" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0)) // 蓝色系
        "completed" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))  // 绿色系
        "cancelled" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))  // 红色系
        "refunded" -> Pair(Color(0xFFF3E5F5), Color(0xFF6A1B9A))   // 紫色系
        "failed" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))     // 红色系
        else -> Pair(Color(0xFFF5F5F5), Color(0xFF616161))         // 灰色系
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status,
            color = textColor,
            style = MaterialTheme.typography.labelMedium
        )
    }
} 