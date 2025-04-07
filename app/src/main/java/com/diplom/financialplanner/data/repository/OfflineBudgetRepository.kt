package com.diplom.financialplanner.data.repository

import android.util.Log
import com.diplom.financialplanner.data.database.dao.BudgetDao
import com.diplom.financialplanner.data.database.dao.BudgetWithLimits
import com.diplom.financialplanner.data.database.dao.BudgetCategoryProgress
import com.diplom.financialplanner.data.database.entity.BudgetCategoryLimitEntity
import com.diplom.financialplanner.data.database.entity.BudgetEntity
import com.diplom.financialplanner.data.database.entity.CategoryEntity // Добавлен импорт
import com.diplom.financialplanner.data.database.entity.TransactionEntity // Добавлен импорт
import com.diplom.financialplanner.data.database.entity.TransactionType // Добавлен импорт
import kotlinx.coroutines.flow.* // Убедитесь, что импортированы flowOf, combine, map, flatMapLatest, distinctUntilChanged
import java.util.Date

/**
 * Реализация репозитория бюджетов, работающая с локальной базой данных Room.
 * @param budgetDao DAO для доступа к данным бюджетов и лимитов.
 * @param categoryRepository Репозиторий категорий для получения имен.
 * @param transactionRepository Репозиторий транзакций для получения потока транзакций.
 */
class OfflineBudgetRepository(
    private val budgetDao: BudgetDao,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository // Добавлена зависимость
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
        if (isUpdate) {
            budgetDao.deleteLimitsForBudget(budgetId)
        }
        budgetDao.insertLimits(limitsWithBudgetId)
        Log.d("BudgetRepo", "Saved budget (ID: $budgetId, IsUpdate: $isUpdate) with ${limits.size} limits.")
        return budgetId
    }

    override suspend fun deleteBudget(budget: BudgetEntity) {
        budgetDao.deleteBudget(budget)
    }

    override fun getCurrentBudgetProgress(date: Date): Flow<List<BudgetCategoryProgress>> {
        return budgetDao.getCurrentBudgetWithLimits(date)
            .flatMapLatest { budgetWithLimits: BudgetWithLimits? -> // Явно указываем тип budgetWithLimits
                if (budgetWithLimits == null) {
                    Log.d("BudgetRepo", "getCurrentBudgetProgress: No current budget found.")
                    flowOf(emptyList()) // Возвращаем пустой список
                } else {
                    Log.d("BudgetRepo", "getCurrentBudgetProgress: Budget found: ${budgetWithLimits.budget.name}. Calculating.")
                    // Получаем необходимые потоки
                    val allTransactionsFlow: Flow<List<TransactionEntity>> = transactionRepository.getAllTransactionsStream()
                    val categoriesFlow: Flow<List<CategoryEntity>> = categoryRepository.getCategoriesByTypeStream("expense")

                    // Объединяем потоки
                    combine(
                        flowOf(budgetWithLimits.limits), // Поток из списка лимитов
                        allTransactionsFlow,             // Поток всех транзакций
                        categoriesFlow                   // Поток категорий расходов
                    ) { limits: List<BudgetCategoryLimitEntity>, // Явно указываем типы в лямбде combine
                        allTransactions: List<TransactionEntity>,
                        categories: List<CategoryEntity> ->

                        Log.v("BudgetRepo", "Combining for budget progress: limits=${limits.size}, transactions=${allTransactions.size}, categories=${categories.size}")

                        // --- Пересчет трат ВНУТРИ combine ---
                        val startDate = budgetWithLimits.budget.startDate
                        val endDate = budgetWithLimits.budget.endDate
                        // Рассчитываем траты по категориям за период
                        val spentMap: Map<Long, Double> = allTransactions
                            .filter { transaction: TransactionEntity -> // Явно указываем тип transaction
                                transaction.type == TransactionType.EXPENSE &&
                                        transaction.categoryId != null &&
                                        limits.any { limit -> limit.categoryId == transaction.categoryId } && // Проверяем наличие лимита для категории транзакции
                                        !transaction.date.before(startDate) && !transaction.date.after(endDate)
                            }
                            .groupBy { transaction: TransactionEntity -> transaction.categoryId!! } // Группируем (уже отфильтровали null)
                            .mapValues { entry: Map.Entry<Long, List<TransactionEntity>> -> // Явно указываем тип entry
                                entry.value.sumOf { transaction -> transaction.amount } // Суммируем
                            }
                        // --- Конец пересчета трат ---

                        val categoryMap: Map<Long, CategoryEntity> = categories.associateBy { category -> category.id } // Явно указываем тип category

                        // Формируем итоговый список прогресса
                        limits.mapNotNull { limit: BudgetCategoryLimitEntity -> // Явно указываем тип limit
                            categoryMap[limit.categoryId]?.let { category: CategoryEntity -> // Явно указываем тип category
                                val spentAmount = spentMap[limit.categoryId] ?: 0.0
                                BudgetCategoryProgress(
                                    categoryId = limit.categoryId,
                                    categoryName = category.name,
                                    limitAmount = limit.limitAmount,
                                    spentAmount = spentAmount
                                )
                            } ?: run {
                                Log.w("BudgetRepo", "Category with ID ${limit.categoryId} not found for limit, skipping.")
                                null
                            }
                        }
                    }
                }
            }
            .distinctUntilChanged() // Эмитим, только если список прогресса изменился
            .catch { e -> // Обработка ошибок потока
                Log.e("BudgetRepo", "Error in getCurrentBudgetProgress flow", e)
                emit(emptyList()) // Возвращаем пустой список при ошибке
            }
    }

    override suspend fun isCategoryUsedInBudgets(categoryId: Long): Boolean {
        return budgetDao.countLimitsForCategory(categoryId) > 0
    }
}