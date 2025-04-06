package com.diplom.financialplanner.ui.categories

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.data.repository.BudgetRepository
import com.diplom.financialplanner.data.repository.CategoryRepository
import com.diplom.financialplanner.data.repository.TransactionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Состояние UI для экрана управления категориями. */
data class CategoryUiState(
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

/** Состояние для диалога добавления/редактирования категории. */
data class CategoryInputState(
    val categoryId: Long? = null,
    val name: String = "",
    val type: String = "expense", // Тип по умолчанию - Расход
    val iconResName: String? = null, // Имя ресурса иконки
    val colorHex: String? = null,   // HEX цвета
    val saveSuccess: Boolean = false // Флаг успеха для закрытия диалога
)

/**
 * ViewModel для экрана управления категориями (`CategoryManagerFragment`).
 */
class CategoryViewModel(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository, // Для проверки использования в транзакциях
    private val budgetRepository: BudgetRepository      // Для проверки использования в бюджетах
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    private val _inputState = MutableStateFlow(CategoryInputState())
    val inputState: StateFlow<CategoryInputState> = _inputState.asStateFlow()

    init {
        loadCategories()
    }

    /** Загружает списки категорий расходов и доходов. */
    private fun loadCategories() {
        viewModelScope.launch {
            combine(
                categoryRepository.getCategoriesByTypeStream("expense"),
                categoryRepository.getCategoriesByTypeStream("income")
            ) { expenses, incomes ->
                CategoryUiState(expenseCategories = expenses, incomeCategories = incomes, isLoading = false)
            }
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> handleLoadingError(e) }
                .collect { _uiState.value = it }
        }
    }

    /** Подготавливает состояние для диалога добавления/редактирования. */
    fun prepareCategory(category: CategoryEntity? = null) {
        _inputState.value = category?.let {
            // Редактирование существующей
            CategoryInputState(
                categoryId = it.id,
                name = it.name,
                type = it.type,
                iconResName = it.iconResName,
                colorHex = it.colorHex
            )
        } ?: CategoryInputState() // Создание новой
    }

    // --- Обновление полей ввода ---
    fun updateInputName(name: String) { _inputState.update { it.copy(name = name) } }
    fun updateInputType(type: String) { _inputState.update { it.copy(type = type) } }
    // TODO: Функции для обновления иконки (iconResName) и цвета (colorHex)
    // fun updateInputIcon(iconName: String?) { _inputState.update { it.copy(iconResName = iconName) } }
    // fun updateInputColor(colorHex: String?) { _inputState.update { it.copy(colorHex = colorHex) } }
    // --- Конец обновления полей ввода ---


    /** Сохраняет категорию (новую или измененную). */
    fun saveCategory() {
        val currentInput = _inputState.value
        // Валидация имени
        if (currentInput.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = R.string.error_category_name_required.toString()) } // TODO: Context
            return
        }

        // Создаем сущность для сохранения
        val category = CategoryEntity(
            id = currentInput.categoryId ?: 0L, // 0 для новой
            name = currentInput.name.trim(),
            type = currentInput.type,
            // Устанавливаем иконку и цвет (или значения по умолчанию)
            iconResName = currentInput.iconResName ?: "ic_category_other",
            colorHex = currentInput.colorHex ?: "#BDBDBD" // TODO: Использовать цвет из ресурсов?
        )

        viewModelScope.launch {
            try {
                val messageResId: Int
                if (category.id == 0L) {
                    // Вставка новой категории
                    categoryRepository.insertCategory(category)
                    messageResId = R.string.category_saved_success
                } else {
                    // Обновление существующей
                    categoryRepository.updateCategory(category) // Убедитесь, что метод есть в репо/дао
                    messageResId = R.string.category_updated_success
                }
                _uiState.update { it.copy(userMessage = messageResId.toString()) } // TODO: Context
                _inputState.update { it.copy(saveSuccess = true) } // Сигнал успеха для диалога
            } catch (e: Exception) { handleSaveError(e) }
        }
    }

    /** Удаляет указанную категорию после проверки на использование. */
    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            try {
                // Проверяем, используется ли категория в транзакциях или бюджетах
                val isUsed = coroutineScope {
                    val usedInTransactions = async { transactionRepository.isCategoryUsed(category.id) }
                    val usedInBudgets = async { budgetRepository.isCategoryUsedInBudgets(category.id) }
                    usedInTransactions.await() || usedInBudgets.await()
                }

                if (isUsed) {
                    Log.w("CategoryVM", "Attempt to delete category ${category.id} which is in use.")
                    // Показываем ошибку пользователю
                    _uiState.update { it.copy(errorMessage = R.string.error_category_in_use.toString()) } // TODO: Context
                    return@launch // Прерываем удаление
                }

                // Если категория не используется, удаляем ее
                categoryRepository.deleteCategory(category) // Убедитесь, что метод есть в репо/дао
                _uiState.update { it.copy(userMessage = "Категория '${category.name}' удалена") } // TODO: Ресурс строки
            } catch (e: Exception) { handleDeleteError(e, category) }
        }
    }

    // --- Обработка ошибок ---
    private fun handleLoadingError(e: Throwable) {
        Log.e("CategoryVM", "Error loading categories", e)
        _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки категорий: ${e.message}") }
    }
    private fun handleSaveError(e: Throwable) {
        Log.e("CategoryVM", "Error saving category", e)
        _uiState.update { it.copy(errorMessage = "Ошибка сохранения категории: ${e.message}") }
    }
    private fun handleDeleteError(e: Throwable, category: CategoryEntity) {
        Log.e("CategoryVM", "Error deleting category ${category.id}", e)
        // Дополнительная проверка на ограничение внешнего ключа (если используется RESTRICT)
        if (e is SQLiteConstraintException) {
            _uiState.update { it.copy(errorMessage = R.string.error_category_in_use.toString()) } // TODO: Context
        } else {
            _uiState.update { it.copy(errorMessage = "Ошибка удаления категории: ${e.message}") }
        }
    }
    // --- Конец обработки ошибок ---

    // --- Методы очистки ---
    fun clearUserMessage() { _uiState.update { it.copy(userMessage = null) } }
    fun clearErrorMessage() { _uiState.update { it.copy(errorMessage = null) } }
    fun consumeInputSuccess() { _inputState.update { it.copy(saveSuccess = false) } }
    // --- Конец методов очистки ---

    /** Фабрика для создания ViewModel. */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                CategoryViewModel(
                    application.container.categoryRepository,
                    application.container.transactionRepository,
                    application.container.budgetRepository // Передаем все нужные репозитории
                )
            }
        }
    }
}