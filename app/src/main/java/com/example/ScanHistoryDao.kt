package com.example

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ScanHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ScanHistoryEntity)

    @Delete
    suspend fun delete(history: ScanHistoryEntity)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}
