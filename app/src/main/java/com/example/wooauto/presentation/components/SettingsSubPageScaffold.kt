package com.example.wooauto.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 统一的“设置二级页”骨架：
 * - 保留左侧侧栏与全局 TopBar（由外层 AppContent 承载）；
 * - 负责生成“一级 - 二级”复合标题；
 * - 负责基本内边距与滚动容器交由调用方决定；
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubPageScaffold(
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable (Modifier) -> Unit
) {
    // TopBar 由全局 WooAppBar 生成，我们只负责内层内容区域。
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                )
                .padding(contentPadding)
        ) {
            content(Modifier)
        }
    }
}


