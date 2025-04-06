package com.diplom.financialplanner.data.repository

import android.util.Log
import com.diplom.financialplanner.data.database.dao.BudgetDao
import com.diplom.financialplanner.data.database.dao.BudgetWithLimits
import com.diplom.financialplanner.data.database.dao.BudgetCategoryProgress
import com.diplom.financialplanner.data.database.entity.BudgetCategoryLimitEntity
import com.diplom.financialplanner.data.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * Реализация репозитория бюджетов, работающая с локальной базой данных Room.
 * @param budgetDao DAO для доступа к данным бюджетов и лимитов.
 * @param categoryRepository Репозиторий категорий для получения имен.
 */
class OfflineBudgetRepository(
    private val budgetDao: BudgetDao,
    private val categoryRepository: CategoryRepository
) : BudgetRepository {

    override fun getCurrentBudgetWithLimits(date: Date): Flow<BudgetWithLimits?> =
        budgetDao.getCurrentBudgetWithLimits(date)

    override fun getBudgetWithLimits(budgetId: Long): Flow<BudgetWithLimits?> =
        budgetDao.getBudgetWithLimits(budgetId)

    override fun getAllBudgetsStream(): Flow<List<BudgetEntity>> =
        budgetDao.getAllBudgets()

    override suspend fun saveBudget(budget: BudgetEntity, limits: List<BudgetCategoryLimitEntity>): Long {
        val isUpdate = budget.id != 0L
        val budgetId = if (isUpdate) {
            budgetDao.updateBudget(budget)
            budget.id
        } else {
            budgetDao.insertBudget(budget)
        }

        val limitsWithBudgetId = limits.map { it.copy(budgetId = budgetId) }

        // Удаляем старые лимиты только при обновлении
        if(isUpdate) {
            budgetDao.deleteLimitsForBudget(budgetId)
        }
        budgetDao.insertLimits(limitsWithBudgetId) // Вставляем новые/обновленные
        Log.d("BudgetRepo", "Saved budget (ID: $budgetId, IsUpdate: $isUpdate) with ${limits.size} limits.")
        return budgetId
    }

    override suspend fun deleteBudget(budget: BudgetEntity) {
        budgetDao.deleteBudget(budget)
    }

    override fun getCurrentBudgetProgress(date: Date): Flow<List<BudgetCategoryProgress>> {
        return budgetDao.getCurrentBudgetWithLimits(date)
            .flatMapLatest { budgetWithLimits ->
                if (budgetWithLimits == null) {
                    Log.d("BudgetRepo", "getCurrentBudgetProgress: No current budget found.")
                    flowOf(emptyList()) // Нет бюджета - пустой список прогресса
                } else {
                    Log.d("BudgetRepo", "getCurrentBudgetProgress: Budget found: ${budgetWithLimits.budget.name}. Calculating.")
                    // Запрос трат за период бюджета
                    val spentFlow = flowOf(
                        budgetDao.getSpentAmountByCategoryForPeriod(
                            budgetWithLimits.budget.startDate,
                            budgetWithLimits.budget.endDate
                        )
                    )
                    // Запрос категорий расходов для получения имен
                    val categoriesFlow = categoryRepository.getCategoriesByTypeStream("expense")

                    // Объединяем лимиты, траты и категории
                    combine(
                        flowOf(budgetWithLimits.limits), // Лимиты из текущего бюджета
                        spentFlow,                       // Траты за период
                        categoriesFlow                   // Все категории расходов
                    ) { limits, spentList, categories ->
                        Log.v("BudgetRepo", "Combining limits (${limits.size}), spent (${spentList.size}), categories (${categories.size})")
                        val spentMap = spentList.associateBy { it.categoryId }
                        val categoryMap = categories.associateBy { it.id }

                        // Создаем список прогресса, объединяя данные
                        limits.mapNotNull { limit ->
                            categoryMap[limit.categoryId]?.let { category ->
                                val spentAmount = spentMap[limit.categoryId]?.totalSpent ?: 0.0
                                BudgetCategoryProgress(
                                    categoryId = limit.categoryId,
                                    categoryName = category.name,
                                    limitAmount = limit.limitAmount,
                                    spentAmount = spentAmount
                                )
                            } ?: run {
                                Log.w("BudgetRepo", "Category with ID ${limit.categoryId} not found for limit, skipping.")
                                null // Исключаем, если категория не найдена
                            }
                        }
                    }
                }
            }
    }

    override suspend fun isCategoryUsedInBudgets(categoryId: Long): Boolean {
        // Проверяем, есть ли хотя бы один лимит с этой категорией
        return budgetDao.countLimitsForCategory(categoryId) > 0
    }
}