package com.example.wooauto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column

@Composable
fun ScrollableWithEdgeScrim(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier, androidx.compose.foundation.ScrollState) -> Unit
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier) {
        content(Modifier.verticalScroll(scrollState), scrollState)

        val showTop = scrollState.value > 0
        val showBottom = scrollState.value < scrollState.maxValue
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
                    .height(16.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.06f))))
            )
        }
    }
}


