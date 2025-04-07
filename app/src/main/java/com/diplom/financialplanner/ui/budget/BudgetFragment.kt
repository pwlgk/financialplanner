package com.diplom.financialplanner.ui.budget

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible // Убедитесь, что этот импорт есть
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.BudgetEntity
import com.diplom.financialplanner.databinding.FragmentBudgetBinding // Используем ViewBinding
import com.diplom.financialplanner.ui.adapters.BudgetHistoryAdapter
import com.diplom.financialplanner.ui.adapters.BudgetProgressAdapter
import com.diplom.financialplanner.util.SwipeToDeleteCallback // Используем SwipeToDeleteCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Фрагмент для отображения информации о бюджетах:
 * - Прогресс выполнения текущего активного бюджета.
 * - История созданных бюджетов с возможностью редактирования и удаления.
 */
class BudgetFragment : Fragment() {

    // ViewBinding
    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!! // Свойство для безопасного доступа к binding

    // ViewModel
    private val viewModel: BudgetViewModel by viewModels { BudgetViewModel.provideFactory() }

    // Adapters
    private lateinit var budgetProgressAdapter: BudgetProgressAdapter
    private lateinit var budgetHistoryAdapter: BudgetHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        Log.d("BudgetFrag", "onCreateView called")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("BudgetFrag", "onViewCreated called")

        setupRecyclerViews()
        setupFab()
        observeViewModel()
    }

    /** Настраивает оба RecyclerView (прогресс и история) и их адаптеры. */
    private fun setupRecyclerViews() {
        // Адаптер для прогресса текущего бюджета
        budgetProgressAdapter = BudgetProgressAdapter()
        binding.rvBudgetProgress.apply {
            adapter = budgetProgressAdapter
            layoutManager = LinearLayoutManager(requireContext())
            // Отключаем вложенную прокрутку, так как есть NestedScrollView
            isNestedScrollingEnabled = false
        }

        // Адаптер для истории бюджетов
        budgetHistoryAdapter = BudgetHistoryAdapter(
            onBudgetClick = { budgetId ->
                // Клик по бюджету в истории -> переход к редактированию
                Log.d("BudgetFrag", "Navigate to edit budget ID: $budgetId")
                val action = BudgetFragmentDirections.actionNavigationBudgetToBudgetSetupFragment(budgetId)
                try { findNavController().navigate(action) } catch (e: Exception) { handleNavigationError(e, getString(R.string.budget_setup_title)) }
            },
            onBudgetLongClick = { budget ->
                // Долгое нажатие -> показать диалог удаления
                Log.d("BudgetFrag", "Long click on budget: ${budget.name}")
                showDeleteConfirmationDialog(budget)
            }
        )
        binding.rvBudgetHistory.apply {
            adapter = budgetHistoryAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
        // Добавляем Swipe-to-delete для истории бюджетов
        attachSwipeToDelete(binding.rvBudgetHistory, budgetHistoryAdapter)
    }

    /** Настраивает FloatingActionButton для добавления нового бюджета. */
    private fun setupFab() {
        binding.fabAddBudget.setOnClickListener {
            Log.d("BudgetFrag", "Navigate to add new budget")
            val action = BudgetFragmentDirections.actionNavigationBudgetToBudgetSetupFragment(0L) // 0L для нового
            try { findNavController().navigate(action) } catch (e: Exception) { handleNavigationError(e, getString(R.string.add_budget)) }
        }
    }

    /** Наблюдает за изменениями в ViewModel и обновляет UI. */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d("BudgetFrag_Observe", "State Received: isLoadingProgress=${state.isLoadingProgress}, isLoadingAll=${state.isLoadingAllBudgets}, currentExists=${state.isCurrentBudgetExists}, historyCount=${state.allBudgets.size}")

                    // --- Управление видимостью индикаторов загрузки ---
                    binding.progressBarBudget.isVisible = state.isLoadingProgress
                    binding.progressBarHistory.isVisible = state.isLoadingAllBudgets

                    // --- Отображение секции Текущего Бюджета ИЛИ Заглушки ---
                    val shouldShowCurrentBudget = !state.isLoadingProgress && state.isCurrentBudgetExists
                    val shouldShowNoBudget = !state.isLoadingProgress && !state.isCurrentBudgetExists

                    // Используем LinearLayout ID, если используется layout с LinearLayout
                    binding.layoutCurrentBudgetSection?.isVisible = shouldShowCurrentBudget
                    binding.layoutNoBudgetSection?.isVisible = shouldShowNoBudget

                    // Обновляем данные прогресса, только если секция видима
                    if (shouldShowCurrentBudget) {
                        binding.tvCurrentBudgetName.text = state.currentBudgetName ?: ""
                        Log.d("BudgetFrag_Update", "Submitting progress list: ${state.currentBudgetProgress.size} items")
                        budgetProgressAdapter.submitList(state.currentBudgetProgress)
                    }

                    // --- Отображение секции Истории Бюджетов ---
                    val hasHistoryData = state.allBudgets.isNotEmpty()
                    // Секция истории (заголовок и разделитель) видна, если загрузка завершена
                    val showHistorySection = !state.isLoadingAllBudgets

                    // Видимость заголовка и разделителя
                    binding.divider.isVisible = showHistorySection
                    binding.tvBudgetHistoryLabel.isVisible = showHistorySection
                    // Видимость контейнера истории (если используется LinearLayout)
                    binding.layoutHistorySection?.isVisible = showHistorySection

                    // Видимость списка ИЛИ заглушки "Нет истории" внутри секции
                    binding.rvBudgetHistory.isVisible = showHistorySection && hasHistoryData
                    binding.tvNoBudgetHistory.isVisible = showHistorySection && !hasHistoryData

                    // Обновляем данные адаптера истории, только если список должен быть видим
                    if (binding.rvBudgetHistory.isVisible) {
                        Log.d("BudgetFrag_Update", "Submitting history list: ${state.allBudgets.size} items")
                        budgetHistoryAdapter.submitList(state.allBudgets)
                    } else {
                        Log.d("BudgetFrag_Update", "History RecyclerView is not visible or empty.")
                    }

                    // --- Обработка сообщений и ошибок ---
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


    /** Прикрепляет обработчик свайпа для удаления к RecyclerView истории. */
    private fun attachSwipeToDelete(recyclerView: RecyclerView, adapter: BudgetHistoryAdapter) {
        val swipeHandler = object : SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition // Используем adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val budgetToDelete = adapter.currentList[position]
                    showDeleteConfirmationDialog(budgetToDelete) { confirmed ->
                        if (!confirmed) {
                            // Восстанавливаем вид элемента, если удаление отменено
                            adapter.notifyItemChanged(position)
                        }
                        // Если подтверждено, ViewModel удалит, и Flow обновит список
                    }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    /** Показывает диалог подтверждения удаления бюджета. */
    private fun showDeleteConfirmationDialog(budget: BudgetEntity, callback: ((Boolean) -> Unit)? = null) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(getString(R.string.delete_budget_confirmation_title))
            .setMessage(getString(R.string.delete_budget_confirmation_message, budget.name))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                callback?.invoke(false) // Сообщаем об отмене (для свайпа)
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                viewModel.deleteBudget(budget) // Вызываем удаление
                dialog.dismiss()
                callback?.invoke(true) // Сообщаем об удалении (для свайпа)
            }
            .setOnCancelListener { // Если закрыли диалог иначе (например, кнопкой назад)
                callback?.invoke(false)
            }
            .show()
    }

    /** Обрабатывает ошибки навигации. */
    private fun handleNavigationError(e: Exception, destinationContext: String) {
        Log.e("BudgetFrag", "Navigation failed to $destinationContext", e)
        showSnackbar(getString(R.string.error_navigation_failed_details, destinationContext), true)
    }

    /** Показывает Snackbar. */
    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (_binding == null) return // Не показывать, если view уже уничтожен
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.design_default_color_on_error))
        }
        snackbar.show()
    }

    /** Очищает ресурсы при уничтожении View. */
    override fun onDestroyView() {
        super.onDestroyView()
        // Важно обнулять адаптеры у RecyclerView для избежания утечек памяти
        binding.rvBudgetProgress.adapter = null
        binding.rvBudgetHistory.adapter = null
        _binding = null // Очищаем ссылку на биндинг
        Log.d("BudgetFrag", "onDestroyView called")
    }
}