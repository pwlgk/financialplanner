package com.diplom.financialplanner.ui.expense

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

/** Состояние UI для экрана добавления/редактирования расхода. */
data class AddEditExpenseUiState(
    val amount: String = "", // Сумма как строка для EditText
    val categoryId: Long? = null, // ID выбранной категории
    val description: String = "", // Описание
    val availableCategories: List<CategoryEntity> = emptyList(), // Список доступных категорий расходов
    val isSaving: Boolean = false, // Идет ли процесс сохранения
    val saveSuccess: Boolean = false, // Успешно ли сохранено (для навигации)
    val errorMessage: String? = null,
    val isLoading: Boolean = true, // Загрузка категорий или данных транзакции
    val isEditMode: Boolean = false, // Флаг режима редактирования
    val initialDate: Date = Date() // Дата для инициализации DatePicker'а
)

/**
 * ViewModel для экрана добавления/редактирования расхода (`AddEditExpenseFragment`).
 */
class AddEditExpenseViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle // Для получения ID транзакции из аргументов
) : ViewModel() {

    // Получаем ID транзакции из аргументов (0L если новая)
    private val transactionId: Long = savedStateHandle.get<Long>("transactionId") ?: 0L
    private val isEditMode = transactionId != 0L

    private val _uiState = MutableStateFlow(AddEditExpenseUiState(isEditMode = isEditMode))
    val uiState: StateFlow<AddEditExpenseUiState> = _uiState.asStateFlow()

    // Храним выбранную дату отдельно, чтобы избежать проблем с форматированием в StateFlow
    private var selectedDate: Date = Date()

    init {
        Log.d("AddEditExpenseVM", "Init ViewModel. EditMode: $isEditMode, Transaction ID: $transactionId")
        // Начинаем загрузку категорий
        loadCategories()
        // Если режим редактирования, загружаем детали транзакции
        if (isEditMode) {
            loadTransactionDetails(transactionId)
        } else {
            // Для нового расхода устанавливаем текущую дату по умолчанию
            updateSelectedDate(Date()) // Устанавливаем и сохраняем текущую дату
            // Убираем флаг загрузки, т.к. транзакцию не грузим
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Загружает список категорий расходов. */
    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getCategoriesByTypeStream("expense")
                .catch { e -> handleLoadingError(e, "категорий") }
                .collect { categories ->
                    Log.d("AddEditExpenseVM", "Expense categories loaded: ${categories.size}")
                    // Обновляем список категорий и флаг загрузки (если не редактирование)
                    _uiState.update {
                        it.copy(
                            availableCategories = categories,
                            // isLoading остается true, если мы еще грузим транзакцию (в режиме ред.)
                            isLoading = it.isLoading && isEditMode
                        )
                    }
                }
        }
    }

    /** Загружает детали существующей транзакции для редактирования. */
    private fun loadTransactionDetails(id: Long) {
        // Устанавливаем флаг загрузки (мог быть сброшен после загрузки категорий)
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val transaction = transactionRepository.getTransactionById(id)
                if (transaction != null && transaction.type == TransactionType.EXPENSE) {
                    Log.d("AddEditExpenseVM", "Transaction loaded: $transaction")
                    updateSelectedDate(transaction.date) // Устанавливаем дату из транзакции
                    // Обновляем стейт данными транзакции
                    _uiState.update {
                        it.copy(
                            amount = formatAmountForInput(transaction.amount), // Форматируем для поля ввода
                            categoryId = transaction.categoryId,
                            description = transaction.description ?: "",
                            isLoading = false // Загрузка завершена
                        )
                    }
                } else {
                    Log.e("AddEditExpenseVM", "Expense transaction with ID $id not found.")
                    // Устанавливаем ошибку, если транзакция не найдена или не расход
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Расход не найден.") } // TODO: Ресурс строки
                }
            } catch (e: Exception) { handleLoadingError(e, "деталей расхода") }
        }
    }

    // --- Методы для обновления полей из UI ---
    fun updateAmount(amountStr: String) { _uiState.update { it.copy(amount = amountStr) } }
    fun updateDescription(desc: String) { _uiState.update { it.copy(description = desc) } }
    fun updateCategorySelection(category: CategoryEntity?) { _uiState.update { it.copy(categoryId = category?.id) } }
    fun updateSelectedDate(date: Date) {
        selectedDate = date
        _uiState.update { it.copy(initialDate = date) } // Обновляем initialDate для DatePicker
    }
    // ------------------------------------------

    /** Сохраняет расход (новый или измененный). */
    fun saveExpense() {
        val currentState = _uiState.value
        val amountDouble = parseAmount(currentState.amount) // Парсим сумму

        // --- Валидация ---
        if (amountDouble == null || amountDouble <= 0) {
            _uiState.update { it.copy(errorMessage = R.string.error_amount_invalid.toString()) } // TODO: Context + Ресурс строки
            return
        }
        if (currentState.categoryId == null) {
            _uiState.update { it.copy(errorMessage = R.string.error_category_required.toString()) } // TODO: Context
            return
        }
        // Дата всегда выбрана (selectedDate)
        // --- Конец валидации ---

        _uiState.update { it.copy(isSaving = true, errorMessage = null) } // Статус сохранения

        val transactionToSave = TransactionEntity(
            id = if (isEditMode) transactionId else 0L, // ID для обновления или 0 для вставки
            type = TransactionType.EXPENSE,
            amount = amountDouble,
            categoryId = currentState.categoryId, // ID категории уже в стейте
            date = selectedDate, // Используем сохраненную дату
            description = currentState.description.trim().takeIf { it.isNotEmpty() } // null если пусто
        )

        Log.d("AddEditExpenseVM", "Saving expense (EditMode: $isEditMode): $transactionToSave")

        viewModelScope.launch {
            try {
                if (isEditMode) {
                    transactionRepository.updateTransaction(transactionToSave)
                } else {
                    transactionRepository.insertTransaction(transactionToSave)
                }
                Log.i("AddEditExpenseVM", "Expense saved successfully.")
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) } // Сигнал успеха
            } catch (e: Exception) {
                Log.e("AddEditExpenseVM", "Error saving expense", e)
                _uiState.update { it.copy(isSaving = false, errorMessage = "Ошибка сохранения: ${e.message}") } // TODO: Ресурс строки
            }
        }
    }

    /** Обработчик ошибок загрузки. */
    private fun handleLoadingError(e: Throwable, context: String) {
        Log.e("AddEditExpenseVM", "Error loading $context", e)
        _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки $context: ${e.message}") } // TODO: Ресурс строки
    }

    /** Вспомогательная функция для парсинга суммы из строки. */
    private fun parseAmount(amountStr: String): Double? {
        // Учитываем запятую как разделитель
        return amountStr.replace(',', '.').toDoubleOrNull()
    }

    /** Вспомогательная функция для форматирования суммы для поля ввода. */
    private fun formatAmountForInput(amount: Double): String {
        return amount.toString()
    }


    /** Сбрасывает флаг успешного сохранения. */
    fun consumeInputSuccess() { _uiState.update { it.copy(saveSuccess = false) } }
    /** Сбрасывает сообщение об ошибке. */
    fun clearErrorMessage() { _uiState.update { it.copy(errorMessage = null) } }

    /** Фабрика для ViewModel. */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                AddEditExpenseViewModel(
                    application.container.transactionRepository,
                    application.container.categoryRepository,
                    createSavedStateHandle()
                )
            }
        }
    }
}