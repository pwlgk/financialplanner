package com.diplom.financialplanner.ui.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.FinancialGoalEntity
import com.diplom.financialplanner.databinding.ItemGoalBinding
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Адаптер для отображения списка финансовых целей.
 * @param onEditClick Лямбда для обработки клика по карточке (редактирование цели).
 * @param onEditAmountClick Лямбда для обработки клика по кнопке (редактирование суммы).
 */
class GoalAdapter(
    private val onEditClick: (FinancialGoalEntity) -> Unit,
    private val onEditAmountClick: (FinancialGoalEntity) -> Unit
) : ListAdapter<FinancialGoalEntity, GoalAdapter.GoalViewHolder>(GoalDiffCallback()) {

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = 0 // Без копеек
        // currency = Currency.getInstance("RUB") // Можно указать валюту
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GoalViewHolder {
        val binding = ItemGoalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GoalViewHolder(binding, currencyFormatter, onEditClick, onEditAmountClick)
    }

    override fun onBindViewHolder(holder: GoalViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** ViewHolder для элемента списка целей. */
    class GoalViewHolder(
        private val binding: ItemGoalBinding,
        private val formatter: NumberFormat,
        private val onEditClick: (FinancialGoalEntity) -> Unit,
        private val onEditAmountClick: (FinancialGoalEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentGoal: FinancialGoalEntity? = null

        init {
            binding.root.setOnClickListener {
                currentGoal?.let { onEditClick(it) }
            }
            binding.btnEditGoalAmount.setOnClickListener{
                currentGoal?.let { onEditAmountClick(it) }
            }
        }

        /** Связывает данные FinancialGoalEntity с View элементами. */
        fun bind(goal: FinancialGoalEntity) {
            currentGoal = goal
            val context = binding.root.context
            binding.tvGoalName.text = goal.name

            val current = goal.currentAmount
            val target = goal.targetAmount

            // Расчет прогресса в процентах
            val progressPercent = if (target > 0) {
                ((current / target) * 100).coerceIn(0.0, 100.0).roundToInt()
            } else 0 // Если цель 0, прогресс 0

            binding.progressBarGoal.progress = progressPercent
            binding.tvGoalProgressPercent.text = context.getString(R.string.goal_percent_format, progressPercent) // Формат "%d%%"

            // Текст "Текущее / Цель"
            binding.tvGoalProgressText.text = context.getString(
                R.string.goal_progress_text_format,
                formatter.format(current),
                formatter.format(target)
            )

            // Цвет прогресс-бара и процента
            val achieved = current >= target && target > 0 // Цель достигнута, если текущее >= цели и цель > 0
            val progressColor = ContextCompat.getColor(context, if (achieved) R.color.colorIncome else R.color.colorPrimary) // Зеленый или основной цвет темы
            val percentColor = ContextCompat.getColor(context, if (achieved) R.color.colorIncome else R.color.design_default_color_primary) // Цвет для текста процента

            binding.progressBarGoal.progressTintList = ColorStateList.valueOf(progressColor)
            binding.tvGoalProgressPercent.setTextColor(percentColor)

            // Можно добавить визуальное подтверждение достижения цели
            // binding.tvGoalName.alpha = if (achieved) 0.7f else 1.0f // Например, сделать текст бледнее
        }
    }

    /** DiffUtil Callback для расчета различий. */
    private class GoalDiffCallback : DiffUtil.ItemCallback<FinancialGoalEntity>() {
        override fun areItemsTheSame(oldItem: FinancialGoalEntity, newItem: FinancialGoalEntity): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: FinancialGoalEntity, newItem: FinancialGoalEntity): Boolean {
            return oldItem == newItem
        }
    }
}