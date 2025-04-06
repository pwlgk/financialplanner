package com.diplom.financialplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction // Важно для запросов с @Relation
import androidx.room.Update
import com.diplom.financialplanner.data.database.entity.BudgetCategoryLimitEntity
import com.diplom.financialplanner.data.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Модель для получения бюджета вместе со связанными с ним лимитами по категориям.
 */
data class BudgetWithLimits(
    @Embedded val budget: BudgetEntity,
    @Relation(
        parentColumn = "id", // Поле budget.id
        entityColumn = "budget_id" // Поле budget_category_limits.budget_id
    )
    val limits: List<BudgetCategoryLimitEntity>
)

/**
 * Модель для представления прогресса выполнения бюджета по конкретной категории.
 * Содержит информацию о лимите, фактических тратах и имени категории.
 */
data class BudgetCategoryProgress(
    val categoryId: Long,
    val categoryName: String?, // Имя категории
    val limitAmount: Double,   // Установленный лимит
    val spentAmount: Double    // Фактически потрачено за период бюджета
)

/**
 * Вспомогательная модель для хранения результата агрегации трат по категориям за период.
 * Используется внутри BudgetDao.
 */
data class CategorySpent(
    @androidx.room.ColumnInfo(name = "category_id") val categoryId: Long,
    @androidx.room.ColumnInfo(name = "total_spent") val totalSpent: Double
)

/**
 * Data Access Object для работы с сущностями бюджетов (BudgetEntity) и лимитов (BudgetCategoryLimitEntity).
 */
@Dao
interface BudgetDao {

    // --- Работа с бюджетами (BudgetEntity) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE id = :budgetId")
    suspend fun getBudgetById(budgetId: Long): BudgetEntity?

    /**
     * Получает поток бюджета, который активен на указанную дату.
     * @param date Дата, для которой ищется активный бюджет.
     * @return Flow, эмитящий активный BudgetEntity или null, если такого нет.
     */
    @Query("SELECT * FROM budgets WHERE :date BETWEEN start_date AND end_date LIMIT 1")
    fun getCurrentBudget(date: Date): Flow<BudgetEntity?>

    /**
     * Получает поток списка всех созданных бюджетов, отсортированных по дате начала (сначала новые).
     * @return Flow, эмитящий список всех BudgetEntity при изменениях.
     */
    @Query("SELECT * FROM budgets ORDER BY start_date DESC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    // --- Работа с лимитами (BudgetCategoryLimitEntity) ---

    /**
     * Вставляет список лимитов. Использует REPLACE при конфликте (budget_id, category_id).
     * @param limits Список сущностей лимитов для вставки.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLimits(limits: List<BudgetCategoryLimitEntity>)

    /**
     * Удаляет все лимиты, связанные с определенным бюджетом.
     * Вызывается перед вставкой новых лимитов при обновлении бюджета.
     * @param budgetId ID бюджета, для которого удаляются лимиты.
     */
    @Query("DELETE FROM budget_category_limits WHERE budget_id = :budgetId")
    suspend fun deleteLimitsForBudget(budgetId: Long)

    /**
     * Получает поток списка лимитов для конкретного бюджета.
     * @param budgetId ID бюджета.
     * @return Flow, эмитящий список лимитов BudgetCategoryLimitEntity для данного бюджета.
     */
    @Query("SELECT * FROM budget_category_limits WHERE budget_id = :budgetId")
    fun getLimitsForBudget(budgetId: Long): Flow<List<BudgetCategoryLimitEntity>>

    // --- Комбинированные запросы ---

    /**
     * Получает поток бюджета вместе со связанными с ним лимитами по ID бюджета.
     * @param budgetId ID бюджета.
     * @return Flow, эмитящий BudgetWithLimits или null.
     */
    @Transaction // Важно для @Relation
    @Query("SELECT * FROM budgets WHERE id = :budgetId")
    fun getBudgetWithLimits(budgetId: Long): Flow<BudgetWithLimits?>

    /**
     * Получает поток текущего активного бюджета вместе со связанными с ним лимитами.
     * @param date Дата, для которой ищется активный бюджет (обычно текущая).
     * @return Flow, эмитящий активный BudgetWithLimits или null.
     */
    @Transaction
    @Query("SELECT * FROM budgets WHERE :date BETWEEN start_date AND end_date LIMIT 1")
    fun getCurrentBudgetWithLimits(date: Date): Flow<BudgetWithLimits?>

    // --- Запросы для отслеживания прогресса (более сложные) ---

    /**
     * Рассчитывает агрегированные суммы расходов по категориям за указанный период.
     * Этот запрос используется внутри репозитория для расчета BudgetCategoryProgress.
     * @param startDate Начальная дата периода.
     * @param endDate Конечная дата периода.
     * @return Список объектов CategorySpent с ID категории и суммой трат.
     */
    @Query("""
        SELECT category_id, SUM(amount) as total_spent
        FROM transactions
        WHERE type = 'EXPENSE' AND date BETWEEN :startDate AND :endDate AND category_id IS NOT NULL
        GROUP BY category_id
    """)
    suspend fun getSpentAmountByCategoryForPeriod(startDate: Date, endDate: Date): List<CategorySpent>

    /**
     * Подсчитывает количество лимитов, связанных с определенной категорией.
     * Может использоваться для проверки перед удалением категории.
     * @param categoryId ID категории.
     * @return Количество лимитов (Int).
     */
    @Query("SELECT COUNT(*) FROM budget_category_limits WHERE category_id = :categoryId")
    suspend fun countLimitsForCategory(categoryId: Long): Int
}