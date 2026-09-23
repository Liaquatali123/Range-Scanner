package com.example

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "ScanViewModel"
    private val scanEngine = ScanEngine(application)
    
    // Room database dependencies
    private val database = AppDatabase.getDatabase(application)
    private val repository = ScanRepository(database.scanHistoryDao(), scanEngine)

    // Configuration Inputs (Saved across configuration changes via ViewModel)
    val cidrInput = mutableStateOf("104.16.0.0/24")
    val portsInput = mutableStateOf("80,443")
    val concurrencyInput = mutableStateOf("12")
    val timeoutInput = mutableStateOf("4000") // Default timeout in milliseconds (4 seconds)
    val allowInsecureSsl = mutableStateOf(false)

    // Current Scan State
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Stream of real-time scan results displayed in the UI
    val scanResults = mutableStateListOf<ScanResult>()

    // Current sequential scan directory references
    var currentScanNumber = mutableStateOf("01")
    var currentScanDir = mutableStateOf<File?>(null)

    // Saved/Status location string displayed in the UI
    val saveLocationText: String
        get() {
            return "/storage/emulated/0/RangeScanner"
        }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Expose all Scan History logs from Room database reactively
    val historyList: StateFlow<List<ScanHistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Collect scan results and append them to the real-time list
        viewModelScope.launch {
            scanEngine.resultFlow.collect { result ->
                // Ensure UI operations are lightweight
                scanResults.add(0, result) // Add new results at the top
            }
        }
    }

    /**
     * Start the scanning process
     */
    fun startScan() {
        val cidr = cidrInput.value.trim()
        val ports = portsInput.value.trim()
        val concurrency = concurrencyInput.value.toIntOrNull()?.coerceIn(1, 500) ?: 12
        val timeoutMs = timeoutInput.value.toLongOrNull()?.coerceIn(100, 30000) ?: 4000L

        // Accept and implement a user-configurable network timeout setting (between 1-30 seconds) on the ScanRepository
        val timeoutSeconds = (timeoutMs / 1000L).coerceIn(1L, 30L)
        repository.setTimeoutSeconds(timeoutSeconds)

        scanResults.clear()
        _errorMessage.value = null

        // Get the next scan directory beforehand so we can display it in UI
        try {
            val (nextNum, nextDir) = scanEngine.getNextScanDirectory()
            currentScanNumber.value = nextNum
            currentScanDir.value = nextDir
        } catch (e: Exception) {
            _scanState.value = ScanState.Error("Failed to initialize scan directory: ${e.message}")
            return
        }

        scanEngine.startScan(
            cidr = cidr,
            portsInput = ports,
            concurrency = concurrency,
            timeoutMs = timeoutMs,
            allowInsecureSsl = allowInsecureSsl.value,
            onStateChanged = { state ->
                _scanState.value = state
                if (state is ScanState.Preparing) {
                    currentScanNumber.value = state.scanNumber
                } else if (state is ScanState.Completed) {
                    saveScanToHistory(state, cidr, ports)
                } else if (state is ScanState.Stopped) {
                    saveScanToHistory(state, cidr, ports)
                }
            }
        )
    }

    /**
     * Save finished scan summary to Room Database
     */
    private fun saveScanToHistory(state: ScanState, cidr: String, ports: String) {
        viewModelScope.launch {
            val entity = when (state) {
                is ScanState.Completed -> {
                    ScanHistoryEntity(
                        scanNumber = currentScanNumber.value,
                        cidr = cidr,
                        ports = ports,
                        totalTargets = state.total,
                        liveHosts = state.liveHosts,
                        durationMs = state.durationMs,
                        status = "Completed"
                    )
                }
                is ScanState.Stopped -> {
                    ScanHistoryEntity(
                        scanNumber = currentScanNumber.value,
                        cidr = cidr,
                        ports = ports,
                        totalTargets = state.total,
                        liveHosts = state.liveHosts,
                        durationMs = 0L,
                        status = "Stopped"
                    )
                }
                else -> return@launch
            }
            repository.insert(entity)
        }
    }

    /**
     * Delete individual history entry
     */
    fun deleteHistoryItem(item: ScanHistoryEntity) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    /**
     * Delete all scan history logs
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    /**
     * Stop the current scanning process
     */
    fun stopScan() {
        scanEngine.stopScan()
    }

    /**
     * Copy all live hosts to the clipboard
     */
    fun copyAllLiveHostsToClipboard(): String {
        val dir = currentScanDir.value ?: return "No scan directory available. Run a scan first."
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val hosts = mutableListOf<String>()
        val files = dir.listFiles() ?: return "No saved results found in directory."

        for (file in files) {
            // Read status files (e.g. 200.txt, 403.txt, etc.) excluding the log
            if (file.isFile && file.name.endsWith(".txt") && file.name != "scan_log.txt") {
                try {
                    file.readLines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !hosts.contains(trimmed)) {
                            hosts.add(trimmed)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to read results from file: ${file.name}", e)
                }
            }
        }

        if (hosts.isEmpty()) {
            return "No live hosts found to copy."
        }

        val clipboardText = hosts.joinToString("\n")
        val clip = ClipData.newPlainText("Range Scanner Live Hosts", clipboardText)
        clipboard.setPrimaryClip(clip)

        return "Copied ${hosts.size} hosts to clipboard"
    }

    override fun onCleared() {
        super.onCleared()
        scanEngine.stopScan()
    }
}
