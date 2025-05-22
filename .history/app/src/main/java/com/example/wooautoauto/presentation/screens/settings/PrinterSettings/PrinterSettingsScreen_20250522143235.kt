@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val printerConfigs by viewModel.printerConfigs.collectAsState()
    val currentPrinterConfig by viewModel.currentPrinterConfig.collectAsState()
    val printerStatus by viewModel.printerStatus.collectAsState()
    val isPrinting by viewModel.isPrinting.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val availablePrinters by viewModel.availablePrinters.collectAsState()
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var printerToDelete by remember { mutableStateOf<PrinterConfig?>(null) }
    var showScanDialog by remember { mutableStateOf(false) }
    
    // 将字符串资源移到 Composable 函数内部
    val printerConnectedSuccessMsg = stringResource(id = R.string.printer_connected_success)
    val printerDisconnectedMsg = stringResource(id = R.string.printer_disconnected)
    val printerConnectionFailedMsg = stringResource(id = R.string.printer_connection_failed)
    val testPrintSuccessMsg = stringResource(id = R.string.test_print_success)
    val testPrintFailedMsg = stringResource(id = R.string.test_print_failed)
    val setAsDefaultSuccessMsg = stringResource(id = R.string.set_as_default_success)
    val printerDeletedMsg = stringResource(id = R.string.printer_deleted)
    val closeButtonDesc = stringResource(R.string.close)
    val printerSettingsTitle = stringResource(id = R.string.printer_settings_title)
    val connectingPrinterMsg = stringResource(id = R.string.connecting_printer)
    val printerErrorMsg = stringResource(id = R.string.printer_error)
    
    LaunchedEffect(key1 = Unit) {
        viewModel.loadPrinterConfigs()
    }
    
    LaunchedEffect(printerStatus, connectionErrorMessage) {
        if (printerStatus == PrinterStatus.CONNECTED && showScanDialog) {
            showScanDialog = false
            snackbarHostState.showSnackbar(printerConnectedSuccessMsg)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(printerSettingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = closeButtonDesc)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { paddingValues ->
        PrinterSettingsContent(
            modifier = Modifier.padding(paddingValues),
            printerConfigs = printerConfigs,
            currentPrinterConfig = currentPrinterConfig,
            printerStatus = printerStatus,
            isPrinting = isPrinting,
            isScanning = isScanning,
            availablePrinters = availablePrinters,
            connectionErrorMessage = connectionErrorMessage,
            showDeleteDialog = showDeleteDialog,
            printerToDelete = printerToDelete,
            showScanDialog = showScanDialog,
            onConnect = { printer ->
                coroutineScope.launch {
                    if (printerStatus == PrinterStatus.CONNECTED && currentPrinterConfig?.id == printer.id) {
                        viewModel.disconnectPrinter(printer)
                        snackbarHostState.showSnackbar(printerDisconnectedMsg)
                    } else {
                        val connected = viewModel.connectPrinter(printer)
                        if (connected) {
                            snackbarHostState.showSnackbar(printerConnectedSuccessMsg)
                        } else {
                            snackbarHostState.showSnackbar(printerConnectionFailedMsg)
                        }
                    }
                }
            },
            onEdit = { printer ->
                navController.navigate(Screen.PrinterDetails.printerDetailsRoute(printer.id))
            },
            onDelete = { printer ->
                printerToDelete = printer
                showDeleteDialog = true
            },
            onTestPrint = { printer ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(connectingPrinterMsg)
                    try {
                        val success = viewModel.testPrint(printer)
                        if (success) {
                            snackbarHostState.showSnackbar(testPrintSuccessMsg)
                        } else {
                            val errorMsgDisplay = viewModel.connectionErrorMessage.value ?: testPrintFailedMsg
                            snackbarHostState.showSnackbar(errorMsgDisplay)
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("$printerErrorMsg ${e.message ?: ""}")
                    }
                }
            },
            onSetDefault = { printer, isDefault ->
                if (isDefault) {
                    val updatedConfig = printer.copy(isDefault = true)
                    viewModel.savePrinterConfig(updatedConfig)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(setAsDefaultSuccessMsg)
                    }
                }
            },
            onClearError = { viewModel.clearConnectionError() },
            onAddPrinter = { showScanDialog = true },
            onScan = { viewModel.scanPrinters() },
            onStopScan = { viewModel.stopScanning() },
            onDeviceSelected = { device ->
                viewModel.connectPrinter(device)
            },
            onDismissDeleteDialog = { showDeleteDialog = false },
            onConfirmDelete = {
                printerToDelete?.let { printer ->
                    viewModel.deletePrinterConfig(printer.id)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(printerDeletedMsg)
                    }
                }
                showDeleteDialog = false
            },
            onDismissScanDialog = { showScanDialog = false }
        )
    }
} 