package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: ScanViewModel, modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(0) }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Hero Image Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.scanner_hero_banner),
                contentDescription = "Scanner Header Graphic",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Overlay tint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
            // App Title Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, top = 16.dp)
            ) {
                Text(
                    text = "Range Scanner",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.testTag("app_title")
                )
                Text(
                    text = "Storage: ${viewModel.saveLocationText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).testTag("save_location_label")
                )
            }
        }

        // Tab Navigation
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("tab_row")
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Network Scanner")
                    }
                },
                modifier = Modifier.height(48.dp).testTag("tab_scanner")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan History")
                    }
                },
                modifier = Modifier.height(48.dp).testTag("tab_history")
            )
        }

        // Error message notification bar
        errorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Screen Content (Displays the active tab screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (selectedTab == 0) {
                NetworkScannerTab(viewModel = viewModel, scanState = scanState)
            } else {
                ScanHistoryTab(
                    viewModel = viewModel,
                    onLoadConfig = { selectedTab = 0 }
                )
            }
        }
    }
}

@Composable
fun NetworkScannerTab(viewModel: ScanViewModel, scanState: ScanState) {
    val context = LocalContext.current
    val results = viewModel.scanResults

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Form Inputs (CIDR & Ports)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = viewModel.cidrInput.value,
                onValueChange = { viewModel.cidrInput.value = it },
                label = { Text("CIDR Range") },
                placeholder = { Text("104.16.0.0/24") },
                singleLine = true,
                modifier = Modifier
                    .weight(1.5f)
                    .testTag("cidr_input"),
                enabled = scanState !is ScanState.Running && scanState !is ScanState.Preparing
            )

            OutlinedTextField(
                value = viewModel.portsInput.value,
                onValueChange = { viewModel.portsInput.value = it },
                label = { Text("Ports") },
                placeholder = { Text("80,443") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ports_input"),
                enabled = scanState !is ScanState.Running && scanState !is ScanState.Preparing
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Advanced Configuration Row (Concurrency limit and Timeout limit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = viewModel.concurrencyInput.value,
                onValueChange = { viewModel.concurrencyInput.value = it },
                label = { Text("Concurrency (1-500)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("concurrency_input"),
                enabled = scanState !is ScanState.Running && scanState !is ScanState.Preparing
            )

            OutlinedTextField(
                value = viewModel.timeoutInput.value,
                onValueChange = { viewModel.timeoutInput.value = it },
                label = { Text("Timeout (ms)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("timeout_input"),
                enabled = scanState !is ScanState.Running && scanState !is ScanState.Preparing
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Insecure TLS option and extra information
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(enabled = scanState !is ScanState.Running && scanState !is ScanState.Preparing) {
                        viewModel.allowInsecureSsl.value = !viewModel.allowInsecureSsl.value
                    }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
                    .testTag("ssl_switch_container")
            ) {
                Checkbox(
                    checked = viewModel.allowInsecureSsl.value,
                    onCheckedChange = { viewModel.allowInsecureSsl.value = it },
                    enabled = scanState !is ScanState.Running && scanState !is ScanState.Preparing,
                    modifier = Modifier.testTag("ssl_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Allow Insecure SSL",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Scan self-signed certificates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scan Directory and Live Count status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Scan Number: ${viewModel.currentScanNumber.value}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.testTag("scan_number_label")
                )
                val currentCount = when (scanState) {
                    is ScanState.Running -> scanState.liveHosts
                    is ScanState.Completed -> scanState.liveHosts
                    is ScanState.Stopped -> scanState.liveHosts
                    else -> 0
                }
                Text(
                    text = "Live Hosts: $currentCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("live_hosts_counter")
                )
            }

            // Copy Hosts Button (Enabled when results exist)
            Button(
                onClick = {
                    val resultMsg = viewModel.copyAllLiveHostsToClipboard()
                    Toast.makeText(context, resultMsg, Toast.LENGTH_SHORT).show()
                },
                enabled = results.any { it.isLive },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.testTag("copy_hosts_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Live Hosts")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control Buttons (Start Scan / Stop)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.startScan() },
                enabled = scanState !is ScanState.Running && scanState !is ScanState.Preparing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("start_scan_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Scan")
            }

            Button(
                onClick = { viewModel.stopScan() },
                enabled = scanState is ScanState.Running || scanState is ScanState.Preparing,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("stop_scan_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Scan")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Section
        AnimatedVisibility(visible = scanState is ScanState.Running || scanState is ScanState.Preparing || scanState is ScanState.Completed || scanState is ScanState.Stopped) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val progressText = when (scanState) {
                    is ScanState.Preparing -> "Preparing Scan..."
                    is ScanState.Running -> "Scanning... ${scanState.completed} / ${scanState.total}"
                    is ScanState.Completed -> "Scan Completed! Checked ${scanState.total} targets."
                    is ScanState.Stopped -> "Scan Stopped. Partially checked ${scanState.completed} targets."
                    else -> ""
                }

                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("progress_label")
                )

                Spacer(modifier = Modifier.height(6.dp))

                val progressProgress = when (scanState) {
                    is ScanState.Running -> {
                        if (scanState.total > 0) scanState.completed.toFloat() / scanState.total.toFloat() else 0f
                    }
                    is ScanState.Completed -> 1f
                    is ScanState.Stopped -> {
                        if (scanState.total > 0) scanState.completed.toFloat() / scanState.total.toFloat() else 0f
                    }
                    else -> 0f
                }

                // Smooth linear progress indicator animation
                val animatedProgress by animateFloatAsState(
                    targetValue = progressProgress,
                    animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                    label = "scan_progress"
                )

                if (scanState is ScanState.Preparing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("progress_bar")
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("progress_bar"),
                    )
                }

                if (scanState is ScanState.Completed) {
                    Text(
                        text = "Scan finished in ${String.format(Locale.US, "%.2f", scanState.durationMs / 1000.0)}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp).testTag("duration_label")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Results Section Header
        Text(
            text = "Results Stream",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 8.dp).testTag("results_header")
        )

        // Results lazy list (RecyclerView equivalent)
        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results to display. Press 'Start Scan' above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("no_results_text")
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("results_list"),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { result ->
                    ResultItem(result)
                }
            }
        }
    }
}

@Composable
fun ResultItem(result: ScanResult) {
    // Determine colors for status codes
    val (badgeBg, badgeText, statusLabel) = when {
        result.statusCode in 200..299 -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "HTTP ${result.statusCode}")
        result.statusCode in 300..399 -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "HTTP ${result.statusCode}")
        result.statusCode in 400..499 -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "HTTP ${result.statusCode}")
        result.statusCode in 500..599 -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "HTTP ${result.statusCode}")
        else -> Triple(Color(0xFFECEFF1), Color(0xFF455A64), result.statusText)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("result_item_${result.ip}_${result.port}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result.protocol == "https") Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (result.protocol == "https") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${result.ip}:${result.port}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${result.responseTimeMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusLabel,
                    color = badgeText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
fun ScanHistoryTab(
    viewModel: ScanViewModel,
    onLoadConfig: () -> Unit
) {
    val context = LocalContext.current
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }

    // Lazy load pagination state
    val listState = rememberLazyListState()

    // Reset limit to 10 on tab entry
    LaunchedEffect(Unit) {
        viewModel.resetHistoryLimit()
    }

    // Load more when scrolled near the end
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItemsCount - 2 && totalItemsCount > 0
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMoreHistory()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scan History Logs",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("history_title")
            )

            if (historyList.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearAllHistory() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No scan history saved yet.\nComplete a scan to see your previous summaries here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("no_history_text")
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("history_list"),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyList, key = { it.id }) { history ->
                    HistoryItemCard(
                        history = history,
                        dateFormat = dateFormat,
                        onDelete = { viewModel.deleteHistoryItem(history) },
                        onLoad = {
                            viewModel.cidrInput.value = history.cidr
                            viewModel.portsInput.value = history.ports
                            onLoadConfig()
                            Toast.makeText(context, "Loaded Configuration for Scan #${history.scanNumber}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    history: ScanHistoryEntity,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit,
    onLoad: () -> Unit
) {
    val statusColor = if (history.status == "Completed") Color(0xFF2E7D32) else Color(0xFFE65100)
    val statusIcon = if (history.status == "Completed") Icons.Default.CheckCircle else Icons.Default.Warning

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${history.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = history.status,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan #${history.scanNumber}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = dateFormat.format(Date(history.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Target Range: ${history.cidr}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = "Ports: ${history.ports}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Found ${history.liveHosts} live hosts from ${history.totalTargets} checked targets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (history.durationMs > 0L) {
                Text(
                    text = "Duration: ${String.format(Locale.US, "%.2f", history.durationMs / 1000.0)}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onLoad,
                    modifier = Modifier.testTag("load_config_${history.id}")
                ) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load Setup")
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_history_${history.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete Log",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
