package com.example

sealed interface ScanState {
    data object Idle : ScanState

    data class Preparing(
        val scanNumber: String
    ) : ScanState

    data class Running(
        val completed: Int,
        val total: Int,
        val liveHosts: Int
    ) : ScanState

    data class Completed(
        val total: Int,
        val liveHosts: Int,
        val durationMs: Long
    ) : ScanState

    data class Stopped(
        val completed: Int,
        val total: Int,
        val liveHosts: Int
    ) : ScanState

    data class Error(
        val message: String
    ) : ScanState
}
