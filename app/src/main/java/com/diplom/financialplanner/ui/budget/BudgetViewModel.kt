package com.diplom.financialplanner.ui.budget

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.R // Импорт R для доступа к ресурсам строк
import com.diplom.financialplanner.data.database.dao.BudgetCategoryProgress
import com.diplom.financialplanner.data.database.entity.BudgetEntity
import com.diplom.financialplanner.data.repository.BudgetRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Состояние UI для экрана управления бюджетами.
 * Содержит список всех бюджетов и прогресс выполнения текущего активного бюджета.
 */
data class BudgetScreenUiState(
    val allBudgets: List<BudgetEntity> = emptyList(),
    val currentBudgetProgress: List<BudgetCategoryProgress> = emptyList(),
    val currentBudgetName: String? = null,
    val isCurrentBudgetExists: Boolean = false,
    val isLoadingAllBudgets: Boolean = true,
    val isLoadingProgress: Boolean = true,
    val userMessage: String? = null, // Для Snackbar/Toast сообщений (может быть ID ресурса или строка)
    val errorMessage: String? = null // Сообщение об ошибке (может быть ID ресурса или строка)
)

/**
 * ViewModel для экрана управления бюджетами (`BudgetFragment`).
 * Отвечает за загрузку списка бюджетов и данных о прогрессе текущего бюджета.
 */
class BudgetViewModel(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetScreenUiState())
    val uiState: StateFlow<BudgetScreenUiState> = _uiState.asStateFlow()

    init {
        Log.d("BudgetVM", "Initializing and loading budget data...")
        loadAllBudgets()
        loadCurrentBudgetProgressAndName()
    }

    /** Загружает список всех бюджетов из репозитория. */
    private fun loadAllBudgets() {
        viewModelScope.launch {
            budgetRepository.getAllBudgetsStream()
                .onStart {
                    Log.d("BudgetVM_History", "Starting to load all budgets...")
                    _uiState.update { it.copy(isLoadingAllBudgets = true) }
                }
                .catch { e ->
                    Log.e("BudgetVM_History", "Error loading all budgets", e)
                    _uiState.update { it.copy(isLoadingAllBudgets = false, errorMessage = "Ошибка загрузки списка бюджетов: ${e.message}") }
                }
                .collect { budgets ->
                    // --- ЛОГГИРОВАНИЕ РЕЗУЛЬТАТА ---
                    Log.i("BudgetVM_History", ">>> All Budgets Flow Emitted: ${budgets.size} items")
                    if (budgets.isNotEmpty()) {
                        Log.d("BudgetVM_History", "First budget in list: ${budgets[0]}") // Логгируем первый для примера
                    }
                    // --- КОНЕЦ ЛОГГИРОВАНИЯ ---
                    _uiState.update { it.copy(isLoadingAllBudgets = false, allBudgets = budgets) }
                }
        }
    }

    /** Загружает прогресс и имя текущего активного бюджета. */
    private fun loadCurrentBudgetProgressAndName() {
        viewModelScope.launch {
            combine(
                budgetRepository.getCurrentBudgetProgress(),
                budgetRepository.getCurrentBudgetWithLimits().map { it?.budget?.name }
            ) { progressList, budgetName -> Pair(progressList, budgetName) }
                .onStart { _uiState.update { it.copy(isLoadingProgress = true) } }
                .catch { e -> handleLoadingError(e, "текущего бюджета") }
                .collect { (progressList, budgetName) ->
                    Log.d("BudgetVM", "Current budget info loaded: Name=$budgetName, ProgressItems=${progressList.size}")
                    val budgetExists = progressList.isNotEmpty() || budgetName != null
                    _uiState.update {
                        it.copy(
                            isLoadingProgress = false,
                            currentBudgetProgress = progressList,
                            currentBudgetName = budgetName,
                            isCurrentBudgetExists = budgetExists
                        )
                    }
                }
        }
    }

    /** Удаляет указанный бюджет. */
    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch {
            try {
                budgetRepository.deleteBudget(budget)
                // Формируем сообщение с именем удаленного бюджета
                val message = R.string.budget_deleted_feedback.toString() + " \"${budget.name}\""
                _uiState.update { it.copy(userMessage = message) }
                Log.i("BudgetVM", "Budget deleted successfully: ID=${budget.id}")
            } catch (e: Exception) {
                Log.e("BudgetVM", "Error deleting budget ${budget.id}", e)
                _uiState.update { it.copy(errorMessage = "Ошибка удаления бюджета: ${e.message}") }
            }
        }
    }

    /** Обработчик ошибок загрузки данных. */
    private fun handleLoadingError(e: Throwable, context: String) {
        Log.e("BudgetVM", "Error loading $context", e)
        _uiState.update {
            it.copy(
                isLoadingAllBudgets = false,
                isLoadingProgress = false,
                errorMessage = "Ошибка загрузки $context: ${e.message}"
            )
        }
    }

    /** Сбрасывает сообщение для пользователя после его отображения. */
    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Сбрасывает сообщение об ошибке после его отображения. */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Фабрика для создания ViewModel с нужными зависимостями. */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                BudgetViewModel(application.container.budgetRepository)
            }
        }
    }
}