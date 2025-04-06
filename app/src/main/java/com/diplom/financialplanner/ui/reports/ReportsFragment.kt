package com.diplom.financialplanner.ui.reports

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.util.Pair // Для DateRangePicker
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.dao.CategorySpending
import com.diplom.financialplanner.databinding.FragmentReportsBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * Фрагмент для отображения финансовых отчетов (расходы/доходы по категориям, доходы vs расходы).
 */
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels { ReportsViewModel.provideFactory() }

    // Форматтер для отображения периода в кнопке
    private val periodButtonFormatter = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPieChart()
        setupBarChart()
        setupPeriodButton()
        setupReportTypeDropdown()
        observeViewModel()
    }

    /** Первичная настройка PieChart. */
    private fun setupPieChart() {
        binding.pieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            // setExtraOffsets(5f, 10f, 5f, 5f) // Дополнительные отступы, если нужны

            // Настройка "дырки" в центре
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 58f
            transparentCircleRadius = 61f // Прозрачный круг вокруг дырки
            setDrawCenterText(true) // Разрешаем текст в центре
            centerText = ""
            // Устанавливаем размер текста напрямую
            setCenterTextSize(16f) // Подберите нужный размер
            // Устанавливаем цвет текста (например, из атрибутов темы)
            // Используйте ContextCompat.getColor или атрибут темы
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            setCenterTextColor(typedValue.data)

            // Вращение и выделение
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true

            // Легенда (список категорий с цветами)
            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.VERTICAL
                setDrawInside(false) // Рисовать снаружи графика
                xEntrySpace = 7f
                yEntrySpace = 2f // Уменьшаем расстояние по вертикали
                yOffset = 0f
                textSize = 10f
                isWordWrapEnabled = true // Перенос длинных названий категорий
            }


            // Настройка меток на секторах (проценты)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(10f)
        }
    }

    /** Первичная настройка BarChart. */
    private fun setupBarChart() {
        binding.barChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false) // Не рисовать фон сетки
            setDrawBarShadow(false) // Не рисовать тень от столбцов
            setDrawValueAboveBar(true) // Рисовать значения над столбцами
            setPinchZoom(false) // Отключить масштабирование щипком
            isDoubleTapToZoomEnabled = false // Отключить зум по двойному тапу
            setScaleEnabled(false) // Отключить любое масштабирование

            // Ось X (подписи "Доходы", "Расходы")
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM // Позиция оси внизу
                setDrawGridLines(false) // Не рисовать вертикальные линии сетки
                granularity = 1f // Минимальный интервал между значениями оси X
                setDrawAxisLine(true) // Рисовать линию оси
                textColor = ContextCompat.getColor(context, R.color.design_default_color_on_surface) // Цвет текста
                // valueFormatter будет установлен позже, когда будут данные
            }

            // Левая ось Y (значения сумм)
            axisLeft.apply {
                setDrawGridLines(true) // Рисовать горизонтальные линии сетки
                axisMinimum = 0f // Минимальное значение оси Y - ноль
                valueFormatter = LargeValueFormatter() // Форматировать большие числа (1k, 1M)
                setDrawAxisLine(true) // Рисовать линию оси
                textColor = ContextCompat.getColor(context, R.color.design_default_color_on_surface)
                gridColor = ContextCompat.getColor(context, R.color.material_dynamic_neutral_90) // Цвет сетки
            }

            // Правая ось Y (отключена)
            axisRight.isEnabled = false

            // Легенда (отключена, т.к. всего 2 столбца с разными цветами)
            legend.isEnabled = false

            // Анимация
            animateY(1000)
        }
    }

    /** Настраивает кнопку выбора периода. */
    private fun setupPeriodButton() {
        binding.btnSelectPeriod.setOnClickListener {
            showDateRangePicker()
        }
    }

    /** Показывает DateRangePicker для выбора периода. */
    private fun showDateRangePicker() {
        // Устанавливаем текущий выбранный диапазон или сегодняшнюю дату, если диапазон не выбран
        val currentStartDate = viewModel.uiState.value.startDate?.time ?: MaterialDatePicker.todayInUtcMilliseconds()
        val currentEndDate = viewModel.uiState.value.endDate?.time ?: MaterialDatePicker.todayInUtcMilliseconds()

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.date_range_picker_title))
            .setSelection(Pair(currentStartDate, currentEndDate))
            // Можно добавить CalendarConstraints для ограничения выбора дат
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            // selection.first - начало диапазона в UTC ms
            // selection.second - конец диапазона в UTC ms
            val startDate = Date(selection.first)
            val endDate = Date(selection.second)
            Log.d("ReportsFrag", "Date range selected: Start=${startDate}, End=${endDate}")
            // Передаем выбранные даты в ViewModel (он сам настроит начало/конец дня)
            viewModel.setPeriod(startDate, endDate)
        }

        picker.show(childFragmentManager, "DATE_RANGE_PICKER")
    }

    /** Настраивает выпадающий список для выбора типа отчета. */
    private fun setupReportTypeDropdown() {
        // Получаем названия типов отчетов
        val reportTypeNames = ReportType.values().map { type ->
            getStringForReportType(type) // Используем вспомогательную функцию
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, reportTypeNames)
        binding.actvReportType.setAdapter(adapter)

        // Устанавливаем начальное значение из ViewModel
        val currentReportType = viewModel.uiState.value.selectedReportType
        binding.actvReportType.setText(getStringForReportType(currentReportType), false)

        // Обработчик выбора
        binding.actvReportType.setOnItemClickListener { _, _, position, _ ->
            val selectedType = ReportType.values()[position]
            if (selectedType != viewModel.uiState.value.selectedReportType) {
                Log.d("ReportsFrag", "Report type selected: $selectedType")
                viewModel.setReportType(selectedType) // Обновляем тип в ViewModel
            }
        }
    }

    /** Наблюдает за ViewModel и обновляет UI. */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d("ReportsFrag", "Observing state: isLoading=${state.isLoading}, noData=${state.noDataAvailable}, type=${state.selectedReportType}, data=${state.reportData::class.simpleName}")

                    binding.progressBarReports.isVisible = state.isLoading

                    // Обновляем кнопку периода
                    if (state.startDate != null && state.endDate != null) {
                        binding.btnSelectPeriod.text =
                            "${periodButtonFormatter.format(state.startDate)} - ${periodButtonFormatter.format(state.endDate)}"
                    } else {
                        binding.btnSelectPeriod.text = getString(R.string.select_period)
                    }

                    // Обновляем выбранный тип отчета в Dropdown (на случай програмного изменения)
                    val currentDropdownText = binding.actvReportType.text.toString()
                    val expectedDropdownText = getStringForReportType(state.selectedReportType)
                    if (currentDropdownText != expectedDropdownText) {
                        binding.actvReportType.setText(expectedDropdownText, false)
                    }


                    // Показываем заглушку "Нет данных" только если не идет загрузка и данных действительно нет
                    binding.tvNoReportData.isVisible = state.noDataAvailable && !state.isLoading
                    // Устанавливаем текст заглушки
                    binding.tvNoReportData.text = if(state.selectedReportType == ReportType.INCOME_VS_EXPENSE && state.noDataAvailable && !state.isLoading){
                        "Нет доходов или расходов за период." // TODO: Ресурс строки
                    } else {
                        getString(R.string.no_data_for_report)
                    }


                    // Управляем видимостью графиков и обновляем их данные
                    if (!state.isLoading && !state.noDataAvailable) {
                        when (val data = state.reportData) {
                            is ReportData.CategoryReportData -> {
                                binding.pieChart.visibility = View.VISIBLE
                                binding.barChart.visibility = View.GONE
                                val chartTitle = when (state.selectedReportType) {
                                    ReportType.EXPENSE_BY_CATEGORY -> getString(R.string.report_expense_by_category)
                                    ReportType.INCOME_BY_SOURCE -> getString(R.string.report_income_by_source)
                                    else -> ""
                                }
                                binding.pieChart.centerText = chartTitle // Устанавливаем текст здесь
                                updatePieChartData(data.items)
                            }
                            is ReportData.IncomeExpenseReportData -> {
                                binding.pieChart.visibility = View.GONE
                                binding.barChart.visibility = View.VISIBLE
                                updateBarChartData(data)
                            }
                            is ReportData.None -> { // Если данные None, но noDataAvailable = false (не должно быть)
                                binding.pieChart.visibility = View.GONE
                                binding.barChart.visibility = View.GONE
                                binding.tvNoReportData.isVisible = true // Показываем заглушку
                            }
                        }
                    } else {
                        // Скрываем графики при загрузке или если нет данных
                        binding.pieChart.visibility = View.GONE
                        binding.barChart.visibility = View.GONE
                        if (!state.isLoading) { // Очищаем только если загрузка завершена
                            binding.pieChart.data?.clearValues()
                            binding.pieChart.invalidate()
                            binding.barChart.data?.clearValues()
                            binding.barChart.invalidate()
                        }
                    }

                    // Обработка ошибок
                    state.errorMessage?.let {
                        showSnackbar(it, true)
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    /** Обновляет данные PieChart. */
    private fun updatePieChartData(data: List<CategorySpending>) {
        val entries = ArrayList<PieEntry>()
        // Собираем данные для PieChart (только положительные суммы)
        data.filter { it.totalSpent > 0 }.forEach { spending ->
            entries.add(PieEntry(spending.totalSpent.toFloat(), spending.categoryName ?: getString(R.string.unknown_category)))
        }

        if (entries.isEmpty()) {
            binding.pieChart.data?.clearValues()
            binding.pieChart.invalidate()
            binding.tvNoReportData.isVisible = true // Показать заглушку, если после фильтрации ничего не осталось
            binding.pieChart.visibility = View.GONE
            return
        }

        val dataSet = PieDataSet(entries, "") // Метка для набора данных (не отображается)
        // Настройка цветов и отображения
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.VORDIPLOM_COLORS.toList() + ColorTemplate.LIBERTY_COLORS.toList() // Больше цветов
        dataSet.sliceSpace = 2f
        dataSet.valueLinePart1OffsetPercentage = 80f
        dataSet.valueLinePart1Length = 0.4f
        dataSet.valueLinePart2Length = 0.4f
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE // Значения снаружи

        val pieData = PieData(dataSet)
        pieData.setValueFormatter(PercentFormatter(binding.pieChart)) // Форматируем как проценты
        pieData.setValueTextSize(11f)
        pieData.setValueTextColor(Color.BLACK)

        binding.pieChart.data = pieData
        binding.pieChart.highlightValues(null) // Сбросить выделение
        binding.pieChart.invalidate() // Перерисовать
        binding.pieChart.animateY(1000, Easing.EaseInOutQuad) // Анимация
    }

    /** Обновляет данные BarChart. */
    private fun updateBarChartData(data: ReportData.IncomeExpenseReportData) {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, data.totalIncome.toFloat())) // Доход на позиции 0
        entries.add(BarEntry(1f, data.totalExpense.toFloat())) // Расход на позиции 1

        val labels = listOf(getString(R.string.title_income), getString(R.string.title_expenses))

        // Устанавливаем подписи для оси X
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.barChart.xAxis.labelCount = labels.size // Устанавливаем количество меток

        val dataSet = BarDataSet(entries, "Income vs Expense") // Метка набора (не видна)
        dataSet.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.colorIncome), // Зеленый для дохода
            ContextCompat.getColor(requireContext(), R.color.colorExpense)  // Красный для расхода
        )
        dataSet.setDrawValues(true) // Показываем значения над столбцами
        dataSet.valueFormatter = LargeValueFormatter() // Форматируем значения
        dataSet.valueTextSize = 10f
        dataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.design_default_color_on_surface)

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f // Ширина столбцов

        binding.barChart.data = barData
        binding.barChart.setFitBars(true) // Пытаться уместить столбцы
        binding.barChart.invalidate() // Перерисовать
        binding.barChart.animateY(1000) // Анимация
    }

    /** Вспомогательная функция для получения строки для типа отчета. */
    private fun getStringForReportType(type: ReportType): String {
        return when (type) {
            ReportType.EXPENSE_BY_CATEGORY -> getString(R.string.report_expense_by_category)
            ReportType.INCOME_BY_SOURCE -> getString(R.string.report_income_by_source)
            ReportType.INCOME_VS_EXPENSE -> getString(R.string.report_income_vs_expense)
        }
    }

    /** Показывает Snackbar. */
    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.design_default_color_on_error))
        }
        snackbar.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Очищаем данные графиков, чтобы избежать утечек
        if (_binding?.pieChart?.data != null) {
            _binding?.pieChart?.clear()
        }
        if (_binding?.barChart?.data != null) {
            _binding?.barChart?.clear()
        }
        _binding = null
        Log.d("ReportsFrag", "onDestroyView called")
    }
}