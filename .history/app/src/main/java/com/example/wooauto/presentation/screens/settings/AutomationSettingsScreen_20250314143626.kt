                // 保存设置按钮
                androidx.compose.material3.Button(
                    onClick = {
                        // 保存设置
                        viewModel.saveAutomationSettings(
                            automaticOrderProcessing = automaticOrderProcessing,
                            automaticPrinting = automaticPrinting,
                            inventoryAlerts = inventoryAlerts,
                            dailyBackup = dailyBackup,
                            defaultTemplateType = selectedTemplate
                        )
                        
                        // 显示保存确认
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("自动化设置已保存")
                        }
                        
                        // 通知服务重启轮询，确保设置立即生效
                        viewModel.notifyServiceToRestartPolling()
                        
                        // 返回上一页
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                ) {
                    Text("保存设置")
                } 