package com.diplom.financialplanner.util

import androidx.room.TypeConverter
import java.util.Date

/**
 * TypeConverter для Room, позволяющий хранить объекты java.util.Date в базе данных
 * путем их преобразования в Long (timestamp) и обратно.
 */
class DateConverter {

    /**
     * Конвертирует Long (timestamp в миллисекундах) в объект Date.
     * @param value Timestamp или null.
     * @return Объект Date или null, если value был null.
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Конвертирует объект Date в Long (timestamp в миллисекундах).
     * @param date Объект Date или null.
     * @return Timestamp или null, если date был null.
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}