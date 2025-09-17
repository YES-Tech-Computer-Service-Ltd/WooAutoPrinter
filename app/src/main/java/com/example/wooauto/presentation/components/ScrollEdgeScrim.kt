package com.example.wooauto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.ScrollState

@Composable
fun ScrollableWithEdgeScrim(
    modifier: Modifier = Modifier,
    showScrollbar: Boolean = true,
    content: @Composable (Modifier, ScrollState) -> Unit
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var viewportPx by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.onGloballyPositioned { viewportPx = it.size.height }) {
        content(Modifier.verticalScroll(scrollState), scrollState)

        val showTop = scrollState.value > 0
        val showBottom = scrollState.value < scrollState.maxValue

        // 更明显的渐隐
        if (showTop) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.Transparent)))
            )
        }
        if (showBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.06f))))
            )
        }

        // 右侧细滚动条（仅当可滚动且视口已测量）
        if (showScrollbar && scrollState.maxValue > 0 && viewportPx > 0) {
            val totalPx = viewportPx + scrollState.maxValue
            val fraction = viewportPx.toFloat() / totalPx.toFloat()
            val thumbHeightPx = (viewportPx * fraction).coerceAtLeast(24f)
            val travelPx = (viewportPx - thumbHeightPx).coerceAtLeast(0f)
            val offsetPx = if (scrollState.maxValue > 0) (scrollState.value / scrollState.maxValue.toFloat()) * travelPx else 0f
            val offsetDp = with(density) { offsetPx.toDp() }
            val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = offsetDp)
                    .width(2.dp)
                    .height(thumbHeightDp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f))
            )
        }
    }
}



