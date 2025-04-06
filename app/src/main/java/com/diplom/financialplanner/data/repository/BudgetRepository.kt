package com.diplom.financialplanner.data.repository

import com.diplom.financialplanner.data.database.dao.BudgetWithLimits
import com.diplom.financialplanner.data.database.dao.BudgetCategoryProgress
import com.diplom.financialplanner.data.database.entity.BudgetCategoryLimitEntity
import com.diplom.financialplanner.data.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Интерфейс репозитория для работы с бюджетами и лимитами.
 */
interface BudgetRepository {
    /** Получает поток текущего активного бюджета с лимитами. */
    fun getCurrentBudgetWithLimits(date: Date = Date()): Flow<BudgetWithLimits?>

    /** Получает поток бюджета с лимитами по ID. */
    fun getBudgetWithLimits(budgetId: Long): Flow<BudgetWithLimits?>

    /** Получает поток списка всех бюджетов. */
    fun getAllBudgetsStream(): Flow<List<BudgetEntity>>

    /** Сохраняет бюджет и его лимиты (вставка или обновление). */
    suspend fun saveBudget(budget: BudgetEntity, limits: List<BudgetCategoryLimitEntity>): Long

    /** Удаляет бюджет (связанные лимиты удаляются каскадно). */
    suspend fun deleteBudget(budget: BudgetEntity)

    /** Получает поток прогресса выполнения категорий текущего бюджета. */
    fun getCurrentBudgetProgress(date: Date = Date()): Flow<List<BudgetCategoryProgress>>

    /** Проверяет, используется ли категория в лимитах бюджетов. */
    suspend fun isCategoryUsedInBudgets(categoryId: Long): Boolean
}