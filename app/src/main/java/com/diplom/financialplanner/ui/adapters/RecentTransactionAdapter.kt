package com.diplom.financialplanner.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.TransactionType
import com.diplom.financialplanner.data.model.TransactionWithCategory
import com.diplom.financialplanner.databinding.ItemTransactionBinding
import com.diplom.financialplanner.util.getResId
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale

/**
 * Адаптер для отображения списка недавних транзакций (доходов и расходов) на главном экране.
 * @param onTransactionClick Лямбда для обработки клика.
 */
class RecentTransactionAdapter(
    private val onTransactionClick: (TransactionWithCategory) -> Unit
) : ListAdapter<TransactionWithCategory, RecentTransactionAdapter.RecentTransactionViewHolder>(TransactionDiffCallback()) {

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        currency = Currency.getInstance("RUB")
    }
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentTransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecentTransactionViewHolder(binding, onTransactionClick, currencyFormatter, dateFormatter)
    }

    override fun onBindViewHolder(holder: RecentTransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RecentTransactionViewHolder(
        private val binding: ItemTransactionBinding,
        private val onTransactionClick: (TransactionWithCategory) -> Unit,
        private val currencyFormatter: NumberFormat,
        private val dateFormatter: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentTransactionWithCategory: TransactionWithCategory? = null

        init {
            binding.root.setOnClickListener {
                currentTransactionWithCategory?.let { twc ->
                    onTransactionClick(twc)
                }
            }
        }

        fun bind(twc: TransactionWithCategory) {
            currentTransactionWithCategory = twc
            val transaction = twc.transaction
            val category = twc.category
            val context = binding.root.context

            // Категория Имя
            binding.tvCategoryName.text = category?.name ?: context.getString(R.string.category_not_found_placeholder)

            // Описание
            binding.tvDescription.text = transaction.description
            binding.tvDescription.visibility = if (transaction.description.isNullOrBlank()) View.GONE else View.VISIBLE

            // Сумма (форматируем, знак и цвет зависят от типа)
            val formattedAmount = currencyFormatter.format(transaction.amount)
            if (transaction.type == TransactionType.INCOME) {
                binding.tvAmount.text = context.getString(R.string.income_amount_format, formattedAmount) // Используем форматтер "+%s"
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.colorIncome))
            } else {
                binding.tvAmount.text = context.getString(R.string.expense_amount_format, formattedAmount) // Используем форматтер "-%s"
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.colorExpense))
            }

            // Дата
            binding.tvDate.text = dateFormatter.format(transaction.date)

            // Иконка и цвет категории
            val iconDrawable: Drawable? = category?.iconResName?.let { iconName ->
                val resId = getResId(iconName, R.drawable::class.java, context)
                if (resId != 0) ContextCompat.getDrawable(context, resId) else null
            } ?: ContextCompat.getDrawable(context, R.drawable.ic_category_other)

            binding.ivCategoryIcon.setImageDrawable(iconDrawable)

            val colorInt = try {
                category?.colorHex?.let { Color.parseColor(it) } ?: ContextCompat.getColor(context, R.color.design_default_color_primary_variant)
            } catch (e: IllegalArgumentException) {
                ContextCompat.getColor(context, R.color.design_default_color_primary_variant)
            }

            (binding.ivCategoryIcon.background as? GradientDrawable)?.setColor(colorInt)

            val luminance = Color.luminance(colorInt)
            val iconTintColor = if (luminance > 0.5) Color.BLACK else Color.WHITE
            binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(iconTintColor)
        }
    }

    // DiffCallback тот же
    private class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionWithCategory>() {
        override fun areItemsTheSame(oldItem: TransactionWithCategory, newItem: TransactionWithCategory): Boolean {
            return oldItem.transaction.id == newItem.transaction.id
        }
        override fun areContentsTheSame(oldItem: TransactionWithCategory, newItem: TransactionWithCategory): Boolean {
            return oldItem == newItem
        }
    }
}