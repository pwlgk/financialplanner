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
import com.diplom.financialplanner.data.model.TransactionWithCategory
import com.diplom.financialplanner.databinding.ItemTransactionBinding
import com.diplom.financialplanner.util.getResId
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale

/**
 * Адаптер для отображения списка транзакций (расходов) в RecyclerView.
 * Использует ListAdapter для эффективного обновления списка.
 * @param onTransactionClick Лямбда-функция, вызываемая при клике на элемент списка.
 */
class TransactionAdapter(
    private val onTransactionClick: (TransactionWithCategory) -> Unit
) : ListAdapter<TransactionWithCategory, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    // Форматтеры лучше инициализировать один раз
    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        currency = Currency.getInstance("RUB")
    }
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    /** Создает новый ViewHolder. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding, onTransactionClick, currencyFormatter, dateFormatter)
    }

    /** Заполняет ViewHolder данными из элемента списка. */
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** ViewHolder для элемента списка транзакций. */
    class TransactionViewHolder(
        private val binding: ItemTransactionBinding,
        private val onTransactionClick: (TransactionWithCategory) -> Unit,
        private val currencyFormatter: NumberFormat,
        private val dateFormatter: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentTransactionWithCategory: TransactionWithCategory? = null

        init {
            // Устанавливаем слушатель клика на весь элемент
            binding.root.setOnClickListener {
                currentTransactionWithCategory?.let { twc ->
                    onTransactionClick(twc)
                }
            }
        }

        /** Связывает данные TransactionWithCategory с View элементами. */
        fun bind(twc: TransactionWithCategory) {
            currentTransactionWithCategory = twc
            val transaction = twc.transaction
            val category = twc.category
            val context = binding.root.context

            // Установка имени категории
            binding.tvCategoryName.text = category?.name ?: context.getString(R.string.category_not_found_placeholder)

            // Установка описания (если есть)
            binding.tvDescription.text = transaction.description
            binding.tvDescription.visibility = if (transaction.description.isNullOrBlank()) View.GONE else View.VISIBLE

            // Установка суммы (с минусом и красным цветом для расходов)
            val formattedAmount = currencyFormatter.format(transaction.amount)
            binding.tvAmount.text = context.getString(R.string.expense_amount_format, formattedAmount) // Используем форматтер строки "-%s"
            binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.colorExpense))

            // Установка даты
            binding.tvDate.text = dateFormatter.format(transaction.date)

            // Установка иконки и цвета категории
            val iconDrawable: Drawable? = category?.iconResName?.let { iconName ->
                val resId = getResId(iconName, R.drawable::class.java, context)
                if (resId != 0) ContextCompat.getDrawable(context, resId) else null
            } ?: ContextCompat.getDrawable(context, R.drawable.ic_category_other) // Иконка по умолчанию

            binding.ivCategoryIcon.setImageDrawable(iconDrawable)

            val colorInt = try {
                category?.colorHex?.let { Color.parseColor(it) } ?: ContextCompat.getColor(context, R.color.design_default_color_primary_variant) // Цвет по умолчанию
            } catch (e: IllegalArgumentException) {
                ContextCompat.getColor(context, R.color.design_default_color_primary_variant)
            }

            // Установка цвета фона кружка иконки
            (binding.ivCategoryIcon.background as? GradientDrawable)?.setColor(colorInt)

            // Установка цвета самой иконки (белый или черный для контраста)
            val luminance = Color.luminance(colorInt)
            val iconTintColor = if (luminance > 0.5) Color.BLACK else Color.WHITE
            binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(iconTintColor)
        }
    }

    /** DiffUtil Callback для расчета различий между списками. */
    private class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionWithCategory>() {
        override fun areItemsTheSame(oldItem: TransactionWithCategory, newItem: TransactionWithCategory): Boolean {
            return oldItem.transaction.id == newItem.transaction.id
        }
        override fun areContentsTheSame(oldItem: TransactionWithCategory, newItem: TransactionWithCategory): Boolean {
            return oldItem == newItem // Сравниваем все поля data class
        }
    }
}