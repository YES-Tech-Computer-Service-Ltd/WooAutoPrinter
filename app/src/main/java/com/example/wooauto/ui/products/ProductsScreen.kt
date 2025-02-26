package com.example.wooauto.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.data.database.entities.ProductEntity
import com.example.wooauto.ui.components.EmptyState
import com.example.wooauto.ui.components.ErrorState
import com.example.wooauto.ui.components.LoadingIndicator
import com.example.wooauto.ui.components.SearchField
import com.example.wooauto.ui.components.StatusBadge
import com.example.wooauto.ui.components.getStockStatusColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    onProductClick: (Long) -> Unit,
    viewModel: ProductViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshProducts()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.products)) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Search bar
                SearchField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = stringResource(R.string.search_products)
                )

                // Category filters
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { viewModel.updateCategoryFilter(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }

                // Products grid
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (uiState) {
                        is ProductsUiState.Loading -> {
                            LoadingIndicator()
                        }
                        is ProductsUiState.Empty -> {
                            EmptyState(
                                message = stringResource(R.string.no_products_found),
                                icon = R.drawable.ic_product
                            )
                        }
                        is ProductsUiState.Success -> {
                            val products = (uiState as ProductsUiState.Success).products
                            ProductsGrid(
                                products = products,
                                onProductClick = onProductClick
                            )
                        }
                        is ProductsUiState.Error -> {
                            val message = (uiState as ProductsUiState.Error).message
                            ErrorState(
                                message = message,
                                onRetry = { viewModel.refreshProducts() }
                            )
                        }
                    }

                    PullToRefreshContainer(
                        state = pullToRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Composable
fun ProductsGrid(
    products: List<ProductEntity>,
    onProductClick: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        items(
            items = products,
            key = { it.id }
        ) { product ->
            ProductItem(
                product = product,
                onClick = { onProductClick(product.id) }
            )
        }
    }
}

@Composable
fun ProductItem(
    product: ProductEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Product image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
            ) {
                if (product.imageUrl.isNullOrEmpty()) {
                    // Placeholder if no image
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = product.name.first().toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // Actual product image
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(product.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(id = R.drawable.ic_product),
                        placeholder = painterResource(id = R.drawable.ic_product)
                    )
                }

                // Stock status badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    StatusBadge(
                        status = if (product.stockStatus == "instock") "In Stock" else "Out of Stock",
                        color = getStockStatusColor(product.stockStatus, product.stockQuantity)
                    )
                }
            }

            // Product details
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // Product name
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Product price
                Text(
                    text = if (product.onSale && product.salePrice.isNotEmpty()) {
                        "${product.salePrice} (Sale)"
                    } else {
                        product.regularPrice
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (product.onSale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                // Stock quantity
                Text(
                    text = if (product.stockQuantity != null) {
                        "Stock: ${product.stockQuantity}"
                    } else {
                        product.stockStatus.replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}