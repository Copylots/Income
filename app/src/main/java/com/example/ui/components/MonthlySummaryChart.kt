package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Expense
import com.example.data.model.IncomeReport
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MonthlySummaryChart(
    modifier: Modifier = Modifier,
    reports: List<IncomeReport>,
    expenses: List<Expense>,
    selectedMonth: String // format "YYYY-MM"
) {
    // 1. Calculate totals for each day in selected month
    // Let's filter records for the selected month
    val filteredReports = reports.filter { it.date.startsWith(selectedMonth) }
    val filteredExpenses = expenses.filter { it.date.startsWith(selectedMonth) }

    fun parseShiftTotal(json: String): Long {
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

    // Map of Day (Int 1..31) -> Income Amount
    val dailyIncomeMap = remember(filteredReports) {
        val map = mutableMapOf<Int, Long>()
        for (r in filteredReports) {
            try {
                val day = r.date.split("-").last().toInt()
                val s1 = parseShiftTotal(r.shift1QtyJson)
                val s2 = parseShiftTotal(r.shift2QtyJson)
                val s3 = parseShiftTotal(r.shift3QtyJson)
                map[day] = (map[day] ?: 0L) + s1 + s2 + s3
            } catch (e: Exception) {}
        }
        map
    }

    // Map of Day (Int 1..31) -> Expense Amount
    val dailyExpenseMap = remember(filteredExpenses) {
        val map = mutableMapOf<Int, Long>()
        for (ex in filteredExpenses) {
            try {
                val day = ex.date.split("-").last().toInt()
                map[day] = (map[day] ?: 0L) + ex.amount.toLong()
            } catch (e: Exception) {}
        }
        map
    }

    // Days in Month to plot (we can group into blocks or show 31 slots, let's plot weeks or intervals)
    val totalIncome = dailyIncomeMap.values.sum()
    val totalExpense = dailyExpenseMap.values.sum()

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("in", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    // Tap coordinate tracking for Tooltip
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }
    var tooltipOffset by remember { mutableStateOf(Offset.Zero) }

    val daysToDraw = (1..31).toList()
    val maxAmount = remember(dailyIncomeMap, dailyExpenseMap) {
        val maxInc = dailyIncomeMap.values.maxOrNull() ?: 0L
        val maxExp = dailyExpenseMap.values.maxOrNull() ?: 0L
        maxOf(maxInc, maxExp, 1000000L).toFloat() // Default max to 1jt to avoid divide by zero
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Overall stat boxes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Total Income",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = currencyFormat.format(totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Total Expense",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = currencyFormat.format(totalExpense),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dailyIncomeMap, dailyExpenseMap) {
                        detectTapGestures { offset ->
                            val drawWidth = size.width
                            val paddingLeft = 45f
                            val chartWidth = drawWidth - paddingLeft - 10f
                            val barGroupWidth = chartWidth / 31f

                            val clickedX = offset.x
                            if (clickedX >= paddingLeft) {
                                val dayIndex = ((clickedX - paddingLeft) / barGroupWidth).toInt() + 1
                                if (dayIndex in 1..31) {
                                    selectedDayIndex = dayIndex
                                    tooltipOffset = offset
                                }
                            }
                        }
                    }
            ) {
                val paddingLeft = 45f
                val paddingBottom = 25f
                val chartWidth = size.width - paddingLeft - 10f
                val chartHeight = size.height - paddingBottom - 10f

                // Draw Y-axis guide lines
                val paint = android.graphics.Paint().apply {
                    color = Color.LightGray.copy(alpha = 0.4f).toArgb()
                }
                val textPaint = android.graphics.Paint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 20f
                    textAlign = android.graphics.Paint.Align.RIGHT
                }

                // Draw 4 levels
                for (lvl in 0..4) {
                    val ratio = lvl / 4f
                    val y = chartHeight * (1 - ratio) + 10f
                    drawContext.canvas.nativeCanvas.drawLine(paddingLeft, y, size.width, y, paint)
                    
                    val valText = when (lvl) {
                        0 -> "0"
                        1 -> "${(maxAmount * 0.25f / 1000).toInt()}k"
                        2 -> "${(maxAmount * 0.5f / 1000).toInt()}k"
                        3 -> "${(maxAmount * 0.75f / 1000).toInt()}k"
                        else -> "${(maxAmount / 1000).toInt()}k"
                    }
                    drawContext.canvas.nativeCanvas.drawText(valText, paddingLeft - 5f, y + 7f, textPaint)
                }

                // Bar sizes
                val barGroupWidth = chartWidth / 31f
                val barWidth = barGroupWidth * 0.35f

                // Plot days
                for (day in daysToDraw) {
                    val inc = dailyIncomeMap[day] ?: 0L
                    val exp = dailyExpenseMap[day] ?: 0L

                    val groupCenterX = paddingLeft + (day - 1) * barGroupWidth + barGroupWidth / 2f
                    
                    // Draw Income Bar (Teal / Primary)
                    if (inc > 0) {
                        val incHeight = (inc / maxAmount) * chartHeight
                        drawRoundRect(
                            color = Color(0, 150, 136),
                            topLeft = Offset(groupCenterX - barWidth - 1f, chartHeight - incHeight + 10f),
                            size = Size(barWidth, incHeight),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }

                    // Draw Expense Bar (Red / Error)
                    if (exp > 0) {
                        val expHeight = (exp / maxAmount) * chartHeight
                        drawRoundRect(
                            color = Color(244, 67, 54),
                            topLeft = Offset(groupCenterX + 1f, chartHeight - expHeight + 10f),
                            size = Size(barWidth, expHeight),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }

                    // X-axis label (draw every 5 days to prevent cluttering)
                    if (day % 5 == 0 || day == 1) {
                        val labelPaint = android.graphics.Paint().apply {
                            color = Color.Gray.toArgb()
                            textSize = 20f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            day.toString(),
                            groupCenterX,
                            size.height - 2f,
                            labelPaint
                        )
                    }
                }
            }

            // Interactive Tooltip popup overlays
            selectedDayIndex?.let { day ->
                val inc = dailyIncomeMap[day] ?: 0L
                val exp = dailyExpenseMap[day] ?: 0L
                if (inc > 0L || exp > 0L) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (tooltipOffset.x / 3f).coerceIn(10f, 250f).dp,
                                y = (tooltipOffset.y / 3.2f).coerceIn(10f, 120f).dp
                            )
                            .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        Column {
                            Text(
                                text = "Tanggal: $selectedMonth-${String.format("%02d", day)}",
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (inc > 0) {
                                Text(
                                    text = "Pemasukan: ${currencyFormat.format(inc)}",
                                    color = Color(0, 255, 180),
                                    fontSize = 10.sp
                                )
                            }
                            if (exp > 0) {
                                Text(
                                    text = "Pengeluaran: ${currencyFormat.format(exp)}",
                                    color = Color(255, 120, 120),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
