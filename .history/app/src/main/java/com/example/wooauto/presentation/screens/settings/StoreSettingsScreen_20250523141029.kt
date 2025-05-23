package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import kotlinx.coroutines.launch

/**
 * 店铺设置对话框内容
 * 允许用户配置店铺基本信息，用于小票打印等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsDialogContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val settingsSavedText = stringResource(R.string.settings_saved)
    
    // 获取当前设置值
    val currentStoreName by viewModel.settingsRepository.getStoreNameFlow().collectAsState()
    val currentStoreAddress by viewModel.settingsRepository.getStoreAddressFlow().collectAsState()
    val currentStorePhone by viewModel.settingsRepository.getStorePhoneFlow().collectAsState()
    val currentCurrencySymbol by viewModel.settingsRepository.getCurrencySymbolFlow().collectAsState()
    
    // 本地状态变量
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var storePhone by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("") }
    var storeEmail by remember { mutableStateOf("") }
    var businessHours by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var storeSlogan by remember { mutableStateOf("") }
    
    // 初始化本地状态
    LaunchedEffect(currentStoreName, currentStoreAddress, currentStorePhone, currentCurrencySymbol) {
        storeName = currentStoreName
        storeAddress = currentStoreAddress
        storePhone = currentStorePhone
        currencySymbol = currentCurrencySymbol
    }
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.store_settings)) },
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
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 基本信息
                    Text(
                        text = stringResource(R.string.store_basic_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text(stringResource(R.string.store_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = storeAddress,
                        onValueChange = { storeAddress = it },
                        label = { Text(stringResource(R.string.store_address)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = storePhone,
                            onValueChange = { storePhone = it },
                            label = { Text(stringResource(R.string.store_phone)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = currencySymbol,
                            onValueChange = { currencySymbol = it },
                            label = { Text(stringResource(R.string.currency_symbol)) },
                            modifier = Modifier.weight(0.5f),
                            singleLine = true
                        )
                    }
                    
                    // 联系信息
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(R.string.contact_information),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = storeEmail,
                        onValueChange = { storeEmail = it },
                        label = { Text(stringResource(R.string.store_email)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text(stringResource(R.string.store_website)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // 营业信息
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(R.string.business_information),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = businessHours,
                        onValueChange = { businessHours = it },
                        label = { Text(stringResource(R.string.business_hours)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        placeholder = { Text(stringResource(R.string.business_hours_placeholder)) }
                    )
                    
                    OutlinedTextField(
                        value = storeSlogan,
                        onValueChange = { storeSlogan = it },
                        label = { Text(stringResource(R.string.store_slogan)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        placeholder = { Text(stringResource(R.string.store_slogan_placeholder)) }
                    )
                }
                
                Button(
                    onClick = {
                        coroutineScope.launch {
                            // 保存基本店铺信息
                            viewModel.settingsRepository.setStoreName(storeName)
                            viewModel.settingsRepository.setStoreAddress(storeAddress)
                            viewModel.settingsRepository.setStorePhone(storePhone)
                            viewModel.settingsRepository.setCurrencySymbol(currencySymbol)
                            
                            // TODO: 保存其他店铺信息(当前存储系统暂时不支持，需要扩展)
                            // viewModel.settingsRepository.setStoreEmail(storeEmail)
                            // viewModel.settingsRepository.setStoreWebsite(website)
                            // viewModel.settingsRepository.setBusinessHours(businessHours)
                            // viewModel.settingsRepository.setStoreSlogan(storeSlogan)
                            
                            snackbarHostState.showSnackbar(settingsSavedText)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
                ) {
                    Text(stringResource(id = R.string.save_settings))
                }
            }
        }
    }
} 