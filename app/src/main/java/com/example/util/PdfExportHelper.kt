package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import com.example.data.model.IncomeReport
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportHelper {

    fun generatePdf(context: Context, report: IncomeReport): File? {
        val pdfDocument = PdfDocument()
        
        // A4 page size at 72 DPI (595 x 842)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        // 1. Draw HEADER
        paint.color = Color.rgb(0, 102, 102) // CV Mahardika Teal
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, 595f, 15f, paint)

        // Title
        textPaint.color = Color.rgb(0, 102, 102)
        textPaint.textSize = 20f
        textPaint.isFakeBoldText = true
        canvas.drawText("CV. MAHARDIKA", 40f, 50f, textPaint)

        // Subtitle
        textPaint.color = Color.GRAY
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = false
        canvas.drawText("Laporan Income Harian | Penancangan Lama RT. 01/02 RW. 01", 40f, 65f, textPaint)

        // Date and Leader Info
        textPaint.color = Color.BLACK
        textPaint.textSize = 10f
        canvas.drawText("Nama Leader: ${report.leaderName.uppercase(Locale.getDefault())}", 40f, 90f, textPaint)
        canvas.drawText("Tanggal Laporan: ${report.date}", 400f, 90f, textPaint)

        // Divider Line
        paint.color = Color.rgb(0, 102, 102)
        paint.strokeWidth = 1.5f
        canvas.drawLine(40f, 100f, 555f, 100f, paint)

        // Denominations to parse
        val denominations = listOf(100000, 50000, 20000, 10000, 5000, 2000, 1000)

        // Parse Shift Qty JSON (e.g., {"100000":5})
        fun parseQty(json: String, denom: Int): Int {
            return try {
                val clean = json.replace("{", "").replace("}", "").replace("\"", "")
                val pairs = clean.split(",")
                val match = pairs.find { it.trim().startsWith(denom.toString()) }
                if (match != null) {
                    match.split(":")[1].trim().toInt()
                } else 0
            } catch (e: Exception) {
                0
            }
        }

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID")).apply {
            maximumFractionDigits = 0
        }

        var startY = 120f
        var grandTotal = 0L

        // Draw Shift Tables
        val shifts = listOf("Shift 1", "Shift 2", "Shift 3")
        val shiftJsons = listOf(report.shift1QtyJson, report.shift2QtyJson, report.shift3QtyJson)

        for (i in shifts.indices) {
            val shiftName = shifts[i]
            val qtyJson = shiftJsons[i]

            // Draw Section Title
            paint.color = Color.rgb(0, 102, 102)
            paint.style = Paint.Style.FILL
            canvas.drawRect(40f, startY, 555f, startY + 18f, paint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 10f
            textPaint.isFakeBoldText = true
            canvas.drawText("  ${shiftName.uppercase(Locale.getDefault())}", 45f, startY + 13f, textPaint)

            // Table Header
            startY += 18f
            paint.color = Color.rgb(235, 235, 235)
            canvas.drawRect(40f, startY, 555f, startY + 15f, paint)

            textPaint.color = Color.BLACK
            textPaint.textSize = 9f
            textPaint.isFakeBoldText = true
            canvas.drawText("Pecahan Uang", 50f, startY + 11f, textPaint)
            canvas.drawText("Lembar Uang (Qty)", 220f, startY + 11f, textPaint)
            canvas.drawText("Jumlah Uang", 420f, startY + 11f, textPaint)

            startY += 15f
            textPaint.isFakeBoldText = false
            var shiftTotal = 0L

            paint.color = Color.rgb(210, 210, 210)
            paint.strokeWidth = 0.5f

            for (denom in denominations) {
                val qty = parseQty(qtyJson, denom)
                val total = denom.toLong() * qty
                shiftTotal += total

                canvas.drawText(currencyFormat.format(denom), 50f, startY + 11f, textPaint)
                canvas.drawText(qty.toString(), 250f, startY + 11f, textPaint)
                canvas.drawText(currencyFormat.format(total), 420f, startY + 11f, textPaint)

                canvas.drawLine(40f, startY + 15f, 555f, startY + 15f, paint)
                startY += 15f
            }

            // Draw Shift Total
            paint.color = Color.rgb(245, 245, 245)
            canvas.drawRect(40f, startY, 555f, startY + 16f, paint)

            textPaint.isFakeBoldText = true
            textPaint.color = Color.BLACK
            canvas.drawText("TOTAL ${shiftName.uppercase(Locale.getDefault())}", 50f, startY + 12f, textPaint)
            canvas.drawText(currencyFormat.format(shiftTotal), 420f, startY + 12f, textPaint)

            startY += 25f
            grandTotal += shiftTotal
        }

        // Draw GRAND TOTAL
        paint.color = Color.rgb(255, 215, 0) // Gold Color
        canvas.drawRect(40f, startY, 555f, startY + 22f, paint)

        textPaint.color = Color.BLACK
        textPaint.textSize = 11f
        textPaint.isFakeBoldText = true
        canvas.drawText(" GRAND TOTAL SEMUA SHIFT", 45f, startY + 15f, textPaint)
        canvas.drawText(currencyFormat.format(grandTotal), 420f, startY + 15f, textPaint)

        startY += 40f

        // Draw Signature Area
        textPaint.color = Color.BLACK
        textPaint.textSize = 10f
        canvas.drawText("Leader,", 430f, startY, textPaint)

        // Render Signature Drawing
        if (report.signaturePointsJson.isNotEmpty()) {
            paint.color = Color.BLUE
            paint.strokeWidth = 2.5f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.style = Paint.Style.STROKE

            try {
                val lines = report.signaturePointsJson.split("|")
                for (line in lines) {
                    if (line.isEmpty()) continue
                    val points = line.split(";")
                    for (k in 0 until points.size - 1) {
                        val p1 = points[k].split(",")
                        val p2 = points[k + 1].split(",")
                        if (p1.size == 2 && p2.size == 2) {
                            val x1 = p1[0].toFloat()
                            val y1 = p1[1].toFloat()
                            val x2 = p2[0].toFloat()
                            val y2 = p2[1].toFloat()

                            // Signature box coordinate map
                            // We mapped signature coordinates from 0..1 relative to signature area
                            // Let's draw it in a 120 x 50 box
                            val startSigX = 400f
                            val startSigY = startY + 10f
                            val scaleX = 110f
                            val scaleY = 45f

                            canvas.drawLine(
                                startSigX + (x1 * scaleX),
                                startSigY + (y1 * scaleY),
                                startSigX + (x2 * scaleX),
                                startSigY + (y2 * scaleY),
                                paint
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        startY += 65f
        textPaint.isFakeBoldText = true
        textPaint.textSize = 10f
        canvas.drawText("( ${report.leaderName.uppercase(Locale.getDefault())} )", 410f, startY, textPaint)

        // Auto Footer
        textPaint.color = Color.rgb(180, 180, 180)
        textPaint.textSize = 7f
        textPaint.isFakeBoldText = false
        canvas.drawText("Developer By Create: HZdotCOM", 220f, 810f, textPaint)

        pdfDocument.finishPage(page)

        // Save to file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "Laporan_Income_Mahardika_$timestamp.pdf"
        
        val folder = File(context.getExternalFilesDir(null), "Laporan_Mahardika")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val file = File(folder, fileName)

        return try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
