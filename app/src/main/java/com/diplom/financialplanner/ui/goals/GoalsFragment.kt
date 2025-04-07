package com.diplom.financialplanner.ui.goals

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.FinancialGoalEntity
import com.diplom.financialplanner.databinding.DialogAddEditGoalBinding // ViewBinding для диалога
import com.diplom.financialplanner.databinding.FragmentGoalsBinding
import com.diplom.financialplanner.ui.adapters.GoalAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Фрагмент для отображения и управления списком финансовых целей.
 */
class GoalsFragment : Fragment() {

    private var _binding: FragmentGoalsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GoalsViewModel by viewModels { GoalsViewModel.provideFactory() }
    private lateinit var goalAdapter: GoalAdapter
    // Храним ссылку на активный диалог для его закрытия
    private var currentDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGoalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeViewModel()
        observeInputStateForDialog() // Наблюдаем за успехом сохранения для закрытия диалога
    }

    /** Настраивает RecyclerView и адаптер для списка целей. */
    private fun setupRecyclerView() {
        goalAdapter = GoalAdapter(
            onEditClick = { goal ->
                // Клик по карточке - открываем диалог редактирования цели
                viewModel.prepareEditGoal(goal)
                showAddEditGoalDialog()
            },
            onEditAmountClick = { goal ->
                // Клик по иконке - открываем диалог редактирования суммы
                viewModel.prepareEditAmount(goal)
                showUpdateAmountDialog(goal)
            }
        )
        binding.rvGoals.apply {
            adapter = goalAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        // Добавляем Swipe-to-delete
        attachSwipeToDelete()
    }

    /** Настраивает Swipe-to-delete для RecyclerView. */
    private fun attachSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Разрешаем свайп влево и вправо
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false // Перемещение не поддерживается
            }

            // Вызывается при завершении свайпа
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition // Получаем позицию элемента
                if (position != RecyclerView.NO_POSITION) {
                    val goalToDelete = goalAdapter.currentList[position] // Получаем цель
                    // Показываем диалог подтверждения
                    showDeleteConfirmationDialog(goalToDelete) { confirmed ->
                        if (!confirmed) {
                            // Если пользователь отменил удаление,
                            // уведомляем адаптер, чтобы элемент вернулся на место
                            goalAdapter.notifyItemChanged(position)
                        }
                        // Если подтвердил, ViewModel обработает удаление, список обновится сам
                    }
                }
            }
        }
        // Прикрепляем хелпер к RecyclerView
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvGoals)
    }


    /** Настраивает FloatingActionButton для добавления новой цели. */
    private fun setupFab() {
        binding.fabAddGoal.setOnClickListener {
            viewModel.prepareNewGoal() // Готовим ViewModel
            showAddEditGoalDialog()    // Показываем диалог добавления
        }
    }

    /** Показывает диалог для добавления или редактирования цели. */
    private fun showAddEditGoalDialog() {
        // Проверяем, не открыт ли уже диалог
        if (currentDialog?.isShowing == true) {
            return
        }
        val dialogBinding = DialogAddEditGoalBinding.inflate(layoutInflater)
        val inputState = viewModel.goalInputState.value // Текущее состояние ввода
        val isEditing = inputState.goalId != null

        // Предзаполняем поля
        dialogBinding.etGoalName.setText(inputState.name)
        dialogBinding.etGoalTargetAmount.setText(inputState.targetAmount)
        // Предзаполняем текущую сумму (важно для редактирования)
        dialogBinding.etGoalCurrentAmount.setText(inputState.currentAmount)


        // Видимость полей: Показываем все для редактирования, кроме текущей суммы для новой цели
        dialogBinding.tilGoalName.visibility = View.VISIBLE
        dialogBinding.tilGoalTargetAmount.visibility = View.VISIBLE
        // Поле текущей суммы показываем только при редактировании цели
        dialogBinding.tilGoalCurrentAmount.visibility = if(isEditing) View.VISIBLE else View.GONE
        dialogBinding.tilGoalCurrentAmount.hint = getString(R.string.current_goal_amount) // Устанавливаем хинт


        // Настраиваем listeners для обновления ViewModel при вводе
        dialogBinding.etGoalName.doOnTextChanged { text, _, _, _ -> viewModel.updateGoalName(text.toString()) }
        dialogBinding.etGoalTargetAmount.doOnTextChanged { text, _, _, _ -> viewModel.updateTargetAmount(text.toString()) }
        // Обновляем и текущую сумму, если поле видимо
        if (isEditing) {
            dialogBinding.etGoalCurrentAmount.doOnTextChanged { text, _, _, _ -> viewModel.updateCurrentAmountInput(text.toString()) }
        }


        // Строим и показываем диалог
        currentDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEditing) R.string.edit_goal_dialog_title else R.string.add_goal_dialog_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null) // Закрыть диалог
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.saveGoal() // Вызываем сохранение цели
            }
            // Добавляем кнопку удаления, только если это редактирование
            .apply {
                if (isEditing) {
                    setNeutralButton(R.string.delete) { _, _ ->
                        inputState.goalId?.let { id ->
                            // Находим цель для удаления
                            viewModel.uiState.value.goals.find { it.id == id }?.let { goalToDelete ->
                                showDeleteConfirmationDialog(goalToDelete) // Показываем подтверждение
                            }
                        }
                    }
                }
            }
            .setOnDismissListener { currentDialog = null } // Очищаем ссылку при закрытии
            .show()
    }

    /** Показывает диалог для обновления ТОЛЬКО текущей накопленной суммы. */
    private fun showUpdateAmountDialog(goal: FinancialGoalEntity) {
        if (currentDialog?.isShowing == true) {
            return
        }
        val dialogBinding = DialogAddEditGoalBinding.inflate(layoutInflater)
        val inputState = viewModel.goalInputState.value // Состояние должно быть подготовлено

        // Предзаполняем поле текущей суммы
        dialogBinding.etGoalCurrentAmount.setText(inputState.currentAmount)

        // Показываем только поле текущей суммы
        dialogBinding.tilGoalName.visibility = View.GONE
        dialogBinding.tilGoalTargetAmount.visibility = View.GONE
        dialogBinding.tilGoalCurrentAmount.visibility = View.VISIBLE
        dialogBinding.tilGoalCurrentAmount.hint = getString(R.string.current_goal_amount) // Используем стандартный хинт

        // Listener только для текущей суммы
        dialogBinding.etGoalCurrentAmount.doOnTextChanged { text, _, _, _ -> viewModel.updateCurrentAmountInput(text.toString()) }

        currentDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.update_goal_amount_title))
            .setMessage(goal.name) // Показываем имя цели
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.update) { _, _ ->
                viewModel.saveCurrentAmount() // Вызываем специальный метод сохранения суммы
            }
            .setOnDismissListener { currentDialog = null }
            .show()
    }


    /** Показывает диалог подтверждения удаления цели. */
    private fun showDeleteConfirmationDialog(goal: FinancialGoalEntity, swipeCallback: ((Boolean) -> Unit)? = null) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_goal_confirmation_title))
            .setMessage(getString(R.string.delete_goal_confirmation_message, goal.name))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                swipeCallback?.invoke(false) // Уведомляем callback об отмене
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                viewModel.deleteGoal(goal) // Вызываем удаление во ViewModel
                dialog.dismiss()
                swipeCallback?.invoke(true) // Уведомляем callback об удалении
                // Сообщение об успехе покажется через observeViewModel
            }
            .setOnCancelListener { swipeCallback?.invoke(false) } // Обработка закрытия диалога
            .show()
    }


    /** Наблюдает за основным состоянием UI (список, загрузка, сообщения). */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarGoals.isVisible = state.isLoading
                    binding.tvNoGoalsPlaceholder.isVisible = !state.isLoading && state.goals.isEmpty()
                    binding.rvGoals.isVisible = !state.isLoading && state.goals.isNotEmpty()

                    goalAdapter.submitList(state.goals)

                    // Показ Snackbar для сообщений и ошибок
                    state.userMessage?.let { message ->
                        // Пытаемся получить строку из ресурсов, если это ID
                        val messageText = try { getString(message.toInt()) } catch (e: NumberFormatException) { message }
                        showSnackbar(messageText)
                        viewModel.clearUserMessage()
                    }
                    state.errorMessage?.let { error ->
                        val errorText = try { getString(error.toInt()) } catch (e: NumberFormatException) { error }
                        showSnackbar(errorText, true)
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    /** Наблюдает за состоянием ввода (флаг успеха) для закрытия диалогов. */
    private fun observeInputStateForDialog() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.goalInputState
                    .map { it.saveSuccess } // Следим только за флагом успеха
                    .distinctUntilChanged()   // Реагируем только на изменение
                    .filter { it }            // Только когда стало true
                    .collect {
                        Log.d("GoalsFrag", "Goal save/update success detected, dismissing dialog.")
                        currentDialog?.dismiss() // Закрываем активный диалог
                        currentDialog = null
                        viewModel.consumeInputSuccess() // Сбрасываем флаг во ViewModel
                    }
            }
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

    /** Очищает ресурсы при уничтожении View. */
    override fun onDestroyView() {
        currentDialog?.dismiss() // Закрываем диалог, если он был открыт
        currentDialog = null
        binding.rvGoals.adapter = null // Очищаем адаптер
        _binding = null
        super.onDestroyView()
    }
}