package com.diplom.financialplanner.ui.expense

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
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
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.databinding.FragmentAddEditExpenseBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Фрагмент для добавления или редактирования расхода.
 */
class AddEditExpenseFragment : Fragment() {

    private var _binding: FragmentAddEditExpenseBinding? = null
    private val binding get() = _binding!!

    // Получаем аргументы навигации (transactionId, titleArg)
    private val args: AddEditExpenseFragmentArgs by navArgs()

    private val viewModel: AddEditExpenseViewModel by viewModels { AddEditExpenseViewModel.provideFactory() }

    // Форматтер для отображения даты в поле ввода
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    // Адаптер для выпадающего списка категорий
    private lateinit var categoryAdapter: ArrayAdapter<String>
    // Кэшированный список категорий для поиска по имени
    private var categoriesList: List<CategoryEntity> = emptyList()
    // Флаг, чтобы избежать повторной установки начальных данных при пересоздании View
    private var initialDataSet = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditExpenseBinding.inflate(inflater, container, false)
        Log.d("AddEditExpenseFrag", "onCreateView called. Args: transactionId=${args.transactionId}, title=${args.titleArg}")
        // Заголовок AppBar устанавливается через label="{titleArg}" в графе навигации
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("AddEditExpenseFrag", "onViewCreated called.")

        setupCategoryInput()
        setupDateInput()
        setupInputListeners()
        setupSaveButton()
        observeViewModel()
        observeSaveSuccess() // Отдельное наблюдение за успехом сохранения
    }

    /** Настраивает поле выбора категории (AutoCompleteTextView). */
    private fun setupCategoryInput() {
        // Используем mutableListOf() для создания ИЗМЕНЯЕМОГО списка
        categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        (binding.tilCategory.editText as? AutoCompleteTextView)?.setAdapter(categoryAdapter)

        // Обработчик выбора категории из списка
        (binding.tilCategory.editText as? AutoCompleteTextView)?.setOnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedCategory = categoriesList.find { it.name == selectedName }
            Log.d("AddEditExpenseFrag", "Category selected: $selectedName, Entity: $selectedCategory")
            viewModel.updateCategorySelection(selectedCategory) // Сообщаем ViewModel
            binding.tilCategory.error = null // Сбрасываем ошибку, если была
        }
        // Сброс ошибки при получении фокуса
        binding.actvCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilCategory.error = null
        }
    }

    /** Настраивает поле ввода даты и обработку клика для показа DatePicker. */
    private fun setupDateInput() {
        // Клик по полю или иконке вызывает DatePicker
        binding.etDate.setOnClickListener { showDatePickerDialog() }
        binding.tilDate.setEndIconOnClickListener { showDatePickerDialog() }
        // Начальное значение даты установится в observeViewModel
    }

    /** Показывает стандартный DatePickerDialog. */
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        // Используем initialDate из стейта для инициализации календаря
        calendar.time = viewModel.uiState.value.initialDate

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                // Вызывается при выборе даты в DatePicker
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDayOfMonth)
                    // Устанавливаем время на начало дня, чтобы избежать проблем с часовыми поясами при сохранении
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0);
                }
                val newDate = selectedCalendar.time
                Log.d("AddEditExpenseFrag", "Date selected: $newDate")
                updateDateEditText(newDate) // Обновляем текст в поле
                viewModel.updateSelectedDate(newDate) // Обновляем дату во ViewModel
                binding.tilDate.error = null // Сбрасываем ошибку даты
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    /** Обновляет текст в поле даты. */
    private fun updateDateEditText(date: Date) {
        binding.etDate.setText(dateFormatter.format(date))
    }

    /** Настраивает слушатели изменения текста для полей суммы и описания. */
    private fun setupInputListeners() {
        binding.etAmount.doOnTextChanged { text, _, _, _ ->
            viewModel.updateAmount(text.toString())
            binding.tilAmount.error = null // Сброс ошибки при вводе
        }
        binding.etDescription.doOnTextChanged { text, _, _, _ ->
            viewModel.updateDescription(text.toString())
            // Для описания ошибка не предусмотрена
        }
    }

    /** Настраивает кнопку сохранения. */
    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            viewModel.saveExpense() // Вызываем метод сохранения во ViewModel
        }
    }

    /** Наблюдает за изменениями UI State в ViewModel. */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.v("AddEditExpenseFrag", "Observing UI State: isLoading=${state.isLoading}, isSaving=${state.isSaving}, categories=${state.availableCategories.size}")

                    // Обновление состояния загрузки/сохранения
                    binding.progressBarSave.isVisible = state.isSaving || state.isLoading
                    binding.btnSave.isEnabled = !state.isSaving && !state.isLoading
                    binding.btnSave.text = if (state.isSaving) getString(R.string.saving) else getString(R.string.save)

                    // Устанавливаем начальные данные только один раз после загрузки
                    if (!initialDataSet && !state.isLoading) {
                        Log.d("AddEditExpenseFrag", "Setting initial data. Amount: ${state.amount}, Desc: ${state.description}, CatId: ${state.categoryId}, Date: ${state.initialDate}")
                        binding.etAmount.setText(state.amount)
                        updateDateEditText(state.initialDate) // Устанавливаем дату
                        binding.etDescription.setText(state.description)
                        initialDataSet = true // Помечаем, что данные установлены
                    }

                    // Обновление списка категорий в адаптере
                    if (state.availableCategories != categoriesList) {
                        Log.d("AddEditExpenseFrag", "Updating category dropdown. Count: ${state.availableCategories.size}")
                        categoriesList = state.availableCategories
                        val categoryNames = categoriesList.map { it.name }
                        categoryAdapter.clear()
                        categoryAdapter.addAll(categoryNames)

                        // Восстановление выбранной категории после загрузки или при пересоздании
                        if (initialDataSet || !state.isLoading) { // Делаем это после установки initialDataSet или если загрузка завершена
                            state.categoryId?.let { id ->
                                val selectedCategory = categoriesList.find { it.id == id }
                                selectedCategory?.let { cat ->
                                    // Устанавливаем текст в AutoCompleteTextView без вызова фильтрации
                                    binding.actvCategory.setText(cat.name, false)
                                    Log.d("AddEditExpenseFrag", "Restored category selection: ${cat.name}")
                                }
                            }
                        }
                    }

                    // Обработка ошибок валидации и сохранения
                    state.errorMessage?.let { message ->
                        handleInputError(message)
                        viewModel.clearErrorMessage() // Сбрасываем ошибку после показа
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
                    .filter { it } // Только при успехе (true)
                    .collect {
                        Log.d("AddEditExpenseFrag", "Save successful, navigating back.")
                        val successMessageResId = if (viewModel.uiState.value.isEditMode) R.string.expense_updated_success else R.string.expense_saved_success
                        Toast.makeText(requireContext(), successMessageResId, Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp() // Возвращаемся назад
                        // Сбрасывать флаг не нужно, т.к. ViewModel уничтожится
                    }
            }
        }
    }


    /** Обрабатывает и показывает ошибки ввода. */
    private fun handleInputError(message: String) {
        val errorText = try { getString(message.toInt()) } catch (e: Exception) { message }
        Log.w("AddEditExpenseFrag", "Input Error: $errorText")
        when {
            // Ищем ключевые слова (упрощенный вариант)
            errorText.contains("сумму", ignoreCase = true) -> {
                binding.tilAmount.error = errorText
                binding.etAmount.requestFocus() // Устанавливаем фокус на поле с ошибкой
            }
            errorText.contains("категорию", ignoreCase = true) -> {
                binding.tilCategory.error = errorText
                binding.actvCategory.requestFocus()
            }
            errorText.contains("дату", ignoreCase = true) -> {
                binding.tilDate.error = errorText
                // Фокус на поле даты не имеет смысла, т.к. оно не редактируется напрямую
            }
            else -> showSnackbar(errorText, true) // Общая ошибка
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

    /** Очищает ресурсы при уничтожении View. */
    override fun onDestroyView() {
        super.onDestroyView()
        // Очищаем адаптер AutoCompleteTextView, чтобы избежать утечек памяти
        (binding.tilCategory.editText as? AutoCompleteTextView)?.setAdapter(null)
        _binding = null
        Log.d("AddEditExpenseFrag", "onDestroyView called")
    }
}