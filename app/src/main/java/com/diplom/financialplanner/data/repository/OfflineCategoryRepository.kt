package com.diplom.financialplanner.data.repository

import com.diplom.financialplanner.data.database.dao.CategoryDao
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Реализация репозитория категорий, работающая с локальной базой данных Room.
 * @param categoryDao DAO для доступа к данным категорий.
 */
class OfflineCategoryRepository(private val categoryDao: CategoryDao) : CategoryRepository {

    override fun getCategoriesByTypeStream(type: String): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByType(type)

    override suspend fun getCategoryById(id: Long): CategoryEntity? =
        categoryDao.getCategoryById(id)

    override suspend fun insertCategory(category: CategoryEntity): Long =
        categoryDao.insertCategory(category)

    override suspend fun updateCategory(category: CategoryEntity) =
        categoryDao.updateCategory(category)

    override suspend fun deleteCategory(category: CategoryEntity) =
        categoryDao.deleteCategory(category)

    override fun getAllCategoriesStream(): Flow<List<CategoryEntity>> =
        categoryDao.getAllCategories()
}