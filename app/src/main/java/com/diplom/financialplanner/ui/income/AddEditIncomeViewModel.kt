package com.diplom.financialplanner.ui.income

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.repository.CategoryRepository
import com.diplom.financialplanner.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/** Состояние UI для экрана добавления/редактирования дохода. */
data class AddEditIncomeUiState(
    val amount: String = "",
    val categoryId: Long? = null, // ID категории/источника
    val description: String = "",
    val availableCategories: List<CategoryEntity> = emptyList(), // Категории типа "income"
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true, // Загрузка категорий или данных транзакции
    val isEditMode: Boolean = false,
    val initialDate: Date = Date() // Дата для DatePicker
)

/**
 * ViewModel для экрана добавления/редактирования дохода (`AddEditIncomeFragment`).
 */
class AddEditIncomeViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val transactionId: Long = savedStateHandle.get<Long>("transactionId") ?: 0L
    private val isEditMode = transactionId != 0L

    private val _uiState = MutableStateFlow(AddEditIncomeUiState(isEditMode = isEditMode))
    val uiState: StateFlow<AddEditIncomeUiState> = _uiState.asStateFlow()

    private var selectedDate: Date = Date()

    init {
        Log.d("AddEditIncomeVM", "Init ViewModel. EditMode: $isEditMode, Transaction ID: $transactionId")
        loadIncomeCategories() // Загружаем категории ДОХОДОВ
        if (isEditMode) {
            loadTransactionDetails(transactionId)
        } else {
            updateSelectedDate(Date()) // Устанавливаем текущую дату
            _uiState.update { it.copy(isLoading = false) } // Завершаем загрузку
        }
    }

    /** Загружает список категорий типа "income". */
    private fun loadIncomeCategories() {
        viewModelScope.launch {
            categoryRepository.getCategoriesByTypeStream("income") // Фильтр по типу "income"
                .catch { e -> handleLoadingError(e, "категорий дохода") }
                .collect { categories ->
                    Log.d("AddEditIncomeVM", "Income categories loaded: ${categories.size}")
                    _uiState.update {
                        it.copy(
                            availableCategories = categories,
                            isLoading = it.isLoading && isEditMode // Завершаем загрузку, если не ждем транзакцию
                        )
                    }
                }
        }
    }

    /** Загружает детали существующей транзакции дохода. */
    private fun loadTransactionDetails(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val transaction = transactionRepository.getTransactionById(id)
                // Убеждаемся, что транзакция найдена и это ДОХОД
                if (transaction != null && transaction.type == TransactionType.INCOME) {
                    Log.d("AddEditIncomeVM", "Income Transaction loaded: $transaction")
                    updateSelectedDate(transaction.date)
                    _uiState.update {
                        it.copy(
                            amount = formatAmountForInput(transaction.amount),
                            categoryId = transaction.categoryId,
                            description = transaction.description ?: "",
                            isLoading = false
                        )
                    }
                } else {
                    Log.e("AddEditIncomeVM", "Income transaction with ID $id not found or wrong type.")
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Доход не найден.") } // TODO: Ресурс строки
                }
            } catch (e: Exception) { handleLoadingError(e, "деталей дохода") }
        }
    }

    // --- Методы обновления полей ввода ---
    fun updateAmount(amountStr: String) { _uiState.update { it.copy(amount = amountStr) } }
    fun updateDescription(desc: String) { _uiState.update { it.copy(description = desc) } }
    fun updateCategorySelection(category: CategoryEntity?) { _uiState.update { it.copy(categoryId = category?.id) } }
    fun updateSelectedDate(date: Date) {
        selectedDate = date
        _uiState.update { it.copy(initialDate = date) }
    }
    // -------------------------------------

    /** Сохраняет доход (новый или измененный). */
    fun saveIncome() {
        val currentState = _uiState.value
        val amountDouble = parseAmount(currentState.amount)

        // --- Валидация ---
        if (amountDouble == null || amountDouble <= 0) {
            _uiState.update { it.copy(errorMessage = R.string.error_amount_invalid.toString()) } // TODO: Context
            return
        }
        if (currentState.categoryId == null) {
            // Используем ту же строку ошибки, что и для расходов
            _uiState.update { it.copy(errorMessage = R.string.error_category_required.toString()) } // TODO: Context
            return
        }
        // --- Конец валидации ---

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        val transactionToSave = TransactionEntity(
            id = if (isEditMode) transactionId else 0L,
            type = TransactionType.INCOME, // Указываем тип ДОХОД
            amount = amountDouble,
            categoryId = currentState.categoryId,
            date = selectedDate,
            description = currentState.description.trim().takeIf { it.isNotEmpty() }
        )

        Log.d("AddEditIncomeVM", "Saving income (EditMode: $isEditMode): $transactionToSave")

        viewModelScope.launch {
            try {
                if (isEditMode) {
                    transactionRepository.updateTransaction(transactionToSave)
                } else {
                    transactionRepository.insertTransaction(transactionToSave)
                }
                Log.i("AddEditIncomeVM", "Income saved successfully.")
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                Log.e("AddEditIncomeVM", "Error saving income", e)
                _uiState.update { it.copy(isSaving = false, errorMessage = "Ошибка сохранения дохода: ${e.message}") } // TODO: Ресурс строки
            }
        }
    }

    /** Обработчик ошибок загрузки. */
    private fun handleLoadingError(e: Throwable, context: String) {
        Log.e("AddEditIncomeVM", "Error loading $context", e)
        _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки $context: ${e.message}") } // TODO: Ресурс строки
    }

    /** Вспомогательная функция для парсинга суммы. */
    private fun parseAmount(amountStr: String): Double? {
        return amountStr.replace(',', '.').toDoubleOrNull()
    }

    /** Вспомогательная функция для форматирования суммы для поля ввода. */
    private fun formatAmountForInput(amount: Double): String {
        return amount.toString()
    }

    // --- Методы очистки состояния ---
    fun consumeInputSuccess() { _uiState.update { it.copy(saveSuccess = false) } }
    fun clearErrorMessage() { _uiState.update { it.copy(errorMessage = null) } }
    // --- Конец методов очистки ---

    /** Фабрика для ViewModel. */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                AddEditIncomeViewModel(
                    application.container.transactionRepository,
                    application.container.categoryRepository,
                    createSavedStateHandle()
                )
            }
        }
    }
}