package com.diplom.financialplanner.ui.reports

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.repository.TransactionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

/**
 * Sealed Interface для представления данных разных типов отчетов.
 * Позволяет безопасно обрабатывать разные структуры данных в UI.
 */
sealed interface ReportData {
    /** Состояние по умолчанию или при отсутствии данных/ошибке. */
    object None : ReportData
    /** Данные для отчета по категориям (расходы или доходы). */
    data class CategoryReportData(val items: List<CategorySpending>) : ReportData
    /** Данные для отчета "Доходы vs Расходы". */
    data class IncomeExpenseReportData(val totalIncome: Double, val totalExpense: Double) : ReportData
}

/** Перечисление доступных типов отчетов. */
enum class ReportType {
    EXPENSE_BY_CATEGORY,
    INCOME_BY_SOURCE,
    INCOME_VS_EXPENSE
}

/** Состояние UI для экрана отчетов. */
data class ReportsUiState(
    val startDate: Date? = null, // Начало выбранного периода
    val endDate: Date? = null,   // Конец выбранного периода
    val selectedReportType: ReportType = ReportType.EXPENSE_BY_CATEGORY, // Текущий выбранный тип отчета
    val reportData: ReportData = ReportData.None, // Данные для отображения (зависят от типа отчета)
    val isLoading: Boolean = false, // Идет ли загрузка данных
    val errorMessage: String? = null, // Сообщение об ошибке
    val noDataAvailable: Boolean = false // Флаг, указывающий, что данных нет (после загрузки)
)

/**
 * ViewModel для экрана отчетов (`ReportsFragment`).
 * Отвечает за загрузку данных для отчетов в зависимости от выбранного типа и периода.
 */
class ReportsViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        // Устанавливаем период по умолчанию - текущий месяц
        setDefaultPeriod()
        loadReportData() // Загружаем данные для периода по умолчанию
    }

    /** Устанавливает период по умолчанию (текущий месяц). */
    private fun setDefaultPeriod() {
        val calendar = Calendar.getInstance()
        // Конец периода - текущий момент (конец дня)
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
        val end = calendar.time
        // Начало периода - первый день текущего месяца
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.time
        _uiState.update { it.copy(startDate = start, endDate = end) }
        Log.d("ReportsVM", "Default period set: $start - $end")
    }

    /**
     * Устанавливает новый период для отчетов и запускает перезагрузку данных.
     * @param startDate Начальная дата периода.
     * @param endDate Конечная дата периода.
     */
    fun setPeriod(startDate: Date, endDate: Date) {
        // Гарантируем, что время установлено на начало/конец дня
        val startCalendar = Calendar.getInstance().apply { time = startDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val endCalendar = Calendar.getInstance().apply { time = endDate; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        val finalStartDate = startCalendar.time
        val finalEndDate = endCalendar.time

        _uiState.update { it.copy(startDate = finalStartDate, endDate = finalEndDate) }
        Log.d("ReportsVM", "Period updated: $finalStartDate - $finalEndDate")
        loadReportData() // Перезагружаем данные
    }

    /**
     * Устанавливает новый тип отчета и запускает перезагрузку данных.
     * @param reportType Выбранный тип отчета.
     */
    fun setReportType(reportType: ReportType) {
        _uiState.update { it.copy(selectedReportType = reportType) }
        Log.d("ReportsVM", "Report type updated: $reportType")
        loadReportData() // Перезагружаем данные
    }

    /** Загружает данные для отчета в зависимости от текущего состояния (тип, период). */
    private fun loadReportData() {
        val currentState = _uiState.value
        val startDate = currentState.startDate
        val endDate = currentState.endDate

        // Проверка на наличие периода
        if (startDate == null || endDate == null) {
            Log.w("ReportsVM", "Cannot load data: period is not set.")
            _uiState.update { it.copy(errorMessage = "Период не выбран", reportData = ReportData.None, isLoading = false) } // TODO: Ресурс строки
            return
        }

        // Устанавливаем состояние загрузки
        _uiState.update { it.copy(isLoading = true, errorMessage = null, reportData = ReportData.None, noDataAvailable = false) }
        Log.i("ReportsVM", "Loading report data for type: ${currentState.selectedReportType}, Period: $startDate - $endDate")

        viewModelScope.launch {
            try {
                // Загружаем данные в зависимости от типа отчета
                val newReportData: ReportData = when (currentState.selectedReportType) {
                    ReportType.EXPENSE_BY_CATEGORY -> {
                        val data = transactionRepository.getSpendingByCategoryForPeriod(TransactionType.EXPENSE, startDate, endDate)
                        if (data.isNotEmpty()) ReportData.CategoryReportData(data) else ReportData.None
                    }
                    ReportType.INCOME_BY_SOURCE -> {
                        val data = transactionRepository.getSpendingByCategoryForPeriod(TransactionType.INCOME, startDate, endDate)
                        if (data.isNotEmpty()) ReportData.CategoryReportData(data) else ReportData.None
                    }
                    ReportType.INCOME_VS_EXPENSE -> {
                        // Выполняем запросы параллельно
                        coroutineScope {
                            val incomeDeferred = async { transactionRepository.getTotalAmountByTypeAndDate(TransactionType.INCOME, startDate, endDate) }
                            val expenseDeferred = async { transactionRepository.getTotalAmountByTypeAndDate(TransactionType.EXPENSE, startDate, endDate) }
                            val totalIncome = incomeDeferred.await() ?: 0.0
                            val totalExpense = expenseDeferred.await() ?: 0.0
                            // Считаем, что данные есть, если есть доходы или расходы
                            if (totalIncome > 0 || totalExpense > 0) {
                                ReportData.IncomeExpenseReportData(totalIncome, totalExpense)
                            } else {
                                ReportData.None
                            }
                        }
                    }
                }
                Log.i("ReportsVM", "Report data loaded successfully: ${newReportData::class.simpleName}")
                // Обновляем стейт с полученными данными
                _uiState.update {
                    it.copy(
                        reportData = newReportData,
                        isLoading = false,
                        noDataAvailable = (newReportData == ReportData.None) // Нет данных, если результат None
                    )
                }

            } catch (e: Exception) {
                Log.e("ReportsVM", "Error loading report data", e)
                // Обновляем стейт с ошибкой
                _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки отчета: ${e.message}", reportData = ReportData.None) } // TODO: Ресурс строки
            }
        }
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
                ReportsViewModel(application.container.transactionRepository)
            }
        }
    }
}