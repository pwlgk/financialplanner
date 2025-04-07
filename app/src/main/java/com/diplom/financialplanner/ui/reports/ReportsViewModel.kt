package com.diplom.financialplanner.ui.reports

import android.content.Context // Импорт Context
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diplom.financialplanner.FinancialPlannerApplication
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.data.database.dao.TimeSeriesDataPoint
import java.text.NumberFormat // Для форматирования чисел в CSV
import java.text.SimpleDateFormat // Для форматирования дат в CSV
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.repository.CategoryRepository // Для получения иконок/цветов (опционально)
import com.diplom.financialplanner.data.repository.TransactionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs


// Sealed Interface для данных отчетов
sealed interface ReportData {
    object None : ReportData
    data class CategoryReportData(val items: List<CategorySpending>) : ReportData
    data class IncomeExpenseReportData(val totalIncome: Double, val totalExpense: Double) : ReportData
    data class TimeSeriesReportData(val points: List<TimeSeriesDataPoint>) : ReportData
}

// Enum для типов отчетов
enum class ReportType {
    EXPENSE_BY_CATEGORY,
    INCOME_BY_SOURCE,
    INCOME_VS_EXPENSE,
    EXPENSE_TREND,
    INCOME_TREND,
}

// Состояние UI экрана отчетов
data class ReportsUiState(
    val isLoading: Boolean = false,
    val selectedReportType: ReportType = ReportType.EXPENSE_BY_CATEGORY, // Тип отчета по умолчанию
    val startDate: Date? = null,
    val endDate: Date? = null,
    val reportData: ReportData = ReportData.None,
    val noDataAvailable: Boolean = false, // Флаг, что данных нет (важно!)
    val errorMessage: String? = null
)

// ViewModel для экрана отчетов
class ReportsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val applicationContext: Context // Добавляем контекст для доступа к строкам
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var currentLoadingJob: Job? = null

    init {
        setDefaultPeriod()
    }

    private fun setDefaultPeriod() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1); setTimeStartOfDay(calendar); val start = calendar.time
        calendar.time = Date() // Сброс на текущую дату перед вычислением конца месяца
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)); setTimeEndOfDay(calendar); val end = calendar.time
        _uiState.update { it.copy(startDate = start, endDate = end) }
        Log.d("ReportsVM", "Default period set: $start - $end")
    }

    fun setPeriod(startDate: Date, endDate: Date) {
        val startCalendar = Calendar.getInstance().apply { time = startDate; setTimeStartOfDay(this) }
        val endCalendar = Calendar.getInstance().apply { time = endDate; setTimeEndOfDay(this) }
        val finalStartDate = startCalendar.time
        val finalEndDate = endCalendar.time
        if (_uiState.value.startDate != finalStartDate || _uiState.value.endDate != finalEndDate) {
            _uiState.update { it.copy(startDate = finalStartDate, endDate = finalEndDate) }
            Log.d("ReportsVM", "Period updated: $finalStartDate - $finalEndDate")
            loadReportData()
        } else { Log.d("ReportsVM", "Period not changed, skipping data load.") }
    }
    /**
     * Генерирует строку в формате CSV на основе текущих данных отчета.
     * @return Строка CSV или null, если данных нет или произошла ошибка.
     */
    fun getCsvDataForCurrentReport(): String? {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.noDataAvailable || currentState.reportData == ReportData.None) {
            Log.w("ReportsVM", "No data available to export.")
            _uiState.update { it.copy(errorMessage = applicationContext.getString(R.string.error_no_data_to_export)) }
            return null
        }

        // Используем системный разделитель строк
        val lineSeparator = System.getProperty("line.separator") ?: "\n"
        // Форматтер для чисел в CSV (используем точку как разделитель)
        val numberFormatter = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
            isGroupingUsed = false // Без группировки разрядов
        }
        // Форматтер для дат в CSV
        val csvDateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val csvBuilder = StringBuilder()

        try {
            // Добавляем заголовок с типом отчета и периодом
            csvBuilder.append("Отчет: ${getStringForReportType(currentState.selectedReportType)}")
                .append(lineSeparator)
            if (currentState.startDate != null && currentState.endDate != null) {
                csvBuilder.append("Период: ${csvDateFormatter.format(currentState.startDate)} - ${csvDateFormatter.format(currentState.endDate)}")
                    .append(lineSeparator)
            }
            csvBuilder.append(lineSeparator) // Пустая строка

            // Генерируем данные в зависимости от типа отчета
            when (val data = currentState.reportData) {
                is ReportData.CategoryReportData -> {
                    csvBuilder.append("Категория;Сумма;Процент") // Заголовок CSV
                        .append(lineSeparator)
                    val total = data.items.sumOf { it.totalSpent }.takeIf { it != 0.0 } ?: 1.0 // Общая сумма для %
                    data.items.forEach { item ->
                        val percentage = (abs(item.totalSpent) / abs(total)) * 100
                        csvBuilder.append("\"${item.categoryName ?: "Без категории"}\";") // В кавычках на случай запятых в названии
                        csvBuilder.append("${numberFormatter.format(item.totalSpent)};")
                        csvBuilder.append("${numberFormatter.format(percentage)}%")
                        csvBuilder.append(lineSeparator)
                    }
                }
                is ReportData.IncomeExpenseReportData -> {
                    csvBuilder.append("Тип;Сумма").append(lineSeparator)
                    csvBuilder.append("Доход;${numberFormatter.format(data.totalIncome)}").append(lineSeparator)
                    csvBuilder.append("Расход;${numberFormatter.format(data.totalExpense)}").append(lineSeparator)
                    csvBuilder.append("Баланс;${numberFormatter.format(data.totalIncome - data.totalExpense)}").append(lineSeparator)
                }
                is ReportData.TimeSeriesReportData -> {
                    csvBuilder.append("Дата;Сумма").append(lineSeparator)
                    // Используем форматтер из фрагмента (или создаем новый)
                    val axisDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    data.points.forEach { point ->
                        csvBuilder.append("${axisDateFormatter.format(Date(point.timestamp))};")
                        csvBuilder.append(numberFormatter.format(point.amount))
                        csvBuilder.append(lineSeparator)
                    }
                }
                is ReportData.None -> { return null  }
            }
            Log.i("ReportsVM", "CSV data generated successfully for ${currentState.selectedReportType}")
            return csvBuilder.toString()

        } catch (e: Exception) {
            Log.e("ReportsVM", "Error generating CSV data", e)
            _uiState.update { it.copy(errorMessage = "Ошибка генерации CSV: ${e.message}") }
            return null
        }
    }

    /** Вспомогательная функция для получения строки для типа отчета. */
    private fun getStringForReportType(type: ReportType): String {
        val resId = when (type) {
            ReportType.EXPENSE_BY_CATEGORY -> R.string.report_expense_by_category
            ReportType.INCOME_BY_SOURCE -> R.string.report_income_by_source
            ReportType.INCOME_VS_EXPENSE -> R.string.report_income_vs_expense
            ReportType.EXPENSE_TREND -> R.string.report_expense_trend
            ReportType.INCOME_TREND -> R.string.report_income_trend
        }
        return try { applicationContext.getString(resId) } catch (e: Exception) { type.name }
    }




    fun setReportType(reportType: ReportType) {
        if (_uiState.value.selectedReportType != reportType) {
            _uiState.update { it.copy(selectedReportType = reportType) }
            Log.d("ReportsVM", "Report type updated: $reportType")
            loadReportData()
        } else { Log.d("ReportsVM", "Report type not changed, skipping data load.") }
    }

    fun loadReportData() {
        currentLoadingJob?.cancel()
        val currentState = _uiState.value
        val startDate = currentState.startDate ?: return
        val endDate = currentState.endDate ?: return

        _uiState.update { it.copy(isLoading = true, errorMessage = null, reportData = ReportData.None, noDataAvailable = false) }
        Log.i("ReportsVM", "Loading report data for type: ${currentState.selectedReportType}, Period: $startDate - $endDate")

        currentLoadingJob = viewModelScope.launch {
            try {
                val newReportData: ReportData = when (currentState.selectedReportType) {
                    ReportType.EXPENSE_BY_CATEGORY -> loadCategoryReport(TransactionType.EXPENSE, startDate, endDate)
                    ReportType.INCOME_BY_SOURCE -> loadCategoryReport(TransactionType.INCOME, startDate, endDate)
                    ReportType.INCOME_VS_EXPENSE -> loadIncomeExpenseReport(startDate, endDate)
                    ReportType.EXPENSE_TREND -> loadTimeSeriesReport(TransactionType.EXPENSE, startDate, endDate)
                    ReportType.INCOME_TREND -> loadTimeSeriesReport(TransactionType.INCOME, startDate, endDate)
                }
                Log.i("ReportsVM", "Report data loaded successfully: ${newReportData::class.simpleName}")
                _uiState.update {
                    it.copy(
                        reportData = newReportData, isLoading = false,
                        noDataAvailable = (newReportData == ReportData.None)
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.i("ReportsVM", "Report loading cancelled.")
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    Log.e("ReportsVM", "Error loading report data", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = applicationContext.getString(R.string.error_loading_report_generic, e.message ?: e.toString()), reportData = ReportData.None) }
                }
            } finally {
                if (currentLoadingJob == coroutineContext[Job]) { currentLoadingJob = null }
            }
        }
    }

    // --- Функции загрузки для разных типов отчетов ---
    private suspend fun loadCategoryReport(type: TransactionType, startDate: Date, endDate: Date): ReportData {
        val data = transactionRepository.getSpendingByCategoryForPeriod(type, startDate, endDate)
        return if (data.isNotEmpty()) ReportData.CategoryReportData(data) else ReportData.None
    }
    private suspend fun loadIncomeExpenseReport(startDate: Date, endDate: Date): ReportData {
        return coroutineScope {
            val incomeDeferred = async { transactionRepository.getTotalAmountByTypeAndDate(TransactionType.INCOME, startDate, endDate) }
            val expenseDeferred = async { transactionRepository.getTotalAmountByTypeAndDate(TransactionType.EXPENSE, startDate, endDate) }
            val totalIncome = incomeDeferred.await() ?: 0.0
            val totalExpense = expenseDeferred.await() ?: 0.0
            if (totalIncome > 0 || totalExpense > 0) {
                ReportData.IncomeExpenseReportData(totalIncome, totalExpense)
            } else {
                ReportData.None
            }
        }
    }
    private suspend fun loadTimeSeriesReport(type: TransactionType, startDate: Date, endDate: Date): ReportData {
        val diffTime = endDate.time - startDate.time
        val diffDays = diffTime / (1000 * 60 * 60 * 24)
        val points = if (diffDays <= 62) { // Меньше ~2 месяцев - по дням
            Log.d("ReportsVM", "Loading TREND data by DAY")
            transactionRepository.getAggregatedAmountByDay(type, startDate, endDate)
        } else { // Иначе по месяцам
            Log.d("ReportsVM", "Loading TREND data by MONTH")
            transactionRepository.getAggregatedAmountByMonth(type, startDate, endDate)
        }
        return if (points.isNotEmpty()) ReportData.TimeSeriesReportData(points) else ReportData.None
    }



    private fun setTimeStartOfDay(calendar: Calendar) { calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0) }
    private fun setTimeEndOfDay(calendar: Calendar) { calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999) }

    // --- Методы очистки ---
    fun clearErrorMessage() { _uiState.update { it.copy(errorMessage = null) } }

    /** Фабрика */
    companion object {
        fun provideFactory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinancialPlannerApplication)
                ReportsViewModel(
                    application.container.transactionRepository,
                    application.container.categoryRepository, // Передаем CategoryRepository
                    application.applicationContext // Передаем контекст
                )
            }
        }
    }

}