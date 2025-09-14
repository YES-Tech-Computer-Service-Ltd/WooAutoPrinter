package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.wooauto.presentation.components.ScrollableWithEdgeScrim
import com.example.wooauto.presentation.components.SettingsSubPageScaffold
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
    
    Surface(color = MaterialTheme.colorScheme.surface) {
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
            },
            bottomBar = {}
        ) { paddingValuesInternal ->
            Column(
                modifier = Modifier
                    .padding(paddingValuesInternal)
                    .fillMaxSize()
            ) {
                ScrollableWithEdgeScrim(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                ) { scrollModifier, _ ->
                    Column(
                        modifier = scrollModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(0.96f)) {
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
                
                        }
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

    SettingsSubPageScaffold(
        navController = androidx.hilt.navigation.compose.hiltViewModel<SettingsViewModel>().let { _ ->
            // 占位参数，当前骨架主要承接布局；返回逻辑由 WooAppBar 处理
            // 这里传 NavController 不是必须，因此暂不依赖
            null
        } as? androidx.navigation.NavController ?: return,
        sectionKey = "general",
        subKey = "language"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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