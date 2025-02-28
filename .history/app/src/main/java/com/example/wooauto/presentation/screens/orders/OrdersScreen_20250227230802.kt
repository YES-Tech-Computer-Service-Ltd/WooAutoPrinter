package com.example.wooauto.presentation.screens.orders

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.domain.models.Order
import com.example.wooauto.presentation.theme.WooAutoTheme

data class OrderItem(
    val id: String, 
    val date: String, 
    val orderNumber: String, 
    val customerName: String, 
    val amount: String, 
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    Log.d("OrdersScreen", "订单屏幕初始化")
    
    val isConfigured by viewModel.isConfigured.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 当进入此屏幕时执行刷新操作
    LaunchedEffect(key1 = Unit) {
        Log.d("OrdersScreen", "LaunchedEffect 触发，刷新订单数据")
        // 这里应该调用ViewModel的刷新方法
        // 例如: viewModel.refreshOrders()
    }
    
    // 当从设置页面返回时，检查配置状态并刷新
    LaunchedEffect(key1 = isConfigured) {
        Log.d("OrdersScreen", "配置状态变化: $isConfigured")
        if (isConfigured) {
            // 配置已完成，刷新数据
            // viewModel.refreshOrders()
        }
    }
    
    // 这里使用模拟数据，实际应用中应该从viewModel获取
    val orders = remember {
        mutableStateOf(
            listOf(
                OrderItem("1", "2023-05-01", "WC12345", "张三", "¥108.00", "待处理"),
                OrderItem("2", "2023-05-01", "WC12346", "李四", "¥86.50", "待处理"),
                OrderItem("3", "2023-05-02", "WC12347", "王五", "¥156.00", "已完成"),
                OrderItem("4", "2023-05-02", "WC12348", "赵六", "¥92.50", "待处理"),
                OrderItem("5", "2023-05-03", "WC12349", "钱七", "¥138.00", "已完成")
            )
        )
    }
    
    val searchQuery = remember { mutableStateOf("") }
    val showOrderDetail = remember { mutableStateOf(false) }
    val selectedOrder = remember { mutableStateOf<OrderItem?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 加载中状态
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在加载...")
            }
        } 
        // API 未配置状态
        else if (!isConfigured) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "未配置",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "WooCommerce API 未配置",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请先在设置页面配置您的 WooCommerce API 连接信息，才能查看订单数据。",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { 
                        Log.d("OrdersScreen", "点击前往设置按钮，导航到：${NavigationItem.Settings.route}")
                        navController.navigate(NavigationItem.Settings.route)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置"
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("前往设置")
                }
            }
        } 
        // 已配置，显示订单列表
        else {
            Log.d("OrdersScreen", "显示订单列表")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部提示
                Text(
                    text = "订单列表",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 搜索框
                OutlinedTextField(
                    value = searchQuery.value,
                    onValueChange = { searchQuery.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索订单...") },
                    leadingIcon = { 
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 订单列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filteredOrders = orders.value.filter {
                        it.orderNumber.contains(searchQuery.value, ignoreCase = true) ||
                        it.customerName.contains(searchQuery.value, ignoreCase = true)
                    }
                    
                    if (filteredOrders.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "未找到订单",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        items(filteredOrders) { order ->
                            OrderCard(
                                order = order,
                                onClick = {
                                    selectedOrder.value = order
                                    showOrderDetail.value = true
                                }
                            )
                        }
                    }
                }
            }
            
            // 显示订单详情对话框
            if (showOrderDetail.value && selectedOrder.value != null) {
                OrderDetailDialog(
                    order = selectedOrder.value!!,
                    onDismiss = { showOrderDetail.value = false }
                )
            }
        }
    }
}

@Composable
fun OrderCard(
    order: OrderItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "订单号: ${order.orderNumber}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "客户: ${order.customerName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "日期: ${order.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = order.amount,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (order.status == "已完成") 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else 
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = order.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (order.status == "已完成") 
                                MaterialTheme.colorScheme.primary
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }
                    
                    if (order.status == "已完成") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已完成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailDialog(
    order: OrderItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "订单详情",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "订单号: ${order.orderNumber}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "客户: ${order.customerName}",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "日期: ${order.date}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "金额: ${order.amount}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "状态: ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (order.status == "已完成") 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else 
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = order.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (order.status == "已完成") 
                                MaterialTheme.colorScheme.primary
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Preview
@Composable
fun OrdersScreenPreview() {
    WooAutoTheme {
        OrdersScreen()
    }
} 