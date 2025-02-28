package com.wooauto.presentation.screens.orders

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.wooauto.presentation.navigation.Screen
import com.wooauto.presentation.theme.WooAutoTheme

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
    val isConfigured by viewModel.isConfigured.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 模拟数据 - 只在API配置完成后才会显示
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
                    onClick = { navController.navigate(Screen.Settings.route) }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
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
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 订单列表标题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "序号", 
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(0.5f)
                    )
                    Text(
                        text = "日期", 
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "订单号", 
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "客户", 
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "金额", 
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(0.8f)
                    )
                    Text(
                        text = "状态", 
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.weight(0.7f))
                }
                
                // 订单列表
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(orders.value) { order ->
                        OrderListItem(
                            order = order,
                            onItemClick = {
                                selectedOrder.value = order
                                showOrderDetail.value = true
                            },
                            onCompleteClick = {
                                // 这里将会调用修改订单状态的逻辑
                            }
                        )
                    }
                }
            }
            
            // 订单详情弹窗
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
fun OrderListItem(
    order: OrderItem,
    onItemClick: () -> Unit,
    onCompleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onItemClick,
        colors = CardDefaults.cardColors(
            containerColor = if (order.status == "已完成") 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = order.id, 
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.5f)
            )
            Text(
                text = order.date, 
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = order.orderNumber, 
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = order.customerName, 
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = order.amount, 
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.8f)
            )
            Text(
                text = order.status, 
                style = MaterialTheme.typography.bodyMedium,
                color = if (order.status == "已完成") 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onCompleteClick,
                modifier = Modifier.weight(0.7f),
                enabled = order.status != "已完成"
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "完成订单"
                )
            }
        }
    }
}

@Composable
fun OrderDetailDialog(order: OrderItem, onDismiss: () -> Unit) {
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
                    .padding(16.dp)
            ) {
                Text(
                    text = "订单详情",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OrderDetailRow("订单号:", order.orderNumber)
                OrderDetailRow("下单日期:", order.date)
                OrderDetailRow("客户名称:", order.customerName)
                OrderDetailRow("订单金额:", order.amount)
                OrderDetailRow("订单状态:", order.status)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "订单项目",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 这里可以添加订单项目列表
                Text(
                    text = "- 黄焖鸡米饭 × 1",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "- 可乐 × 2",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { /* 打印订单 */ }
                    ) {
                        Text("打印订单")
                    }
                    
                    Button(
                        onClick = onDismiss
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(2f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun OrdersScreenPreview() {
    WooAutoTheme {
        OrdersScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun OrderDetailDialogPreview() {
    WooAutoTheme {
        OrderDetailDialog(
            order = OrderItem("1", "2023-05-01", "WC12345", "张三", "¥108.00", "待处理"),
            onDismiss = {}
        )
    }
} 