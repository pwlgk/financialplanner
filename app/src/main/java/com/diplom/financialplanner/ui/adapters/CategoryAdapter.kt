package com.diplom.financialplanner.ui.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.data.database.entity.CategoryEntity
import com.diplom.financialplanner.databinding.ItemCategoryBinding
import com.diplom.financialplanner.util.getResId // Утилита для получения ID ресурса по имени

/**
 * Адаптер для отображения списка категорий (доходов или расходов).
 * @param onCategoryClick Лямбда для обработки клика по категории (редактирование).
 * @param onDeleteClick Лямбда для обработки клика по кнопке удаления.
 */
class CategoryAdapter(
    private val onCategoryClick: (CategoryEntity) -> Unit,
    private val onDeleteClick: (CategoryEntity) -> Unit
) : ListAdapter<CategoryEntity, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding, onCategoryClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** ViewHolder для элемента списка категорий. */
    class CategoryViewHolder(
        private val binding: ItemCategoryBinding,
        private val onCategoryClick: (CategoryEntity) -> Unit,
        private val onDeleteClick: (CategoryEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentCategory: CategoryEntity? = null

        init {
            // Клик по элементу для редактирования
            binding.root.setOnClickListener {
                currentCategory?.let { onCategoryClick(it) }
            }
            // Клик по кнопке для удаления
            binding.btnDeleteCategory.setOnClickListener {
                currentCategory?.let { onDeleteClick(it) }
            }
        }

        /** Связывает данные CategoryEntity с View элементами. */
        fun bind(category: CategoryEntity) {
            currentCategory = category
            binding.tvCategoryName.text = category.name

            val context = binding.root.context

            // Установка иконки
            val iconDrawable: Drawable? = category.iconResName?.let { iconName ->
                // Получаем ID ресурса по его имени
                val resId = getResId(iconName, R.drawable::class.java, context)
                if (resId != 0) ContextCompat.getDrawable(context, resId) else null
            } ?: ContextCompat.getDrawable(context, R.drawable.ic_category_other) // Иконка по умолчанию

            binding.ivCategoryIcon.setImageDrawable(iconDrawable)

            // Установка цвета фона иконки и цвета самой иконки
            val colorInt = try {
                // Пытаемся распарсить HEX цвет, если он есть
                category.colorHex?.let { Color.parseColor(it) }
                // Если цвета нет или ошибка парсинга, используем цвет по умолчанию
                    ?: ContextCompat.getColor(context, R.color.design_default_color_primary_variant)
            } catch (e: IllegalArgumentException) {
                // Обработка ошибки парсинга цвета
                ContextCompat.getColor(context, R.color.design_default_color_primary_variant)
            }

            // Устанавливаем цвет фона кружка (если фон - это GradientDrawable)
            (binding.ivCategoryIcon.background as? GradientDrawable)?.setColor(colorInt)

            // Определяем цвет иконки (белый или черный) для контраста с фоном
            val luminance = Color.luminance(colorInt)
            val iconTintColor = if (luminance > 0.5) Color.BLACK else Color.WHITE
            binding.ivCategoryIcon.imageTintList = ColorStateList.valueOf(iconTintColor)
        }
    }

    /** DiffUtil Callback для расчета различий. */
    private class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryEntity>() {
        override fun areItemsTheSame(oldItem: CategoryEntity, newItem: CategoryEntity): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: CategoryEntity, newItem: CategoryEntity): Boolean {
            // Сравниваем все поля, включая иконку и цвет
            return oldItem == newItem
        }
    }
}