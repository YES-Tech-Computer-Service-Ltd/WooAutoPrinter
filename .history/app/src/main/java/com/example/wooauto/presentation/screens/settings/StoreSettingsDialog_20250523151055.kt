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
import kotlinx.coroutines.launch
import com.example.wooauto.R

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
    
    // 获取当前商店信息状态
    val storeName by viewModel.storeName.collectAsState()
    val storeAddress by viewModel.storeAddress.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    
    // 本地状态用于输入字段
    var storeNameInput by remember { mutableStateOf(storeName) }
    var storeAddressInput by remember { mutableStateOf(storeAddress) }
    var storePhoneInput by remember { mutableStateOf(storePhone) }
    var currencySymbolInput by remember { mutableStateOf(currencySymbol) }
    
    LaunchedEffect(storeName, storeAddress, storePhone, currencySymbol) {
        storeNameInput = storeName
        storeAddressInput = storeAddress
        storePhoneInput = storePhone
        currencySymbolInput = currencySymbol
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
                        .padding(16.dp)
                ) {
                    // 店铺名称
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.store_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = storeNameInput,
                            onValueChange = { storeNameInput = it },
                            label = { Text(stringResource(R.string.store_name_hint)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 店铺地址
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.store_address),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = storeAddressInput,
                            onValueChange = { storeAddressInput = it },
                            label = { Text(stringResource(R.string.store_address_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 联系电话
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.store_phone),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = storePhoneInput,
                            onValueChange = { storePhoneInput = it },
                            label = { Text(stringResource(R.string.store_phone_hint)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 货币符号
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.currency_symbol),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // 货币选择下拉菜单
                        var expanded by remember { mutableStateOf(false) }
                        val currencyOptions = listOf(
                            "¥" to stringResource(R.string.currency_cny),
                            "$" to stringResource(R.string.currency_usd), 
                            "C$" to stringResource(R.string.currency_cad),
                            "€" to stringResource(R.string.currency_eur),
                            "£" to stringResource(R.string.currency_gbp),
                            "¥JP" to stringResource(R.string.currency_jpy),
                            "₩" to stringResource(R.string.currency_krw),
                            "₹" to stringResource(R.string.currency_inr),
                            "₽" to stringResource(R.string.currency_rub),
                            "₴" to stringResource(R.string.currency_uah)
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = currencyOptions.find { it.first == currencySymbolInput }?.let { "${it.first} (${it.second})" } ?: currencySymbolInput,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text(stringResource(R.string.currency_symbol_hint)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                currencyOptions.forEach { (symbol, name) ->
                                    DropdownMenuItem(
                                        text = { Text("$symbol ($name)") },
                                        onClick = {
                                            currencySymbolInput = symbol
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                                Button(                    onClick = {                        // 保存商店信息                        // viewModel.updateStoreName(storeNameInput)                        // viewModel.updateStoreAddress(storeAddressInput)                         // viewModel.updateStorePhone(storePhoneInput)                        viewModel.updateCurrencySymbol(currencySymbolInput)                                                coroutineScope.launch {                            snackbarHostState.showSnackbar(settingsSavedText)                            // 发送广播通知其他页面刷新货币符号                            android.content.Intent("com.example.wooauto.ACTION_CURRENCY_SYMBOL_UPDATED").let { intent ->                                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(viewModel.getApplicationContext()).sendBroadcast(intent)                            }                            // 延迟一点时间后关闭对话框，让用户看到保存成功提示                            kotlinx.coroutines.delay(1000)                            onClose()                        }                    },
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