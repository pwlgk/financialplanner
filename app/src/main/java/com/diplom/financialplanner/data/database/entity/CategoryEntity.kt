package com.diplom.financialplanner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность, представляющая категорию дохода или расхода в базе данных.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    /** Уникальный идентификатор категории (генерируется автоматически). */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Название категории. */
    val name: String,

    /** Тип категории: "expense" для расходов, "income" для доходов. */
    val type: String,

    /** Имя ресурса drawable для иконки категории (например, "ic_category_food"). */
    @ColumnInfo(name = "icon_res_name")
    val iconResName: String? = null,

    /** Цвет категории в формате HEX строки (например, "#FF7043"). */
    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null
)