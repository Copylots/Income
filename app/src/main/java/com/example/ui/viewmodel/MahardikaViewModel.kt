package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Expense
import com.example.data.model.IncomeReport
import com.example.data.repository.MahardikaRepository
import com.example.receiver.NotificationHelper
import com.example.util.ExcelExportHelper
import com.example.util.PdfExportHelper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MahardikaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MahardikaRepository
    private val sharedPrefs = application.getSharedPreferences("mahardika_settings", Context.MODE_PRIVATE)

    // Data lists from database
    val allIncomeReports: StateFlow<List<IncomeReport>>
    val allExpenses: StateFlow<List<Expense>>

    // Input States for Income Report
    var selectedDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    var leaderName = MutableStateFlow("")
    var signaturePoints = MutableStateFlow("")

    // Shift Quantities: Key is Denomination, Value is quantity as String
    val shift1Qty = MutableStateFlow<Map<Int, String>>(emptyMap())
    val shift2Qty = MutableStateFlow<Map<Int, String>>(emptyMap())
    val shift3Qty = MutableStateFlow<Map<Int, String>>(emptyMap())

    // Expense Inputs
    var expenseAmount = MutableStateFlow("")
    var expenseCategory = MutableStateFlow("Operasional")
    var expenseDescription = MutableStateFlow("")
    var expenseDate = MutableStateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))

    // Settings States
    val isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", false))
    val isBiometricsEnabled = MutableStateFlow(sharedPrefs.getBoolean("biometrics_enabled", false))
    val isReminderEnabled = MutableStateFlow(sharedPrefs.getBoolean("reminder_enabled", true))
    val isUnlocked = MutableStateFlow(!sharedPrefs.getBoolean("biometrics_enabled", false))

    // Sync States
    val isCloudSyncAvailable = MutableStateFlow(false)
    val syncStatusText = MutableStateFlow("Offline")

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MahardikaRepository(database.incomeDao(), database.expenseDao())
        
        allIncomeReports = repository.allIncomeReports.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allExpenses = repository.allExpenses.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        isCloudSyncAvailable.value = repository.isCloudSyncAvailable()
        syncStatusText.value = if (repository.isCloudSyncAvailable()) "Terhubung ke Awan" else "Offline (Lokal Saja)"

        // Load preferences & keep Leader Name from last edited if empty
        val savedLeader = sharedPrefs.getString("last_leader_name", "") ?: ""
        leaderName.value = savedLeader

        // When selectedDate changes, load existing report values if present
        viewModelScope.launch {
            selectedDate.collect { date ->
                loadReportForDate(date)
            }
        }

        // Initialize default denominations
        resetDenominations()
        
        // Setup initial reminder alarm if enabled
        if (isReminderEnabled.value) {
            NotificationHelper.createNotificationChannel(application)
            NotificationHelper.scheduleDailyReminder(application)
        }
    }

    private fun resetDenominations() {
        val denominations = listOf(100000, 50000, 20000, 10000, 5000, 2000, 1000)
        val initialMap = denominations.associateWith { "" }
        shift1Qty.value = initialMap
        shift2Qty.value = initialMap
        shift3Qty.value = initialMap
        signaturePoints.value = ""
    }

    private suspend fun loadReportForDate(date: String) {
        val db = AppDatabase.getDatabase(getApplication())
        val report = db.incomeDao().getReportByDate(date)
        if (report != null) {
            leaderName.value = report.leaderName
            signaturePoints.value = report.signaturePointsJson
            shift1Qty.value = deserializeMap(report.shift1QtyJson)
            shift2Qty.value = deserializeMap(report.shift2QtyJson)
            shift3Qty.value = deserializeMap(report.shift3QtyJson)
        } else {
            // No record for this date, keep Leader name, clear others
            val currentLeader = leaderName.value
            resetDenominations()
            leaderName.value = currentLeader
        }
    }

    fun updateShiftQty(shiftNum: Int, denom: Int, qty: String) {
        viewModelScope.launch {
            when (shiftNum) {
                1 -> {
                    val updated = shift1Qty.value.toMutableMap()
                    updated[denom] = qty
                    shift1Qty.value = updated
                }
                2 -> {
                    val updated = shift2Qty.value.toMutableMap()
                    updated[denom] = qty
                    shift2Qty.value = updated
                }
                3 -> {
                    val updated = shift3Qty.value.toMutableMap()
                    updated[denom] = qty
                    shift3Qty.value = updated
                }
            }
            saveCurrentReport()
        }
    }

    fun updateLeaderName(name: String) {
        leaderName.value = name
        sharedPrefs.edit().putString("last_leader_name", name).apply()
        saveCurrentReport()
    }

    fun updateSignature(points: String) {
        signaturePoints.value = points
        saveCurrentReport()
    }

    fun clearSignature() {
        signaturePoints.value = ""
        saveCurrentReport()
    }

    fun selectDate(date: String) {
        selectedDate.value = date
    }

    private fun saveCurrentReport() {
        viewModelScope.launch {
            val report = IncomeReport(
                date = selectedDate.value,
                leaderName = leaderName.value,
                signaturePointsJson = signaturePoints.value,
                shift1QtyJson = serializeMap(shift1Qty.value),
                shift2QtyJson = serializeMap(shift2Qty.value),
                shift3QtyJson = serializeMap(shift3Qty.value)
            )
            repository.insertIncomeReport(report)
        }
    }

    fun resetDailyReport() {
        viewModelScope.launch {
            resetDenominations()
            saveCurrentReport()
        }
    }

    // Expense operations
    fun addExpense() {
        val amt = expenseAmount.value.toDoubleOrNull() ?: return
        val cat = expenseCategory.value
        val desc = expenseDescription.value
        val dateVal = expenseDate.value

        viewModelScope.launch {
            val expense = Expense(
                date = dateVal,
                category = cat,
                amount = amt,
                description = desc
            )
            repository.insertExpense(expense)
            
            // Reset input fields
            expenseAmount.value = ""
            expenseDescription.value = ""
        }
    }

    fun deleteExpenseItem(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense.id, expense.date, expense.category, expense.amount)
        }
    }

    // PDF Export
    fun exportPdf(context: Context, onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            val report = IncomeReport(
                date = selectedDate.value,
                leaderName = leaderName.value,
                signaturePointsJson = signaturePoints.value,
                shift1QtyJson = serializeMap(shift1Qty.value),
                shift2QtyJson = serializeMap(shift2Qty.value),
                shift3QtyJson = serializeMap(shift3Qty.value)
            )
            val file = PdfExportHelper.generatePdf(context, report)
            onComplete(file)
        }
    }

    // Excel Export
    fun exportExcel(context: Context, onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            val file = ExcelExportHelper.exportToCsv(context, allIncomeReports.value, allExpenses.value)
            onComplete(file)
        }
    }

    // Sync trigger
    fun triggerSync() {
        if (!isCloudSyncAvailable.value) return
        viewModelScope.launch {
            syncStatusText.value = "Menyingkronkan..."
            val success = repository.syncPendingData()
            if (success) {
                syncStatusText.value = "Sinkronisasi Berhasil!"
            } else {
                syncStatusText.value = "Gagal Sinkronisasi"
            }
        }
    }

    // Settings Toggle Functions
    fun toggleDarkMode(enabled: Boolean) {
        isDarkMode.value = enabled
        sharedPrefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun toggleBiometrics(enabled: Boolean) {
        isBiometricsEnabled.value = enabled
        sharedPrefs.edit().putBoolean("biometrics_enabled", enabled).apply()
        if (!enabled) {
            isUnlocked.value = true
        }
    }

    fun toggleReminder(enabled: Boolean) {
        isReminderEnabled.value = enabled
        sharedPrefs.edit().putBoolean("reminder_enabled", enabled).apply()
        val app = getApplication<Application>()
        if (enabled) {
            NotificationHelper.createNotificationChannel(app)
            NotificationHelper.scheduleDailyReminder(app)
        } else {
            NotificationHelper.cancelReminder(app)
        }
    }

    // Helper serialization
    private fun serializeMap(map: Map<Int, String>): String {
        return "{" + map.entries.joinToString(",") { "\"${it.key}\":${it.value.ifEmpty { "0" }}" } + "}"
    }

    private fun deserializeMap(json: String): Map<Int, String> {
        val denominations = listOf(100000, 50000, 20000, 10000, 5000, 2000, 1000)
        val map = denominations.associateWith { "" }.toMutableMap()
        try {
            val clean = json.replace("{", "").replace("}", "").replace("\"", "")
            val pairs = clean.split(",")
            for (pair in pairs) {
                if (pair.trim().isEmpty()) continue
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val denom = parts[0].trim().toInt()
                    val qty = parts[1].trim()
                    if (denom in map.keys) {
                        map[denom] = if (qty == "0") "" else qty
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MahardikaVM", "Error parsing map json: ${e.message}")
        }
        return map
    }
}
