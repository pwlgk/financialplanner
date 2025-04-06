package com.diplom.financialplanner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность, представляющая лимит расходов для конкретной категории в рамках определенного бюджета.
 */
@Entity(
    tableName = "budget_category_limits",
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_id"],
            onDelete = ForeignKey.CASCADE // При удалении бюджета удаляются и связанные с ним лимиты
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE // При удалении категории удаляются и лимиты для неё во всех бюджетах
            // onDelete = ForeignKey.RESTRICT // Запретить удаление категории, если для неё заданы лимиты
        )
    ],
    indices = [
        // Уникальный индекс на пару (бюджет, категория), чтобы избежать дублирования лимитов.
        Index(value = ["budget_id", "category_id"], unique = true),
        // Индексы для ускорения поиска по budget_id и category_id
        Index("budget_id"),
        Index("category_id")
    ]
)
data class BudgetCategoryLimitEntity(
    /** Уникальный идентификатор лимита (генерируется автоматически). */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ID бюджета, к которому относится этот лимит. */
    @ColumnInfo(name = "budget_id") // Явное указание индекса
    val budgetId: Long,

    /** ID категории, для которой установлен лимит. */
    @ColumnInfo(name = "category_id") // Явное указание индекса
    val categoryId: Long,

    /** Сумма установленного лимита для этой категории в этом бюджете. */
    @ColumnInfo(name = "limit_amount")
    val limitAmount: Double
)