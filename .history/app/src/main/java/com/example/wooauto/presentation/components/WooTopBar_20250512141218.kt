package com.example.wooauto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 统一的顶部栏组件，可以根据需要显示标题或搜索框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WooTopBar(
    title: String = "",
    showSearch: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchPlaceholder: String = "",
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    locale: Locale = Locale.getDefault(),
    modifier: Modifier = Modifier,
    additionalActions: @Composable (() -> Unit)? = null
) {
    // 创建渐变背景颜色
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) // 稍微亮一点
        )
    )
    
    Box(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth()
            .background(brush = gradientBrush)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 如果显示搜索框
            if (showSearch) {
                // 搜索框样式
                CustomSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    placeholder = searchPlaceholder,
                    locale = locale
                )
            } else {
                // 否则显示标题
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, // 增大字号
                        letterSpacing = 0.5.sp // 增加字母间距
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, top = 2.dp) // 稍微下移
                )
            }
            
            // 附加操作按钮
            additionalActions?.invoke()

            // 刷新按钮
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .size(44.dp)
                    .padding(end = 2.dp) // 减少右边距
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (locale.language == "zh") "刷新" else "Refresh",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp) // 调整图标大小
                    )
                }
            }
        }
    }
}

/**
 * 统一的搜索框组件
 */
@Composable
private fun CustomSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    locale: Locale = Locale.getDefault()
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 38.dp, max = 38.dp) // 降低高度，减少垂直空间
            .shadow(
                elevation = 3.dp, // 轻微的阴影效果
                shape = RoundedCornerShape(20.dp), // 增加圆角
                spotColor = Color.Black.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(20.dp)) // 与阴影圆角保持一致
            .background(Color.White),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 15.sp,
            color = Color.Black
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { /* 可以触发搜索动作 */ }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp), // 减少垂直内边距
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = if (locale.language == "zh") "搜索" else "Search",
                    modifier = Modifier.size(16.dp), // 搜索图标更小一点
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = Color.Gray.copy(alpha = 0.7f)
                        )
                    }
                    innerTextField()
                }
                
                if (value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(22.dp) // 清除按钮更小一点
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = if (locale.language == "zh") "清除" else "Clear",
                            modifier = Modifier.size(14.dp), // 清除图标更小一点
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    )
} 