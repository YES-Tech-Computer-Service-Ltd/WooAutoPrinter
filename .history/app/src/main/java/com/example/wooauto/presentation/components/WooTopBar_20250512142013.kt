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
    showRefreshButton: Boolean = true,
    locale: Locale = Locale.getDefault(),
    modifier: Modifier = Modifier,
    additionalActions: @Composable (() -> Unit)? = null
) {
    // 使用固定颜色，确保各页面颜色统一
    val backgroundColor = MaterialTheme.colorScheme.primary
    
    Box(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth()
            .background(color = backgroundColor)
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
                        fontSize = 20.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, top = 2.dp)
                )
            }
            
            // 附加操作按钮
            additionalActions?.invoke()

            // 刷新按钮，只在需要时显示
            if (showRefreshButton) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .size(44.dp)
                        .padding(end = 2.dp)
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
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
    
    // 使用半透明白色作为背景，与顶部栏更好地融合
    val searchBackgroundColor = Color.White.copy(alpha = 0.15f)
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 38.dp, max = 38.dp)
            // 移除阴影效果，减少色差
            .clip(RoundedCornerShape(20.dp))
            .background(searchBackgroundColor),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 15.sp,
            color = Color.White // 使用白色文字，与顶部栏文字颜色一致
        ),
        singleLine = true,
        cursorBrush = SolidColor(Color.White), // 修改光标颜色为白色
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { /* 可以触发搜索动作 */ }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = if (locale.language == "zh") "搜索" else "Search",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.8f) // 图标使用半透明白色
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f) // 占位符文字使用半透明白色
                        )
                    }
                    innerTextField()
                }
                
                if (value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = if (locale.language == "zh") "清除" else "Clear",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.8f) // 清除图标使用半透明白色
                        )
                    }
                }
            }
        }
    )
} 