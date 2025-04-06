package com.diplom.financialplanner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Сущность, представляющая бюджет на определенный период.
 */
@Entity(
    tableName = "budgets",
    indices = [
        // Уникальный индекс на пару дат, чтобы избежать дублирования бюджетов на один и тот же период.
        Index(value = ["start_date", "end_date"], unique = true)
    ]
)
data class BudgetEntity(
    /** Уникальный идентификатор бюджета (генерируется автоматически). */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Название бюджета (например, "Бюджет на Июль 2024"). */
    val name: String,

    /** Дата начала периода бюджета. */
    @ColumnInfo(name = "start_date")
    val startDate: Date,

    /** Дата окончания периода бюджета. */
    @ColumnInfo(name = "end_date")
    val endDate: Date
)