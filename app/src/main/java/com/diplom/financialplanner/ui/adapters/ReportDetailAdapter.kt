package com.diplom.financialplanner.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.databinding.ItemReportCategoryDetailBinding
import com.diplom.financialplanner.util.getResId // Используем вашу утилиту
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

// Модель данных для элемента списка деталей отчета
data class ReportDetailItem(
    val categoryId: Long,
    val categoryName: String?,
    val categoryIconResName: String?,
    val categoryColorHex: String?,
    val currentAmount: Double,
    val transactionCount: Int? = null, // Количество операций
    val percentage: Float? = null,    // Процент от общей суммы
    val previousAmount: Double? = null, // Оставляем для будущего
    val totalAmount: Double? = null     // Оставляем для будущего
)

class ReportDetailAdapter : ListAdapter<ReportDetailItem, ReportDetailAdapter.ReportDetailViewHolder>(ReportDetailDiffCallback()) {


    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        currency = Currency.getInstance("RUB"); maximumFractionDigits = 2 // Оставляем копейки
    }
    // Форматтер для процентов
    private val numberPercentFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 1 // Один знак после запятой для процентов
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportDetailViewHolder {
        val binding = ItemReportCategoryDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportDetailViewHolder(binding, currencyFormatter, numberPercentFormatter)
    }

    override fun onBindViewHolder(holder: ReportDetailViewHolder, position: Int) {
        holder.bind(getItem(position)) // Передаем только item
    }

    class ReportDetailViewHolder(
        private val binding: ItemReportCategoryDetailBinding,
        private val currencyFormatter: NumberFormat,
        private val numberPercentFormatter: NumberFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ResourceType")
        fun bind(item: ReportDetailItem) {
            val context = binding.root.context
            binding.tvCategoryNameReport.text = item.categoryName ?: context.getString(R.string.category_unknown)

            // --- Иконка и Цвет Фона ---
            val colorInt = try {
                item.categoryColorHex?.let { Color.parseColor(it) }
                    ?: getThemeColor(context, com.google.android.material.R.attr.colorSecondary) // Fallback
            } catch (e: Exception) {
                getThemeColor(context, com.google.android.material.R.attr.colorSecondary)
            }

            // 1. Устанавливаем ЦВЕТ ФОНА для кружка
            (binding.ivCategoryIconReport.background as? GradientDrawable)?.setColor(colorInt)

            // 2. Получаем ИКОНКУ категории
            val iconDrawable: Drawable? = item.categoryIconResName?.let { iconName ->
                getResId(iconName, context).takeIf { it != 0 }?.let { ContextCompat.getDrawable(context, it)?.mutate() } // mutate() важен для tint
            } ?: ContextCompat.getDrawable(context, R.drawable.ic_category_other)?.mutate() // Иконка по умолчанию

            // 3. Устанавливаем ИКОНКУ в ImageView
            binding.ivCategoryIconReport.setImageDrawable(iconDrawable)

            // 4. Определяем КОНТРАСТНЫЙ ЦВЕТ для самой иконки (tint)
            // Используем ColorUtils.calculateLuminance для определения, светлый фон или темный
            val luminance = ColorUtils.calculateLuminance(colorInt)
            val iconTintColor = if (luminance > 0.5) { // Если фон светлый
                getThemeColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer) // Берем темный цвет текста/иконки на контейнере
            } else { // Если фон темный
                getThemeColor(context, com.google.android.material.R.attr.colorOnSecondary) // Берем светлый цвет текста/иконки на основном цвете (часто белый)
            }
            binding.ivCategoryIconReport.imageTintList = ColorStateList.valueOf(iconTintColor)
            // --- Конец Иконка и Цвет ---

            // --- Основная сумма ---
            val currentAmountFormatted = currencyFormatter.format(abs(item.currentAmount))
            val amountColorRes = if (item.currentAmount > 0.01) R.color.colorExpense
            else if (item.currentAmount < -0.01) R.color.colorIncome
            else com.google.android.material.R.attr.colorOnSurface
            val amountColor = if (amountColorRes == R.color.colorExpense || amountColorRes == R.color.colorIncome) {
                ContextCompat.getColor(context, amountColorRes)
            } else { getThemeColor(context, amountColorRes) }
            binding.tvCategoryAmountReport.setTextColor(amountColor)
            binding.tvCategoryAmountReport.text = currentAmountFormatted


            // --- Дополнительная информация: Количество и Процент ---
            val countText = item.transactionCount?.let {
                context.resources.getQuantityString(R.plurals.transaction_count, it, it)
            } ?: ""

            val percentageToShow = item.percentage // Получаем процент из Item
            val percentageText = if (percentageToShow != null && percentageToShow >= 0.1) {
                // Используем форматтер для чисел + знак %
                "${numberPercentFormatter.format(percentageToShow)}%"
            } else { "" }

            // Собираем строку
            val detailText = listOfNotNull(countText.takeIf { it.isNotEmpty() }, percentageText.takeIf { it.isNotEmpty() })
                .joinToString("  •  ") // Используем точку как разделитель

            binding.tvCategoryComparisonReport.text = detailText // Используем этот TextView
            binding.tvCategoryComparisonReport.setTextColor(getThemeColor(context, android.R.attr.textColorSecondary)) // Второстепенный цвет
            binding.tvCategoryComparisonReport.visibility = if (detailText.isNotEmpty()) View.VISIBLE else View.GONE
        }

        @ColorInt
        private fun getThemeColor(context: Context, @AttrRes themeAttrId: Int): Int {
            val typedValue = TypedValue()
            return if (context.theme.resolveAttribute(themeAttrId, typedValue, true)) {
                if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) { typedValue.data }
                else if (typedValue.type == TypedValue.TYPE_REFERENCE) {
                    try { ContextCompat.getColor(context, typedValue.resourceId) }
                    catch (e: Exception) { Log.w("ReportDetailVH", "Could not resolve theme attr $themeAttrId resource ID ${typedValue.resourceId} as color.", e); Color.GRAY }
                } else { Log.w("ReportDetailVH", "Theme attr $themeAttrId resolved to unexpected type: ${typedValue.type}"); Color.GRAY }
            } else { Log.w("ReportDetailVH", "Could not resolve theme attribute: $themeAttrId"); Color.GRAY }
        }
    }

    // DiffUtil Callback
    private class ReportDetailDiffCallback : DiffUtil.ItemCallback<ReportDetailItem>() {
        override fun areItemsTheSame(oldItem: ReportDetailItem, newItem: ReportDetailItem): Boolean {
            // Сравниваем по ID для стабильности списка
            return oldItem.categoryId == newItem.categoryId
        }
        @SuppressLint("DiffUtilEquals") // Подавляем предупреждение, т.к. data class сравнивает все поля
        override fun areContentsTheSame(oldItem: ReportDetailItem, newItem: ReportDetailItem): Boolean {
            // Сравниваем все содержимое для определения изменений
            return oldItem == newItem
        }
    }
}
