package com.diplom.financialplanner.ui.income

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
import com.diplom.financialplanner.databinding.FragmentIncomeListBinding // Используем биндинг для доходов
import com.diplom.financialplanner.ui.adapters.IncomeAdapter // Используем адаптер для доходов
import com.diplom.financialplanner.util.SwipeToDeleteCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Фрагмент для отображения списка доходов.
 */
class IncomeListFragment : Fragment() {

    private var _binding: FragmentIncomeListBinding? = null
    private val binding get() = _binding!!

    // Используем IncomeListViewModel
    private val viewModel: IncomeListViewModel by viewModels { IncomeListViewModel.provideFactory() }
    // Используем IncomeAdapter
    private lateinit var incomeAdapter: IncomeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIncomeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    /** Настраивает RecyclerView и IncomeAdapter. */
    private fun setupRecyclerView() {
        incomeAdapter = IncomeAdapter { transactionWithCategory ->
            // Клик по элементу -> переход к редактированию дохода
            val transactionId = transactionWithCategory.transaction.id
            Log.d("IncomeListFrag", "Navigating to edit income ID: $transactionId")
            // Используем action для навигации к AddEditIncomeFragment
            val action = IncomeListFragmentDirections.actionNavigationIncomeToAddEditIncomeFragment(
                transactionId = transactionId,
                titleArg = getString(R.string.edit_income_title)
            )
            try { findNavController().navigate(action) } catch (e: Exception) { handleNavigationError(e) }
        }

        binding.rvIncome.apply { // Используем ID из fragment_income_list.xml
            adapter = incomeAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        // Добавляем Swipe-to-delete
        attachSwipeToDelete(binding.rvIncome, incomeAdapter)
    }

    /** Прикрепляет обработчик свайпа для удаления к RecyclerView. */
    private fun attachSwipeToDelete(recyclerView: RecyclerView, adapter: IncomeAdapter) {
        val swipeHandler = object : SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val transactionWithCategory = adapter.currentList[position]
                    Log.d("IncomeListFrag", "Swiped to delete income: ID=${transactionWithCategory.transaction.id}")
                    // Показываем диалог подтверждения
                    showDeleteConfirmationDialog(transactionWithCategory.transaction) { confirmed ->
                        if (!confirmed) adapter.notifyItemChanged(position)
                    }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    /** Показывает диалог подтверждения удаления дохода. */
    private fun showDeleteConfirmationDialog(transaction: TransactionEntity, callback: (Boolean) -> Unit) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.delete_transaction_confirmation_title)
            .setMessage(R.string.delete_income_confirmation_message) // Сообщение для дохода
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss(); callback(false) }
            .setPositiveButton(R.string.delete) { dialog, _ ->
                viewModel.deleteIncome(transaction) // Вызываем метод удаления для доходов
                dialog.dismiss()
                callback(true)
            }
            .setOnCancelListener { callback(false) }
            .show()
    }

    /** Настраивает FAB для добавления нового дохода. */
    private fun setupFab() {
        binding.fabAddIncome.setOnClickListener { // Используем ID из fragment_income_list.xml
            Log.d("IncomeListFrag", "Navigating to add new income")
            // Переход к AddEditIncomeFragment
            val action = IncomeListFragmentDirections.actionNavigationIncomeToAddEditIncomeFragment(
                transactionId = 0L, // 0L для добавления
                titleArg = getString(R.string.add_income_title)
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
                    binding.tvEmptyListPlaceholder.isVisible = !state.isLoading && state.incomeList.isEmpty()
                    binding.rvIncome.isVisible = !state.isLoading && state.incomeList.isNotEmpty()

                    incomeAdapter.submitList(state.incomeList) // Обновляем список доходов

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
        Log.e("IncomeListFrag", "Navigation failed", e)
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
        binding.rvIncome.adapter = null // Очищаем адаптер
        _binding = null
    }
}