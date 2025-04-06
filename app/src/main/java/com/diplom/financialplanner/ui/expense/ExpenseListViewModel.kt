package com.diplom.financialplanner.ui.expense

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import com.diplom.financialplanner.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Состояние UI для экрана списка расходов. */
data class ExpenseListUiState(
    val expenses: List<TransactionWithCategory> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val userMessage: String? = null // Для сообщений (например, об удалении)
)

/**
 * ViewModel для экрана списка расходов (`ExpenseListFragment`).
 */
class ExpenseListViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    // StateFlow для UI State
    private val _uiState = MutableStateFlow(ExpenseListUiState(isLoading = true))
    val uiState: StateFlow<ExpenseListUiState> = _uiState.asStateFlow()

    init {
        // Загружаем расходы при инициализации
        loadExpenses()
    }

    /** Загружает список расходов из репозитория. */
    private fun loadExpenses() {
        viewModelScope.launch {
            transactionRepository.getTransactionsWithCategoryByTypeStream(TransactionType.EXPENSE)
                .onStart { _uiState.update { it.copy(isLoading = true) } } // Показываем загрузку
                .catch { e ->
                    // Обработка ошибки загрузки
                    Log.e("ExpenseListVM", "Error loading expenses", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки расходов: ${e.message}") } // TODO: Ресурс строки
                }
                .collect { transactions ->
                    // Обновляем стейт полученным списком
                    Log.d("ExpenseListVM", "Expenses loaded: ${transactions.size} items")
                    _uiState.update { it.copy(isLoading = false, expenses = transactions) }
                }
        }
    }

    /** Удаляет расход. */
    fun deleteExpense(transaction: TransactionEntity) {
        // Дополнительная проверка типа, хотя вызываться должно только для расходов
        if (transaction.type != TransactionType.EXPENSE) {
            Log.w("ExpenseListVM", "Attempted to delete non-expense transaction: ${transaction.id}")
            _uiState.update { it.copy(errorMessage = "Ошибка: Попытка удалить не расход.") } // TODO: Ресурс строки
            return
        }

        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(transaction)
                Log.i("ExpenseListVM", "Expense deleted: ${transaction.id}")
                // Показываем сообщение об успехе
                _uiState.update { it.copy(userMessage = R.string.expense_deleted_success.toString()) } // TODO: Контекст для getString
            } catch (e: Exception) {
                Log.e("ExpenseListVM", "Error deleting expense ${transaction.id}", e)
                _uiState.update { it.copy(errorMessage = "Ошибка удаления расхода: ${e.message}") } // TODO: Ресурс строки
            }
        }
    }

    /** Сбрасывает сообщение для пользователя. */
    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Сбрасывает сообщение об ошибке. */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Фабрика для создания ViewModel. */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                ExpenseListViewModel(application.container.transactionRepository)
            }
        }
    }
}