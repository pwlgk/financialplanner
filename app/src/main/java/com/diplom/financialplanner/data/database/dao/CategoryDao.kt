package com.diplom.financialplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object для работы с сущностями категорий (CategoryEntity).
 * Предоставляет методы для вставки, обновления, удаления и получения категорий.
 */
@Dao
interface CategoryDao {

    /**
     * Вставляет новую категорию в базу данных или заменяет существующую при конфликте PrimaryKey.
     * @param category Сущность категории для вставки.
     * @return ID вставленной или замененной категории (Long).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    /**
     * Обновляет существующую категорию в базе данных.
     * @param category Сущность категории с обновленными данными.
     */
    @Update
    suspend fun updateCategory(category: CategoryEntity)

    /**
     * Удаляет категорию из базы данных.
     * ВНИМАНИЕ: Убедитесь, что настроены внешние ключи (ForeignKey) в связанных таблицах (например, transactions)
     * или что категория не используется перед вызовом этого метода, чтобы избежать ошибок целостности.
     * @param category Сущность категории для удаления.
     */
    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    /**
     * Получает поток (Flow) списка категорий определенного типа (расход или доход),
     * отсортированных по имени в алфавитном порядке.
     * @param type Строка, представляющая тип категории ("expense" или "income").
     * @return Flow, эмитящий список категорий указанного типа при каждом изменении данных.
     */
    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name ASC")
    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>>

    /**
     * Получает одну категорию по её уникальному идентификатору (ID).
     * suspend указывает, что функция должна вызываться из корутины или другой suspend функции.
     * @param id Уникальный идентификатор категории.
     * @return Сущность CategoryEntity, если найдена, иначе null.
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    /**
     * Получает поток (Flow) списка всех категорий, отсортированных по имени в алфавитном порядке.
     * @return Flow, эмитящий полный список категорий при каждом изменении данных.
     */
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

}