package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.presentation.components.WooTopBar
import java.util.Locale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsDialogContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentLocale by viewModel.currentLocale.collectAsState(initial = Locale.getDefault())
    val scrollState = rememberScrollState()
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.language)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }
        ) { paddingValuesInternal ->
            Column(
                modifier = Modifier
                    .padding(paddingValuesInternal)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // 语言选项列表
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column {
                        // 英语选项
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLanguage(Locale.ENGLISH)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.language_changed))
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.english),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (currentLocale.language == "en") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider()
                        
                        // 中文选项
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLanguage(Locale.SIMPLIFIED_CHINESE)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.language_changed))
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.chinese),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (currentLocale.language == "zh") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentLocale by viewModel.currentLocale.collectAsState(initial = Locale.getDefault())
    
    Scaffold(
        topBar = {
            WooTopBar(
                title = stringResource(id = R.string.language),
                showSearch = false,
                isRefreshing = false,
                showRefreshButton = false,
                locale = currentLocale
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                    start = 0.dp,
                    end = 0.dp
                )
                .verticalScroll(rememberScrollState())
        ) {
            // 语言选项列表
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    // 英语选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAppLanguage(Locale.ENGLISH)
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.english),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (currentLocale.language == "en") {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider()
                    
                    // 中文选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAppLanguage(Locale.SIMPLIFIED_CHINESE)
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.chinese),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (currentLocale.language == "zh") {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
} 