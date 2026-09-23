package com.example

data class ScanResult(
    val ip: String,
    val port: Int,
    val statusCode: Int, // 0 if connection exception
    val statusText: String, // "OK", "Timeout", "Connection Refused", etc.
    val responseTimeMs: Long,
    val isLive: Boolean,
    val error: String? = null,
    val protocol: String? = null // "http" or "https"
)
