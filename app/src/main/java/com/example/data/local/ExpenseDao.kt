package com.example.data.local

import androidx.room.*
import com.example.data.model.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpensesFlow(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date LIKE :month || '%' ORDER BY date DESC")
    fun getExpensesByMonthFlow(month: String): Flow<List<Expense>> // format "YYYY-MM"

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<Expense>

    @Query("UPDATE expenses SET isSynced = :isSynced, syncTimestamp = :timestamp WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, isSynced: Boolean, timestamp: Long)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}
