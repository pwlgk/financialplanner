package com.diplom.financialplanner.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.data.database.entity.BudgetEntity
import com.diplom.financialplanner.databinding.ItemBudgetHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Адаптер для отображения списка истории бюджетов.
 * @param onBudgetClick Лямбда для обработки клика по бюджету (переход к редактированию).
 * @param onBudgetLongClick Лямбда для обработки долгого нажатия (вызов удаления).
 */
class BudgetHistoryAdapter(
    private val onBudgetClick: (Long) -> Unit,
    private val onBudgetLongClick: (BudgetEntity) -> Unit
) : ListAdapter<BudgetEntity, BudgetHistoryAdapter.BudgetHistoryViewHolder>(BudgetDiffCallback()) {

    private val periodFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetHistoryViewHolder {
        val binding =
            ItemBudgetHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BudgetHistoryViewHolder(binding, periodFormatter, onBudgetClick, onBudgetLongClick)
    }

    override fun onBindViewHolder(holder: BudgetHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** ViewHolder для элемента истории бюджетов. */
    class BudgetHistoryViewHolder(
        private val binding: ItemBudgetHistoryBinding,
        private val formatter: SimpleDateFormat,
        private val onBudgetClick: (Long) -> Unit,
        private val onBudgetLongClick: (BudgetEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentBudget: BudgetEntity? = null

        init {
            // Обычный клик для редактирования
            binding.root.setOnClickListener {
                currentBudget?.let { budget ->
                    onBudgetClick(budget.id)
                }
            }
            // Долгое нажатие для удаления
            binding.root.setOnLongClickListener {
                currentBudget?.let { budget ->
                    onBudgetLongClick(budget)
                    true // Событие обработано
                } ?: false
            }
        }

        /** Связывает данные BudgetEntity с View элементами. */
        fun bind(budget: BudgetEntity) {
            currentBudget = budget
            binding.tvBudgetHistoryName.text = budget.name
            val startDateFormatted = formatter.format(budget.startDate)
            val endDateFormatted = formatter.format(budget.endDate)
            binding.tvBudgetHistoryPeriod.text = "$startDateFormatted - $endDateFormatted"
        }
    }

    /** DiffUtil Callback для расчета различий. */
    private class BudgetDiffCallback : DiffUtil.ItemCallback<BudgetEntity>() {
        override fun areItemsTheSame(oldItem: BudgetEntity, newItem: BudgetEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BudgetEntity, newItem: BudgetEntity): Boolean {
            return oldItem == newItem
        }
    }
}