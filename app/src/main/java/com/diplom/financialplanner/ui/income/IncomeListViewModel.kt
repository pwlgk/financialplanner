package com.diplom.financialplanner.ui.income

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

/** Состояние UI для экрана списка доходов. */
data class IncomeListUiState(
    val incomeList: List<TransactionWithCategory> = emptyList(), // Список доходов с категориями
    val isLoading: Boolean = true, // Флаг загрузки
    val errorMessage: String? = null, // Сообщение об ошибке
    val userMessage: String? = null // Сообщение для пользователя (например, об удалении)
)

/**
 * ViewModel для экрана списка доходов (`IncomeListFragment`).
 * Отвечает за загрузку и предоставление списка доходов, а также за их удаление.
 */
class IncomeListViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    // StateFlow для хранения и предоставления состояния UI
    private val _uiState = MutableStateFlow(IncomeListUiState(isLoading = true))
    val uiState: StateFlow<IncomeListUiState> = _uiState.asStateFlow()

    init {
        // Загружаем список доходов при инициализации ViewModel
        loadIncome()
    }

    /** Загружает список доходов из репозитория. */
    private fun loadIncome() {
        viewModelScope.launch {
            // Подписываемся на поток доходов с категориями из репозитория
            transactionRepository.getTransactionsWithCategoryByTypeStream(TransactionType.INCOME)
                .onStart {
                    // Устанавливаем флаг загрузки перед началом получения данных
                    _uiState.update { it.copy(isLoading = true) }
                    Log.d("IncomeListVM", "Loading income list...")
                }
                .catch { e ->
                    // Обрабатываем ошибку при загрузке
                    handleLoadingError(e)
                }
                .collect { transactions ->
                    // При получении данных обновляем стейт
                    Log.i("IncomeListVM", "Income list loaded: ${transactions.size} items")
                    _uiState.update {
                        it.copy(
                            isLoading = false, // Загрузка завершена
                            incomeList = transactions // Обновляем список
                        )
                    }
                }
        }
    }

    /** Удаляет указанный доход. */
    fun deleteIncome(transaction: TransactionEntity) {
        // Проверка, что удаляется именно доход
        if (transaction.type != TransactionType.INCOME) {
            Log.w("IncomeListVM", "Attempted to delete non-income transaction: ID=${transaction.id}")
            _uiState.update { it.copy(errorMessage = "Ошибка: Попытка удалить не доход.") }
            return
        }

        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(transaction)
                Log.i("IncomeListVM", "Income deleted: ID=${transaction.id}")
                // Отправляем сообщение об успехе (будет показано во фрагменте)
                _uiState.update { it.copy(userMessage = R.string.income_deleted_success.toString()) }
            } catch (e: Exception) {
                // Обрабатываем ошибку удаления
                handleDeleteError(e, transaction.id)
            }
        }
    }

    /** Обработчик ошибок загрузки. */
    private fun handleLoadingError(e: Throwable) {
        Log.e("IncomeListVM", "Error loading income list", e)
        _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки доходов: ${e.message}") }
    }

    /** Обработчик ошибок удаления. */
    private fun handleDeleteError(e: Throwable, id: Long) {
        Log.e("IncomeListVM", "Error deleting income ID=$id", e)
        _uiState.update { it.copy(errorMessage = "Ошибка удаления дохода: ${e.message}") }
    }


    /** Сбрасывает сообщение для пользователя после его отображения. */
    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Сбрасывает сообщение об ошибке после его отображения. */
    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Фабрика для создания ViewModel. */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                IncomeListViewModel(application.container.transactionRepository)
            }
        }
    }
}