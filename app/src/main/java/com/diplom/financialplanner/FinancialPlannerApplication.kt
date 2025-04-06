package com.diplom.financialplanner

import android.app.Application
import android.content.Context
import android.util.Log // Добавлен импорт для логгирования
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
 */
class FinancialPlannerApplication : Application() {

    /** Контейнер зависимостей. */
    lateinit var container: AppContainer
        private set // Делаем сеттер приватным, чтобы нельзя было изменить контейнер извне

    override fun onCreate() {
        super.onCreate()
        // Создаем контейнер при старте
        container = DefaultAppContainer(this)
        Log.i("FinancialPlannerApp", "Application container initialized.")
    }
}

/**
 * Интерфейс контейнера зависимостей.
 */
interface AppContainer {
    val transactionRepository: TransactionRepository
    val categoryRepository: CategoryRepository
    val budgetRepository: BudgetRepository
    val financialGoalRepository: FinancialGoalRepository
}

/**
 * Реализация контейнера зависимостей по умолчанию.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // Ленивая инициализация базы данных
    private val database: AppDatabase by lazy {
        Log.d("AppContainer", "Initializing AppDatabase...")
        AppDatabase.getDatabase(context)
    }

    // Ленивая инициализация репозиториев
    override val transactionRepository: TransactionRepository by lazy {
        Log.d("AppContainer", "Creating TransactionRepository instance")
        OfflineTransactionRepository(database.transactionDao())
    }

    override val categoryRepository: CategoryRepository by lazy {
        Log.d("AppContainer", "Creating CategoryRepository instance")
        OfflineCategoryRepository(database.categoryDao())
    }

    // BudgetRepository теперь требует три зависимости
    override val budgetRepository: BudgetRepository by lazy {
        Log.d("AppContainer", "Creating BudgetRepository instance")
        OfflineBudgetRepository(
            budgetDao = database.budgetDao(),
            categoryRepository = categoryRepository, // Зависимость от CategoryRepository
            transactionRepository = transactionRepository // <--- ИСПРАВЛЕНО: Передаем TransactionRepository
        )
    }

    override val financialGoalRepository: FinancialGoalRepository by lazy {
        Log.d("AppContainer", "Creating FinancialGoalRepository instance")
        OfflineFinancialGoalRepository(database.financialGoalDao())
    }
}