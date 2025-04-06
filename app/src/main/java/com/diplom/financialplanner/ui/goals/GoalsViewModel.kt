package com.diplom.financialplanner.ui.goals

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.R // Для строк успеха/ошибок
import com.diplom.financialplanner.data.database.entity.FinancialGoalEntity
import com.diplom.financialplanner.data.repository.FinancialGoalRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Состояние UI для основного экрана списка целей. */
data class GoalsUiState(
    val goals: List<FinancialGoalEntity> = emptyList(),
    val isLoading: Boolean = true,
    val userMessage: String? = null, // Для Snackbar/Toast
    val errorMessage: String? = null
)

/** Состояние для диалога/экрана добавления/редактирования цели. */
data class GoalInputState(
    val goalId: Long? = null, // null для новой цели
    val name: String = "",
    val targetAmount: String = "",
    val currentAmount: String = "", // Для редактирования текущей суммы
    val isEditingAmountOnly: Boolean = false, // Флаг для диалога редактирования суммы
    val saveSuccess: Boolean = false // Флаг для закрытия диалога/фрагмента
)

/**
 * ViewModel для управления списком и редактированием финансовых целей (`GoalsFragment`).
 */
class GoalsViewModel(
    private val goalRepository: FinancialGoalRepository,
    // savedStateHandle может понадобиться, если редактирование будет на отдельном экране
    // private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    private val _goalInputState = MutableStateFlow(GoalInputState())
    val goalInputState: StateFlow<GoalInputState> = _goalInputState.asStateFlow()

    init {
        loadGoals()
    }

    /** Загружает список всех целей из репозитория. */
    private fun loadGoals() {
        viewModelScope.launch {
            goalRepository.getAllGoalsStream()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> handleLoadingError(e) }
                .collect { goalsList ->
                    Log.d("GoalsVM", "Goals loaded: ${goalsList.size}")
                    _uiState.update { it.copy(isLoading = false, goals = goalsList) }
                }
        }
    }

    /** Подготавливает состояние для добавления новой цели. */
    fun prepareNewGoal() {
        _goalInputState.value = GoalInputState()
    }

    /** Подготавливает состояние для редактирования существующей цели. */
    fun prepareEditGoal(goal: FinancialGoalEntity) {
        _goalInputState.value = GoalInputState(
            goalId = goal.id,
            name = goal.name,
            targetAmount = goal.targetAmount.toString(),
            currentAmount = goal.currentAmount.toString() // Используем текущую сумму для предзаполнения
        )
    }

    /** Подготавливает состояние для редактирования ТОЛЬКО текущей накопленной суммы. */
    fun prepareEditAmount(goal: FinancialGoalEntity) {
        _goalInputState.value = GoalInputState(
            goalId = goal.id,
            name = goal.name, // Имя нужно для заголовка диалога
            targetAmount = goal.targetAmount.toString(), // Цель нужна для информации
            currentAmount = goal.currentAmount.toString(), // Предзаполняем текущей суммой
            isEditingAmountOnly = true // Устанавливаем флаг
        )
    }

    // --- Методы для обновления полей ввода из UI ---
    fun updateGoalName(name: String) { _goalInputState.update { it.copy(name = name) } }
    fun updateTargetAmount(amount: String) { _goalInputState.update { it.copy(targetAmount = amount) } }
    fun updateCurrentAmountInput(amount: String) { _goalInputState.update { it.copy(currentAmount = amount) } }
    // -------------------------------------------------

    /** Сохраняет цель (новую или измененную). */
    fun saveGoal() {
        val inputState = _goalInputState.value
        val targetAmount = inputState.targetAmount.toDoubleOrNull()
        // При сохранении всей цели, currentAmount берется из состояния ввода,
        // которое было установлено при prepareEditGoal или оставлено пустым для новой цели.
        val currentAmount = inputState.currentAmount.toDoubleOrNull() ?: 0.0

        // --- Валидация ---
        if (inputState.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = R.string.error_goal_name_required.toString()) } // TODO: Передать Context для getString
            return
        }
        if (targetAmount == null || targetAmount <= 0) {
            _uiState.update { it.copy(errorMessage = R.string.error_goal_target_amount_required.toString()) } // TODO: Передать Context
            return
        }
        if (currentAmount < 0) { // Проверяем и текущую сумму
            _uiState.update { it.copy(errorMessage = R.string.error_goal_current_amount_required.toString()) } // TODO: Передать Context
            return
        }
        // --- Конец валидации ---

        val goalEntity = FinancialGoalEntity(
            id = inputState.goalId ?: 0L, // 0 для новой цели
            name = inputState.name.trim(),
            targetAmount = targetAmount,
            // Если редактируем всю цель, сохраняем введенную/предзаполненную текущую сумму
            // Для новой цели currentAmount будет 0.0 (установлено выше)
            currentAmount = currentAmount
        )

        viewModelScope.launch {
            try {
                val messageResId: Int
                if (goalEntity.id == 0L) {
                    goalRepository.insertGoal(goalEntity)
                    messageResId = R.string.goal_saved_success
                } else {
                    goalRepository.updateGoal(goalEntity)
                    messageResId = R.string.goal_updated_success
                }
                _uiState.update { it.copy(userMessage = messageResId.toString()) } // TODO: Передать Context
                _goalInputState.update { it.copy(saveSuccess = true) } // Сигнал успе
            } catch (e: Exception) { handleSaveError(e) }
        }
    }

    /** Сохраняет только обновленную текущую накопленную сумму. */
    fun saveCurrentAmount() {
        val inputState = _goalInputState.value
        val goalId = inputState.goalId
        val newAmount = inputState.currentAmount.toDoubleOrNull()

        // Валидация
        if (goalId == null) {
            Log.e("GoalsVM", "Cannot update amount: goalId is null")
            _uiState.update { it.copy(errorMessage = "Ошибка: Не найдена цель для обновления.") } // TODO: Ресурс строки
            return
        }
        if (newAmount == null || newAmount < 0) {
            _uiState.update { it.copy(errorMessage = R.string.error_goal_current_amount_required.toString()) } // TODO: Передать Context
            return
        }
        // --- Конец валидации ---

        viewModelScope.launch {
            try {
                goalRepository.updateCurrentAmount(goalId, newAmount)
                _uiState.update { it.copy(userMessage = R.string.goal_amount_updated_success.toString()) } // TODO: Передать Context
                _goalInputState.update { it.copy(saveSuccess = true) } // Сигнал успеха
            } catch (e: Exception) { handleUpdateAmountError(e, goalId) }
        }
    }

    /** Удаляет указанную финансовую цель. */
    fun deleteGoal(goal: FinancialGoalEntity) {
        viewModelScope.launch {
            try {
                goalRepository.deleteGoal(goal)
                _uiState.update { it.copy(userMessage = "Цель '${goal.name}' удалена") } // TODO: Ресурс строки
            } catch (e: Exception) { handleDeleteError(e, goal) }
        }
    }

    // --- Обработка ошибок ---
    private fun handleLoadingError(e: Throwable) {
        Log.e("GoalsVM", "Error loading goals", e)
        _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки целей: ${e.message}") } // TODO: Ресурс строки
    }
    private fun handleSaveError(e: Throwable) {
        Log.e("GoalsVM", "Error saving goal", e)
        _uiState.update { it.copy(errorMessage = "Ошибка сохранения цели: ${e.message}") } // TODO: Ресурс строки
    }
    private fun handleUpdateAmountError(e: Throwable, goalId: Long) {
        Log.e("GoalsVM", "Error updating current amount for goal $goalId", e)
        _uiState.update { it.copy(errorMessage = "Ошибка обновления суммы: ${e.message}") } // TODO: Ресурс строки
    }
    private fun handleDeleteError(e: Throwable, goal: FinancialGoalEntity) {
        Log.e("GoalsVM", "Error deleting goal ${goal.id}", e)
        _uiState.update { it.copy(errorMessage = "Ошибка удаления цели: ${e.message}") } // TODO: Ресурс строки
    }
    // --- Конец обработки ошибок ---


    // --- Методы очистки сообщений и статуса ---
    fun clearUserMessage() { _uiState.update { it.copy(userMessage = null) } }
    fun clearErrorMessage() { _uiState.update { it.copy(errorMessage = null) } }
    fun consumeInputSuccess() { _goalInputState.update { it.copy(saveSuccess = false) } }
    // --- Конец методов очистки ---

    /** Фабрика для создания ViewModel. */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                GoalsViewModel(
                    application.container.financialGoalRepository
                    // createSavedStateHandle() // Раскомментировать, если понадобится SavedStateHandle
                )
            }
        }
    }
}