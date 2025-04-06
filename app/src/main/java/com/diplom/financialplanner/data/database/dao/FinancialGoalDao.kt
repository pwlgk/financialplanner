package com.diplom.financialplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diplom.financialplanner.data.database.entity.FinancialGoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object для работы с сущностями финансовых целей (FinancialGoalEntity).
 */
@Dao
interface FinancialGoalDao {

    /**
     * Вставляет новую цель или заменяет существующую.
     * @param goal Сущность цели для вставки.
     * @return ID вставленной/замененной цели.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: FinancialGoalEntity): Long

    /**
     * Обновляет существующую цель.
     * @param goal Сущность цели с обновленными данными.
     */
    @Update
    suspend fun updateGoal(goal: FinancialGoalEntity)

    /**
     * Удаляет цель из базы данных.
     * @param goal Сущность цели для удаления.
     */
    @Delete
    suspend fun deleteGoal(goal: FinancialGoalEntity)

    /**
     * Получает цель по её ID.
     * @param goalId Уникальный идентификатор цели.
     * @return FinancialGoalEntity или null, если не найдена.
     */
    @Query("SELECT * FROM financial_goals WHERE id = :goalId")
    suspend fun getGoalById(goalId: Long): FinancialGoalEntity?

    /**
     * Получает поток списка всех финансовых целей, отсортированных по дате создания (сначала новые).
     * @return Flow, эмитящий список всех FinancialGoalEntity при изменениях.
     */
    @Query("SELECT * FROM financial_goals ORDER BY creation_date DESC")
    fun getAllGoals(): Flow<List<FinancialGoalEntity>>

    /**
     * Обновляет только поле `current_amount` для указанной цели.
     * @param goalId ID цели, у которой обновляется сумма.
     * @param newAmount Новое значение накопленной суммы.
     */
    @Query("UPDATE financial_goals SET current_amount = :newAmount WHERE id = :goalId")
    suspend fun updateCurrentAmount(goalId: Long, newAmount: Double)
}