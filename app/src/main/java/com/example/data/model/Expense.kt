package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String, // format "YYYY-MM-DD"
    val category: String,
    val amount: Double,
    val description: String,
    val isSynced: Boolean = false,
    val syncTimestamp: Long = 0L
)
