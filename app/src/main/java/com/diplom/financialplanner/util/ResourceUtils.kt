package com.diplom.financialplanner.util

import android.content.Context
import android.util.Log
import com.diplom.financialplanner.R // Убедитесь, что R импортируется

/**
 * Утилитарная функция для получения идентификатора ресурса Drawable по его строковому имени.
 * @param resName Имя ресурса (например, "ic_category_food").
 * @param context Контекст приложения.
 * @return ID ресурса (int) или 0, если ресурс не найден.
 */
fun getResId(resName: String?, context: Context): Int {
    if (resName.isNullOrBlank()) {
        Log.w("ResourceUtils", "Resource name is null or blank.")
        return 0
    }
    return try {
        // Стандартный и рекомендуемый способ получения ID ресурса по имени
        context.resources.getIdentifier(resName, "drawable", context.packageName)
    } catch (e: Exception) {
        // Обработка исключения, если ресурс не найден
        Log.e("ResourceUtils", "Failed to get resource ID for drawable: $resName", e)
        0 // Возвращаем 0 в случае ошибки
    }
}

/**
 * Перегруженная версия для получения ID ресурса любого типа (не только drawable).
 * Менее безопасна, так как использует Reflection, что может не работать с обфускацией кода.
 * Оставлена для примера, но рекомендуется использовать getIdentifier.
 *
 * @param resName Имя ресурса.
 * @param resClass Класс R-ресурсов (например, R.drawable::class.java).
 * @param context Контекст (не используется в этой реализации, но оставлен для консистентности).
 * @return ID ресурса или 0.
 */
@Deprecated("Using reflection is less reliable. Prefer using context.resources.getIdentifier.")
fun getResId(resName: String?, resClass: Class<*>, context: Context): Int {
    if (resName.isNullOrBlank()) return 0
    return try {
        val idField = resClass.getDeclaredField(resName)
        idField.getInt(idField)
    } catch (e: Exception) {
        Log.e("ResourceUtils", "Failed to get resource ID via reflection for: $resName in class ${resClass.simpleName}", e)
        0
    }
}