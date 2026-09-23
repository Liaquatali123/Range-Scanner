package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scanNumber: String,
    val cidr: String,
    val ports: String,
    val totalTargets: Int,
    val liveHosts: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val status: String // "Completed", "Stopped", "Failed"
)
