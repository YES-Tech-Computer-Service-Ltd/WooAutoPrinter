package com.example.wooauto.presentation.screens.settings.stores

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.presentation.components.SettingsSubPageScaffold

@Composable
fun StoreEditScreen(
    navController: NavController,
    viewModel: StoreEditViewModel = hiltViewModel()
) {
    val storeName by viewModel.storeName.collectAsState()
    val siteUrl by viewModel.siteUrl.collectAsState()
    val consumerKey by viewModel.consumerKey.collectAsState()
    val consumerSecret by viewModel.consumerSecret.collectAsState()
    val storeAddress by viewModel.storeAddress.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()
    val isActive by viewModel.isActive.collectAsState()

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is StoreEditViewModel.UiEvent.SaveSuccess -> {
                    navController.popBackStack()
                }
                is StoreEditViewModel.UiEvent.ShowSnackbar -> {
                    // Handle snackbar (maybe pass a scaffold state or use a local one if needed, 
                    // but SettingsSubPageScaffold usually handles main content. 
                    // Here we might need to propagate it up or use a local SnackbarHost)
                }
            }
        }
    }

    SettingsSubPageScaffold(
        title = stringResource(if (storeName.isEmpty()) R.string.add_store else R.string.edit_store),
        onBackClick = { navController.popBackStack() },
        actions = {
            IconButton(onClick = { viewModel.onEvent(StoreEditEvent.Save) }) {
                Icon(imageVector = Icons.Default.Save, contentDescription = stringResource(R.string.save))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = storeName,
                onValueChange = { viewModel.onEvent(StoreEditEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.store_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = siteUrl,
                onValueChange = { viewModel.onEvent(StoreEditEvent.UrlChanged(it)) },
                label = { Text(stringResource(R.string.website_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = consumerKey,
                onValueChange = { viewModel.onEvent(StoreEditEvent.KeyChanged(it)) },
                label = { Text(stringResource(R.string.api_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = consumerSecret,
                onValueChange = { viewModel.onEvent(StoreEditEvent.SecretChanged(it)) },
                label = { Text(stringResource(R.string.api_secret)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            OutlinedTextField(
                value = storeAddress,
                onValueChange = { viewModel.onEvent(StoreEditEvent.AddressChanged(it)) },
                label = { Text(stringResource(R.string.store_address)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = storePhone,
                onValueChange = { viewModel.onEvent(StoreEditEvent.PhoneChanged(it)) },
                label = { Text(stringResource(R.string.store_phone)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.store_active))
                Switch(
                    checked = isActive,
                    onCheckedChange = { viewModel.onEvent(StoreEditEvent.ActiveChanged(it)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.onEvent(StoreEditEvent.Delete) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.delete_store))
            }
        }
    }
}

