package com.diplom.financialplanner // Замените на ваш корневой пакет

import android.app.Application
import android.content.Context
import android.util.Log
import com.diplom.financialplanner.data.database.AppDatabase
import com.diplom.financialplanner.data.repository.BudgetRepository
import com.diplom.financialplanner.data.repository.CategoryRepository
import com.diplom.financialplanner.data.repository.FinancialGoalRepository
import com.diplom.financialplanner.data.repository.OfflineBudgetRepository
import com.diplom.financialplanner.data.repository.OfflineCategoryRepository
import com.diplom.financialplanner.data.repository.OfflineFinancialGoalRepository
import com.diplom.financialplanner.data.repository.OfflineTransactionRepository
import com.diplom.financialplanner.data.repository.TransactionRepository

/**
 * Базовый класс Application для инициализации глобальных зависимостей.
 * Использует простой контейнер для ручного внедрения зависимостей (Manual DI).
 */
class FinancialPlannerApplication : Application() {

    /**
     * AppContainer содержит экземпляры репозиториев.
     * Будет инициализирован в onCreate().
     */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        // Создаем экземпляр контейнера зависимостей при старте приложения
        container = DefaultAppContainer(this)
    }
}

/**
 * Интерфейс контейнера зависимостей. Определяет, какие зависимости доступны.
 */
interface AppContainer {
    val transactionRepository: TransactionRepository
    val categoryRepository: CategoryRepository
    val budgetRepository: BudgetRepository
    val financialGoalRepository: FinancialGoalRepository
}

/**
 * Реализация контейнера зависимостей. Создает экземпляры репозиториев.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // Ленивая инициализация базы данных
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    // Ленивая инициализация репозиториев. Они создаются только при первом обращении.
    override val transactionRepository: TransactionRepository by lazy {
        OfflineTransactionRepository(database.transactionDao())
    }

    override val categoryRepository: CategoryRepository by lazy {
        OfflineCategoryRepository(database.categoryDao())
    }

    // BudgetRepository требует и budgetDao, и categoryRepository
    override val budgetRepository: BudgetRepository by lazy {
        Log.d("AppContainer", "Creating BudgetRepository instance") // Добавьте лог
        OfflineBudgetRepository(database.budgetDao(), categoryRepository)
    }

    override val financialGoalRepository: FinancialGoalRepository by lazy {
        OfflineFinancialGoalRepository(database.financialGoalDao())
    }
}