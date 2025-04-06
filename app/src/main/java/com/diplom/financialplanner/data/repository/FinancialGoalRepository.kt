package com.diplom.financialplanner.data.repository

import com.diplom.financialplanner.data.database.entity.FinancialGoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс репозитория для работы с финансовыми целями.
 */
interface FinancialGoalRepository {
    /** Получает поток списка всех целей. */
    fun getAllGoalsStream(): Flow<List<FinancialGoalEntity>>

    /** Получает цель по ID. */
    suspend fun getGoalById(id: Long): FinancialGoalEntity?

    /** Вставляет или обновляет цель. */
    suspend fun insertGoal(goal: FinancialGoalEntity): Long

    /** Обновляет цель. */
    suspend fun updateGoal(goal: FinancialGoalEntity)

    /** Удаляет цель. */
    suspend fun deleteGoal(goal: FinancialGoalEntity)

    /** Обновляет только текущую накопленную сумму цели. */
    suspend fun updateCurrentAmount(goalId: Long, newAmount: Double)
}