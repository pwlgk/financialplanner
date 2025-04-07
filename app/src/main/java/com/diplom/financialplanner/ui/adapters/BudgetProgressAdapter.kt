package com.diplom.financialplanner.ui.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.dao.BudgetCategoryProgress
import com.diplom.financialplanner.databinding.ItemBudgetProgressBinding
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Адаптер для отображения прогресса выполнения бюджета по категориям.
 */
class BudgetProgressAdapter : ListAdapter<BudgetCategoryProgress, BudgetProgressAdapter.BudgetProgressViewHolder>(BudgetProgressDiffCallback()) {

    // Форматтер для сумм (без копеек)
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        currency = Currency.getInstance("RUB")
        maximumFractionDigits = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetProgressViewHolder {
        val binding = ItemBudgetProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BudgetProgressViewHolder(binding, currencyFormatter)
    }

    override fun onBindViewHolder(holder: BudgetProgressViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** ViewHolder для элемента прогресса бюджета. */
    class BudgetProgressViewHolder(
        private val binding: ItemBudgetProgressBinding,
        private val formatter: NumberFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Связывает данные BudgetCategoryProgress с View элементами. */
        fun bind(item: BudgetCategoryProgress) {
            val context = binding.root.context
            binding.tvCategoryNameProgress.text = item.categoryName ?: context.getString(R.string.unknown_category)

            val spent = item.spentAmount
            val limit = item.limitAmount
            val formattedSpent = formatter.format(spent)
            val formattedLimit = formatter.format(limit)

            // Отображение "Потрачено / Лимит"
            binding.tvSpentVsLimit.text = context.getString(R.string.spent_vs_limit_format, formattedSpent, formattedLimit)

            // Расчет и установка прогресса ProgressBar (0-100)
            val progressPercent = if (limit > 0) {
                (spent / limit * 100).coerceIn(0.0, 100.0).toInt()
            } else {
                if (spent > 0) 100 else 0 // Если лимит 0, но есть траты, считаем 100% (превышение)
            }
            binding.progressBarCategory.progress = progressPercent

            // Определение и установка цвета ProgressBar
            val progressColor = when {
                limit > 0 && spent > limit -> ContextCompat.getColor(context, R.color.colorExpense) // Превышен
                limit == 0.0 && spent > 0 -> ContextCompat.getColor(context, R.color.colorExpense) // Превышен (лимит 0)
                progressPercent > 85 -> ContextCompat.getColor(context, R.color.colorWarning) // Близко к лимиту
                else -> ContextCompat.getColor(context, R.color.colorIncome) // Норма (используем цвет дохода)
            }
            binding.progressBarCategory.progressTintList = ColorStateList.valueOf(progressColor)

        }
    }

    /** DiffUtil Callback для расчета различий. */
    private class BudgetProgressDiffCallback : DiffUtil.ItemCallback<BudgetCategoryProgress>() {
        override fun areItemsTheSame(oldItem: BudgetCategoryProgress, newItem: BudgetCategoryProgress): Boolean {
            // ID категории уникален в рамках одного расчета прогресса
            return oldItem.categoryId == newItem.categoryId
        }
        override fun areContentsTheSame(oldItem: BudgetCategoryProgress, newItem: BudgetCategoryProgress): Boolean {
            return oldItem == newItem // Сравниваем все поля
        }
    }
}