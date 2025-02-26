package com.example.wooauto.ui.settings.website

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wooauto.R
import com.example.wooauto.ui.components.LoadingButton
import com.example.wooauto.ui.settings.viewmodel.ApiTestState
import com.example.wooauto.ui.settings.viewmodel.WebsiteSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteSetupScreen(
    onBackClick: () -> Unit,
    viewModel: WebsiteSetupViewModel = viewModel()
) {
    val websiteUrl by viewModel.websiteUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val apiSecret by viewModel.apiSecret.collectAsState()
    val pollingInterval by viewModel.pollingInterval.collectAsState()
    val apiTestState by viewModel.apiTestState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.website_setup)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Website URL
            OutlinedTextField(
                value = websiteUrl,
                onValueChange = { viewModel.updateWebsiteUrl(it) },
                label = { Text(stringResource(R.string.website_url)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text(stringResource(R.string.api_key)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            // API Secret
            OutlinedTextField(
                value = apiSecret,
                onValueChange = { viewModel.updateApiSecret(it) },
                label = { Text(stringResource(R.string.api_secret)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            // Polling Interval
            OutlinedTextField(
                value = pollingInterval.toString(),
                onValueChange = { 
                    it.toLongOrNull()?.let { seconds -> 
                        viewModel.updatePollingInterval(seconds)
                    }
                },
                label = { Text(stringResource(R.string.polling_interval)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                singleLine = true
            )

            // Test Connection Button
            LoadingButton(
                onClick = { viewModel.testApiConnection() },
                loading = apiTestState is ApiTestState.Testing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.test_connection))
            }

            // API Test Result
            when (apiTestState) {
                is ApiTestState.Success -> {
                    Text(
                        text = "API连接成功！",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                is ApiTestState.Error -> {
                    Text(
                        text = (apiTestState as ApiTestState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                else -> {}
            }
        }
    }
}