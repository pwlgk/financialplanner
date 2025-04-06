package com.diplom.financialplanner.ui.budget

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.R // Для строк
import com.diplom.financialplanner.data.database.entity.BudgetCategoryLimitEntity
import com.diplom.financialplanner.data.database.entity.BudgetEntity
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.data.repository.BudgetRepository
import com.diplom.financialplanner.data.repository.CategoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** Состояние UI для экрана настройки/редактирования бюджета. */
data class BudgetSetupUiState(
    val budgetId: Long? = null,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH), // 0-11
    val budgetName: String = "",
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val currentLimits: Map<Long, Double> = emptyMap(), // <CategoryId, Amount>
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null, // Может быть ID ресурса или строка
    val periodSelected: Boolean = false
) {
    /** Вычисляет дату начала бюджета. */
    val startDate: Date?
        get() = if (!periodSelected) null else Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

    /** Вычисляет дату окончания бюджета. */
    val endDate: Date?
        get() = if (!periodSelected) null else Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.time
}

/**
 * ViewModel для экрана настройки/редактирования бюджета (`BudgetSetupFragment`).
 */
class BudgetSetupViewModel(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val budgetIdToEdit: Long = savedStateHandle.get<Long>("budgetId") ?: 0L
    private val isEditMode = budgetIdToEdit != 0L

    private val _uiState = MutableStateFlow(BudgetSetupUiState(budgetId = if(isEditMode) budgetIdToEdit else null))
    val uiState: StateFlow<BudgetSetupUiState> = _uiState.asStateFlow()

    private val budgetNameFormatter = SimpleDateFormat("MMMM yyyy", Locale("ru"))

    init {
        Log.d("BudgetSetupVM", "Init ViewModel. EditMode: $isEditMode, Budget ID: $budgetIdToEdit")
        loadInitialData()
    }

    /** Загружает начальные данные. */
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Загружаем категории расходов
                val categories = categoryRepository.getCategoriesByTypeStream("expense").first()
                Log.d("BudgetSetupVM", "Expense categories loaded: ${categories.size}")

                var initialLimits = emptyMap<Long, Double>()
                var initialYear = _uiState.value.selectedYear
                var initialMonth = _uiState.value.selectedMonth
                var periodInitiallySelected = false
                var initialName = ""

                // Если редактирование, загружаем данные бюджета
                if (isEditMode) {
                    Log.d("BudgetSetupVM", "Loading details for budget ID: $budgetIdToEdit")
                    val budgetWithLimits = budgetRepository.getBudgetWithLimits(budgetIdToEdit).first()
                    if (budgetWithLimits != null) {
                        Log.d("BudgetSetupVM", "Budget details loaded: ${budgetWithLimits.budget.name}")
                        initialLimits = budgetWithLimits.limits.associate { it.categoryId to it.limitAmount }
                        val calendar = Calendar.getInstance().apply { time = budgetWithLimits.budget.startDate }
                        initialYear = calendar.get(Calendar.YEAR)
                        initialMonth = calendar.get(Calendar.MONTH)
                        periodInitiallySelected = true
                        initialName = budgetWithLimits.budget.name // Берем имя из существующего бюджета
                        Log.d("BudgetSetupVM", "Initial limits: $initialLimits, Period: $initialMonth/$initialYear")
                    } else {
                        Log.e("BudgetSetupVM", "Budget with ID $budgetIdToEdit not found for editing.")
                        _uiState.update { it.copy(errorMessage = R.string.error_loading_budget_details.toString()) } // TODO: Context
                    }
                } else {
                    // Если новая цель, пытаемся сгенерировать имя на основе текущей даты (если период не выбран)
                    if (!periodInitiallySelected) {
                        initialName = generateBudgetName(initialYear, initialMonth)
                    }
                }

                // Обновляем стейт
                _uiState.update {
                    it.copy(
                        expenseCategories = categories,
                        currentLimits = initialLimits,
                        selectedYear = initialYear,
                        selectedMonth = initialMonth,
                        periodSelected = periodInitiallySelected,
                        budgetName = initialName, // Устанавливаем имя
                        isLoading = false
                    )
                }
            } catch (e: Exception) { handleLoadingError(e) }
        }
    }

    /** Устанавливает выбранный период бюджета. */
    fun setBudgetPeriod(year: Int, month: Int) {
        Log.d("BudgetSetupVM", "Setting budget period: Month=$month, Year=$year")
        _uiState.update {
            it.copy(
                selectedYear = year,
                selectedMonth = month,
                budgetName = generateBudgetName(year, month),
                periodSelected = true,
                errorMessage = null // Сбрасываем ошибку
            )
        }
    }

    /** Обновляет лимит для конкретной категории. */
    fun updateLimit(categoryId: Long, limit: Double?) {
        val updatedLimits = _uiState.value.currentLimits.toMutableMap()
        if (limit != null && limit > 0) {
            updatedLimits[categoryId] = limit
        } else {
            updatedLimits.remove(categoryId)
        }
        _uiState.update { it.copy(currentLimits = updatedLimits, errorMessage = null) } // Сбрасываем ошибку
    }

    /** Сохраняет бюджет. */
    fun saveBudget() {
        val currentState = _uiState.value
        val startDate = currentState.startDate
        val endDate = currentState.endDate

        // Валидация
        if (!currentState.periodSelected || startDate == null || endDate == null) {
            _uiState.update { it.copy(errorMessage = R.string.error_select_period.toString()) } // TODO: Context
            return
        }
        if (currentState.currentLimits.isEmpty()) {
            _uiState.update { it.copy(errorMessage = R.string.error_set_limits.toString()) } // TODO: Context
            return
        }
        // --- Конец валидации ---

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        val budgetEntity = BudgetEntity(
            id = currentState.budgetId ?: 0L,
            name = currentState.budgetName, // Используем имя из стейта
            startDate = startDate,
            endDate = endDate
        )

        val limitEntities = currentState.currentLimits.map { (catId, amount) ->
            BudgetCategoryLimitEntity(
                budgetId = budgetEntity.id, // ID обновится в репозитории
                categoryId = catId,
                limitAmount = amount
            )
        }
        Log.d("BudgetSetupVM", "Saving Budget: $budgetEntity with ${limitEntities.size} limits")

        viewModelScope.launch {
            try {
                budgetRepository.saveBudget(budgetEntity, limitEntities)
                Log.i("BudgetSetupVM", "Budget saved successfully.")
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) { handleSaveError(e) }
        }
    }

    /** Обработчик ошибок загрузки. */
    private fun handleLoadingError(e: Throwable) {
        Log.e("BudgetSetupVM", "Error loading initial data", e)
        _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки данных: ${e.message}") } // TODO: Ресурс строки
    }

    /** Обработчик ошибок сохранения. */
    private fun handleSaveError(e: Throwable) {
        Log.e("BudgetSetupVM", "Error saving budget", e)
        _uiState.update { it.copy(isSaving = false, errorMessage = "Ошибка сохранения бюджета: ${e.message}") } // TODO: Ресурс строки
    }


    // --- Методы очистки состояния ---
    fun consumeInputSuccess() { _uiState.update { it.copy(saveSuccess = false) } }
    fun clearErrorMessage() { _uiState.update { it.copy(errorMessage = null) } }
    // --- Конец методов очистки ---

    /** Генерирует имя бюджета. */
    private fun generateBudgetName(year: Int, month: Int): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
        }
        val formattedName = budgetNameFormatter.format(calendar.time)
        // Делаем первую букву месяца заглавной
        return formattedName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
    }

    /** Фабрика для ViewModel. */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                BudgetSetupViewModel(
                    application.container.budgetRepository,
                    application.container.categoryRepository,
                    createSavedStateHandle()
                )
            }
        }
    }
}