package com.example.wooauto.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.util.Locale

/**
 * 兼容性搜索栏组件，内部使用新的WooTopBar
 */
@Composable
fun WooTopSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    placeholder: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
    additionalActions: @Composable (() -> Unit)? = null,
    showRefreshButton: Boolean = true,
    showTitle: Boolean = false,
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally
) {
    // 使用新的WooTopBar作为实现
    WooTopBar(
        showSearch = true, // 始终显示搜索框
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        searchPlaceholder = placeholder,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        locale = locale,
        modifier = modifier,
        additionalActions = additionalActions,
        showRefreshButton = showRefreshButton,
        showTitle = showTitle,
        titleAlignment = titleAlignment
    )
} 