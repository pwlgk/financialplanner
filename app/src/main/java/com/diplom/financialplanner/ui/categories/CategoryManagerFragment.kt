package com.diplom.financialplanner.ui.categories

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.databinding.DialogAddEditCategoryBinding // ViewBinding для диалога
import com.diplom.financialplanner.databinding.FragmentCategoryManagerBinding
import com.diplom.financialplanner.ui.adapters.CategoryAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout // Для TabLayout
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Фрагмент для управления категориями (просмотр, добавление, редактирование, удаление).
 */
class CategoryManagerFragment : Fragment() {

    private var _binding: FragmentCategoryManagerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CategoryViewModel by viewModels { CategoryViewModel.provideFactory() }
    private lateinit var categoryAdapter: CategoryAdapter
    private var currentDialog: AlertDialog? = null
    // Переменная для хранения текущего выбранного типа категорий (для фильтрации списка)
    private var currentCategoryType = "expense" // По умолчанию показываем расходы

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupTabLayout() // Настраиваем TabLayout
        observeViewModel()
        observeInputStateForDialog()
    }

    /** Настраивает TabLayout для выбора типа категорий (Расходы/Доходы). */
    private fun setupTabLayout() {
        // Добавляем вкладки программно (или можно сделать через XML)
        binding.tabLayoutCategoryType.addTab(binding.tabLayoutCategoryType.newTab().setText(R.string.expense))
        binding.tabLayoutCategoryType.addTab(binding.tabLayoutCategoryType.newTab().setText(R.string.income))

        // Устанавливаем слушатель выбора вкладок
        binding.tabLayoutCategoryType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentCategoryType = if (tab?.position == 1) "income" else "expense"
                Log.d("CategoryManagerFrag", "Tab selected: $currentCategoryType")
                // Обновляем список в адаптере на основе нового типа
                updateAdapterList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        // Устанавливаем начальную выбранную вкладку (расходы)
        binding.tabLayoutCategoryType.getTabAt(0)?.select()
    }


    /** Настраивает RecyclerView и адаптер категорий. */
    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter(
            onCategoryClick = { category ->
                // Клик по категории - открываем диалог редактирования
                viewModel.prepareCategory(category)
                showAddEditCategoryDialog()
            },
            onDeleteClick = { category ->
                // Клик по кнопке удаления - показываем диалог подтверждения
                showDeleteConfirmationDialog(category)
            }
        )
        binding.rvCategories.apply {
            adapter = categoryAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    /** Настраивает FloatingActionButton для добавления новой категории. */
    private fun setupFab() {
        binding.fabAddCategory.setOnClickListener {
            viewModel.prepareCategory(null) // Готовим для новой
            showAddEditCategoryDialog()     // Показываем диалог
        }
    }

    /** Показывает диалог добавления/редактирования категории. */
    private fun showAddEditCategoryDialog() {
        if (currentDialog?.isShowing == true) return // Не показываем, если уже открыт

        val dialogBinding = DialogAddEditCategoryBinding.inflate(layoutInflater)
        val inputState = viewModel.inputState.value
        val isEditing = inputState.categoryId != null

        // Предзаполнение полей из inputState
        dialogBinding.etCategoryName.setText(inputState.name)
        if (inputState.type == "income") {
            dialogBinding.rbIncome.isChecked = true
        } else {
            dialogBinding.rbExpense.isChecked = true
        }

        // Установка слушателей для обновления ViewModel при изменении полей
        dialogBinding.etCategoryName.doOnTextChanged { text, _, _, _ -> viewModel.updateInputName(text.toString()) }
        dialogBinding.rgCategoryType.setOnCheckedChangeListener { _, checkedId ->
            val type = if (checkedId == R.id.rb_income) "income" else "expense"
            viewModel.updateInputType(type)
        }

        // Создание и показ диалога
        currentDialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog) // Используем стиль
            .setTitle(if (isEditing) R.string.edit_category else R.string.add_category)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null) // Просто закрыть
            .setPositiveButton(R.string.save) { _, _ -> viewModel.saveCategory() } // Вызвать сохранение
            .setOnDismissListener { currentDialog = null } // Сбросить ссылку при закрытии
            .show()
    }

    /** Показывает диалог подтверждения удаления категории. */
    private fun showDeleteConfirmationDialog(category: CategoryEntity) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.delete_category_confirmation_title)
            .setMessage(getString(R.string.delete_category_confirmation_message, category.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteCategory(category) }
            .show()
    }

    /** Наблюдает за основным состоянием UI и обновляет список. */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarCategories.isVisible = state.isLoading

                    // Обновляем список в адаптере на основе текущего выбранного типа
                    updateAdapterList(state) // Передаем актуальное состояние

                    // Показ сообщений и ошибок
                    state.userMessage?.let { message ->
                        val msgText = try { getString(message.toInt()) } catch (e: Exception) { message }
                        showSnackbar(msgText); viewModel.clearUserMessage()
                    }
                    state.errorMessage?.let { error ->
                        val errorText = try { getString(error.toInt()) } catch (e: Exception) { error }
                        showSnackbar(errorText, true); viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    /** Обновляет список категорий в адаптере в соответствии с выбранным типом. */
    private fun updateAdapterList(state: CategoryUiState = viewModel.uiState.value) {
        val categoriesToShow = if (currentCategoryType == "income") {
            state.incomeCategories
        } else {
            state.expenseCategories
        }
        Log.d("CategoryManagerFrag", "Updating adapter list for type '$currentCategoryType'. Count: ${categoriesToShow.size}")
        binding.tvNoCategories.isVisible = !state.isLoading && categoriesToShow.isEmpty()
        binding.rvCategories.isVisible = !state.isLoading && categoriesToShow.isNotEmpty()
        categoryAdapter.submitList(categoriesToShow)
    }


    /** Наблюдает за состоянием ввода для закрытия диалога после успешного сохранения. */
    private fun observeInputStateForDialog() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.inputState
                    .map { it.saveSuccess }
                    .distinctUntilChanged()
                    .filter { it } // Только при успехе (true)
                    .collect {
                        Log.d("CategoryManagerFrag", "Save success detected, dismissing dialog.")
                        currentDialog?.dismiss()
                        currentDialog = null
                        viewModel.consumeInputSuccess() // Сбросить флаг
                    }
            }
        }
    }

    /** Показывает Snackbar. */
    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT) // Длительность SHORT
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.design_default_color_on_error))
        }
        snackbar.show()
    }

    /** Очищает ресурсы при уничтожении View. */
    override fun onDestroyView() {
        currentDialog?.dismiss()
        currentDialog = null
        binding.rvCategories.adapter = null // Важно!
        _binding = null
        super.onDestroyView()
    }
}