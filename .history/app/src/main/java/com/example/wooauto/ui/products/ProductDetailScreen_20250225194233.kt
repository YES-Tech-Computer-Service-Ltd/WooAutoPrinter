package com.example.wooauto.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.data.database.entities.ProductEntity
import com.example.wooauto.ui.components.ErrorState
import com.example.wooauto.ui.components.LoadingIndicator
import com.example.wooauto.ui.components.StatusBadge
import com.example.wooauto.ui.components.getStockStatusColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId: Long,
    onBackClick: () -> Unit,
    viewModel: ProductViewModel = viewModel()
) {
    val productDetailState by viewModel.productDetailState.collectAsState()
    val editProductState by viewModel.editProductState.collectAsState()

    // Load product details
    LaunchedEffect(productId) {
        viewModel.loadProductDetails(productId)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle edit product state changes
    LaunchedEffect(editProductState) {
        when (editProductState) {
            is EditProductState.Success -> {
                snackbarHostState.showSnackbar("Product updated successfully")
                viewModel.resetEditState()
            }
            is EditProductState.Error -> {
                val message = (editProductState as EditProductState.Error).message
                snackbarHostState.showSnackbar("Error: $message")
                viewModel.resetEditState()
            }
            else -> { /* do nothing */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_details)) },
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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    snackbarData = data
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (productDetailState) {
                is ProductDetailState.Loading -> {
                    LoadingIndicator()
                }
                is ProductDetailState.Success -> {
                    val product = (productDetailState as ProductDetailState.Success).product
                    ProductDetailsContent(
                        product = product,
                        isUpdating = editProductState is EditProductState.Updating,
                        onUpdateStock = { newStock ->
                            viewModel.updateProductStock(productId, newStock)
                        },
                        onUpdatePrices = { regularPrice, salePrice ->
                            viewModel.updateProductPrices(productId, regularPrice, salePrice)
                        }
                    )
                }
                is ProductDetailState.Error -> {
                    val message = (productDetailState as ProductDetailState.Error).message
                    ErrorState(
                        message = message,
                        onRetry = { viewModel.loadProductDetails(productId) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductDetailsContent(
    product: ProductEntity,
    isUpdating: Boolean,
    onUpdateStock: (Int?) -> Unit,
    onUpdatePrices: (String, String) -> Unit
) {
    // Form state
    var regularPrice by remember { mutableStateOf(product.regularPrice) }
    var salePrice by remember { mutableStateOf(product.salePrice) }
    var stockQuantity by remember {
        mutableStateOf(product.stockQuantity?.toString() ?: "")
    }

    // Reset form when product changes
    LaunchedEffect(product) {
        regularPrice = product.regularPrice
        salePrice = product.salePrice
        stockQuantity = product.stockQuantity?.toString() ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Product image and basic info
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Product image
                if (product.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(product.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(200.dp)
                            .padding(16.dp),
                        error = painterResource(id = R.drawable.ic_product),
                        placeholder = painterResource(id = R.drawable.ic_product)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_product),
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                }

                // Product name
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Product status and SKU
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Product status
                    StatusBadge(
                        status = product.status.uppercase(),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Product SKU
                    if (product.sku.isNotEmpty()) {
                        Text(
                            text = "SKU: ${product.sku}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Product categories
                if (product.categoryNames.isNotEmpty()) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = product.categoryNames.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Product description
        if (product.description.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Edit price section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Edit Prices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Regular Price Field
                OutlinedTextField(
                    value = regularPrice,
                    onValueChange = { regularPrice = it },
                    label = { Text("Regular Price") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Sale Price Field
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { salePrice = it },
                    label = { Text("Sale Price") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Update button
                Button(
                    onClick = { onUpdatePrices(regularPrice, salePrice) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating && (regularPrice != product.regularPrice || salePrice != product.salePrice)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Update Prices")
                    }
                }
            }
        }

        // Edit stock section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Edit Stock",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Current stock status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Stock Status: ",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    StatusBadge(
                        status = if (product.stockStatus == "instock") "In Stock" else "Out of Stock",
                        color = getStockStatusColor(product.stockStatus, product.stockQuantity)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stock Quantity Field
                OutlinedTextField(
                    value = stockQuantity,
                    onValueChange = { stockQuantity = it },
                    label = { Text("Stock Quantity") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Update button
                Button(
                    onClick = {
                        val stockValue = stockQuantity.toIntOrNull()
                        onUpdateStock(stockValue)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating && stockQuantity != (product.stockQuantity?.toString() ?: "")
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Update Stock")
                    }
                }
            }
        }
    }
}