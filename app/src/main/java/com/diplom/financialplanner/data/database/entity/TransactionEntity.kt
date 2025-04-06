package com.diplom.financialplanner.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Enum для определения типа транзакции.
 */
enum class TransactionType {
    INCOME, EXPENSE
}

/**
 * Сущность, представляющая финансовую транзакцию (доход или расход) в базе данных.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"], // Поле в CategoryEntity
            childColumns = ["category_id"], // Поле в TransactionEntity
            onDelete = ForeignKey.SET_NULL // При удалении категории, category_id в транзакции станет NULL
            // onDelete = ForeignKey.RESTRICT // Запретить удаление категории, если есть связанные транзакции
            // onDelete = ForeignKey.CASCADE // Удалить транзакции при удалении категории (ОПАСНО!)
        )
    ],
    indices = [
        Index("category_id"), // Индекс для ускорения поиска по категории
        Index("date")         // Индекс для ускорения поиска по дате
    ]
)
data class TransactionEntity(
    /** Уникальный идентификатор транзакции (генерируется автоматически). */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Тип транзакции (доход или расход). */
    val type: TransactionType,

    /** Сумма транзакции. */
    val amount: Double,

    /** ID связанной категории (nullable, если категория удалена или не указана). */
    @ColumnInfo(name = "category_id") // Указываем имя столбца и делаем его индексируемым
    val categoryId: Long?,

    /** Дата и время транзакции. */
    val date: Date,

    /** Необязательное описание/комментарий к транзакции. */
    val description: String? = null
)