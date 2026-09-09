package com.example.data.local

import androidx.room.*
import com.example.data.model.IncomeReport
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Query("SELECT * FROM income_reports ORDER BY date DESC")
    fun getAllReportsFlow(): Flow<List<IncomeReport>>

    @Query("SELECT * FROM income_reports WHERE date = :date")
    suspend fun getReportByDate(date: String): IncomeReport?

    @Query("SELECT * FROM income_reports WHERE date = :date")
    fun getReportByDateFlow(date: String): Flow<IncomeReport?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: IncomeReport)

    @Delete
    suspend fun deleteReport(report: IncomeReport)

    @Query("SELECT * FROM income_reports WHERE isSynced = 0")
    suspend fun getUnsyncedReports(): List<IncomeReport>

    @Query("UPDATE income_reports SET isSynced = :isSynced, syncTimestamp = :timestamp WHERE date = :date")
    suspend fun updateSyncStatus(date: String, isSynced: Boolean, timestamp: Long)

    @Query("DELETE FROM income_reports")
    suspend fun clearAll()
}
