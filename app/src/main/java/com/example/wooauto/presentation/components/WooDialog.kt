package com.example.wooauto.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

data class DialogAction(
    val text: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false
)

@Composable
fun WooDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    primary: DialogAction? = null,
    secondary: DialogAction? = null,
    icon: ImageVector? = null
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val widthFraction = if (isTablet) 0.48f else 0.92f

    SettingsModal(
        onDismissRequest = onDismiss,
        widthFraction = widthFraction,
        heightFraction = 0.0f, // wrap by content via sizeIn
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .sizeIn(minHeight = 120.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (secondary != null) {
                    OutlinedButton(
                        onClick = secondary.onClick,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(secondary.text) }
                }
                if (secondary != null && primary != null) Spacer(modifier = Modifier.width(10.dp))
                if (primary != null) {
                    val colors = if (primary.destructive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    Button(
                        onClick = primary.onClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = colors
                    ) { Text(primary.text) }
                }
            }
        }
    }
}


