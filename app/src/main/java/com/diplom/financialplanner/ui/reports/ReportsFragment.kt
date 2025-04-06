package com.diplom.financialplanner.ui.reports

import android.app.Activity // Для ActivityResult
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
//import com.diplom.financialplanner.data.model.CategoryComparisonData // Убедитесь, что этот импорт правильный
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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

// --- ОТЛАДОЧНЫЕ ФЛАГИ ---
private const val DEBUG_LOGS = true // Установите в false, чтобы отключить подробные логи
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
        setupRefreshButton()
        setupExportButton()

        checkDropdownState("After setupReportTypeDropdown in onViewCreated")

        // Наблюдение за ViewModel
        observeViewModel()

        checkDropdownState("End of onViewCreated")
        debugLog("onViewCreated: Setup complete")
    }

    override fun onResume() {
        super.onResume()
        debugLog("onResume called")
        // --- ВАЖНО: Сбрасываем фильтр адаптера при возвращении на экран ---
        // Это ключевое исправление для бага с AutoCompleteTextView
        if (::reportTypeAdapter.isInitialized) {
            debugLog("[onResume] Resetting adapter filter.")
            // Вызов filter(null) заставляет адаптер показать все элементы
            reportTypeAdapter.filter.filter(null) { count ->
                // Этот колбэк выполнится асинхронно после фильтрации
                debugLog("[onResume] Adapter filter reset complete. New filtered count reported by callback: $count")
                // Проверяем состояние еще раз после сброса фильтра
                checkDropdownState("After filter reset in onResume (Callback)")
            }
            // Проверяем состояние немедленно (может показать старый count до завершения фильтрации)
            checkDropdownState("After filter reset call in onResume (Immediate check)")
        } else {
            warnLog("[onResume] reportTypeAdapter not initialized, cannot reset filter.")
        }
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
            setUsePercentValues(true); description.isEnabled = false; isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT); holeRadius = 50f; transparentCircleRadius = 55f
            setDrawCenterText(true); centerText = ""; setCenterTextSize(14f)
            setCenterTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))
            rotationAngle = 270f; isRotationEnabled = true; isHighlightPerTapEnabled = true
            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.CENTER; horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.VERTICAL; setDrawInside(false); xEntrySpace = 7f; yEntrySpace = 5f
                yOffset = 0f; textSize = 11f; isWordWrapEnabled = true
                textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            setEntryLabelColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface)); setEntryLabelTextSize(10f)
            setExtraOffsets(5f, 10f, 40f, 10f)
        }
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
                    viewModel.setReportType(selectedType)
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
            showDateRangePicker()
        }
    }

    private fun setupRefreshButton() {
        binding.btnRefreshReport.setOnClickListener {
            debugLog("Refresh button clicked")
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
                debugLog("observeViewModel: Starting collection")
                viewModel.uiState.collect { state ->
                    debugLog("Observing state: isLoading=${state.isLoading}, noData=${state.noDataAvailable}, type=${state.selectedReportType}, data=${state.reportData::class.simpleName}")

                    // 1. Обновляем состояние контролов (кнопки, текст дропдауна)
                    updateControlsState(state)

                    // 2. Управляем общей видимостью
                    binding.progressBarReports.isVisible = state.isLoading
                    binding.tvNoReportData.isVisible = !state.isLoading && state.noDataAvailable
                    binding.reportContentContainer.isVisible = !state.isLoading

                    // 3. Отображение контента
                    if (!state.isLoading && !state.noDataAvailable) {
                        binding.tvNoReportData.visibility = View.GONE
                        showAndPopulateReportView(state.selectedReportType, state.reportData)
                    } else if (!state.isLoading && state.noDataAvailable) {
                        hideAllReportViews()
                        binding.tvNoReportData.text = getString(R.string.no_data_for_report)
                        binding.tvNoReportData.visibility = View.VISIBLE
                        debugLog("No data available, showing 'no data' message.")
                    } else { // isLoading == true
                        hideAllReportViews()
                        binding.tvNoReportData.visibility = View.GONE
                        debugLog("Data is loading.")
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
        // Обновляем кнопку периода
        updatePeriodButtonText(state)
        // Блокируем/разблокируем кнопки
        updateControlsEnabled(state.isLoading, state.noDataAvailable)

        // Устанавливаем ТЕКСТ в AutoCompleteTextView
        val expectedDropdownText = getStringForReportType(state.selectedReportType)
        val currentText = binding.actvReportType.text?.toString() ?: ""

        // Устанавливаем текст, только если он отличается
        if (currentText != expectedDropdownText) {
            debugLog("UpdateControlsState: Need to set dropdown text to '$expectedDropdownText'. Current: '$currentText'")
            binding.actvReportType.setText(expectedDropdownText, false) // false - не фильтровать адаптер

            // Сброс фильтра теперь делается в onResume, здесь НЕ НУЖНО
            // reportTypeAdapter.filter.filter(null)
            // binding.actvReportType.clearFocus() // Тоже убрал, чтобы не мешать

        } else {
            debugLog("UpdateControlsState: Dropdown text '$expectedDropdownText' already set.")
        }
        checkDropdownState("After updateControlsState")
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
        debugLog("showAndPopulateReportView for type: $reportType, data type: ${reportData::class.simpleName}")
        hideAllReportViews()

        when (reportData) {
            is ReportData.CategoryReportData -> {
                binding.pieChart.visibility = View.VISIBLE
                binding.rvReportDetails.visibility = View.VISIBLE
                updatePieChartData(reportData.items, reportType)
                updateTopCategoriesList(reportData.items)
            }
            is ReportData.IncomeExpenseReportData -> {
                binding.barChart.visibility = View.VISIBLE
                updateBarChartData(reportData)
            }
            is ReportData.TimeSeriesReportData -> {
                binding.lineChart.visibility = View.VISIBLE
                updateLineChartData(reportData.points, reportType)
            }
            is ReportData.ComparisonReportData -> {
                binding.rvReportDetails.visibility = View.VISIBLE
                updateComparisonReportUI(reportData)
            }
            is ReportData.None -> {
                warnLog("showAndPopulateReportView called with ReportData.None")
                binding.tvNoReportData.visibility = View.VISIBLE
            }
        }
    }


    /** Скрывает все View, отображающие контент отчета. */
    private fun hideAllReportViews() {
        binding.pieChart.visibility = View.GONE
        binding.barChart.visibility = View.GONE
        binding.lineChart.visibility = View.GONE
        binding.rvReportDetails.visibility = View.GONE
    }

    /** Очищает данные графиков и списка. */
    private fun clearChartsAndList() {
        // Используем безопасный доступ через _binding?
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
    // (Методы updatePieChartData, updateBarChartData, updateLineChartData без изменений)
    private fun updatePieChartData(data: List<CategorySpending>, reportType: ReportType) {
        debugLog("Updating PieChart with ${data.size} items for type $reportType")
        val entries = ArrayList<PieEntry>()
        data.filter { abs(it.totalSpent) >= 0.01 }.forEach {
            entries.add(PieEntry(abs(it.totalSpent).toFloat(), it.categoryName ?: getString(R.string.category_unknown)))
        }

        if (entries.isEmpty()) {
            debugLog("No valid entries for PieChart, clearing.")
            clearChartsAndList(); binding.tvNoReportData.visibility = View.VISIBLE
            return
        }

        binding.pieChart.centerText = getStringForReportType(reportType)

        val dataSet = PieDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.VORDIPLOM_COLORS.toList() + ColorTemplate.JOYFUL_COLORS.toList()
            sliceSpace = 2f; valueLinePart1OffsetPercentage = 80f; valueLinePart1Length = 0.4f; valueLinePart2Length = 0.4f
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE; valueFormatter = PercentFormatter(binding.pieChart)
            valueTextSize = 11f; valueTextColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        }

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.highlightValues(null); binding.pieChart.invalidate(); binding.pieChart.animateY(1000, Easing.EaseInOutQuad)
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

    private fun updateComparisonReportUI(data: ReportData.ComparisonReportData) {
        reportDetailAdapter.setMode(ReportDetailAdapter.Mode.COMPARISON)
        val detailItems: List<ReportDetailItem> = data.items.mapNotNull { comparisonItem ->
            if (abs(comparisonItem.currentPeriodAmount) < 0.01 && abs(comparisonItem.previousPeriodAmount) < 0.01) { null }
            else {
                ReportDetailItem(
                    categoryId = comparisonItem.categoryId,
                    categoryName = comparisonItem.categoryName ?: getString(R.string.category_unknown),
                    categoryIconResName = comparisonItem.iconResName,
                    categoryColorHex = comparisonItem.colorHex,
                    currentAmount = comparisonItem.currentPeriodAmount,
                    previousAmount = comparisonItem.previousPeriodAmount,
                    percentage = null, totalAmount = null
                )
            }
        }
        reportDetailAdapter.submitList(detailItems)
        debugLog("Updated comparison report recycler view with ${detailItems.size} items.")
    }

    private fun updateTopCategoriesList(data: List<CategorySpending>) {
        reportDetailAdapter.setMode(ReportDetailAdapter.Mode.TOP_SPENDING)
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
            .setTitleText(getString(R.string.date_range_picker_title))
            .setSelection(Pair(currentStartDate, currentEndDate))
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startDate = Date(selection.first)
            val endDateCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selection.second
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }
            val endDate = endDateCalendar.time
            debugLog("Date range selected: $startDate - $endDate")
            viewModel.setPeriod(startDate, endDate)
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
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, defaultFileName)
        }
        try {
            debugLog("Launching ACTION_CREATE_DOCUMENT with default name: $defaultFileName")
            createFileLauncher.launch(intent)
        } catch (e: Exception) {
            errorLog("Could not launch ACTION_CREATE_DOCUMENT", e)
            showSnackbar("Не удалось запустить выбор файла: ${e.message}", true)
        }
    }

    private fun writeCsvToFile(uri: Uri) {
        val csvData = viewModel.getCsvDataForCurrentReport()
        if (csvData.isNullOrBlank()) {
            warnLog("CSV data is null or blank, cannot export.")
            showSnackbar(getString(R.string.report_export_failed) + ": Нет данных для генерации отчета.", true)
            return
        }

        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM for Excel
                outputStream.write(csvData.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                debugLog("CSV data successfully written to $uri")
                showSnackbar(getString(R.string.report_exported_success))
            } ?: throw IOException("ContentResolver returned null OutputStream for URI: $uri")
        } catch (e: Exception) {
            errorLog("Error writing CSV data to $uri", e)
            showSnackbar(getString(R.string.report_export_failed) + ": ${e.message}", true)
        }
    }

    // --- Utility Methods ---

    private fun getStringForReportType(type: ReportType): String {
        val resId = when (type) {
            ReportType.EXPENSE_BY_CATEGORY -> R.string.report_expense_by_category
            ReportType.INCOME_BY_SOURCE -> R.string.report_income_by_source
            ReportType.INCOME_VS_EXPENSE -> R.string.report_income_vs_expense
            ReportType.EXPENSE_TREND -> R.string.report_expense_trend
            ReportType.INCOME_TREND -> R.string.report_income_trend
            ReportType.EXPENSE_COMPARISON -> R.string.report_expense_comparison
        }
        return try { getString(resId) }
        catch (e: Exception) { warnLog("Could not find string resource for ReportType: $type", e); type.name }
    }

    @ColorInt
    private fun getThemeColor(attrResId: Int): Int {
        val typedValue = TypedValue()
        return if (context?.theme?.resolveAttribute(attrResId, typedValue, true) == true) { // Используем context? для безопасности
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else if (typedValue.type == TypedValue.TYPE_REFERENCE && context != null) {
                try { ContextCompat.getColor(context!!, typedValue.resourceId) } // context!! безопасен здесь
                catch (e: Exception) { warnLog("Could not resolve theme attr $attrResId resource ID ${typedValue.resourceId} as color.", e); Color.GRAY }
            } else { warnLog("Theme attr $attrResId resolved to unexpected type: ${typedValue.type}"); Color.GRAY }
        } else { warnLog("Could not resolve theme attribute: $attrResId"); Color.GRAY }
    }


    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (_binding == null || view == null) {
            warnLog("Snackbar requested but view is null. Message: $message")
            return
        }
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
        if (_binding == null) {
            debugLog("[$contextMessage] Check Dropdown State: Binding is NULL")
            return
        }
        // Используем поле класса reportTypeAdapter, если оно инициализировано
        val adapterInstance = if (::reportTypeAdapter.isInitialized) reportTypeAdapter else null
        val currentAdapterView = binding.actvReportType.adapter // Адаптер, который видит View

        val adapterInstanceHashCode = adapterInstance?.hashCode() ?: "null"
        val viewAdapterHashCode = currentAdapterView?.hashCode() ?: "null"

        val filteredCount = currentAdapterView?.count ?: -1 // Текущее количество видимых (отфильтрованных)
        val originalCount = adapterInstance?.count ?: -2 // Количество в нашем экземпляре адаптера

        val currentText = binding.actvReportType.text?.toString() ?: "null"
        val isPopupShowing = binding.actvReportType.isPopupShowing

        // Более подробный лог
        debugLog("[$contextMessage] Check Dropdown State: \n" +
                "  Adapter Instance Hash = $adapterInstanceHashCode\n" +
                "  View's Adapter Hash   = $viewAdapterHashCode\n" +
                "  Filtered Count (View) = $filteredCount\n" +
                "  Original Count (Inst) = $originalCount\n" +
                "  Current Text          = '$currentText'\n" +
                "  isPopupShowing        = $isPopupShowing")

        // Дополнительная проверка
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

} // Конец класса ReportsFragment


// --- Убедитесь, что ReportType Enum существует ---
/*
package com.diplom.financialplanner.ui.reports // Или где у вас Enum

import androidx.annotation.StringRes
import com.diplom.financialplanner.R

enum class ReportType(@StringRes val stringResId: Int) {
    EXPENSE_BY_CATEGORY(R.string.report_expense_by_category),
    INCOME_BY_SOURCE(R.string.report_income_by_source),
    INCOME_VS_EXPENSE(R.string.report_income_vs_expense),
    EXPENSE_TREND(R.string.report_expense_trend),
    INCOME_TREND(R.string.report_income_trend),
    EXPENSE_COMPARISON(R.string.report_expense_comparison)
}
*/

// --- Убедитесь, что ReportsUiState существует и содержит нужные поля ---
/*
package com.diplom.financialplanner.ui.reports // Или где у вас UiState

import java.util.Date

data class ReportsUiState(
    val isLoading: Boolean = false,
    val selectedReportType: ReportType = ReportType.EXPENSE_BY_CATEGORY, // Тип отчета по умолчанию
    val startDate: Date? = null,
    val endDate: Date? = null,
    val reportData: ReportData = ReportData.None,
    val noDataAvailable: Boolean = false, // Флаг, что данных нет (важно!)
    val errorMessage: String? = null
)
*/

// --- Убедитесь, что ReportData sealed interface/class существует ---
/*
package com.diplom.financialplanner.ui.reports // Или где у вас ReportData

import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.data.database.dao.TimeSeriesDataPoint
import com.diplom.financialplanner.data.model.CategoryComparisonData

sealed interface ReportData {
    object None : ReportData // Нет данных или не загружено
    data class CategoryReportData(val items: List<CategorySpending>) : ReportData // Для круговой диаграммы
    data class IncomeExpenseReportData(val totalIncome: Double, val totalExpense: Double) : ReportData // Для столбчатой
    data class TimeSeriesReportData(val points: List<TimeSeriesDataPoint>) : ReportData // Для линейного графика
    data class ComparisonReportData(val items: List<CategoryComparisonData>) : ReportData // Для сравнения периодов
}
*/