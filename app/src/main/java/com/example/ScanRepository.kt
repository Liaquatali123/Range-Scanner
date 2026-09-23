package com.example

import kotlinx.coroutines.flow.Flow

class ScanRepository(
    private val scanHistoryDao: ScanHistoryDao,
    private val scanEngine: ScanEngine
) {
    val allHistory: Flow<List<ScanHistoryEntity>> = scanHistoryDao.getAllHistory()

    private var timeoutSeconds: Long = 4 // Default 4 seconds

    /**
     * Accept and implement a user-configurable network timeout setting (between 1-30 seconds),
     * passing this value to the OkHttp client configurations.
     */
    fun setTimeoutSeconds(seconds: Long) {
        val coerced = seconds.coerceIn(1, 30)
        timeoutSeconds = coerced
        scanEngine.updateRequestTimeout(coerced)
    }

    fun getTimeoutSeconds(): Long = timeoutSeconds

    suspend fun insert(history: ScanHistoryEntity) {
        scanHistoryDao.insert(history)
    }

    suspend fun delete(history: ScanHistoryEntity) {
        scanHistoryDao.delete(history)
    }

    suspend fun clearAll() {
        scanHistoryDao.clearAll()
    }
}
