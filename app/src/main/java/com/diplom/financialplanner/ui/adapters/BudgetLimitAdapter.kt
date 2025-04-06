package com.diplom.financialplanner.ui.adapters

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged // Удобный extension
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.databinding.ItemBudgetCategoryLimitBinding
import java.text.DecimalFormat // Для более контролируемого форматирования
import java.text.NumberFormat
import java.util.Locale

// Модель данных (без изменений)
data class BudgetLimitItem(
    val category: CategoryEntity,
    var limit: Double? = null
)

// Интерфейс (без изменений)
interface BudgetLimitChangeListener {
    fun onLimitChanged(categoryId: Long, newLimit: Double?)
}


class BudgetLimitAdapter(
    private val listener: BudgetLimitChangeListener
) : ListAdapter<BudgetLimitItem, BudgetLimitAdapter.BudgetLimitViewHolder>(BudgetLimitDiffCallback()) {

    // Используем DecimalFormat для более предсказуемого вывода без лишних нулей или E-нотации
    private val numberFormatter = DecimalFormat("#0").apply { // Формат без дробной части
        maximumFractionDigits = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetLimitViewHolder {
        val binding = ItemBudgetCategoryLimitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Передаем форматтер в ViewHolder
        return BudgetLimitViewHolder(binding, listener, numberFormatter)
    }

    override fun onBindViewHolder(holder: BudgetLimitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // Метод updateLimits теперь не нужен, т.к. submitList с DiffUtil справится
    /*
    fun updateLimits(limitsMap: Map<Long, Double>) { ... }
    */

    fun getCurrentLimits(): Map<Long, Double> {
        return currentList
            .mapNotNull { item -> item.limit?.takeIf { it > 0 }?.let { item.category.id to it } }
            .toMap()
    }


    class BudgetLimitViewHolder(
        private val binding: ItemBudgetCategoryLimitBinding,
        private val listener: BudgetLimitChangeListener,
        private val formatter: DecimalFormat // Принимаем форматтер
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: BudgetLimitItem? = null
        private var watcher: TextWatcher? = null
        private var isUpdatingFromCode = false // Флаг для предотвращения рекурсии

        /** Связывает данные и устанавливает слушатель ОДИН РАЗ */
        fun bind(item: BudgetLimitItem) {
            currentItem = item
            binding.tvCategoryName.text = item.category.name

            // --- УСТАНОВКА НАЧАЛЬНОГО ЗНАЧЕНИЯ ---
            isUpdatingFromCode = true // Ставим флаг перед установкой текста
            val initialText = item.limit?.takeIf { it > 0 }?.let { formatter.format(it) } ?: ""
            // Устанавливаем только если текст реально отличается
            if (binding.etCategoryLimit.text.toString() != initialText) {
                binding.etCategoryLimit.setText(initialText)
                // Перемещаем курсор в конец после установки текста
                binding.etCategoryLimit.setSelection(initialText.length)
            }
            isUpdatingFromCode = false // Снимаем флаг

            // --- УСТАНОВКА TextWatcher (ЕСЛИ ЕГО ЕЩЕ НЕТ) ---
            // Удаляем старый, если он был прикреплен к этому EditText ранее (из-за переиспользования VH)
            watcher?.let { binding.etCategoryLimit.removeTextChangedListener(it) }

            // Создаем и добавляем новый watcher
            watcher = binding.etCategoryLimit.doAfterTextChanged { editable ->
                // Игнорируем изменения, сделанные программно через setText
                if (isUpdatingFromCode) return@doAfterTextChanged

                val inputText = editable?.toString()
                // Пытаемся распарсить, допускаем пустую строку как null
                val newLimit = if (inputText.isNullOrBlank()) {
                    null
                } else {
                    try {
                        // Используем Locale.US для парсинга, так как Double.parseDouble ожидает точку
                        val format = NumberFormat.getInstance(Locale.US)
                        format.parse(inputText)?.toDouble()
                    } catch (e: Exception) {
                        Log.w("BudgetLimitVH", "Failed to parse input: $inputText", e)
                        null // Некорректный ввод -> null
                    }
                }

                // Округляем до целого, если нужно (или используем Double)
                val roundedLimit = newLimit?.let { kotlin.math.round(it) }?.takeIf { it > 0 }

                // Обновляем значение в нашем объекте данных, только если оно изменилось
                if (currentItem?.limit != roundedLimit) {
                    currentItem?.limit = roundedLimit
                    // Уведомляем слушателя
                    currentItem?.let { boundItem ->
                        listener.onLimitChanged(boundItem.category.id, roundedLimit)
                    }
                }
            }
        }
    }

    // DiffUtil Callback
    private class BudgetLimitDiffCallback : DiffUtil.ItemCallback<BudgetLimitItem>() {
        override fun areItemsTheSame(oldItem: BudgetLimitItem, newItem: BudgetLimitItem): Boolean {
            return oldItem.category.id == newItem.category.id
        }

        // Теперь сравниваем только ID категории, т.к. лимит может меняться пользователем
        // и мы не хотим лишних перерисовок из-за этого сравнения.
        // Содержимое (имя категории) обновляем, если изменился сам объект категории.
        override fun areContentsTheSame(oldItem: BudgetLimitItem, newItem: BudgetLimitItem): Boolean {
            // Сравниваем объект CategoryEntity и текущее значение лимита,
            // чтобы DiffUtil корректно обновлял, если лимит загрузился из ViewModel
            return oldItem.category == newItem.category && oldItem.limit == newItem.limit
        }
    }
}