package com.diplom.financialplanner.ui.budget

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible // Важно использовать этот импорт
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
import com.diplom.financialplanner.databinding.FragmentBudgetBinding
import com.diplom.financialplanner.ui.adapters.BudgetHistoryAdapter
import com.diplom.financialplanner.ui.adapters.BudgetProgressAdapter
import com.diplom.financialplanner.util.SwipeToDeleteCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Фрагмент для отображения информации о бюджетах:
 * - Прогресс выполнения текущего активного бюджета.
 * - История созданных бюджетов.
 */
class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BudgetViewModel by viewModels { BudgetViewModel.provideFactory() }
    private lateinit var budgetProgressAdapter: BudgetProgressAdapter
    private lateinit var budgetHistoryAdapter: BudgetHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupFab()
        observeViewModel()
    }

    /** Настраивает RecyclerView для прогресса и истории бюджетов. */
    private fun setupRecyclerViews() {
        // Адаптер для прогресса текущего бюджета
        budgetProgressAdapter = BudgetProgressAdapter()
        binding.rvBudgetProgress.apply {
            adapter = budgetProgressAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }

        // Адаптер для истории бюджетов
        budgetHistoryAdapter = BudgetHistoryAdapter(
            onBudgetClick = { budgetId ->
                Log.d("BudgetFrag", "Navigate to edit budget ID: $budgetId")
                val action = BudgetFragmentDirections.actionNavigationBudgetToBudgetSetupFragment(budgetId)
                try { findNavController().navigate(action) } catch (e: Exception) { handleNavigationError(e, getString(R.string.budget_setup_title))}
            },
            onBudgetLongClick = { budget ->
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
            val action = BudgetFragmentDirections.actionNavigationBudgetToBudgetSetupFragment(0L)
            try { findNavController().navigate(action) } catch (e: Exception) { handleNavigationError(e, getString(R.string.add_budget))}
        }
    }

    /** Наблюдает за ViewModel и обновляет UI в соответствии с состоянием. */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d("BudgetFrag", "Observing state: isLoadingProgress=${state.isLoadingProgress}, isLoadingAll=${state.isLoadingAllBudgets}, currentExists=${state.isCurrentBudgetExists}, historyCount=${state.allBudgets.size}")

                    // --- Управление видимостью индикаторов загрузки ---
                    // Показываем ProgressBar для текущего бюджета, только если он грузится
                    binding.progressBarBudget.isVisible = state.isLoadingProgress
                    // Показываем ProgressBar для истории, только если она грузится
                    binding.progressBarHistory.isVisible = state.isLoadingAllBudgets

                    // --- Отображение секции Текущего Бюджета ИЛИ Заглушки ---
                    // Показываем секцию "Текущий бюджет", если не идет загрузка и бюджет есть
                    binding.layoutCurrentBudgetSection.isVisible = !state.isLoadingProgress && state.isCurrentBudgetExists
                    // Показываем секцию "Нет бюджета", если не идет загрузка и бюджета нет
                    binding.layoutNoBudgetSection.isVisible = !state.isLoadingProgress && !state.isCurrentBudgetExists

                    // Обновляем данные прогресса, если секция видима
                    if (binding.layoutCurrentBudgetSection.isVisible) {
                        binding.tvCurrentBudgetName.text = state.currentBudgetName ?: ""
                        budgetProgressAdapter.submitList(state.currentBudgetProgress)
                    }

                    // --- Отображение секции Истории Бюджетов ---
                    val hasHistoryData = state.allBudgets.isNotEmpty()
                    // Показываем всю секцию истории, как только загрузка завершена
                    binding.layoutHistorySection.isVisible = !state.isLoadingAllBudgets

                    // Внутри секции истории управляем видимостью списка и заглушки
                    if (binding.layoutHistorySection.isVisible) {
                        binding.rvBudgetHistory.isVisible = hasHistoryData
                        binding.tvNoBudgetHistory.isVisible = !hasHistoryData
                        // Обновляем данные истории, если список видим
                        if (hasHistoryData) {
                            budgetHistoryAdapter.submitList(state.allBudgets)
                        }
                    } else {
                        // Если вся секция истории скрыта (из-за загрузки), скрываем и ее содержимое
                        binding.rvBudgetHistory.isVisible = false
                        binding.tvNoBudgetHistory.isVisible = false
                    }

                    // Разделитель виден всегда, когда секция истории видима (т.е. загрузка завершена)
                    binding.divider.isVisible = !state.isLoadingAllBudgets

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
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val budgetToDelete = adapter.currentList[position]
                    showDeleteConfirmationDialog(budgetToDelete) { confirmed ->
                        if (!confirmed) {
                            adapter.notifyItemChanged(position)
                        }
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
                callback?.invoke(false)
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                viewModel.deleteBudget(budget)
                dialog.dismiss()
                callback?.invoke(true)
            }
            .setOnCancelListener { callback?.invoke(false) }
            .show()
    }

    /** Обрабатывает ошибки навигации. */
    private fun handleNavigationError(e: Exception, destinationContext: String) {
        Log.e("BudgetFrag", "Navigation failed to $destinationContext", e)
        showSnackbar(getString(R.string.error_navigation_failed_details, destinationContext), true)
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

    /** Очищает ресурсы при уничтожении View. */
    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvBudgetProgress.adapter = null
        binding.rvBudgetHistory.adapter = null
        _binding = null
        Log.d("BudgetFrag", "onDestroyView called")
    }
}