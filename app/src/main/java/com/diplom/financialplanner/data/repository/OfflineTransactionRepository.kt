package com.diplom.financialplanner.data.repository

import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.data.database.dao.TransactionDao
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Реализация репозитория транзакций, работающая с локальной базой данных Room.
 * @param transactionDao DAO для доступа к данным транзакций.
 */
class OfflineTransactionRepository(private val transactionDao: TransactionDao) : TransactionRepository {

    override fun getTransactionsWithCategoryByTypeStream(type: TransactionType): Flow<List<TransactionWithCategory>> =
        transactionDao.getTransactionsWithCategoryByType(type)

    override fun getAllTransactionsStream(): Flow<List<TransactionEntity>> =
        transactionDao.getAllTransactions()

    override fun getRecentTransactionsWithCategory(limit: Int): Flow<List<TransactionWithCategory>> =
        transactionDao.getRecentTransactionsWithCategory(limit)

    override suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionDao.getTransactionById(id)

    override suspend fun insertTransaction(transaction: TransactionEntity): Long =
        transactionDao.insertTransaction(transaction)

    override suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.updateTransaction(transaction)

    override suspend fun deleteTransaction(transaction: TransactionEntity) =
        transactionDao.deleteTransaction(transaction)

    override suspend fun getTotalAmountByTypeAndDate(type: TransactionType, startDate: Date, endDate: Date): Double? =
        transactionDao.getTotalAmountByTypeAndDate(type, startDate, endDate)

    override suspend fun getSpendingByCategoryForPeriod(type: TransactionType, startDate: Date, endDate: Date): List<CategorySpending> =
        transactionDao.getSpendingByCategoryForPeriod(type, startDate, endDate)

    override suspend fun isCategoryUsed(categoryId: Long): Boolean {
        // Проверяем, есть ли хотя бы одна транзакция с этой категорией
        return transactionDao.countTransactionsForCategory(categoryId) > 0
    }
}