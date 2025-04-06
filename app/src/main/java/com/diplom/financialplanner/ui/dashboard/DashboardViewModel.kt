package com.diplom.financialplanner.ui.dashboard

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.R // Для строк рекомендаций
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import com.diplom.financialplanner.data.repository.BudgetRepository
import com.diplom.financialplanner.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/** Состояние UI для главного экрана (Dashboard). */
data class DashboardUiState(
    val currentBalance: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val recentTransactions: List<TransactionWithCategory> = emptyList(),
    val isBudgetActive: Boolean = false,
    val budgetName: String? = null,
    val budgetTotalLimit: Double = 0.0,
    val budgetTotalSpent: Double = 0.0,
    val budgetProgressPercent: Int = 0,
    val overspentCategoriesCount: Int = 0,
    val recommendationMessage: String? = null, // Текст рекомендации
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * ViewModel для главного экрана (`DashboardFragment`).
 * Отвечает за загрузку и предоставление сводных данных: баланс, расходы за месяц,
 * недавние транзакции, сводку по бюджету и рекомендации.
 */
class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val recentTransactionsLimit = 5

    init {
        loadAndCombineData()
    }

    fun refreshDashboardData() {
        loadAndCombineData()
    }

    private fun loadAndCombineData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, recommendationMessage = null) }
        Log.d("DashboardVM", "Loading and combining dashboard data...")

        viewModelScope.launch {
            // Получаем базовые потоки
            val allTransactionsFlow = transactionRepository.getAllTransactionsStream()
            val recentTransactionsFlow = transactionRepository.getRecentTransactionsWithCategory(recentTransactionsLimit)
            val monthlyExpensesFlow = getMonthlyExpensesFlow(allTransactionsFlow) // Передаем поток транзакций
            // Получаем Flow текущего бюджета с ЛИМИТАМИ (без расчета прогресса в репо)
            val currentBudgetFlow = budgetRepository.getCurrentBudgetWithLimits() // Не getCurrentBudgetProgress

            combine(
                allTransactionsFlow,
                recentTransactionsFlow,
                monthlyExpensesFlow,
                currentBudgetFlow // Используем поток с лимитами
            ) { allTransactions, recentTransactions, monthlyExpenses, currentBudgetWithLimits ->

                Log.d("DashboardVM_Combine", "Combine triggered. Transactions: ${allTransactions.size}, Budget: ${currentBudgetWithLimits?.budget?.name}")

                // --- Расчет баланса ---
                val balance = allTransactions.fold(0.0) { acc, transaction ->
                    if (transaction.type == TransactionType.INCOME) acc + transaction.amount else acc - transaction.amount
                }

                // --- Расчеты для бюджета и рекомендаций (теперь ВНУТРИ ViewModel) ---
                val budgetName = currentBudgetWithLimits?.budget?.name
                val isBudgetActive = currentBudgetWithLimits != null
                var totalLimit = 0.0
                var totalSpentInBudget = 0.0
                var overspentCount = 0
                var recommendationMsg: String? = null

                if (isBudgetActive && currentBudgetWithLimits != null) {
                    val budget = currentBudgetWithLimits.budget
                    val limitsMap = currentBudgetWithLimits.limits.associateBy { it.categoryId }
                    totalLimit = currentBudgetWithLimits.limits.sumOf { it.limitAmount }

                    // Фильтруем ВСЕ транзакции по периоду бюджета и категориям с лимитами
                    val relevantExpenses = allTransactions.filter { t ->
                        t.type == TransactionType.EXPENSE &&
                                t.categoryId != null &&
                                limitsMap.containsKey(t.categoryId) && // Только расходы по категориям с лимитом
                                !t.date.before(budget.startDate) && !t.date.after(budget.endDate)
                    }

                    totalSpentInBudget = relevantExpenses.sumOf { it.amount }

                    // Считаем превышения по категориям
                    val spentByCategory = relevantExpenses.groupBy { it.categoryId!! }
                        .mapValues { entry -> entry.value.sumOf { it.amount } }

                    overspentCount = limitsMap.count { (catId, limit) ->
                        limit.limitAmount > 0 && (spentByCategory[catId] ?: 0.0) > limit.limitAmount
                    }

                    // Формируем рекомендацию
                    if (overspentCount > 0) {
                        // Найдем имя первой превышенной категории (если нужно)
                        val firstOverspentCatId = limitsMap.entries.find { (catId, limit) ->
                            limit.limitAmount > 0 && (spentByCategory[catId] ?: 0.0) > limit.limitAmount
                        }?.key
                        // TODO: Загрузить имя категории по firstOverspentCatId, если нужно точное сообщение
                        recommendationMsg = "Внимание! Превышены лимиты по $overspentCount категориям."
                    }
                    Log.d("DashboardVM_Combine", "Budget active: Limit=$totalLimit, Spent=$totalSpentInBudget, Overspent=$overspentCount")
                } else {
                    Log.d("DashboardVM_Combine", "No active budget found.")
                }

                // --- Формируем стейт ---
                DashboardUiState(
                    currentBalance = balance,
                    monthlyExpenses = monthlyExpenses ?: 0.0,
                    recentTransactions = recentTransactions,
                    isBudgetActive = isBudgetActive,
                    budgetName = budgetName,
                    budgetTotalLimit = totalLimit,
                    budgetTotalSpent = totalSpentInBudget,
                    budgetProgressPercent = if (isBudgetActive && totalLimit > 0) ((totalSpentInBudget / totalLimit) * 100).coerceIn(0.0, 100.0).toInt() else 0,
                    overspentCategoriesCount = overspentCount,
                    recommendationMessage = recommendationMsg,
                    isLoading = false,
                    errorMessage = null
                )
            }.catch { e ->
                Log.e("DashboardVM", "Error in combine operator", e)
                emit(DashboardUiState(isLoading = false, errorMessage = "Ошибка загрузки данных: ${e.message}"))
            }.collect { combinedState ->
                _uiState.value = combinedState
                Log.d("DashboardVM_Combine", "UI State Updated.")
            }
        }
    }

    // Передаем поток транзакций, чтобы избежать лишней подписки
    private fun getMonthlyExpensesFlow(allTransactionsFlow: Flow<List<TransactionEntity>>): Flow<Double?> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1); calendar.set(Calendar.HOUR_OF_DAY, 0); /*...*/ val startDate = calendar.time
        calendar.add(Calendar.MONTH, 1); calendar.add(Calendar.MILLISECOND, -1); /*...*/ val endDate = calendar.time
        // Log.d("DashboardVM_Debug", "Monthly Expenses Flow - Period: $startDate to $endDate") // Можно оставить для отладки

        return allTransactionsFlow // Используем переданный поток
            .map { allTransactions ->
                allTransactions.filter {
                    it.type == TransactionType.EXPENSE && !it.date.before(startDate) && !it.date.after(endDate)
                }.sumOf { it.amount }.takeIf { it > 0 }
            }
            .catch { e -> Log.e("DashboardVM_Debug", "Error in Monthly Expenses Flow calculation", e); emit(null) }
            .distinctUntilChanged()
    }

    // ... (deleteRecentTransaction, clearErrorMessage, provideFactory) ...
    // Метод удаления (без изменений)
    fun deleteRecentTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(transaction)
                Log.i("DashboardVM", "Recent transaction deleted: ${transaction.id}")
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error deleting recent transaction ${transaction.id}", e)
                _uiState.update { it.copy(errorMessage = "Ошибка удаления транзакции: ${e.message}") }
            }
        }
    }

    // Метод очистки ошибки (без изменений)
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Фабрика (без изменений)
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                DashboardViewModel(
                    application.container.transactionRepository,
                    application.container.budgetRepository
                )
            }
        }
    }
}