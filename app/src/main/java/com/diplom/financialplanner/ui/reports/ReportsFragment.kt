package com.diplom.financialplanner.ui.reports

import android.app.Activity // Для ActivityResult
import android.content.Context
import android.content.Intent // Для Intent
import android.graphics.Color
import android.net.Uri // Для Uri файла
import android.os.Bundle
import android.util.Log
import android.util.TypedValue // Для получения цвета из атрибута темы
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView // Явный импорт для ясности
import androidx.activity.result.ActivityResultLauncher // Для ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts // Для контракта
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.util.Pair // Для MaterialDatePicker
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.data.database.dao.TimeSeriesDataPoint
import com.diplom.financialplanner.databinding.FragmentReportsBinding
import com.diplom.financialplanner.ui.adapters.ReportDetailAdapter
import com.diplom.financialplanner.ui.adapters.ReportDetailItem
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

private const val DEBUG_LOGS = false
private const val DEBUG_TAG = "ReportsFrag_DEBUG"

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!


    private val viewModel: ReportsViewModel by viewModels {
        ReportsViewModel.provideFactory()
    }

    // Адаптеры
    private lateinit var reportDetailAdapter: ReportDetailAdapter
    private lateinit var reportTypeAdapter: ArrayAdapter<String> // Адаптер для дропдауна

    // Список значений Enum типов отчетов (кэшируем для удобства)
    private val reportTypeValues = ReportType.values()

    // Форматтеры дат
    private val periodButtonFormatter = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
    private val lineChartDateFormatter = SimpleDateFormat("dd.MM", Locale.getDefault())

    // Лаунчер для сохранения файла
    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        currency = Currency.getInstance("RUB"); maximumFractionDigits = 2
    }
    private val listPercentFormatter: NumberFormat = NumberFormat.getPercentInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 1
    }

    // --- Lifecycle Methods ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        debugLog("onCreate called")
        // Регистрируем лаунчер для ACTION_CREATE_DOCUMENT
        createFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            debugLog("ActivityResult received, resultCode: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    debugLog("Got URI for saving file: $uri")
                    writeCsvToFile(uri)
                } ?: run {
                    errorLog("Failed to get URI from result data.")
                    showSnackbar(getString(R.string.report_export_failed) + ": Не удалось получить URI файла.", true)
                }
            } else {
                debugLog("File creation cancelled by user.")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        debugLog("onCreateView called")
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        debugLog("onViewCreated: Start setup. InstanceState: ${savedInstanceState != null}")

        // Настройка UI
        setupCharts()
        setupDetailRecyclerView()
        setupPeriodButton()
        setupReportTypeDropdown() // Инициализируем дропдаун и адаптер
        setupRefreshButton() // Кнопка Обновить остается, на случай ручного обновления
        setupExportButton()
        setupPieChartInteraction()

        checkDropdownState("After setupReportTypeDropdown in onViewCreated")

        // Наблюдение за ViewModel
        observeViewModel()

        checkDropdownState("End of onViewCreated")
        debugLog("onViewCreated: Setup complete")
    }

    override fun onResume() {
        super.onResume()
        debugLog("onResume called")

        if (::reportTypeAdapter.isInitialized) {
            debugLog("[onResume] Resetting adapter filter.")
            reportTypeAdapter.filter.filter(null) { count ->
                debugLog("[onResume] Adapter filter reset complete. New filtered count reported by callback: $count")
                checkDropdownState("After filter reset in onResume (Callback)")
            }
            checkDropdownState("After filter reset call in onResume (Immediate check)")
        } else {
            warnLog("[onResume] reportTypeAdapter not initialized, cannot reset filter.")
        }

        debugLog("[onResume] Triggering data load.")
        viewModel.loadReportData()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        debugLog("onDestroyView called")
        // Безопасная очистка
        _binding?.pieChart?.data = null
        _binding?.barChart?.data = null
        _binding?.lineChart?.data = null
        _binding?.rvReportDetails?.adapter = null
        _binding = null
    }

    // --- UI Setup Methods ---

    private fun setupCharts() {
        debugLog("Setting up charts")
        setupPieChart()
        setupBarChart()
        setupLineChart()
    }

    private fun setupPieChart() {
        binding.pieChart.apply {
            setUsePercentValues(false) // Используем абсолютные значения для размера секторов
            description.isEnabled = false
            legend.isEnabled = false // <-- ЛЕГЕНДА ОТКЛЮЧЕНА

            isDrawHoleEnabled = true // Включаем "пончик"
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 65f // Достаточно большое отверстие
            transparentCircleRadius = 70f

            setDrawCenterText(true) // Включаем текст в центре
            centerText = "" // Будет обновляться при клике
            setCenterTextSize(16f) // Крупный текст для названия категории
            setCenterTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))

            rotationAngle = 270f // Начинаем с 9 часов для лучшего вида
            isRotationEnabled = true
            isHighlightPerTapEnabled = true // Включаем выделение по тапу

            setDrawEntryLabels(false) // <-- ОТКЛЮЧАЕМ МЕТКИ КАТЕГОРИЙ НА ДИАГРАММЕ

            setExtraOffsets(10f, 10f, 10f, 10f) // Равные небольшие отступы
        }
    }
    private fun setupPieChartInteraction() {
        binding.pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (h != null && e is PieEntry) {
                    val pieEntry = e
                    val label = pieEntry.label ?: ""
                    debugLog("PieChart sector selected: Label='$label', Value=${pieEntry.y}")
                    binding.pieChart.centerText = label // Показываем название категории

                } else {
                    onNothingSelected()
                }
            }

            override fun onNothingSelected() {
                debugLog("PieChart selection cleared.")
                binding.pieChart.centerText = "" // Очищаем центр
                binding.pieChart.highlightValues(null) // Снимаем выделение
            }
        })
    }

    private fun setupBarChart() {
        binding.barChart.apply {
            description.isEnabled = false; setDrawGridBackground(false); setDrawBarShadow(false); setDrawValueAboveBar(true)
            setPinchZoom(false); isDoubleTapToZoomEnabled = false; setScaleEnabled(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM; setDrawGridLines(false); granularity = 1f
                setDrawAxisLine(true); textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
            }
            axisLeft.apply {
                setDrawGridLines(true); axisMinimum = 0f; valueFormatter = LargeValueFormatter()
                setDrawAxisLine(true); textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
                gridColor = getThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            }
            axisRight.isEnabled = false; legend.isEnabled = false
        }
    }

    private fun setupLineChart() {
        binding.lineChart.apply {
            description.isEnabled = false; setTouchEnabled(true); isDragEnabled = true; setScaleEnabled(true)
            setPinchZoom(true); setDrawGridBackground(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM; setDrawGridLines(false); granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getAxisLabel(value: Float, axis: AxisBase?) = try {
                        lineChartDateFormatter.format(Date(value.toLong()))
                    } catch (e: Exception) {
                        errorLog("Error formatting date for line chart axis: $value", e); ""
                    }
                }
                textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface); setDrawAxisLine(true)
                labelRotationAngle = -45f; setAvoidFirstLastClipping(true)
            }
            axisLeft.apply {
                setDrawGridLines(true); axisMinimum = 0f; valueFormatter = LargeValueFormatter()
                textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface); gridColor = getThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
                setDrawAxisLine(true)
            }
            axisRight.isEnabled = false; legend.apply { form = Legend.LegendForm.LINE; textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant) }
        }
    }

    private fun setupDetailRecyclerView() {
        debugLog("Setting up RecyclerView")
        reportDetailAdapter = ReportDetailAdapter()
        binding.rvReportDetails.apply {
            adapter = reportDetailAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupReportTypeDropdown() {
        val reportTypeNames = reportTypeValues.map { getStringForReportType(it) }
        debugLog("setupReportTypeDropdown: Creating adapter with ${reportTypeNames.size} items: $reportTypeNames")

        reportTypeAdapter = ArrayAdapter(
            requireContext(),
            R.layout.list_item_dropdown, // Используем кастомный макет
            reportTypeNames
        )
        binding.actvReportType.setAdapter(reportTypeAdapter)

        checkDropdownState("Adapter set in setupReportTypeDropdown")

        binding.actvReportType.setOnItemClickListener { _, _, position, _ ->
            if (position >= 0 && position < reportTypeValues.size) {
                val selectedType = reportTypeValues[position]
                debugLog("Dropdown item clicked. Position: $position, Selected Type: $selectedType")
                if (selectedType != viewModel.uiState.value.selectedReportType) {
                    debugLog("User selected NEW report type via click: $selectedType")
                    viewModel.setReportType(selectedType) // ViewModel сама вызовет loadReportData
                } else {
                    debugLog("User selected the SAME report type: $selectedType")
                }
            } else {
                errorLog("Invalid position selected in dropdown: $position")
            }
        }
        debugLog("setupReportTypeDropdown: Setup complete.")
    }


    private fun setupPeriodButton() {
        binding.btnSelectPeriod.setOnClickListener {
            debugLog("Select period button clicked")
            showDateRangePicker() // Выбор периода также вызовет loadReportData через ViewModel
        }
    }

    /** Кнопка теперь для ручного обновления, если авто-обновление не сработало или нужно принудительно */
    private fun setupRefreshButton() {
        binding.btnRefreshReport.setOnClickListener {
            debugLog("Manual Refresh button clicked")
            viewModel.loadReportData()
        }
    }

    private fun setupExportButton() {
        binding.btnExportReport.setOnClickListener {
            debugLog("Export button clicked")
            if (viewModel.uiState.value.reportData == ReportData.None || viewModel.uiState.value.noDataAvailable) {
                warnLog("Cannot export: No data available.")
                showSnackbar(getString(R.string.error_no_data_to_export), true)
            } else {
                launchCreateDocumentIntent()
            }
        }
    }

    // --- ViewModel Observation ---

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // ... (логирование, обновление контролов) ...
                    updateControlsState(state) // Обновляет кнопки и общую сумму

                    binding.progressBarReports.isVisible = state.isLoading
                    binding.tvNoReportData.isVisible = !state.isLoading && state.noDataAvailable

                    // Управление видимостью основного контента
                    if (!state.isLoading && !state.noDataAvailable) {
                        binding.tvNoReportData.visibility = View.GONE
                        showAndPopulateReportView(state.selectedReportType, state.reportData)
                    } else if (!state.isLoading && state.noDataAvailable) {
                        hideAllReportViews()
                        binding.tvNoReportData.text = getString(R.string.no_data_for_report)
                        binding.tvNoReportData.visibility = View.VISIBLE
                    } else {
                        hideAllReportViews()
                        binding.tvNoReportData.visibility = View.GONE
                    }

                    // 4. Обработка ошибок
                    state.errorMessage?.let { errorMsg ->
                        errorLog("Error received from ViewModel: $errorMsg")
                        showSnackbar(errorMsg, true)
                        viewModel.clearErrorMessage()
                        hideAllReportViews()
                        binding.tvNoReportData.text = getString(R.string.error_loading_report)
                        binding.tvNoReportData.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    /** Обновляет состояние кнопок и ТЕКСТА AutoCompleteTextView. */
    private fun updateControlsState(state: ReportsUiState) {
        updatePeriodButtonText(state)
        updateControlsEnabled(state.isLoading, state.noDataAvailable)

        // --- Обновление Текста Общей Суммы ---
        var totalAmount = 0.0
        var totalLabelResId = R.string.total_expenses // Значение по умолчанию
        var showTotal = false // Показывать ли блок общей суммы

        if (!state.isLoading && !state.noDataAvailable) {
            showTotal = true // Показываем, если есть данные
            when (val data = state.reportData) {
                is ReportData.CategoryReportData -> {
                    totalAmount = data.items.sumOf { it.totalSpent }
                    totalLabelResId = if (state.selectedReportType == ReportType.EXPENSE_BY_CATEGORY) R.string.total_expenses else R.string.total_income
                }
                is ReportData.IncomeExpenseReportData -> {
                    totalAmount = data.totalIncome - data.totalExpense // Баланс
                    totalLabelResId = R.string.balance_for_period
                }
                is ReportData.TimeSeriesReportData -> {
                    totalAmount = data.points.sumOf { it.amount }
                    totalLabelResId = if (state.selectedReportType == ReportType.EXPENSE_TREND) R.string.total_expenses else R.string.total_income
                }
                is ReportData.None -> { showTotal = false }
            }
        }

        binding.tvReportTotalAmountLabel.isVisible = showTotal
        binding.tvReportTotalAmount.isVisible = showTotal
        if(showTotal) {
            binding.tvReportTotalAmountLabel.text = getString(totalLabelResId)
            binding.tvReportTotalAmount.text = currencyFormatter.format(abs(totalAmount))
            val totalColorAttr = when {
                state.selectedReportType == ReportType.INCOME_VS_EXPENSE && totalAmount < -0.01 -> com.google.android.material.R.attr.colorError
                state.selectedReportType == ReportType.INCOME_BY_SOURCE || state.selectedReportType == ReportType.INCOME_TREND -> R.color.colorIncome // Прямой цвет дохода
                state.selectedReportType == ReportType.EXPENSE_BY_CATEGORY || state.selectedReportType == ReportType.EXPENSE_TREND -> com.google.android.material.R.attr.colorError
                else -> com.google.android.material.R.attr.colorPrimary
            }
            binding.tvReportTotalAmount.setTextColor(getThemeColor(totalColorAttr))
        }


        // --- Обновление Текста Дропдауна ---
        val expectedDropdownText = getStringForReportType(state.selectedReportType)
        val currentText = binding.actvReportType.text.toString()
        if (currentText != expectedDropdownText) {
            binding.actvReportType.setText(expectedDropdownText, false)
        }
    }


    // --- Вспомогательные методы для обновления контролов ---
    private fun updatePeriodButtonText(state: ReportsUiState) {
        if (state.startDate != null && state.endDate != null) {
            binding.btnSelectPeriod.text = "${periodButtonFormatter.format(state.startDate)} - ${
                periodButtonFormatter.format(state.endDate)
            }"
        } else {
            binding.btnSelectPeriod.text = getString(R.string.select_period)
        }
    }

    private fun updateControlsEnabled(isLoading: Boolean, noData: Boolean) {
        val controlsEnabled = !isLoading
        binding.btnRefreshReport.isEnabled = controlsEnabled
        binding.btnSelectPeriod.isEnabled = controlsEnabled
        binding.actvReportType.isEnabled = controlsEnabled
        binding.btnExportReport.isEnabled = controlsEnabled && !noData
    }


    /** Показывает нужный View для отчета и заполняет его данными. */
    private fun showAndPopulateReportView(reportType: ReportType, reportData: ReportData) {
        hideAllReportViews()

        // Показываем/скрываем заголовок списка деталей
        val showDetailsList = reportData is ReportData.CategoryReportData
        binding.tvDetailsHeader.isVisible = showDetailsList
        if(showDetailsList) {
            binding.tvDetailsHeader.text = when(reportType) {
                ReportType.EXPENSE_BY_CATEGORY -> getString(R.string.details_expenses_by_category)
                ReportType.INCOME_BY_SOURCE -> getString(R.string.details_income_by_source)
                else -> "" // Другие типы пока не используют этот список
            }
        }

        when (reportData) {
            is ReportData.CategoryReportData -> {
                binding.pieChart.visibility = View.VISIBLE
                binding.rvReportDetails.visibility = View.VISIBLE // Список теперь тоже виден
                updatePieChartData(reportData.items, reportType)
                updateCategoryDetailList(reportData.items) // Обновляем список
            }
            is ReportData.IncomeExpenseReportData -> {
                binding.barChart.visibility = View.VISIBLE
                updateBarChartData(reportData)
            }
            is ReportData.TimeSeriesReportData -> {
                binding.lineChart.visibility = View.VISIBLE
                updateLineChartData(reportData.points, reportType)
            }
            is ReportData.None -> {
                binding.tvNoReportData.visibility = View.VISIBLE
            }
        }
    }



    /** Скрывает все View, отображающие контент отчета. */
    private fun hideAllReportViews() {
        _binding?.pieChart?.visibility = View.GONE
        _binding?.barChart?.visibility = View.GONE
        _binding?.lineChart?.visibility = View.GONE
        _binding?.rvReportDetails?.visibility = View.GONE
        _binding?.tvDetailsHeader?.visibility = View.GONE // Скрываем и заголовок списка
    }

    /** Очищает данные графиков и списка. */
    private fun clearChartsAndList() {
        _binding?.pieChart?.data = null
        _binding?.pieChart?.invalidate()
        _binding?.barChart?.data = null
        _binding?.barChart?.invalidate()
        _binding?.lineChart?.data = null
        _binding?.lineChart?.invalidate()

        if (::reportDetailAdapter.isInitialized) {
            reportDetailAdapter.submitList(emptyList())
        }
        debugLog("Charts and list data cleared.")
    }

    // --- Chart Data Update Methods ---
    private fun updatePieChartData(data: List<CategorySpending>, reportType: ReportType) {
        val entries = ArrayList<PieEntry>()
        data.forEach {
            entries.add(PieEntry(abs(it.totalSpent).toFloat(), it.categoryName ?: getString(R.string.category_unknown)))
        }

        if (entries.isEmpty()) { return }
        binding.pieChart.centerText = "" // Сброс центра

        val dataSet = PieDataSet(entries, "").apply {
            colors = generateColorsForCategories(data)
            sliceSpace = 3f
            setDrawValues(false)
            selectionShift = 8f // Выдвижение при клике
        }

        binding.pieChart.data = PieData(dataSet).apply {
            setDrawValues(false) // Убедимся еще раз
        }
        binding.pieChart.legend.isEnabled = false
        // --- ИСПРАВЛЕНИЕ: Перемещаем вызов сюда ---
        binding.pieChart.setDrawEntryLabels(false) // <-- Отключаем метки здесь

        binding.pieChart.highlightValues(null)
        binding.pieChart.invalidate()
        binding.pieChart.animateY(1000, Easing.EaseOutCubic)
    }

    /** Генерирует список цветов для PieChart на основе цветов категорий */
    private fun generateColorsForCategories(categories: List<CategorySpending>): List<Int> {
        val defaultColors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.VORDIPLOM_COLORS.toList() + ColorTemplate.JOYFUL_COLORS.toList()
        val colorList = mutableListOf<Int>()
        categories.forEachIndexed { index, category ->
            val color = try {
                category.colorHex?.let { Color.parseColor(it) }
            } catch (e: Exception) { null }
            colorList.add(color ?: defaultColors[index % defaultColors.size])
        }
        return colorList
    }
    private fun updateCategoryDetailList(data: List<CategorySpending>) {

        val totalAbsoluteAmount = data.sumOf { abs(it.totalSpent) }.takeIf { it > 0.01 }

        val detailItems: List<ReportDetailItem> = data.map { spendingItem ->
            val percentage = totalAbsoluteAmount?.let { total ->
                (abs(spendingItem.totalSpent) / total * 100).toFloat()
            }
            ReportDetailItem(
                categoryId = spendingItem.categoryId,
                categoryName = spendingItem.categoryName ?: getString(R.string.category_unknown),
                categoryIconResName = spendingItem.iconResName,
                categoryColorHex = spendingItem.colorHex,
                currentAmount = spendingItem.totalSpent,
                transactionCount = spendingItem.transactionCount, // Передаем количество
                percentage = percentage, // Передаем процент
                totalAmount = totalAbsoluteAmount // totalAmount нужен для расчета процента в адаптере (хотя мы уже рассчитали)
            )
        }
        reportDetailAdapter.submitList(detailItems)
        debugLog("Updated category detail list with ${detailItems.size} items.")
    }

    private fun updateBarChartData(data: ReportData.IncomeExpenseReportData) {
        debugLog("Updating BarChart: Income=${data.totalIncome}, Expense=${data.totalExpense}")
        val entries = ArrayList<BarEntry>().apply { add(BarEntry(0f, data.totalIncome.toFloat())); add(BarEntry(1f, data.totalExpense.toFloat())) }
        val labels = listOf(getString(R.string.title_income), getString(R.string.title_expenses))
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.barChart.xAxis.labelCount = labels.size; binding.barChart.xAxis.isGranularityEnabled = true; binding.barChart.xAxis.granularity = 1f
        val dataSet = BarDataSet(entries, "Income vs Expense").apply {
            colors = listOf(ContextCompat.getColor(requireContext(), R.color.colorIncome), ContextCompat.getColor(requireContext(), R.color.colorExpense))
            setDrawValues(true); valueFormatter = LargeValueFormatter(); valueTextSize = 10f; valueTextColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        }
        binding.barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
        binding.barChart.setFitBars(true); binding.barChart.invalidate(); binding.barChart.animateY(1000)
    }

    private fun updateLineChartData(points: List<TimeSeriesDataPoint>, reportType: ReportType) {
        debugLog("Updating LineChart with ${points.size} points for type $reportType")
        if (points.isEmpty()) { debugLog("No data points for LineChart, clearing."); clearChartsAndList(); binding.tvNoReportData.visibility = View.VISIBLE; return }
        val entries = ArrayList<Entry>()
        points.forEach { entries.add(Entry(it.timestamp.toFloat(), it.amount.toFloat())) }
        entries.sortBy { it.x }
        val dataSetLabel = getStringForReportType(reportType)
        val dataSetColor = ContextCompat.getColor(requireContext(), if (reportType == ReportType.EXPENSE_TREND) R.color.colorExpense else R.color.colorIncome)
        val lineDataSet = LineDataSet(entries, dataSetLabel).apply {
            color = dataSetColor; setCircleColor(dataSetColor); lineWidth = 2f; circleRadius = 3f; setDrawCircleHole(false)
            valueTextSize = 9f; setDrawFilled(true); fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.chart_fade_background)
            valueFormatter = LargeValueFormatter(); mode = LineDataSet.Mode.CUBIC_BEZIER
            highLightColor = Color.rgb(200, 200, 200); isHighlightEnabled = true; setDrawHorizontalHighlightIndicator(false)
        }
        binding.lineChart.data = LineData(listOf<ILineDataSet>(lineDataSet))
        binding.lineChart.xAxis.axisMinimum = entries.firstOrNull()?.x ?: 0f; binding.lineChart.xAxis.axisMaximum = entries.lastOrNull()?.x ?: 0f
        binding.lineChart.xAxis.labelCount = 5; binding.lineChart.xAxis.setCenterAxisLabels(false)
        binding.lineChart.invalidate(); binding.lineChart.animateX(1000)
    }

    // --- RecyclerView Update Methods ---



    private fun updateTopCategoriesList(data: List<CategorySpending>) {
        val totalAbsoluteAmount = data.sumOf { abs(it.totalSpent) }.takeIf { it > 0.01 }
        val detailItems: List<ReportDetailItem> = data
            .filter { abs(it.totalSpent) >= 0.01 }
            .map { spendingItem ->
                ReportDetailItem(
                    categoryId = spendingItem.categoryId,
                    categoryName = spendingItem.categoryName ?: getString(R.string.category_unknown),
                    categoryIconResName = spendingItem.iconResName,
                    categoryColorHex = spendingItem.colorHex,
                    currentAmount = spendingItem.totalSpent,
                    previousAmount = null,
                    totalAmount = totalAbsoluteAmount
                )
            }
        reportDetailAdapter.submitList(detailItems)
        debugLog("Updated top categories list with ${detailItems.size} items. Total: $totalAbsoluteAmount")
    }

    // --- Date Picker ---

    private fun showDateRangePicker() {
        val currentStartDate = viewModel.uiState.value.startDate?.time ?: MaterialDatePicker.thisMonthInUtcMilliseconds()
        val currentEndDate = viewModel.uiState.value.endDate?.time ?: MaterialDatePicker.todayInUtcMilliseconds()
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.date_range_picker_title)).setSelection(Pair(currentStartDate, currentEndDate)).build()
        picker.addOnPositiveButtonClickListener { selection ->
            val startDate = Date(selection.first)
            val endDateCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selection.second
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }
            val endDate = endDateCalendar.time
            debugLog("Date range selected: $startDate - $endDate")
            viewModel.setPeriod(startDate, endDate) // ViewModel вызовет loadReportData
        }
        picker.show(childFragmentManager, "DATE_RANGE_PICKER_TAG")
    }

    // --- Export Methods ---

    private fun launchCreateDocumentIntent() {
        val typeStr = viewModel.uiState.value.selectedReportType.name.lowercase(Locale.US)
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = sdf.format(Date())
        val defaultFileName = "financial_report_${typeStr}_$timestamp.csv"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, defaultFileName)
        }
        try { debugLog("Launching ACTION_CREATE_DOCUMENT with default name: $defaultFileName"); createFileLauncher.launch(intent) }
        catch (e: Exception) { errorLog("Could not launch ACTION_CREATE_DOCUMENT", e); showSnackbar("Не удалось запустить выбор файла: ${e.message}", true) }
    }

    private fun writeCsvToFile(uri: Uri) {
        val csvData = viewModel.getCsvDataForCurrentReport()
        if (csvData.isNullOrBlank()) {
            warnLog("CSV data is null or blank, cannot export.")
            showSnackbar(getString(R.string.report_export_failed) + ": Нет данных для генерации отчета.", true); return
        }
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM
                outputStream.write(csvData.toByteArray(Charsets.UTF_8)); outputStream.flush()
                debugLog("CSV data successfully written to $uri"); showSnackbar(getString(R.string.report_exported_success))
            } ?: throw IOException("ContentResolver returned null OutputStream for URI: $uri")
        } catch (e: Exception) { errorLog("Error writing CSV data to $uri", e); showSnackbar(getString(R.string.report_export_failed) + ": ${e.message}", true) }
    }

    // --- Utility Methods ---

    private fun getStringForReportType(type: ReportType): String {
        val resId = when (type) {
            ReportType.EXPENSE_BY_CATEGORY -> R.string.report_expense_by_category
            ReportType.INCOME_BY_SOURCE -> R.string.report_income_by_source
            ReportType.INCOME_VS_EXPENSE -> R.string.report_income_vs_expense
            ReportType.EXPENSE_TREND -> R.string.report_expense_trend
            ReportType.INCOME_TREND -> R.string.report_income_trend
        }
        return try { getString(resId) }
        catch (e: Exception) { warnLog("Could not find string resource for ReportType: $type", e); type.name }
    }

    @ColorInt
    private fun getThemeColor(@AttrRes themeAttrId: Int): Int {
        val currentContext = context
        return currentContext?.let { ctx ->
            val typedValue = TypedValue()
            if (ctx.theme.resolveAttribute(themeAttrId, typedValue, true)) {
                if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    typedValue.data
                } else if (typedValue.type == TypedValue.TYPE_REFERENCE) {
                    try { ContextCompat.getColor(ctx, typedValue.resourceId) }
                    catch (e: Exception) { warnLog("Could not resolve theme attr $themeAttrId resource ID ${typedValue.resourceId} as color.", e); getFallbackThemeColor(ctx) }
                } else { warnLog("Theme attr $themeAttrId resolved to unexpected type: ${typedValue.type}"); getFallbackThemeColor(ctx) }
            } else { warnLog("Could not resolve theme attribute: $themeAttrId"); getFallbackThemeColor(ctx) }
        } ?: run { warnLog("Context was null when trying to resolve theme attribute: $themeAttrId"); Color.GRAY }
    }

    @ColorInt
    private fun getFallbackThemeColor(context: Context) : Int {
        val typedValue = TypedValue(); val fallbackAttr = android.R.attr.textColorSecondary
        return if (context.theme.resolveAttribute(fallbackAttr, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) { typedValue.data }
            else if (typedValue.type == TypedValue.TYPE_REFERENCE) {
                try { ContextCompat.getColor(context, typedValue.resourceId) }
                catch (e: Exception) { warnLog("Could not resolve FALLBACK theme attr $fallbackAttr resource ID ${typedValue.resourceId} as color.", e); Color.GRAY }
            } else { warnLog("Fallback theme attr $fallbackAttr resolved to unexpected type: ${typedValue.type}"); Color.GRAY }
        } else { warnLog("Could not resolve FALLBACK theme attribute: $fallbackAttr"); Color.GRAY }
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (_binding == null || view == null) { warnLog("Snackbar requested but view is null. Message: $message"); return }
        try {
            val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            if (isError) {
                snackbar.setBackgroundTint(getThemeColor(com.google.android.material.R.attr.colorErrorContainer))
                snackbar.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnErrorContainer))
            }
            snackbar.show()
        } catch (e: Exception) { errorLog("Error showing Snackbar", e) }
    }

    // --- ОТЛАДОЧНАЯ ФУНКЦИЯ ---
    private fun checkDropdownState(contextMessage: String) {
        if (!DEBUG_LOGS) return
        if (_binding == null) { debugLog("[$contextMessage] Check Dropdown State: Binding is NULL"); return }
        val adapterInstance = if (::reportTypeAdapter.isInitialized) reportTypeAdapter else null
        val currentAdapterView = binding.actvReportType.adapter
        val adapterInstanceHashCode = adapterInstance?.hashCode() ?: "null"
        val viewAdapterHashCode = currentAdapterView?.hashCode() ?: "null"
        val filteredCount = currentAdapterView?.count ?: -1
        val originalCount = adapterInstance?.count ?: -2
        val currentText = binding.actvReportType.text?.toString() ?: "null"
        val isPopupShowing = binding.actvReportType.isPopupShowing
        debugLog("[$contextMessage] Check Dropdown State: \n" +
                "  Adapter Instance Hash = $adapterInstanceHashCode\n" +
                "  View's Adapter Hash   = $viewAdapterHashCode\n" +
                "  Filtered Count (View) = $filteredCount\n" +
                "  Original Count (Inst) = $originalCount\n" +
                "  Current Text          = '$currentText'\n" +
                "  isPopupShowing        = $isPopupShowing")
        if (adapterInstance != null && filteredCount != originalCount && originalCount > 0) {
            warnLog("[$contextMessage] Dropdown adapter IS filtered! Filtered ($filteredCount) != Original ($originalCount)")
        } else if (adapterInstance != null && currentAdapterView != adapterInstance) {
            errorLog("[$contextMessage] Mismatch between adapter instance and View's adapter!")
        }
    }

    // --- Вспомогательные функции логирования ---
    private fun debugLog(message: String) { if (DEBUG_LOGS) Log.d(DEBUG_TAG, message) }
    private fun warnLog(message: String, tr: Throwable? = null) { if (DEBUG_LOGS) Log.w(DEBUG_TAG, message, tr) }
    private fun errorLog(message: String, tr: Throwable? = null) { if (DEBUG_LOGS) Log.e(DEBUG_TAG, message, tr) }

}
