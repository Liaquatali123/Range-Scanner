package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class ScanEngine(private val context: Context) {

    private val tag = "ScanEngine"

    // Configuration limits
    var maxTargetLimit: Int = 65536
    var defaultConcurrency: Int = 12
    var requestTimeoutSeconds: Long = 4

    // Configurable ignored status codes for "live" definitions (e.g. HTTP 302 Found)
    val ignoredStatusCodes = mutableSetOf(302)

    // Result streaming flow
    private val _resultFlow = MutableSharedFlow<ScanResult>(extraBufferCapacity = 100)
    val resultFlow: SharedFlow<ScanResult> = _resultFlow

    // Active scan job
    private var scanJob: Job? = null
    private val fileWriteMutex = Mutex()

    // Flag to determine if scan was stopped
    private var isStopped = false

    // Statistics breakdown
    private val statusBreakdown = mutableMapOf<Int, Int>()
    private val errorBreakdown = mutableMapOf<String, Int>()

    // Unsafe TLS OkHttpClient (Optional, isolated behind user choice if requested, but default is secure)
    private var secureClient: OkHttpClient? = null
    private var insecureClient: OkHttpClient? = null

    init {
        setupHttpClients()
    }

    private fun setupHttpClients() {
        val baseBuilder = OkHttpClient.Builder()
            .connectTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(requestTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)

        secureClient = baseBuilder.build()

        // Configure insecure client with disabled SSL verification (isolated/explicitly documented)
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            insecureClient = baseBuilder
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize insecure SSL context, fallback to secure client", e)
            insecureClient = secureClient
        }
    }

    /**
     * Rebuild the OkHttp clients with a custom timeout configuration (between 1-30 seconds).
     */
    fun updateRequestTimeout(seconds: Long) {
        requestTimeoutSeconds = seconds.coerceIn(1, 30)
        setupHttpClients()
    }

    /**
     * Parse CIDR notation and expand it into a list of IP addresses.
     * Throws IllegalArgumentException for validation errors.
     */
    fun expandCidr(cidr: String): List<String> {
        val parts = cidr.split("/")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid CIDR format. Must be X.X.X.X/Y")
        }

        val ipStr = parts[0].trim()
        val prefixStr = parts[1].trim()

        val prefix = prefixStr.toIntOrNull() ?: throw IllegalArgumentException("Invalid subnet prefix: $prefixStr")
        if (prefix < 0 || prefix > 32) {
            throw IllegalArgumentException("Subnet prefix must be between 0 and 32")
        }

        val octets = ipStr.split(".")
        if (octets.size != 4) {
            throw IllegalArgumentException("Invalid IPv4 address format: $ipStr")
        }

        var baseIpLong = 0L
        for (i in 0..3) {
            val octet = octets[i].toIntOrNull() ?: throw IllegalArgumentException("Invalid octet in IP: ${octets[i]}")
            if (octet < 0 || octet > 255) {
                throw IllegalArgumentException("IP octet must be between 0 and 255: $octet")
            }
            baseIpLong = (baseIpLong shl 8) or octet.toLong()
        }

        // Calculate mask
        val mask = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        val network = baseIpLong and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)

        val totalHosts = broadcast - network + 1

        val list = mutableListOf<String>()
        when {
            prefix == 32 -> {
                list.add(longToIp(baseIpLong))
            }
            prefix == 31 -> {
                list.add(longToIp(network))
                list.add(longToIp(network + 1))
            }
            else -> {
                // Exclude network and broadcast addresses
                for (ipLong in (network + 1)..(broadcast - 1)) {
                    list.add(longToIp(ipLong))
                }
            }
        }
        return list
    }

    private fun longToIp(ipLong: Long): String {
        return String.format(
            Locale.US,
            "%d.%d.%d.%d",
            (ipLong ushr 24) and 0xFF,
            (ipLong ushr 16) and 0xFF,
            (ipLong ushr 8) and 0xFF,
            ipLong and 0xFF
        )
    }

    /**
     * Parse and clean ports list.
     */
    fun parsePorts(portsInput: String): List<Int> {
        val cleanInput = portsInput.replace("\\s".toRegex(), "")
        if (cleanInput.isEmpty()) {
            throw IllegalArgumentException("Ports list cannot be empty")
        }

        val parts = cleanInput.split(",")
        val result = mutableListOf<Int>()
        for (part in parts) {
            val portNum = part.toIntOrNull() ?: throw IllegalArgumentException("Malformed port value: $part")
            if (portNum < 1 || portNum > 65535) {
                throw IllegalArgumentException("Port number out of range (1-65535): $portNum")
            }
            if (!result.contains(portNum)) {
                result.add(portNum)
            }
        }
        return result
    }

    /**
     * Find next sequential scan directory (e.g., 01, 02, etc.) under RangeScanner/
     */
    fun getNextScanDirectory(): Pair<String, File> {
        val rootDir = File("/storage/emulated/0", "RangeScanner")
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        var maxIndex = 0
        val files = rootDir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    val name = file.name
                    val index = name.toIntOrNull()
                    if (index != null && index > maxIndex) {
                        maxIndex = index
                    }
                }
            }
        }

        val nextIndex = maxIndex + 1
        val scanNumStr = String.format(Locale.US, "%02d", nextIndex)
        val scanDir = File(rootDir, scanNumStr)
        scanDir.mkdirs()

        return Pair(scanNumStr, scanDir)
    }

    /**
     * Start the concurrent scanning operation.
     */
    fun startScan(
        cidr: String,
        portsInput: String,
        concurrency: Int,
        timeoutMs: Long,
        allowInsecureSsl: Boolean,
        onStateChanged: (ScanState) -> Unit
    ) {
        if (scanJob?.isActive == true) {
            onStateChanged(ScanState.Error("A scan is already in progress"))
            return
        }

        isStopped = false
        statusBreakdown.clear()
        errorBreakdown.clear()

        scanJob = CoroutineScope(Dispatchers.Default).launch {
            val ips: List<String>
            val ports: List<Int>

            try {
                ips = expandCidr(cidr)
                ports = parsePorts(portsInput)
            } catch (e: IllegalArgumentException) {
                withContext(Dispatchers.Main) {
                    onStateChanged(ScanState.Error(e.message ?: "Validation error"))
                }
                return@launch
            }

            val totalTargets = ips.size * ports.size * 2 // HTTP and HTTPS for each IP+Port
            if (totalTargets == 0) {
                withContext(Dispatchers.Main) {
                    onStateChanged(ScanState.Error("No scan targets generated"))
                }
                return@launch
            }

            val (scanNumber, scanDir) = getNextScanDirectory()

            withContext(Dispatchers.Main) {
                onStateChanged(ScanState.Preparing(scanNumber))
            }

            val startTime = Date()
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            var completedCount = 0
            var liveHostsCount = 0

            val semaphore = Semaphore(if (concurrency > 0) concurrency else defaultConcurrency)
            val baseClient = if (allowInsecureSsl) insecureClient ?: secureClient!! else secureClient!!
            
            // Build specialized dynamic client with custom timeout choice
            val client = baseClient.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()

            // Start scanning target list concurrently
            val jobs = mutableListOf<Job>()

            coroutineScope {
                for (ip in ips) {
                    if (isStopped) break
                    for (port in ports) {
                        if (isStopped) break

                        // HTTP Target
                        jobs.add(launch {
                            if (isStopped) return@launch
                            semaphore.acquire()
                            try {
                                if (isStopped) return@launch
                                val result = checkTarget(client, ip, port, "http")
                                completedCount++

                                val wasLive = handleResult(scanDir, result)
                                if (wasLive) {
                                    liveHostsCount++
                                }

                                withContext(Dispatchers.Main) {
                                    onStateChanged(ScanState.Running(completedCount, totalTargets, liveHostsCount))
                                }
                            } finally {
                                semaphore.release()
                            }
                        })

                        // HTTPS Target
                        jobs.add(launch {
                            if (isStopped) return@launch
                            semaphore.acquire()
                            try {
                                if (isStopped) return@launch
                                val result = checkTarget(client, ip, port, "https")
                                completedCount++

                                val wasLive = handleResult(scanDir, result)
                                if (wasLive) {
                                    liveHostsCount++
                                }

                                withContext(Dispatchers.Main) {
                                    onStateChanged(ScanState.Running(completedCount, totalTargets, liveHostsCount))
                                }
                            } finally {
                                semaphore.release()
                            }
                        })
                    }
                }

                // Wait for all active checks to finish or cancel if stopped
                jobs.joinAll()
            }

            val endTime = Date()
            val durationMs = endTime.time - startTime.time

            // Write final scan log asynchronously to prevent UI freeze or blocking on completion
            CoroutineScope(Dispatchers.IO).launch {
                writeScanLog(
                    scanDir = scanDir,
                    scanNumber = scanNumber,
                    cidr = cidr,
                    portsInput = portsInput,
                    startTimeStr = df.format(startTime),
                    endTimeStr = df.format(endTime),
                    durationMs = durationMs,
                    totalTargets = totalTargets,
                    completedTargets = completedCount,
                    liveHosts = liveHostsCount,
                    stopped = isStopped
                )
            }

            withContext(Dispatchers.Main) {
                if (isStopped) {
                    onStateChanged(ScanState.Stopped(completedCount, totalTargets, liveHostsCount))
                } else {
                    onStateChanged(ScanState.Completed(totalTargets, liveHostsCount, durationMs))
                }
            }
        }
    }

    /**
     * Stop active scan
     */
    fun stopScan() {
        if (scanJob?.isActive == true) {
            isStopped = true
            scanJob?.cancel()
            Log.d(tag, "Scan stopped by user")
        }
    }

    /**
     * Test a single target endpoint using OkHttp
     */
    private suspend fun checkTarget(
        client: OkHttpClient,
        ip: String,
        port: Int,
        protocol: String
    ): ScanResult = withContext(Dispatchers.IO) {
        val url = "$protocol://$ip:$port/"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RangeScanner/1.0")
            .header("Accept", "*/*")
            .header("Connection", "close")
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val responseTime = System.currentTimeMillis() - startTime
                val code = response.code
                val text = response.message

                val isLive = code in 200..599 && !ignoredStatusCodes.contains(code)

                ScanResult(
                    ip = ip,
                    port = port,
                    statusCode = code,
                    statusText = if (text.isEmpty()) "HTTP $code" else text,
                    responseTimeMs = responseTime,
                    isLive = isLive,
                    protocol = protocol
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error scanning $protocol://$ip:$port : ${e.javaClass.simpleName} - ${e.message}")
            val responseTime = System.currentTimeMillis() - startTime
            val (errorName, errorDetail) = when (e) {
                is SocketTimeoutException -> "Timeout" to (e.message ?: "Read/Connect Timeout")
                is ConnectException -> "Connection Refused" to (e.message ?: "Connection Refused")
                is UnknownHostException -> "Unknown Host" to (e.message ?: "DNS Lookup Failed")
                is SSLHandshakeException -> "SSL Error" to (e.message ?: "SSL/TLS Handshake Failed")
                is IOException -> {
                    val msg = e.message ?: ""
                    when {
                        msg.contains("Cleartext", ignoreCase = true) -> "Cleartext Blocked" to "Cleartext HTTP traffic is not permitted by security policy"
                        msg.contains("unreachable", ignoreCase = true) -> "Unreachable" to msg
                        msg.contains("route", ignoreCase = true) -> "No Route" to msg
                        msg.contains("reset", ignoreCase = true) -> "Connection Reset" to msg
                        else -> "Socket Error" to msg.ifEmpty { "I/O Error" }
                    }
                }
                else -> "Other" to (e.message ?: "Unknown Connection Exception")
            }

            ScanResult(
                ip = ip,
                port = port,
                statusCode = 0,
                statusText = errorName,
                responseTimeMs = responseTime,
                isLive = false,
                error = errorDetail,
                protocol = protocol
            )
        }
    }

    /**
     * Handle scan result: Stream it to UI flow, update counters, and append to files.
     * Returns true if the host is LIVE.
     */
    private suspend fun handleResult(scanDir: File, result: ScanResult): Boolean {
        // Stream to UI Flow immediately
        _resultFlow.emit(result)

        if (result.statusCode > 0) {
            // Update status statistics
            synchronized(statusBreakdown) {
                statusBreakdown[result.statusCode] = (statusBreakdown[result.statusCode] ?: 0) + 1
            }

            // Save to status code file (e.g. 200.txt) if it is considered live (asynchronously off-thread)
            if (result.isLive) {
                CoroutineScope(Dispatchers.IO).launch {
                    writeResultToFile(scanDir, result)
                }
                return true
            }
        } else {
            // Update error statistics
            synchronized(errorBreakdown) {
                val errType = result.statusText
                errorBreakdown[errType] = (errorBreakdown[errType] ?: 0) + 1
            }
        }
        return false
    }

    /**
     * Write result entry atomically to status file (e.g., scanDir/200.txt)
     */
    private suspend fun writeResultToFile(scanDir: File, result: ScanResult) {
        fileWriteMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val fileName = "${result.statusCode}.txt"
                    val file = File(scanDir, fileName)
                    FileWriter(file, true).use { writer ->
                        writer.write("${result.ip}:${result.port}\n")
                    }
                } catch (e: IOException) {
                    Log.e(tag, "Failed to write scan result to status file", e)
                }
            }
        }
    }

    /**
     * Write/Update the scan_log.txt summary file
     */
    private suspend fun writeScanLog(
        scanDir: File,
        scanNumber: String,
        cidr: String,
        portsInput: String,
        startTimeStr: String,
        endTimeStr: String,
        durationMs: Long,
        totalTargets: Int,
        completedTargets: Int,
        liveHosts: Int,
        stopped: Boolean
    ) {
        fileWriteMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val logFile = File(scanDir, "scan_log.txt")
                    FileWriter(logFile, false).use { writer ->
                        writer.write("Range Scanner\n")
                        writer.write("=============\n\n")
                        writer.write("Scan Number: $scanNumber\n")
                        writer.write("CIDR: $cidr\n")
                        writer.write("Ports: $portsInput\n")
                        writer.write("Start Time: $startTimeStr\n")
                        writer.write("End Time: $endTimeStr\n")
                        writer.write("Duration: ${durationMs / 1000.0} seconds\n")
                        writer.write("Total Targets: $totalTargets\n")
                        writer.write("Completed Targets: $completedTargets\n")
                        writer.write("Live Hosts: $liveHosts\n")
                        writer.write("Stopped: $stopped\n\n")

                        writer.write("Status Breakdown:\n")
                        val sortedStatus = statusBreakdown.toSortedMap()
                        for ((code, count) in sortedStatus) {
                            writer.write("$code: $count\n")
                        }
                        if (sortedStatus.isEmpty()) {
                            writer.write("None\n")
                        }
                        writer.write("\n")

                        writer.write("Errors:\n")
                        writer.write("Timeout: ${errorBreakdown["Timeout"] ?: 0}\n")
                        writer.write("Connection Refused: ${errorBreakdown["Connection Refused"] ?: 0}\n")
                        writer.write("SSL Error: ${errorBreakdown["SSL Error"] ?: 0}\n")
                        writer.write("Other: ${errorBreakdown["Other"] ?: 0}\n")
                    }
                } catch (e: IOException) {
                    Log.e(tag, "Failed to write scan_log.txt", e)
                }
            }
        }
    }
}
