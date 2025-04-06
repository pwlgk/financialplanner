package com.diplom.financialplanner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Сущность, представляющая финансовую цель пользователя.
 */
@Entity(tableName = "financial_goals")
data class FinancialGoalEntity(
    /** Уникальный идентификатор цели (генерируется автоматически). */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Название цели (например, "Отпуск", "Новый телефон"). */
    val name: String,

    /** Целевая сумма, которую нужно накопить. */
    @ColumnInfo(name = "target_amount")
    val targetAmount: Double,

    /** Текущая накопленная сумма (может обновляться пользователем). */
    @ColumnInfo(name = "current_amount", defaultValue = "0.0") // Указываем значение по умолчанию
    var currentAmount: Double = 0.0,

    /** Дата создания цели (устанавливается автоматически при создании объекта). */
    @ColumnInfo(name = "creation_date")
    val creationDate: Date = Date()
)