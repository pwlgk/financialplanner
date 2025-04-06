package com.diplom.financialplanner.data.repository

import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.data.database.dao.TimeSeriesDataPoint
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface TransactionRepository {
    fun getTransactionsWithCategoryByTypeStream(type: TransactionType): Flow<List<TransactionWithCategory>>
    fun getAllTransactionsStream(): Flow<List<TransactionEntity>>
    fun getRecentTransactionsWithCategory(limit: Int): Flow<List<TransactionWithCategory>>
    suspend fun getTransactionById(id: Long): TransactionEntity?
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    suspend fun updateTransaction(transaction: TransactionEntity)
    suspend fun deleteTransaction(transaction: TransactionEntity)
    suspend fun getTotalAmountByTypeAndDate(type: TransactionType, startDate: Date, endDate: Date): Double?
    suspend fun getSpendingByCategoryForPeriod(type: TransactionType, startDate: Date, endDate: Date): List<CategorySpending>
    suspend fun isCategoryUsed(categoryId: Long): Boolean
    suspend fun getAggregatedAmountByDay(type: TransactionType, startDate: Date, endDate: Date): List<TimeSeriesDataPoint>
    suspend fun getAggregatedAmountByMonth(type: TransactionType, startDate: Date, endDate: Date): List<TimeSeriesDataPoint>
}