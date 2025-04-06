package com.diplom.financialplanner.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout // Убедитесь, что не используется, если удалили updateConstraints
// import androidx.constraintlayout.widget.ConstraintSet // Больше не нужен
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import com.diplom.financialplanner.data.database.entity.TransactionEntity
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.databinding.FragmentDashboardBinding
import com.diplom.financialplanner.ui.adapters.RecentTransactionAdapter
import com.diplom.financialplanner.ui.main.MainActivity
import com.diplom.financialplanner.util.SwipeToDeleteCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Фрагмент главного экрана (Dashboard).
 * Отображает сводную финансовую информацию.
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels { DashboardViewModel.provideFactory() }
    private lateinit var recentTransactionAdapter: RecentTransactionAdapter

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        currency = Currency.getInstance("RUB")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        Log.d("DashboardFrag", "onCreateView called")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("DashboardFrag", "onViewCreated called")

        setupRecyclerView()
        setupQuickActionButtons()
        setupBudgetCardListeners()
        setupNavigationButtons() // Используем новый метод
        observeViewModel()
    }

    private fun setupRecyclerView() {
        recentTransactionAdapter = RecentTransactionAdapter { transactionWithCategory ->
            val transaction = transactionWithCategory.transaction
            Log.d("DashboardFrag", "Transaction clicked: ID=${transaction.id}, Type=${transaction.type}")
            val action = if (transaction.type == TransactionType.EXPENSE) {
                DashboardFragmentDirections.actionNavigationDashboardToAddEditExpenseFragment(
                    transactionId = transaction.id,
                    titleArg = getString(R.string.edit_expense_title)
                )
            } else {
                DashboardFragmentDirections.actionNavigationDashboardToAddEditIncomeFragment(
                    transactionId = transaction.id,
                    titleArg = getString(R.string.edit_income_title)
                )
            }
            try { findNavController().navigate(action) } catch (e: Exception) { handleNavigationError(e, "редактирование транзакции") } // Добавлен контекст
        }
        binding.rvRecentTransactions.apply {
            adapter = recentTransactionAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
        attachSwipeToDelete(binding.rvRecentTransactions, recentTransactionAdapter)
    }

    private fun attachSwipeToDelete(recyclerView: RecyclerView, adapter: RecentTransactionAdapter) {
        val swipeHandler = object : SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val transactionWithCategory = adapter.currentList[position]
                    Log.d("DashboardFrag", "Swiped to delete transaction: ID=${transactionWithCategory.transaction.id}")
                    showDeleteConfirmationDialog(transactionWithCategory.transaction) { confirmed ->
                        if (!confirmed) {
                            adapter.notifyItemChanged(position)
                        }
                    }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun showDeleteConfirmationDialog(transaction: TransactionEntity, callback: (Boolean) -> Unit) {
        val messageResId = if (transaction.type == TransactionType.EXPENSE)
            R.string.delete_expense_confirmation_message
        else
            R.string.delete_income_confirmation_message

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.delete_transaction_confirmation_title)
            .setMessage(getString(messageResId))
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss(); callback(false) }
            .setPositiveButton(R.string.delete) { dialog, _ ->
                viewModel.deleteRecentTransaction(transaction)
                dialog.dismiss()
                callback(true)
            }
            .setOnCancelListener { callback(false) }
            .show()
    }

    private fun setupQuickActionButtons() {
        binding.btnQuickAddExpense.setOnClickListener {
            try {
                val action = DashboardFragmentDirections.actionNavigationDashboardToAddEditExpenseFragment(
                    transactionId = 0L,
                    titleArg = getString(R.string.add_expense_title)
                )
                findNavController().navigate(action)
            } catch (e: Exception) { handleNavigationError(e, getString(R.string.add_expense)) } // Контекст
        }
        binding.btnQuickAddIncome.setOnClickListener {
            try {
                val action = DashboardFragmentDirections.actionNavigationDashboardToAddEditIncomeFragment(
                    transactionId = 0L,
                    titleArg = getString(R.string.add_income_title)
                )
                findNavController().navigate(action)
            } catch (e: Exception) { handleNavigationError(e, getString(R.string.add_income)) } // Контекст
        }
    }

    private fun setupBudgetCardListeners() {
        binding.cardBudgetSummary.setOnClickListener {
            navigateToBottomNavItem(R.id.navigation_budget)
        }
        binding.btnGoToBudgetSetup.setOnClickListener {
            try {
                val action = DashboardFragmentDirections.actionNavigationDashboardToBudgetSetupFragment(
                    budgetId = 0L
                )
                findNavController().navigate(action)
            } catch (e: Exception) { handleNavigationError(e, getString(R.string.budget_setup_title)) } // Контекст
        }
    }

    /** Настраивает кнопки навигации к Категориям и Целям. */
    private fun setupNavigationButtons() { // Переименованный метод
        binding.btnManageCategories.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_dashboard_to_categories)
            } catch (e: Exception) { handleNavigationError(e, getString(R.string.title_categories)) } // Контекст
        }
        // ДОБАВЛЕН ОБРАБОТЧИК ДЛЯ ЦЕЛЕЙ
        binding.btnGoToGoals.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_dashboard_to_goals) // Используем action ID
            } catch (e: Exception) { handleNavigationError(e, getString(R.string.title_goals)) } // Контекст
        }
    }

    /** Обрабатывает ошибки навигации, показывая Snackbar. */
    private fun handleNavigationError(e: Exception, destinationContext: String = "экран") {
        Log.e("DashboardFrag", "Navigation to $destinationContext failed", e)
        // Используем строку с подстановкой
        showSnackbar(getString(R.string.error_navigation_failed_details, destinationContext), true)
    }

    /** Переключает на указанную вкладку в BottomNavigationView. */
    private fun navigateToBottomNavItem(itemId: Int) {
        (activity as? MainActivity)?.navigateToBottomNavItem(itemId)
    }

    /** Наблюдает за ViewModel и обновляет UI. */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.v("DashboardFrag", "Updating UI with state: isLoading=${state.isLoading}, BudgetActive=${state.isBudgetActive}, Reco=${state.recommendationMessage}")
                    binding.progressBarDashboard.isVisible = state.isLoading

                    // Управляем видимостью основного контента
                    binding.mainContentLayout.isVisible = !state.isLoading

                    if (!state.isLoading) {
                        // Обновление баланса и расходов
                        binding.tvBalanceAmount.text = currencyFormatter.format(state.currentBalance)
                        binding.tvSummaryAmount.text = getString(R.string.expense_amount_format, currencyFormatter.format(state.monthlyExpenses))

                        // Обновление недавних транзакций
                        recentTransactionAdapter.submitList(state.recentTransactions)
                        // Используем ID LinearLayout, который содержит список и заглушку
                        binding.layoutRecentTransactions.isVisible = state.recentTransactions.isNotEmpty()
                        binding.tvNoRecentTransactions.isVisible = state.recentTransactions.isEmpty()

                        // Обновление карточки бюджета
                        updateBudgetCard(state)

                        // Обновление карточки рекомендации
                        updateRecommendationCard(state)

                        // Отображение ошибок
                        state.errorMessage?.let {
                            val errorText = try { getString(it.toInt()) } catch (e: Exception) { it }
                            showSnackbar(errorText, true)
                            viewModel.clearErrorMessage()
                        }
                    }
                }
            }
        }
    }

    /** Обновляет UI карточки сводки по бюджету. */
    private fun updateBudgetCard(state: DashboardUiState) {
        binding.cardBudgetSummary.isVisible = state.isBudgetActive
        binding.cardNoBudget.isVisible = !state.isBudgetActive

        if (state.isBudgetActive) {
            binding.tvBudgetSummaryName.text = state.budgetName ?: getString(R.string.current_budget_status)
            binding.tvBudgetSummarySpentLimit.text = getString(
                R.string.spent_vs_limit_format,
                currencyFormatter.format(state.budgetTotalSpent),
                currencyFormatter.format(state.budgetTotalLimit)
            )
            binding.progressBarBudgetOverall.progress = state.budgetProgressPercent

            val progressColorRes = when {
                state.budgetTotalSpent > state.budgetTotalLimit && state.budgetTotalLimit > 0 -> R.color.colorExpense
                state.budgetProgressPercent > 85 -> R.color.colorWarning
                else -> R.color.design_default_color_primary // Используем основной цвет по умолчанию
            }
            binding.progressBarBudgetOverall.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), progressColorRes)
            )

            if (state.overspentCategoriesCount > 0) {
                binding.tvBudgetSummaryOverspent.text = getString(R.string.overspent_categories_format, state.overspentCategoriesCount)
                binding.tvBudgetSummaryOverspent.visibility = View.VISIBLE
            } else {
                binding.tvBudgetSummaryOverspent.visibility = View.GONE
            }
        }
    }

    /** Обновляет UI карточки рекомендации. */
    private fun updateRecommendationCard(state: DashboardUiState) {
        binding.cardRecommendation.isVisible = state.recommendationMessage != null
        binding.tvRecommendationMessage.text = state.recommendationMessage ?: ""
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
        binding.rvRecentTransactions.adapter = null
        _binding = null
        Log.d("DashboardFrag", "onDestroyView called")
    }
}