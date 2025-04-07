package com.diplom.financialplanner.data.repository

import android.content.Context
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.data.database.dao.TimeSeriesDataPoint
import com.diplom.financialplanner.data.database.dao.TransactionDao
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.Date

class OfflineTransactionRepository(private val transactionDao: TransactionDao, private val context: Context) : TransactionRepository {

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

    override suspend fun getSpendingByCategoryForPeriod(
        type: TransactionType,
        startDate: Date,
        endDate: Date
    ): List<CategorySpending> {
        // Получаем строку для неизвестной категории из ресурсов
        val unknownCategoryString = context.getString(R.string.category_deleted_placeholder_prefix) ?: "Категория #" // Запасной вариант
        // Передаем строку в метод DAO
        return transactionDao.getSpendingByCategoryForPeriod(type, startDate, endDate, unknownCategoryString)
    }
    override suspend fun isCategoryUsed(categoryId: Long): Boolean =
        transactionDao.countTransactionsForCategory(categoryId) > 0

    override suspend fun getAggregatedAmountByDay(type: TransactionType, startDate: Date, endDate: Date): List<TimeSeriesDataPoint> {
        return transactionDao.getAggregatedAmountByDay(type, startDate, endDate)
    }

    override suspend fun getAggregatedAmountByMonth(type: TransactionType, startDate: Date, endDate: Date): List<TimeSeriesDataPoint> {
        return transactionDao.getAggregatedAmountByMonth(type, startDate, endDate)
    }
}