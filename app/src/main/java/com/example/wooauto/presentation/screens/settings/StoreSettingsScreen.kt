package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.example.wooauto.R
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.presentation.components.ScrollableWithEdgeScrim
import com.example.wooauto.presentation.components.SettingsSubPageScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val settingsSavedText = stringResource(R.string.settings_saved)
    
    // 获取当前商店信息状态
    val storeName by viewModel.storeName.collectAsState()
    val storeAddress by viewModel.storeAddress.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val storeOpenRestoreMode by viewModel.settingsRepository.getStoreOpenRestoreModeFlow()
        .collectAsState(initial = DomainSettingRepository.STORE_OPEN_RESTORE_MODE_AUTO)
    
    // 本地状态用于输入字段
    var storeNameInput by remember { mutableStateOf(storeName) }
    var storeAddressInput by remember { mutableStateOf(storeAddress) }
    var storePhoneInput by remember { mutableStateOf(storePhone) }
    var currencySymbolInput by remember { mutableStateOf(currencySymbol) }
    var storeOpenRestoreModeInput by remember { mutableStateOf(storeOpenRestoreMode) }
    
    LaunchedEffect(storeName, storeAddress, storePhone, currencySymbol, storeOpenRestoreMode) {
        storeNameInput = storeName
        storeAddressInput = storeAddress
        storePhoneInput = storePhone
        currencySymbolInput = currencySymbol
        storeOpenRestoreModeInput = storeOpenRestoreMode
    }
    
    SettingsSubPageScaffold() { modifier ->
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // 顶部说明
            Text(
                text = stringResource(R.string.store_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ScrollableWithEdgeScrim(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { scrollModifier, _ ->
                Column(
                    modifier = scrollModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(modifier = Modifier.fillMaxWidth(0.96f)) {
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

                        // 开店恢复状态
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.store_open_restore_mode_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            Text(
                                text = stringResource(R.string.store_open_restore_mode_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            var restoreExpanded by remember { mutableStateOf(false) }
                            val restoreModeOptions = listOf(
                                DomainSettingRepository.STORE_OPEN_RESTORE_MODE_AUTO to stringResource(R.string.store_open_restore_mode_auto),
                                DomainSettingRepository.STORE_OPEN_RESTORE_MODE_DISABLE to stringResource(R.string.store_open_restore_mode_fixed_disable),
                                DomainSettingRepository.STORE_OPEN_RESTORE_MODE_ENABLE to stringResource(R.string.store_open_restore_mode_fixed_enable)
                            )
                            val restoreLabel = restoreModeOptions
                                .firstOrNull { it.first == storeOpenRestoreModeInput }
                                ?.second
                                ?: storeOpenRestoreModeInput

                            ExposedDropdownMenuBox(
                                expanded = restoreExpanded,
                                onExpandedChange = { restoreExpanded = !restoreExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = restoreLabel,
                                    onValueChange = { },
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.store_open_restore_mode_title)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = restoreExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )

                                ExposedDropdownMenu(
                                    expanded = restoreExpanded,
                                    onDismissRequest = { restoreExpanded = false }
                                ) {
                                    restoreModeOptions.forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                storeOpenRestoreModeInput = value
                                                restoreExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // 保存按钮
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = {
                        // 保存商店信息
                        viewModel.updateStoreName(storeNameInput)
                        viewModel.updateStoreAddress(storeAddressInput)
                        viewModel.updateStorePhone(storePhoneInput)
                        viewModel.updateCurrencySymbol(currencySymbolInput)
                        
                        coroutineScope.launch {
                            try {
                                viewModel.settingsRepository.setStoreOpenRestoreMode(storeOpenRestoreModeInput)
                            } catch (_: Exception) {
                                // ignore
                            }
                            snackbarHostState.showSnackbar(settingsSavedText)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(id = R.string.save_settings))
                }
            }
        }
    }

    // SnackbarHost 需要在 SettingsSubPageScaffold 外部
    SnackbarHost(hostState = snackbarHostState)
}
