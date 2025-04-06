package com.diplom.financialplanner.ui.budget

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast // Импорт для Toast
import androidx.appcompat.app.AlertDialog // Для currentDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.databinding.FragmentBudgetSetupBinding
import com.diplom.financialplanner.ui.adapters.BudgetLimitAdapter
import com.diplom.financialplanner.ui.adapters.BudgetLimitChangeListener
import com.diplom.financialplanner.ui.adapters.BudgetLimitItem
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Фрагмент для настройки (создания или редактирования) бюджета.
 */
class BudgetSetupFragment : Fragment(), BudgetLimitChangeListener {

    private var _binding: FragmentBudgetSetupBinding? = null
    private val binding get() = _binding!!

    private val args: BudgetSetupFragmentArgs by navArgs()
    private val viewModel: BudgetSetupViewModel by viewModels { BudgetSetupViewModel.provideFactory() }

    private lateinit var budgetLimitAdapter: BudgetLimitAdapter
    private val periodFormatter = SimpleDateFormat("MMMM yyyy", Locale("ru"))
    private var adapterDataInitialized = false
    private var currentDialog: AlertDialog? = null // Для диалогов выбора даты (хотя MaterialDatePicker сам управляет)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetSetupBinding.inflate(inflater, container, false)
        Log.d("BudgetSetupFrag", "onCreateView. BudgetId from args: ${args.budgetId}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("BudgetSetupFrag", "onViewCreated called.")

        setupRecyclerView()
        setupPeriodInput()
        setupSaveButton()
        observeViewModel()
        observeSaveSuccess()
    }

    /** Настраивает RecyclerView для лимитов. */
    private fun setupRecyclerView() {
        budgetLimitAdapter = BudgetLimitAdapter(this)
        binding.rvCategoryLimits.apply {
            adapter = budgetLimitAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    /** Настраивает поле выбора периода. */
    private fun setupPeriodInput() {
        binding.etBudgetPeriod.isFocusable = false
        binding.etBudgetPeriod.isClickable = true
        binding.etBudgetPeriod.setOnClickListener { showMonthYearPicker() }
        binding.tilBudgetPeriod.setEndIconOnClickListener { showMonthYearPicker() }
    }

    /** Показывает MaterialDatePicker для выбора месяца/года. */
    private fun showMonthYearPicker() {
        val currentSelection = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { // Используем UTC для DatePicker
            clear()
            set(Calendar.YEAR, viewModel.uiState.value.selectedYear)
            set(Calendar.MONTH, viewModel.uiState.value.selectedMonth)
        }.timeInMillis

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_budget_period))
            .setSelection(currentSelection)
            // TODO: Рассмотреть MonthPicker, если он доступен и подходит
            // .setCalendarConstraints(...) можно добавить для ограничения выбора
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) // 0-11
            Log.d("BudgetSetupFrag", "Month/Year selected: Month=$month, Year=$year")
            viewModel.setBudgetPeriod(year, month)
        }

        // Показываем DatePicker через childFragmentManager
        datePicker.show(childFragmentManager, "MONTH_YEAR_PICKER_TAG")
    }

    /** Настраивает кнопку сохранения. */
    private fun setupSaveButton() {
        binding.btnSaveBudget.setOnClickListener {
            viewModel.saveBudget()
        }
    }

    /** Наблюдает за основным состоянием UI. */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.v("BudgetSetupFrag", "Observing UI state: isLoading=${state.isLoading}, periodSelected=${state.periodSelected}, categories=${state.expenseCategories.size}, limits=${state.currentLimits.size}")

                    binding.progressBarBudgetSetup.isVisible = state.isLoading
                    binding.btnSaveBudget.isEnabled = !state.isLoading && !state.isSaving
                    binding.btnSaveBudget.text = if (state.isSaving) getString(R.string.saving) else getString(R.string.save_budget)
                    binding.tvNoCategoriesPlaceholder.isVisible = !state.isLoading && state.expenseCategories.isEmpty()
                    binding.rvCategoryLimits.isVisible = !state.isLoading && state.expenseCategories.isNotEmpty()

                    // Обновление поля периода
                    if (state.periodSelected) {
                        val calendar = Calendar.getInstance().apply {
                            clear()
                            set(Calendar.YEAR, state.selectedYear)
                            set(Calendar.MONTH, state.selectedMonth)
                        }
                        val formattedPeriod = periodFormatter.format(calendar.time)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
                        binding.etBudgetPeriod.setText(formattedPeriod)
                        binding.tilBudgetPeriod.error = null
                    } else if (!state.isLoading) {
                        binding.etBudgetPeriod.setText("")
                    }

                    // Обновление данных адаптера
                    if (!state.isLoading && state.expenseCategories.isNotEmpty()) {
                        val budgetItems = state.expenseCategories.map { cat ->
                            BudgetLimitItem(cat, state.currentLimits[cat.id])
                        }
                        // Просто вызываем submitList
                        budgetLimitAdapter.submitList(budgetItems)
                        adapterDataInitialized = true
                        Log.d("BudgetSetupFrag", "Adapter list submitted. Count: ${budgetItems.size}")
                    } else if (!state.isLoading && state.expenseCategories.isEmpty()) {
                        budgetLimitAdapter.submitList(emptyList())
                        adapterDataInitialized = true
                    }

                    // Обработка ошибок
                    state.errorMessage?.let { message ->
                        val errorText = try { getString(message.toInt()) } catch (e: Exception) { message }
                        Log.e("BudgetSetupFrag", "Error message from ViewModel: $errorText")
                        when {
                            message == R.string.error_select_period.toString() || errorText.contains("период", ignoreCase = true) -> binding.tilBudgetPeriod.error = errorText
                            else -> showSnackbar(errorText, true)
                        }
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    /** Наблюдает за флагом успешного сохранения для навигации. */
    private fun observeSaveSuccess() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState // Наблюдаем за основным стейтом
                    .map { it.saveSuccess }
                    .distinctUntilChanged()
                    .filter { it } // Только при успехе (true)
                    .collect {
                        Log.d("BudgetSetupFrag", "Save successful, navigating back.")
                        val successMessageResId = if (viewModel.uiState.value.budgetId != null) {
                            R.string.budget_updated_success
                        } else {
                            R.string.budget_saved_success
                        }
                        Toast.makeText(requireContext(), successMessageResId, Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                        viewModel.consumeInputSuccess() // Сбрасываем флаг
                    }
            }
        }
    }

    /** Реализация интерфейса BudgetLimitChangeListener. */
    override fun onLimitChanged(categoryId: Long, newLimit: Double?) {
        viewModel.updateLimit(categoryId, newLimit)
    }

    /** Показывает Snackbar. */
    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.design_default_color_on_error))
        }
        snackbar.show()
    }

    /** Очищает ресурсы. */
    override fun onDestroyView() {
        super.onDestroyView()
        currentDialog?.dismiss() // Закрываем диалог, если он был открыт
        currentDialog = null
        binding.rvCategoryLimits.adapter = null
        _binding = null
        Log.d("BudgetSetupFrag", "onDestroyView called")
    }
}