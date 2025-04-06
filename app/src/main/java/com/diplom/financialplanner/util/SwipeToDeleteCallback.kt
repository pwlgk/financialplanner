package com.diplom.financialplanner.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.diplom.financialplanner.R

/**
 * Абстрактный класс для реализации callback'а свайпа для удаления в RecyclerView.
 * Рисует красный фон и иконку корзины при свайпе.
 * @param context Контекст для доступа к ресурсам.
 */
abstract class SwipeToDeleteCallback(context: Context) : ItemTouchHelper.SimpleCallback(
    0, // Флаги для drag & drop (0 - отключено)
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Разрешаем свайп влево и вправо
) {

    // Иконка удаления (корзина)
    private val deleteIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_delete)
    // Нативные размеры иконки
    private val intrinsicWidth = deleteIcon?.intrinsicWidth ?: 0
    private val intrinsicHeight = deleteIcon?.intrinsicHeight ?: 0
    // Drawable для рисования фона
    private val background = ColorDrawable()
    // Цвет фона при свайпе
    private val backgroundColor = ContextCompat.getColor(context, R.color.colorExpense) // Используем красный цвет

    /**
     * Вызывается при перемещении элемента (drag & drop). Мы его не используем.
     */
    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return false // Перемещение не поддерживается
    }

    /**
     * Вызывается при завершении свайпа. Этот метод должен быть переопределен
     * в конкретной реализации (во фрагменте), чтобы инициировать удаление элемента.
     */
    abstract override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int)

    /**
     * Вызывается при отрисовке свайпа. Отвечает за рисование фона и иконки.
     */
    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, // Смещение по горизонтали (отрицательное влево, положительное вправо)
        dY: Float, // Смещение по вертикали (не используем)
        actionState: Int, // Текущее состояние (SWIPE)
        isCurrentlyActive: Boolean // Активен ли свайп в данный момент
    ) {
        val itemView = viewHolder.itemView // View элемента списка
        val itemHeight = itemView.bottom - itemView.top // Высота элемента

        // 1. Рисуем красный фон
        background.color = backgroundColor
        // Определяем границы фона в зависимости от направления свайпа
        if (dX > 0) { // Свайп вправо
            // Фон рисуется от левого края элемента до текущей позиции свайпа
            background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
        } else if (dX < 0) { // Свайп влево
            // Фон рисуется от текущей позиции свайпа до правого края элемента
            background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
        } else {
            // Если свайпа нет (dX == 0), фон не рисуем
            background.setBounds(0, 0, 0, 0)
        }
        background.draw(c) // Отрисовываем фон на канве

        // 2. Рассчитываем позицию иконки
        val iconTop = itemView.top + (itemHeight - intrinsicHeight) / 2 // Вертикальное центрирование
        val iconMargin = (itemHeight - intrinsicHeight) / 2 // Отступ от края
        val iconLeft: Int
        val iconRight: Int

        if (dX > 0) { // Свайп вправо
            // Иконка рисуется у левого края со смещением
            iconLeft = itemView.left + iconMargin
            iconRight = itemView.left + iconMargin + intrinsicWidth
            deleteIcon?.setBounds(iconLeft, iconTop, iconRight, iconTop + intrinsicHeight)
        } else if (dX < 0) { // Свайп влево
            // Иконка рисуется у правого края со смещением
            iconLeft = itemView.right - iconMargin - intrinsicWidth
            iconRight = itemView.right - iconMargin
            deleteIcon?.setBounds(iconLeft, iconTop, iconRight, iconTop + intrinsicHeight)
        } else { // Свайп отменен
            // Скрываем иконку, устанавливая нулевые границы
            deleteIcon?.setBounds(0, 0, 0, 0)
        }

        // 3. Рисуем иконку
        deleteIcon?.draw(c)

        // Вызываем стандартную реализацию для отрисовки самого элемента поверх фона
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}