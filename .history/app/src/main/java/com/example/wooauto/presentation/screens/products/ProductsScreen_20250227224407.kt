package com.example.wooauto.presentation.screens.products

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.presentation.theme.WooAutoTheme

data class ProductCategory(
    val id: String,
    val name: String
)

data class Product(
    val id: String,
    val name: String,
    val regularPrice: String,
    val salePrice: String?,
    val imageResId: Int,  // 用于示例，实际应使用URL
    val stock: Int,
    val status: String,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen() {
    // 模拟数据
    val categories = remember {
        listOf(
            ProductCategory("0", "全部分类"),
            ProductCategory("1", "主食"),
            ProductCategory("2", "小吃"),
            ProductCategory("3", "饮料"),
            ProductCategory("4", "甜品")
        )
    }
    
    val allProducts = remember {
        listOf(
            Product("1", "黄焖鸡米饭", "38.00", "35.00", R.drawable.ic_launcher_foreground, 100, "publish", "主食"),
            Product("2", "宫保鸡丁", "42.00", null, R.drawable.ic_launcher_foreground, 100, "publish", "主食"),
            Product("3", "糖醋里脊", "46.00", null, R.drawable.ic_launcher_foreground, 100, "publish", "主食"),
            Product("4", "水煮鱼", "68.00", "58.00", R.drawable.ic_launcher_foreground, 100, "publish", "主食"),
            Product("5", "薯条", "18.00", null, R.drawable.ic_launcher_foreground, 100, "publish", "小吃"),
            Product("6", "鸡米花", "22.00", null, R.drawable.ic_launcher_foreground, 100, "publish", "小吃"),
            Product("7", "可乐", "8.00", null, R.drawable.ic_launcher_foreground, 100, "publish", "饮料"),
            Product("8", "雪碧", "8.00", null, R.drawable.ic_launcher_foreground, 100, "publish", "饮料"),
            Product("9", "布丁", "12.00", null, R.drawable.ic_launcher_foreground, 100, "publish", "甜品"),
            Product("10", "芒果慕斯", "16.00", null, R.drawable.ic_launcher_foreground, 100, "publish", "甜品")
        )
    }
    
    var selectedCategoryId by remember { mutableStateOf("0") }
    var expanded by remember { mutableStateOf(false) }
    var showProductDetail by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    
    // 过滤产品
    val filteredProducts = remember(selectedCategoryId, allProducts) {
        if (selectedCategoryId == "0") {
            allProducts
        } else {
            val category = categories.first { it.id == selectedCategoryId }.name
            allProducts.filter { it.category == category }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 分类下拉框
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            OutlinedTextField(
                value = categories.first { it.id == selectedCategoryId }.name,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            selectedCategoryId = category.id
                            expanded = false
                        }
                    )
                }
            }
        }
        
        // 产品网格
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredProducts) { product ->
                ProductGridItem(
                    product = product,
                    onClick = {
                        selectedProduct = product
                        showProductDetail = true
                    }
                )
            }
        }
    }
    
    // 产品详情弹窗
    if (showProductDetail && selectedProduct != null) {
        ProductDetailDialog(
            product = selectedProduct!!,
            onDismiss = { showProductDetail = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductGridItem(product: Product, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            // 产品图片
            Image(
                painter = painterResource(id = product.imageResId),
                contentDescription = product.name,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 产品名称
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 价格
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (product.salePrice != null) {
                    Text(
                        text = "¥${product.salePrice}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "¥${product.regularPrice}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                    )
                } else {
                    Text(
                        text = "¥${product.regularPrice}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 库存
            Text(
                text = "库存: ${product.stock}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailDialog(product: Product, onDismiss: () -> Unit) {
    var regularPrice by remember { mutableStateOf(product.regularPrice) }
    var salePrice by remember { mutableStateOf(product.salePrice ?: "") }
    var stock by remember { mutableStateOf(product.stock.toString()) }
    var status by remember { mutableStateOf(product.status) }
    var category by remember { mutableStateOf(product.category) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "产品详情",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 产品图片
                Image(
                    painter = painterResource(id = product.imageResId),
                    contentDescription = product.name,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 产品名称
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 可编辑字段
                OutlinedTextField(
                    value = regularPrice,
                    onValueChange = { regularPrice = it },
                    label = { Text("标准价格") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { salePrice = it },
                    label = { Text("促销价格") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("库存") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 状态下拉框
                var statusExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when(status) {
                            "publish" -> "已发布"
                            "draft" -> "草稿"
                            else -> status
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("状态") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) }
                    )
                    
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("已发布") },
                            onClick = {
                                status = "publish"
                                statusExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("草稿") },
                            onClick = {
                                status = "draft"
                                statusExpanded = false
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 分类下拉框
                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                    )
                    
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        listOf("主食", "小吃", "饮料", "甜品").forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            // 这里将会调用保存修改的逻辑
                            onDismiss()
                        }
                    ) {
                        Text("保存")
                    }
                    
                    Button(
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProductsScreenPreview() {
    WooAutoTheme {
        ProductsScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun ProductDetailDialogPreview() {
    WooAutoTheme {
        ProductDetailDialog(
            product = Product("1", "黄焖鸡米饭", "38.00", "35.00", R.drawable.ic_launcher_foreground, 100, "publish", "主食"),
            onDismiss = {}
        )
    }
} 