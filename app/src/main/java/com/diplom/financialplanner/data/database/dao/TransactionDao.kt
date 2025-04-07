package com.diplom.financialplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.Date

// --- МОДЕЛИ ДАННЫХ ДЛЯ ОТЧЕТОВ ---

/** Модель данных для результата запроса суммы И КОЛИЧЕСТВА по категориям с атрибутами категории. */
data class CategorySpending(
    val categoryId: Long,
    val categoryName: String?,
    val totalSpent: Double,
    val transactionCount: Int,
    val iconResName: String?,
    val colorHex: String?
)

/** Модель для точки на графике динамики */
data class TimeSeriesDataPoint(
    val timestamp: Long, // Метка времени (мс) для оси X
    val amount: Double   // Сумма за этот момент времени
)


@Dao
interface TransactionDao {

    // --- CRUD Операции (без изменений) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?
    // --- Конец CRUD ---

    // --- Основные Потоки Данных (без изменений) ---
    @Transaction
    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getTransactionsWithCategoryByType(type: TransactionType): Flow<List<TransactionWithCategory>>
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    fun getRecentTransactionsWithCategory(limit: Int): Flow<List<TransactionWithCategory>>
    // --- Конец Основных Потоков ---

    // --- Запросы для Расчетов и Отчетов ---
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsBetweenDates(startDate: Date, endDate: Date): Flow<List<TransactionEntity>>
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByTypeAndDate(type: TransactionType, startDate: Date, endDate: Date): Double?

    /** Рассчитывает суммы И КОЛИЧЕСТВО по категориям за период, ВКЛЮЧАЯ атрибуты категории. */
    @Query("""
        SELECT
            t.category_id as categoryId,
            COALESCE(c.name, :unknownCategoryName || t.category_id) as categoryName, -- Используем плейсхолдер
            SUM(t.amount) as totalSpent,
            COUNT(t.id) as transactionCount, -- <-- Считаем количество
            c.icon_res_name as iconResName,
            c.color_hex as colorHex
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        WHERE t.type = :type AND t.date BETWEEN :startDate AND :endDate AND t.category_id IS NOT NULL
        GROUP BY t.category_id -- Группируем по ID категории
        ORDER BY totalSpent DESC
    """)
    suspend fun getSpendingByCategoryForPeriod(
        type: TransactionType,
        startDate: Date,
        endDate: Date,
        unknownCategoryName: String // Передаем строку для неизвестной категории
    ): List<CategorySpending> // Возвращаемый тип теперь включает transactionCount

    /** Получает суммы, сгруппированные по ДНЯМ. */
    @Query("""
        SELECT
            strftime('%s', date(date/1000, 'unixepoch')) * 1000 as timestamp,
            SUM(amount) as amount
        FROM transactions
        WHERE type = :type AND date BETWEEN :startDate AND :endDate
        GROUP BY date(date/1000, 'unixepoch')
        ORDER BY timestamp ASC
    """)
    suspend fun getAggregatedAmountByDay(type: TransactionType, startDate: Date, endDate: Date): List<TimeSeriesDataPoint>

    /** Получает суммы, сгруппированные по МЕСЯЦАМ. */
    @Query("""
        SELECT
             strftime('%s', date(date/1000, 'unixepoch', 'start of month')) * 1000 as timestamp,
             SUM(amount) as amount
        FROM transactions
        WHERE type = :type AND date BETWEEN :startDate AND :endDate
        GROUP BY strftime('%Y-%m', date/1000, 'unixepoch')
        ORDER BY timestamp ASC
    """)
    suspend fun getAggregatedAmountByMonth(type: TransactionType, startDate: Date, endDate: Date): List<TimeSeriesDataPoint>

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
    suspend fun countTransactionsForCategory(categoryId: Long): Int
    // --- Конец Запросов для Расчетов и Отчетов ---
}