package com.diplom.financialplanner.ui.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.databinding.ItemBudgetCategoryLimitBinding

/**
 * Модель данных для элемента списка в адаптере установки лимитов бюджета.
 * @param category Сущность категории.
 * @param limit Лимит, введенный пользователем (может быть null).
 */
data class BudgetLimitItem(
    val category: CategoryEntity,
    var limit: Double? = null
)

/**
 * Интерфейс для оповещения об изменении лимита в поле ввода.
 */
interface BudgetLimitChangeListener {
    fun onLimitChanged(categoryId: Long, newLimit: Double?)
}

/**
 * Адаптер для RecyclerView на экране настройки бюджета.
 * Отображает категории и поля для ввода лимитов.
 * @param listener Слушатель изменений лимитов.
 */
class BudgetLimitAdapter(
    private val listener: BudgetLimitChangeListener
) : ListAdapter<BudgetLimitItem, BudgetLimitAdapter.BudgetLimitViewHolder>(BudgetLimitDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetLimitViewHolder {
        val binding = ItemBudgetCategoryLimitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BudgetLimitViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: BudgetLimitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Обновляет лимиты в уже отображаемых элементах списка.
     * Используется для предзаполнения при редактировании бюджета.
     * @param limitsMap Карта <ID категории, Сумма лимита>.
     */
    fun updateLimits(limitsMap: Map<Long, Double>) {
        val currentList = currentList // Получаем текущий список
        if (currentList.isNullOrEmpty()) return

        // Проходим по текущему списку и обновляем лимиты, если они изменились
        currentList.forEachIndexed { index, item ->
            limitsMap[item.category.id]?.let { loadedLimit ->
                if (item.limit != loadedLimit) {
                    item.limit = loadedLimit // Обновляем лимит в объекте данных
                    notifyItemChanged(index) // Уведомляем адаптер об изменении элемента
                }
            } // ?: run { // Если для категории нет лимита в карте, а он был установлен локально
            //   if (item.limit != null) {
            //      item.limit = null
            //      notifyItemChanged(index)
            //   }
            //}
        }
    }

    /**
     * Возвращает текущие установленные лимиты из адаптера.
     * @return Карта <ID категории, Сумма лимита>.
     */
    fun getCurrentLimits(): Map<Long, Double> {
        return currentList
            .mapNotNull { item -> item.limit?.takeIf { it > 0 }?.let { limitValue -> item.category.id to limitValue } }
            .toMap()
    }


    /** ViewHolder для элемента списка установки лимитов. */
    class BudgetLimitViewHolder(
        private val binding: ItemBudgetCategoryLimitBinding,
        private val listener: BudgetLimitChangeListener
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: BudgetLimitItem? = null
        private var textWatcher: TextWatcher? = null

        /** Связывает данные BudgetLimitItem с View элементами. */
        fun bind(item: BudgetLimitItem) {
            currentItem = item
            binding.tvCategoryName.text = item.category.name

            // --- Установка TextWatcher для поля ввода ---
            // Сначала удаляем предыдущий watcher, чтобы избежать утечек и двойной обработки
            binding.etCategoryLimit.removeTextChangedListener(textWatcher)

            // Устанавливаем текущее значение лимита в поле ввода
            // Показываем пустую строку, если лимит null или 0
            binding.etCategoryLimit.setText(item.limit?.takeIf { it > 0 }?.let { "%.0f".format(it) } ?: "")

            // Создаем и добавляем новый TextWatcher
            textWatcher = binding.etCategoryLimit.doAfterTextChanged { editable ->
                // Этот код выполнится ПОСЛЕ изменения текста
                val inputText = editable?.toString()
                val newLimit = inputText?.toDoubleOrNull() // Пытаемся преобразовать в Double
                // Обновляем лимит в нашем объекте данных
                currentItem?.limit = newLimit
                // Уведомляем слушателя об изменении
                currentItem?.let { boundItem ->
                    listener.onLimitChanged(boundItem.category.id, newLimit)
                }
            }
        }
    }

    /** DiffUtil Callback для расчета различий. */
    private class BudgetLimitDiffCallback : DiffUtil.ItemCallback<BudgetLimitItem>() {
        override fun areItemsTheSame(oldItem: BudgetLimitItem, newItem: BudgetLimitItem): Boolean {
            return oldItem.category.id == newItem.category.id // Сравниваем по ID категории
        }
        override fun areContentsTheSame(oldItem: BudgetLimitItem, newItem: BudgetLimitItem): Boolean {
            // Сравниваем и категорию, и лимит для корректного обновления UI
            return oldItem.category == newItem.category && oldItem.limit == newItem.limit
        }
    }
}