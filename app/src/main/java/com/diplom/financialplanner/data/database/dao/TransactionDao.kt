package com.diplom.financialplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction // Для запросов со связями
import androidx.room.Update
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Модель данных для результата запроса суммы по категориям с именем.
 * Используется в отчетах.
 */
data class CategorySpending(
    val categoryId: Long,
    val categoryName: String?, // Имя категории (может быть null, если категория удалена)
    val totalSpent: Double
)

/**
 * Data Access Object для работы с сущностями транзакций (TransactionEntity).
 * Предоставляет методы для CRUD операций и специализированных запросов (по дате, типу, с категориями).
 */
@Dao
interface TransactionDao {

    /**
     * Вставляет новую транзакцию в базу данных или заменяет существующую при конфликте ID.
     * @param transaction Сущность транзакции для вставки.
     * @return ID вставленной или замененной транзакции (Long).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    /**
     * Обновляет существующую транзакцию в базе данных.
     * @param transaction Сущность транзакции с обновленными данными.
     */
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    /**
     * Удаляет транзакцию из базы данных.
     * @param transaction Сущность транзакции для удаления.
     */
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    /**
     * Получает одну транзакцию по её уникальному идентификатору (ID).
     * @param id Уникальный идентификатор транзакции.
     * @return Сущность TransactionEntity, если найдена, иначе null.
     */
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    /**
     * Получает поток (Flow) списка транзакций определенного типа вместе с информацией о категории.
     * Использует @Transaction для атомарного выполнения запроса и связи @Relation.
     * Результаты отсортированы по дате в порядке убывания (сначала новые).
     * @param type Тип транзакции (INCOME или EXPENSE).
     * @return Flow, эмитящий список TransactionWithCategory указанного типа при изменениях.
     */
    @Transaction
    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getTransactionsWithCategoryByType(type: TransactionType): Flow<List<TransactionWithCategory>>

    /**
     * Получает поток (Flow) списка всех транзакций, отсортированных по дате (сначала новые).
     * Используется для расчета общего баланса.
     * @return Flow, эмитящий полный список транзакций при изменениях.
     */
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    /**
     * Получает поток (Flow) списка транзакций за указанный период времени, отсортированных по дате (сначала новые).
     * @param startDate Начальная дата периода (включительно).
     * @param endDate Конечная дата периода (включительно).
     * @return Flow, эмитящий список транзакций за период при изменениях.
     */
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsBetweenDates(startDate: Date, endDate: Date): Flow<List<TransactionEntity>>

    /**
     * Получает поток (Flow) списка последних N транзакций вместе с информацией о категории.
     * Используется для отображения на главном экране (Dashboard).
     * @param limit Максимальное количество возвращаемых транзакций.
     * @return Flow, эмитящий список последних TransactionWithCategory при изменениях.
     */
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    fun getRecentTransactionsWithCategory(limit: Int): Flow<List<TransactionWithCategory>>

    /**
     * Рассчитывает общую сумму транзакций определенного типа за указанный период.
     * @param type Тип транзакции (INCOME или EXPENSE).
     * @param startDate Начальная дата периода.
     * @param endDate Конечная дата периода.
     * @return Сумма (Double) или null, если транзакций за период не найдено.
     */
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByTypeAndDate(type: TransactionType, startDate: Date, endDate: Date): Double?

    /**
     * Рассчитывает суммы трат/доходов по категориям за указанный период, включая имя категории.
     * Используется для построения отчетов.
     * @param type Тип транзакции (INCOME или EXPENSE).
     * @param startDate Начальная дата периода.
     * @param endDate Конечная дата периода.
     * @return Список объектов CategorySpending с ID категории, именем и общей суммой.
     */
    @Query("""
        SELECT
            t.category_id as categoryId,
            COALESCE(c.name, 'Категория #' || t.category_id) as categoryName,
            SUM(t.amount) as totalSpent
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        WHERE t.type = :type AND t.date BETWEEN :startDate AND :endDate AND t.category_id IS NOT NULL
        GROUP BY t.category_id, categoryName
        ORDER BY totalSpent DESC
    """)
    suspend fun getSpendingByCategoryForPeriod(type: TransactionType, startDate: Date, endDate: Date): List<CategorySpending>

    /**
     * Подсчитывает количество транзакций, связанных с определенной категорией.
     * Может использоваться для проверки перед удалением категории.
     * @param categoryId ID категории для проверки.
     * @return Количество транзакций (Int).
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
    suspend fun countTransactionsForCategory(categoryId: Long): Int
}