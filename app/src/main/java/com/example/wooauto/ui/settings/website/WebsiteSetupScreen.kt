package com.example.wooauto.ui.settings.website

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wooauto.R
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

    // UI state
    var showApiSecret by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show snackbar for API test result
    LaunchedEffect(apiTestState) {
        if (apiTestState is ApiTestState.Success) {
            snackbarHostState.showSnackbar("API connection successful!")
        } else if (apiTestState is ApiTestState.Error) {
            val error = (apiTestState as ApiTestState.Error).message
            snackbarHostState.showSnackbar("API connection failed: $error")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.website_setup)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "WooCommerce API Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Website URL Field
            OutlinedTextField(
                value = websiteUrl,
                onValueChange = { viewModel.updateWebsiteUrl(it) },
                label = { Text(stringResource(R.string.website_url)) },
                placeholder = { Text("https://example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // API Key Field
            OutlinedTextField(
                value = apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text(stringResource(R.string.api_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // API Secret Field with toggle visibility
            OutlinedTextField(
                value = apiSecret,
                onValueChange = { viewModel.updateApiSecret(it) },
                label = { Text(stringResource(R.string.api_secret)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showApiSecret) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiSecret = !showApiSecret }) {
                        Icon(
                            painter = painterResource(
                                id = if (showApiSecret) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                            ),
                            contentDescription = if (showApiSecret) "Hide API Secret" else "Show API Secret"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Polling Interval Field
            OutlinedTextField(
                value = pollingInterval.toString(),
                onValueChange = {
                    val value = it.toIntOrNull() ?: 0
                    viewModel.updatePollingInterval(value)
                },
                label = { Text(stringResource(R.string.polling_interval)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("seconds") }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Test Connection Button
            Button(
                onClick = { viewModel.testApiConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiTestState !is ApiTestState.Testing &&
                        websiteUrl.isNotBlank() &&
                        apiKey.isNotBlank() &&
                        apiSecret.isNotBlank()
            ) {
                if (apiTestState is ApiTestState.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                }
                Text(stringResource(R.string.test_connection))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Help text
            Text(
                text = "You need to generate API credentials in your WooCommerce store. Go to WooCommerce > Settings > Advanced > REST API > Add Key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}