package com.diplom.financialplanner.ui.expense

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.diplom.financialplanner.databinding.FragmentExpenseListBinding
import com.diplom.financialplanner.ui.adapters.TransactionAdapter
import com.diplom.financialplanner.util.SwipeToDeleteCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Фрагмент для отображения списка расходов.
 */
class ExpenseListFragment : Fragment() {

    private var _binding: FragmentExpenseListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpenseListViewModel by viewModels { ExpenseListViewModel.provideFactory() }
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    /** Настраивает RecyclerView и адаптер. */
    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter { transactionWithCategory ->
            // Клик по элементу -> переход к редактированию
            val transactionId = transactionWithCategory.transaction.id
            Log.d("ExpenseListFrag", "Navigating to edit expense ID: $transactionId")
            val action = ExpenseListFragmentDirections.actionNavigationExpensesToAddEditExpenseFragment(
                transactionId = transactionId,
                titleArg = getString(R.string.edit_expense_title)
            )
            try { findNavController().navigate(action) } catch (e: Exception) { handleNavigationError(e) }
        }

        binding.rvExpenses.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        // Добавляем Swipe-to-delete
        attachSwipeToDelete(binding.rvExpenses, transactionAdapter)
    }

    /** Прикрепляет обработчик свайпа для удаления к RecyclerView. */
    private fun attachSwipeToDelete(recyclerView: RecyclerView, adapter: TransactionAdapter) {
        val swipeHandler = object : SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val transactionWithCategory = adapter.currentList[position]
                    Log.d("ExpenseListFrag", "Swiped to delete expense: ID=${transactionWithCategory.transaction.id}")
                    showDeleteConfirmationDialog(transactionWithCategory.transaction) { confirmed ->
                        if (!confirmed) adapter.notifyItemChanged(position)
                    }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    /** Показывает диалог подтверждения удаления расхода. */
    private fun showDeleteConfirmationDialog(transaction: TransactionEntity, callback: (Boolean) -> Unit) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.delete_transaction_confirmation_title)
            .setMessage(R.string.delete_expense_confirmation_message)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss(); callback(false) }
            .setPositiveButton(R.string.delete) { dialog, _ ->
                viewModel.deleteExpense(transaction) // Вызываем удаление во ViewModel
                dialog.dismiss()
                callback(true)
            }
            .setOnCancelListener { callback(false) }
            .show()
    }


    /** Настраивает FAB для добавления нового расхода. */
    private fun setupFab() {
        binding.fabAddExpense.setOnClickListener {
            Log.d("ExpenseListFrag", "Navigating to add new expense")
            val action = ExpenseListFragmentDirections.actionNavigationExpensesToAddEditExpenseFragment(
                transactionId = 0L, // 0L для добавления
                titleArg = getString(R.string.add_expense_title)
            )
            try { findNavController().navigate(action) } catch (e: Exception) { handleNavigationError(e) }
        }
    }

    /** Наблюдает за ViewModel и обновляет UI. */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.isVisible = state.isLoading
                    binding.tvEmptyListPlaceholder.isVisible = !state.isLoading && state.expenses.isEmpty()
                    binding.rvExpenses.isVisible = !state.isLoading && state.expenses.isNotEmpty()

                    transactionAdapter.submitList(state.expenses)

                    // Показ сообщений
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

    /** Обрабатывает ошибки навигации. */
    private fun handleNavigationError(e: Exception) {
        Log.e("ExpenseListFrag", "Navigation failed", e)
        showSnackbar(getString(R.string.error_navigation_failed), true) // TODO: Добавить строку
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

    /** Очищает ресурсы. */
    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvExpenses.adapter = null
        _binding = null
    }
}