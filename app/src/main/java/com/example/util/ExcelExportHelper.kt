package com.example.util

import android.content.Context
import com.example.data.model.Expense
import com.example.data.model.IncomeReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExportHelper {

    fun exportToCsv(context: Context, reports: List<IncomeReport>, expenses: List<Expense>): File? {
        val sb = java.lang.StringBuilder()

        // 1. Write Daily Income Summary Section
        sb.append("CV. MAHARDIKA - LAPORAN INCOME DAN PENGELUARAN\n")
        sb.append("Diekspor Pada: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n\n")

        sb.append("--- LAPORAN INCOME HARIAN ---\n")
        sb.append("Tanggal,Leader,Shift 1 Total,Shift 2 Total,Shift 3 Total,Grand Total,Status Sinkronisasi\n")

        val denominations = listOf(100000, 50000, 20000, 10000, 5000, 2000, 1000)

        fun calculateShiftTotal(json: String): Long {
            var total = 0L
            try {
                val clean = json.replace("{", "").replace("}", "").replace("\"", "")
                val pairs = clean.split(",")
                for (pair in pairs) {
                    if (pair.trim().isEmpty()) continue
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        val denom = parts[0].trim().toInt()
                        val qty = parts[1].trim().toInt()
                        total += denom.toLong() * qty
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            return total
        }

        for (report in reports) {
            val s1 = calculateShiftTotal(report.shift1QtyJson)
            val s2 = calculateShiftTotal(report.shift2QtyJson)
            val s3 = calculateShiftTotal(report.shift3QtyJson)
            val grand = s1 + s2 + s3
            val syncText = if (report.isSynced) "Synced" else "Offline Only"

            sb.append("${report.date},\"${report.leaderName.replace("\"", "\"\"")}\",Rp $s1,Rp $s2,Rp $s3,Rp $grand,$syncText\n")
        }

        sb.append("\n\n")

        // 2. Write Expenses Section
        sb.append("--- DAFTAR PENGELUARAN ---\n")
        sb.append("Tanggal,Kategori,Jumlah (Amount),Deskripsi,Status Sinkronisasi\n")

        for (expense in expenses) {
            val syncText = if (expense.isSynced) "Synced" else "Offline Only"
            sb.append("${expense.date},\"${expense.category.replace("\"", "\"\"")}\",Rp ${expense.amount.toLong()},\"${expense.description.replace("\"", "\"\"")}\",$syncText\n")
        }

        // Save file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "Laporan_Mahardika_Excel_$timestamp.csv"
        
        val folder = File(context.getExternalFilesDir(null), "Laporan_Mahardika")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val file = File(folder, fileName)

        return try {
            val fos = FileOutputStream(file)
            fos.write(sb.toString().toByteArray())
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
