package com.example.data.repository

import android.util.Log
import com.example.data.local.ExpenseDao
import com.example.data.local.IncomeDao
import com.example.data.model.Expense
import com.example.data.model.IncomeReport
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MahardikaRepository(
    private val incomeDao: IncomeDao,
    private val expenseDao: ExpenseDao
) {
    val allIncomeReports: Flow<List<IncomeReport>> = incomeDao.getAllReportsFlow()
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpensesFlow()

    private var firestore: FirebaseFirestore? = null

    init {
        try {
            firestore = FirebaseFirestore.getInstance()
            Log.d("MahardikaRepo", "Firebase Firestore initialized successfully.")
        } catch (e: Exception) {
            Log.w("MahardikaRepo", "Firebase Firestore initialization failed (perhaps missing google-services.json?): ${e.message}")
        }
    }

    fun isCloudSyncAvailable(): Boolean {
        return firestore != null
    }

    fun getReportByDateFlow(date: String): Flow<IncomeReport?> {
        return incomeDao.getReportByDateFlow(date)
    }

    fun getExpensesByMonthFlow(month: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByMonthFlow(month)
    }

    suspend fun insertIncomeReport(report: IncomeReport) {
        withContext(Dispatchers.IO) {
            // Save locally first
            incomeDao.insertReport(report)
            
            // Try to sync to Firestore
            val fs = firestore
            if (fs != null) {
                try {
                    val docData = hashMapOf(
                        "date" to report.date,
                        "leaderName" to report.leaderName,
                        "signaturePointsJson" to report.signaturePointsJson,
                        "shift1QtyJson" to report.shift1QtyJson,
                        "shift2QtyJson" to report.shift2QtyJson,
                        "shift3QtyJson" to report.shift3QtyJson,
                        "syncTimestamp" to System.currentTimeMillis()
                    )
                    fs.collection("income_reports")
                        .document(report.date)
                        .set(docData)
                        .await()
                    
                    // Mark as synced
                    incomeDao.updateSyncStatus(report.date, true, System.currentTimeMillis())
                    Log.d("MahardikaRepo", "Income report synced to Firestore successfully.")
                } catch (e: Exception) {
                    Log.w("MahardikaRepo", "Failed to sync income report to Firestore: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteIncomeReport(report: IncomeReport) {
        withContext(Dispatchers.IO) {
            incomeDao.deleteReport(report)
            val fs = firestore
            if (fs != null) {
                try {
                    fs.collection("income_reports")
                        .document(report.date)
                        .delete()
                        .await()
                } catch (e: Exception) {
                    Log.w("MahardikaRepo", "Failed to delete from Firestore: ${e.message}")
                }
            }
        }
    }

    suspend fun insertExpense(expense: Expense) {
        withContext(Dispatchers.IO) {
            // Save locally first
            expenseDao.insertExpense(expense)
            
            // Try to sync to Firestore
            val fs = firestore
            if (fs != null) {
                try {
                    // Create a temporary key/ID or use existing
                    val docId = if (expense.id == 0) {
                        // Generate a temporary name but we will sync with real ID later, 
                        // actually we can generate unique hash
                        "${expense.date}_${expense.category}_${expense.amount}"
                    } else {
                        expense.id.toString()
                    }
                    val docData = hashMapOf(
                        "date" to expense.date,
                        "category" to expense.category,
                        "amount" to expense.amount,
                        "description" to expense.description,
                        "syncTimestamp" to System.currentTimeMillis()
                    )
                    fs.collection("expenses")
                        .document(docId)
                        .set(docData)
                        .await()
                    
                    if (expense.id != 0) {
                        expenseDao.updateSyncStatus(expense.id, true, System.currentTimeMillis())
                    }
                    Log.d("MahardikaRepo", "Expense synced to Firestore successfully.")
                } catch (e: Exception) {
                    Log.w("MahardikaRepo", "Failed to sync expense to Firestore: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteExpense(expenseId: Int, expenseDate: String, expenseCategory: String, expenseAmount: Double) {
        withContext(Dispatchers.IO) {
            expenseDao.deleteExpenseById(expenseId)
            val fs = firestore
            if (fs != null) {
                try {
                    val docId = expenseId.toString()
                    fs.collection("expenses")
                        .document(docId)
                        .delete()
                        .await()
                } catch (e: Exception) {
                    // Try alternative doc ID too
                    try {
                        val altId = "${expenseDate}_${expenseCategory}_${expenseAmount}"
                        fs.collection("expenses").document(altId).delete().await()
                    } catch (e2: Exception) {
                        Log.w("MahardikaRepo", "Failed to delete expense from Firestore: ${e2.message}")
                    }
                }
            }
        }
    }

    // Trigger full manual or automatic bidirectional synchronization
    suspend fun syncPendingData(): Boolean {
        return withContext(Dispatchers.IO) {
            val fs = firestore ?: return@withContext false
            var syncSuccess = true

            try {
                // 1. Sync unsynced daily reports from Room to Firestore
                val unsyncedReports = incomeDao.getUnsyncedReports()
                for (report in unsyncedReports) {
                    try {
                        val docData = hashMapOf(
                            "date" to report.date,
                            "leaderName" to report.leaderName,
                            "signaturePointsJson" to report.signaturePointsJson,
                            "shift1QtyJson" to report.shift1QtyJson,
                            "shift2QtyJson" to report.shift2QtyJson,
                            "shift3QtyJson" to report.shift3QtyJson,
                            "syncTimestamp" to System.currentTimeMillis()
                        )
                        fs.collection("income_reports")
                            .document(report.date)
                            .set(docData)
                            .await()
                        
                        incomeDao.updateSyncStatus(report.date, true, System.currentTimeMillis())
                    } catch (e: Exception) {
                        Log.w("MahardikaRepo", "Syncing pending report ${report.date} failed: ${e.message}")
                        syncSuccess = false
                    }
                }

                // 2. Sync unsynced expenses from Room to Firestore
                val unsyncedExpenses = expenseDao.getUnsyncedExpenses()
                for (expense in unsyncedExpenses) {
                    try {
                        val docId = expense.id.toString()
                        val docData = hashMapOf(
                            "date" to expense.date,
                            "category" to expense.category,
                            "amount" to expense.amount,
                            "description" to expense.description,
                            "syncTimestamp" to System.currentTimeMillis()
                        )
                        fs.collection("expenses")
                            .document(docId)
                            .set(docData)
                            .await()
                        
                        expenseDao.updateSyncStatus(expense.id, true, System.currentTimeMillis())
                    } catch (e: Exception) {
                        Log.w("MahardikaRepo", "Syncing pending expense ${expense.id} failed: ${e.message}")
                        syncSuccess = false
                    }
                }

                // 3. Sync FROM Firestore down to local Room DB
                // Fetch reports from Firestore
                val reportSnapshot = fs.collection("income_reports").get().await()
                for (doc in reportSnapshot.documents) {
                    val date = doc.getString("date") ?: continue
                    val leaderName = doc.getString("leaderName") ?: ""
                    val signaturePointsJson = doc.getString("signaturePointsJson") ?: "[]"
                    val shift1QtyJson = doc.getString("shift1QtyJson") ?: "{}"
                    val shift2QtyJson = doc.getString("shift2QtyJson") ?: "{}"
                    val shift3QtyJson = doc.getString("shift3QtyJson") ?: "{}"
                    
                    val localReport = incomeDao.getReportByDate(date)
                    if (localReport == null) {
                        // Insert new report found in cloud
                        val newReport = IncomeReport(
                            date = date,
                            leaderName = leaderName,
                            signaturePointsJson = signaturePointsJson,
                            shift1QtyJson = shift1QtyJson,
                            shift2QtyJson = shift2QtyJson,
                            shift3QtyJson = shift3QtyJson,
                            isSynced = true,
                            syncTimestamp = System.currentTimeMillis()
                        )
                        incomeDao.insertReport(newReport)
                    }
                }

                // Fetch expenses from Firestore
                val expenseSnapshot = fs.collection("expenses").get().await()
                for (doc in expenseSnapshot.documents) {
                    val date = doc.getString("date") ?: continue
                    val category = doc.getString("category") ?: "Other"
                    val amount = doc.getDouble("amount") ?: 0.0
                    val description = doc.getString("description") ?: ""
                    
                    // Simple local insert
                    val newExpense = Expense(
                        date = date,
                        category = category,
                        amount = amount,
                        description = description,
                        isSynced = true,
                        syncTimestamp = System.currentTimeMillis()
                    )
                    expenseDao.insertExpense(newExpense)
                }

                true
            } catch (e: Exception) {
                Log.e("MahardikaRepo", "Bidirectional cloud sync failed: ${e.message}")
                false
            }
        }
    }
}
