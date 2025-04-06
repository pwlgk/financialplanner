package com.diplom.financialplanner.data.repository

import com.diplom.financialplanner.data.database.dao.FinancialGoalDao
import com.diplom.financialplanner.data.database.entity.FinancialGoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Реализация репозитория финансовых целей, работающая с локальной базой данных Room.
 * @param financialGoalDao DAO для доступа к данным целей.
 */
class OfflineFinancialGoalRepository(private val financialGoalDao: FinancialGoalDao) : FinancialGoalRepository {

    override fun getAllGoalsStream(): Flow<List<FinancialGoalEntity>> =
        financialGoalDao.getAllGoals()

    override suspend fun getGoalById(id: Long): FinancialGoalEntity? =
        financialGoalDao.getGoalById(id)

    override suspend fun insertGoal(goal: FinancialGoalEntity): Long =
        financialGoalDao.insertGoal(goal)

    override suspend fun updateGoal(goal: FinancialGoalEntity) =
        financialGoalDao.updateGoal(goal)

    override suspend fun deleteGoal(goal: FinancialGoalEntity) =
        financialGoalDao.deleteGoal(goal)

    override suspend fun updateCurrentAmount(goalId: Long, newAmount: Double) =
        financialGoalDao.updateCurrentAmount(goalId, newAmount)
}