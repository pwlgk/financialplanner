package com.diplom.financialplanner.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.data.database.entity.TransactionEntity

/**
 * Представляет транзакцию вместе со связанной категорией.
 * Используется в запросах DAO с аннотацией @Transaction для объединения данных
 * из таблицы транзакций и таблицы категорий.
 */
data class TransactionWithCategory(
    /** Встроенная сущность транзакции. */
    @Embedded val transaction: TransactionEntity,

    /**
     * Связанная сущность категории. Room автоматически заполнит это поле,
     * найдя категорию, у которой `id` совпадает с `category_id` из `transaction`.
     */
    @Relation(
        parentColumn = "category_id", // Поле в родительской сущности (TransactionEntity)
        entityColumn = "id"           // Поле в дочерней сущности (CategoryEntity)
    )
    val category: CategoryEntity? // Nullable, так как category_id в TransactionEntity может быть null
)