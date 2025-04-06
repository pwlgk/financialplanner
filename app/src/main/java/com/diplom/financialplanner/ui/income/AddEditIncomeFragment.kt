package com.diplom.financialplanner.ui.income

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import androidx.core.view.isVisible
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.databinding.FragmentAddEditIncomeBinding // Биндинг для дохода
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Фрагмент для добавления или редактирования дохода.
 * Аналогичен AddEditExpenseFragment, но работает с AddEditIncomeViewModel.
 */
class AddEditIncomeFragment : Fragment() {

    private var _binding: FragmentAddEditIncomeBinding? = null
    private val binding get() = _binding!!

    // Аргументы навигации (transactionId, titleArg)
    private val args: AddEditIncomeFragmentArgs by navArgs()

    // ViewModel для доходов
    private val viewModel: AddEditIncomeViewModel by viewModels { AddEditIncomeViewModel.provideFactory() }

    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private lateinit var categoryAdapter: ArrayAdapter<String>
    private var categoriesList: List<CategoryEntity> = emptyList()
    private var initialDataSet = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditIncomeBinding.inflate(inflater, container, false)
        Log.d("AddEditIncomeFrag", "onCreateView called. Args: transactionId=${args.transactionId}, title=${args.titleArg}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("AddEditIncomeFrag", "onViewCreated called.")

        setupCategoryInput()
        setupDateInput()
        setupInputListeners()
        setupSaveButton()
        observeViewModel()
        observeSaveSuccess()
    }

    /** Настраивает поле выбора категории (источника) дохода. */
    private fun setupCategoryInput() {
        // Используем mutableListOf()
        categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        (binding.tilCategory.editText as? AutoCompleteTextView)?.setAdapter(categoryAdapter)

        // Обработчик выбора элемента из списка (без изменений)
        (binding.tilCategory.editText as? AutoCompleteTextView)?.setOnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedCategory = categoriesList.find { it.name == selectedName }
            Log.d("AddEditIncomeFrag", "Income Category selected: $selectedName, Entity: $selectedCategory")
            viewModel.updateCategorySelection(selectedCategory)
            binding.tilCategory.error = null
        }
        binding.actvCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilCategory.error = null
        }
    }

    /** Настраивает поле ввода/выбора даты. */
    private fun setupDateInput() {
        binding.etDate.setOnClickListener { showDatePickerDialog() }
        binding.tilDate.setEndIconOnClickListener { showDatePickerDialog() }
    }

    /** Показывает DatePickerDialog. */
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        calendar.time = viewModel.uiState.value.initialDate

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog( requireContext(), { _, selectedYear, selectedMonth, selectedDayOfMonth ->
            val selectedCalendar = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0);
            }
            val newDate = selectedCalendar.time
            Log.d("AddEditIncomeFrag", "Date selected: $newDate")
            updateDateEditText(newDate)
            viewModel.updateSelectedDate(newDate)
            binding.tilDate.error = null
        },
            year, month, day
        )
        datePickerDialog.show()
    }

    /** Обновляет текст в поле даты. */
    private fun updateDateEditText(date: Date) {
        binding.etDate.setText(dateFormatter.format(date))
    }

    /** Настраивает слушатели ввода для суммы и описания. */
    private fun setupInputListeners() {
        binding.etAmount.doOnTextChanged { text, _, _, _ ->
            viewModel.updateAmount(text.toString())
            binding.tilAmount.error = null
        }
        binding.etDescription.doOnTextChanged { text, _, _, _ ->
            viewModel.updateDescription(text.toString())
        }
    }

    /** Настраивает кнопку сохранения. */
    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            viewModel.saveIncome() // Вызываем метод сохранения дохода
        }
    }

    /** Наблюдает за состоянием UI в ViewModel. */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.v("AddEditIncomeFrag", "Observing UI State: isLoading=${state.isLoading}, isSaving=${state.isSaving}, categories=${state.availableCategories.size}")

                    binding.progressBarSave.isVisible = state.isSaving || state.isLoading
                    binding.btnSave.isEnabled = !state.isSaving && !state.isLoading
                    binding.btnSave.text = if (state.isSaving) getString(R.string.saving) else getString(R.string.save)

                    // Установка начальных данных
                    if (!initialDataSet && !state.isLoading) {
                        Log.d("AddEditIncomeFrag", "Setting initial data. Amount: ${state.amount}, Desc: ${state.description}, CatId: ${state.categoryId}, Date: ${state.initialDate}")
                        binding.etAmount.setText(state.amount)
                        updateDateEditText(state.initialDate)
                        binding.etDescription.setText(state.description)
                        initialDataSet = true
                    }

                    // Обновление списка категорий
                    if (state.availableCategories != categoriesList) {
                        Log.d("AddEditIncomeFrag", "Updating category dropdown. Count: ${state.availableCategories.size}")
                        categoriesList = state.availableCategories
                        val categoryNames = categoriesList.map { it.name }
                        categoryAdapter.clear()
                        categoryAdapter.addAll(categoryNames)
                        //categoryAdapter.notifyDataSetChanged()

                        if (initialDataSet || !state.isLoading) {
                            state.categoryId?.let { id ->
                                val selectedCategory = categoriesList.find { it.id == id }
                                selectedCategory?.let { cat ->
                                    binding.actvCategory.setText(cat.name, false)
                                    Log.d("AddEditIncomeFrag", "Restored category selection: ${cat.name}")
                                }
                            }
                        }
                    }

                    // Обработка ошибок
                    state.errorMessage?.let { message ->
                        handleInputError(message)
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    /** Наблюдает за флагом успешного сохранения для навигации назад. */
    private fun observeSaveSuccess() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.saveSuccess }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect {
                        Log.d("AddEditIncomeFrag", "Save successful, navigating back.")
                        val successMessageResId = if (viewModel.uiState.value.isEditMode) R.string.income_updated_success else R.string.income_saved_success
                        Toast.makeText(requireContext(), successMessageResId, Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                        // viewModel.consumeInputSuccess() // Не нужно, т.к. ViewModel уничтожится
                    }
            }
        }
    }

    /** Обрабатывает и показывает ошибки ввода. */
    private fun handleInputError(message: String) {
        val errorText = try { getString(message.toInt()) } catch (e: Exception) { message }
        Log.w("AddEditIncomeFrag", "Input Error: $errorText")
        when {
            errorText.contains("сумму", ignoreCase = true) -> {
                binding.tilAmount.error = errorText
                binding.etAmount.requestFocus()
            }
            errorText.contains("категорию", ignoreCase = true) -> {
                binding.tilCategory.error = errorText
                binding.actvCategory.requestFocus()
            }
            errorText.contains("дату", ignoreCase = true) -> {
                binding.tilDate.error = errorText
            }
            else -> showSnackbar(errorText, true)
        }
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
        (binding.tilCategory.editText as? AutoCompleteTextView)?.setAdapter(null)
        _binding = null
        Log.d("AddEditIncomeFrag", "onDestroyView called")
    }
}