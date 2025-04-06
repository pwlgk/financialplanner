package com.diplom.financialplanner.data.repository

import com.diplom.financialplanner.data.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс репозитория для работы с категориями.
 * Абстрагирует источник данных (БД, сеть и т.д.).
 */
interface CategoryRepository {
    /** Получает поток списка категорий указанного типа. */
    fun getCategoriesByTypeStream(type: String): Flow<List<CategoryEntity>>

    /** Получает категорию по ID. */
    suspend fun getCategoryById(id: Long): CategoryEntity?

    /** Вставляет или обновляет категорию. */
    suspend fun insertCategory(category: CategoryEntity): Long

    /** Обновляет категорию. */
    suspend fun updateCategory(category: CategoryEntity)

    /** Удаляет категорию. */
    suspend fun deleteCategory(category: CategoryEntity)

    /** Получает поток всех категорий. */
    fun getAllCategoriesStream(): Flow<List<CategoryEntity>>
}