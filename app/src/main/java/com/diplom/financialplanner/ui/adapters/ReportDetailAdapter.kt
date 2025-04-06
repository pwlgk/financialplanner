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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.databinding.ItemReportCategoryDetailBinding
import com.diplom.financialplanner.ui.reports.CategoryComparisonData // Убедитесь, что импорт есть
//import com.diplom.financialplanner.util.getResId // Убрали, используем встроенную версию
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Модель данных ReportDetailItem (без изменений)
data class ReportDetailItem(
    val categoryId: Long,
    val categoryName: String?,
    val categoryIconResName: String?,
    val categoryColorHex: String?,
    val currentAmount: Double,
    val percentage: Float? = null, // Оставляем Float? как было изначально
    val previousAmount: Double? = null,
    val totalAmount: Double? = null
)

class ReportDetailAdapter : ListAdapter<ReportDetailItem, ReportDetailAdapter.ReportDetailViewHolder>(ReportDetailDiffCallback()) {

    enum class Mode { TOP_SPENDING, COMPARISON }
    private var currentMode: Mode = Mode.TOP_SPENDING

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("ru", "RU")).apply {
        currency = Currency.getInstance("RUB"); maximumFractionDigits = 0
    }
    private val percentFormatter: NumberFormat = NumberFormat.getPercentInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 1
    }
    private val numberPercentFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 1
    }

    fun setMode(mode: Mode) { if (currentMode != mode) { currentMode = mode } }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportDetailViewHolder {
        val binding = ItemReportCategoryDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportDetailViewHolder(binding, currencyFormatter, numberPercentFormatter)
    }

    override fun onBindViewHolder(holder: ReportDetailViewHolder, position: Int) {
        holder.bind(getItem(position), currentMode)
    }

    class ReportDetailViewHolder(
        private val binding: ItemReportCategoryDetailBinding,
        private val currencyFormatter: NumberFormat,
        private val numberPercentFormatter: NumberFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ResourceType")
        fun bind(item: ReportDetailItem, mode: Mode) {
            val context = binding.root.context
            binding.tvCategoryNameReport.text = item.categoryName ?: context.getString(R.string.unknown_category)

            // --- Иконка и цвет ---
            val iconDrawable: Drawable? = item.categoryIconResName?.let { iconName ->
                getResId(iconName, context).takeIf { it != 0 }?.let { ContextCompat.getDrawable(context, it) }
            } ?: ContextCompat.getDrawable(context, R.drawable.ic_category_other)
            binding.ivCategoryIconReport.setImageDrawable(iconDrawable)
            val colorInt = try { item.categoryColorHex?.let { Color.parseColor(it) } ?: getThemeColor(context, R.color.light_onPrimary) } catch (e: Exception) { getThemeColor(context, R.color.light_onPrimary) } // Используем атрибут темы
            (binding.ivCategoryIconReport.background as? GradientDrawable)?.setColor(colorInt)
            val iconTintColor = if (Color.luminance(colorInt) > 0.5) Color.BLACK else Color.WHITE
            binding.ivCategoryIconReport.imageTintList = ColorStateList.valueOf(iconTintColor)
            // --- Конец Иконка и цвет ---

            // --- Основная сумма ---
            val currentAmountFormatted = currencyFormatter.format(abs(item.currentAmount))
            var amountColor = getThemeColor(context, R.color.black) // Цвет текста по умолчанию из темы

            // Определяем цвет суммы для РАСХОДОВ (предполагаем, что currentAmount > 0 для расходов)
            if (item.currentAmount > 0 && (mode == Mode.TOP_SPENDING || (mode == Mode.COMPARISON && (item.previousAmount==null || item.currentAmount >= item.previousAmount)))) {
                amountColor = ContextCompat.getColor(context, R.color.colorExpense)
            }
            // Определяем цвет суммы для ДОХОДОВ (предполагаем, что currentAmount < 0 для доходов)
            // else if (item.currentAmount < 0) {
            //    amountColor = ContextCompat.getColor(context, R.color.colorIncome)
            // }

            binding.tvCategoryAmountReport.setTextColor(amountColor)
            binding.tvCategoryAmountReport.text = currentAmountFormatted

            // --- Дополнительная информация ---
            var comparisonText = ""
            var comparisonTextColor = getThemeColor(context, android.R.attr.textColorSecondary)

            binding.tvCategoryComparisonReport.visibility = View.VISIBLE // Показываем по умолчанию

            when (mode) {
                Mode.TOP_SPENDING -> {
                    // Рассчитываем процент как Double?
                    val percentageToShow: Double? = item.percentage?.toDouble()
                        ?: item.totalAmount?.takeIf { it > 0 }?.let { total ->
                            (abs(item.currentAmount) / total * 100)
                        }

                    // --- ИСПРАВЛЕННОЕ СРАВНЕНИЕ ---
                    val shouldShowPercentage = percentageToShow != null && percentageToShow >= 0.1

                    if (shouldShowPercentage) {
                        comparisonText = "${numberPercentFormatter.format(percentageToShow)}% ${context.getString(R.string.report_of_total)}"
                        // Цвет текста оставляем по умолчанию (второстепенный)
                    } else {
                        comparisonText = "" // Скрываем, если нечего показывать
                    }
                }
                Mode.COMPARISON -> {
                    if (item.previousAmount != null) {
                        val diff = item.currentAmount - item.previousAmount
                        val diffFormatted = currencyFormatter.format(abs(diff))
                        val diffSign = if (diff > 0.01) "+" else if (diff < -0.01) "-" else ""
                        val diffColorResId = when {
                            diff > 0.01 -> R.color.colorExpense // Рост расхода - красный
                            diff < -0.01 -> R.color.colorIncome  // Снижение расхода - зеленый
                            else -> 0 // Нет изменений
                        }
                        comparisonText = "$diffSign $diffFormatted ${context.getString(R.string.report_vs_previous)}"
                        if (diffColorResId != 0) {
                            comparisonTextColor = ContextCompat.getColor(context, diffColorResId)
                        } // Иначе остается цвет по умолчанию
                    } else {
                        comparisonText = context.getString(R.string.report_no_previous_data)
                    }
                }
            }

            binding.tvCategoryComparisonReport.text = comparisonText
            binding.tvCategoryComparisonReport.setTextColor(comparisonTextColor)
            binding.tvCategoryComparisonReport.visibility = if (comparisonText.isNotEmpty()) View.VISIBLE else View.GONE
        }

        /** Вспомогательная функция для получения цвета из атрибута темы. */
        @ColorInt
        private fun getThemeColor(context: Context, @AttrRes themeAttrId: Int): Int {
            val typedValue = TypedValue()
            return if (context.theme.resolveAttribute(themeAttrId, typedValue, true)) {
                if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    typedValue.data
                } else {
                    try { ContextCompat.getColor(context, typedValue.resourceId) }
                    catch (e: Exception) {
                        Log.w("ReportDetailVH", "Could not resolve theme attribute as color resource: $themeAttrId", e)
                        // ИСПРАВЛЕНИЕ: Используем стандартный атрибут как fallback
                        getFallbackThemeColor(context, android.R.attr.textColorSecondary)
                    }
                }
            } else {
                Log.w("ReportDetailVH", "Could not resolve theme attribute: $themeAttrId")
                // ИСПРАВЛЕНИЕ: Используем стандартный атрибут как fallback
                getFallbackThemeColor(context, android.R.attr.textColorSecondary)
            }
        }

        /** Получение запасного цвета, если атрибут не найден */
        @ColorInt
        private fun getFallbackThemeColor(context: Context, @AttrRes fallbackAttrResId: Int) : Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(fallbackAttrResId, typedValue, true)
            // Предполагаем, что textColorSecondary точно есть
            return if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                ContextCompat.getColor(context, typedValue.resourceId)
            }
        }

    }
    // DiffUtil Callback
    private class ReportDetailDiffCallback : DiffUtil.ItemCallback<ReportDetailItem>() {
        override fun areItemsTheSame(oldItem: ReportDetailItem, newItem: ReportDetailItem): Boolean = oldItem.categoryId == newItem.categoryId
        override fun areContentsTheSame(oldItem: ReportDetailItem, newItem: ReportDetailItem): Boolean = oldItem == newItem
    }
}

// Упрощенная утилита getResId, использующая context.resources.getIdentifier
fun getResId(resName: String?, context: Context): Int {
    if (resName.isNullOrBlank()) return 0
    return try {
        context.resources.getIdentifier(resName, "drawable", context.packageName)
    } catch (e: Exception) {
        Log.e("ResourceUtils", "Failed to get resource ID for drawable: $resName", e)
        0
    }
}