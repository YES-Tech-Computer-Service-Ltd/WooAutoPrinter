package com.example.wooauto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import com.example.wooauto.presentation.components.AppStatusStrip

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
    additionalActions: @Composable (() -> Unit)? = null,
    showTitle: Boolean = true,
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally
) {
    // 定义纯色背景，不使用透明度
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // 获取系统状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    // 使用Column而不是Box，这样可以添加底部分隔线
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = primaryColor)
    ) {
        // 添加顶部安全区域，避免与系统状态栏重叠
        Spacer(modifier = Modifier.height(statusBarHeight))
        
        // 主要内容行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 56.dp)
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
                    locale = locale,
                    primaryColor = primaryColor
                )
            } else if (showTitle && title.isNotEmpty()) {
                // 显示标题
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White,
                    textAlign = when(titleAlignment) {
                        Alignment.Start -> TextAlign.Start
                        Alignment.End -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (titleAlignment == Alignment.Start) 8.dp else 0.dp, top = 2.dp)
                )
            } else {
                // 空白占位符
                Spacer(modifier = Modifier.weight(1f))
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
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = androidx.compose.ui.res.stringResource(id = com.example.wooauto.R.string.refresh),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            
            // 右侧显示时间/网络/电量
            AppStatusStrip(modifier = Modifier.padding(start = 6.dp))
        }
        
        // 添加细线代替阴影，确保视觉分隔效果
        HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.08f))
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
    locale: Locale = Locale.getDefault(),
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // 使用纯色背景，避免透明度导致的色差
    // 使用稍浅一点的蓝色作为搜索框背景
    val searchBackgroundColor = Color(0xFFEEF5FF) // 浅蓝色
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 38.dp, max = 38.dp)
            // 移除阴影效果，避免色差
            .clip(RoundedCornerShape(20.dp))
            .background(searchBackgroundColor)
            // 添加边框代替阴影
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 15.sp,
            color = Color.Black // 使用纯黑色，增强对比度
        ),
        singleLine = true,
        cursorBrush = SolidColor(primaryColor),
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
                    contentDescription = androidx.compose.ui.res.stringResource(id = com.example.wooauto.R.string.search),
                    modifier = Modifier.size(16.dp),
                    tint = primaryColor
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = Color.Gray // 使用纯灰色，避免半透明
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
                            contentDescription = androidx.compose.ui.res.stringResource(id = com.example.wooauto.R.string.clear),
                            modifier = Modifier.size(14.dp),
                            tint = primaryColor
                        )
                    }
                }
            }
        }
    )
} 