package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "income_reports")
data class IncomeReport(
    @PrimaryKey
    val date: String, // format "YYYY-MM-DD"
    val leaderName: String,
    val signaturePointsJson: String, // coordinate list for drawing signature
    val shift1QtyJson: String, // JSON map of cash quantities: e.g. {"100000":12,"50000":4}
    val shift2QtyJson: String,
    val shift3QtyJson: String,
    val isSynced: Boolean = false,
    val syncTimestamp: Long = 0L
)
