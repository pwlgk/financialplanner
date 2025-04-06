package com.diplom.financialplanner.data.repository

import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Интерфейс репозитория для работы с транзакциями.
 */
interface TransactionRepository {
    /** Получает поток транзакций с категориями по типу. */
    fun getTransactionsWithCategoryByTypeStream(type: TransactionType): Flow<List<TransactionWithCategory>>

    /** Получает поток всех транзакций. */
    fun getAllTransactionsStream(): Flow<List<TransactionEntity>>

    /** Получает поток недавних транзакций с категориями. */
    fun getRecentTransactionsWithCategory(limit: Int): Flow<List<TransactionWithCategory>>

    /** Получает транзакцию по ID. */
    suspend fun getTransactionById(id: Long): TransactionEntity?

    /** Вставляет или обновляет транзакцию. */
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    /** Обновляет транзакцию. */
    suspend fun updateTransaction(transaction: TransactionEntity)

    /** Удаляет транзакцию. */
    suspend fun deleteTransaction(transaction: TransactionEntity)

    /** Получает общую сумму по типу за период. */
    suspend fun getTotalAmountByTypeAndDate(type: TransactionType, startDate: Date, endDate: Date): Double?

    /** Получает расходы/доходы по категориям за период. */
    suspend fun getSpendingByCategoryForPeriod(type: TransactionType, startDate: Date, endDate: Date): List<CategorySpending>

    /** Проверяет, используется ли категория в транзакциях. */
    suspend fun isCategoryUsed(categoryId: Long): Boolean
}