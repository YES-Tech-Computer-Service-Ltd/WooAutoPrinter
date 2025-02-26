package com.example.wooauto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wooauto.R
import com.example.wooauto.ui.settings.viewmodel.SettingsViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onPrinterSetupClick: () -> Unit,
    onWebsiteSetupClick: () -> Unit,
    onLanguageChanged: () -> Unit = {}, // 添加语言变更回调
    viewModel: SettingsViewModel = viewModel()
) {
    val language by viewModel.language.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Website Connection Settings
            SettingsSection(
                title = stringResource(R.string.website_setup),
                onClick = onWebsiteSetupClick,
                iconResId = R.drawable.ic_web
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Printer Setup
            SettingsSection(
                title = stringResource(R.string.printer_setup),
                onClick = onPrinterSetupClick,
                iconResId = R.drawable.ic_print
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Language Selection
            LanguageSelector(
                currentLanguage = language,
                onLanguageSelected = {
                    viewModel.updateLanguage(it)
                    onLanguageChanged() // 调用语言变更回调
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // App Info
            AppInfoSection()
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    onClick: () -> Unit,
    iconResId: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_right),
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun LanguageSelector(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // English option
            LanguageOption(
                language = "English",
                code = "en",
                isSelected = currentLanguage == "en",
                onSelected = onLanguageSelected
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Chinese option
            LanguageOption(
                language = "中文",
                code = "zh",
                isSelected = currentLanguage == "zh",
                onSelected = onLanguageSelected
            )
        }
    }
}

@Composable
fun LanguageOption(
    language: String,
    code: String,
    isSelected: Boolean,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(code) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = language,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.weight(1f))

        if (isSelected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AppInfoSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "WooAuto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "© 2025 WooAuto",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}