package com.diplom.financialplanner.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.diplom.financialplanner.data.database.dao.BudgetDao
import com.diplom.financialplanner.data.database.dao.CategoryDao
import com.diplom.financialplanner.data.database.dao.FinancialGoalDao
import com.diplom.financialplanner.data.database.dao.TransactionDao
import com.diplom.financialplanner.data.database.entity.*
import com.diplom.financialplanner.util.DateConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Основной класс базы данных приложения, использующий Room.
 * Определяет список сущностей, версию базы данных и предоставляет доступ к DAO.
 */
@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        BudgetCategoryLimitEntity::class,
        FinancialGoalEntity::class
    ],
    version = 4, // ТЕКУЩАЯ ВЕРСИЯ БАЗЫ ДАННЫХ
    exportSchema = false // Отключаем экспорт схемы для упрощения
)
@TypeConverters(DateConverter::class) // Регистрируем конвертер для типа Date
abstract class AppDatabase : RoomDatabase() {

    /** Абстрактный метод для получения DAO категорий. */
    abstract fun categoryDao(): CategoryDao
    /** Абстрактный метод для получения DAO транзакций. */
    abstract fun transactionDao(): TransactionDao
    /** Абстрактный метод для получения DAO бюджетов. */
    abstract fun budgetDao(): BudgetDao
    /** Абстрактный метод для получения DAO финансовых целей. */
    abstract fun financialGoalDao(): FinancialGoalDao

    companion object {
        @Volatile // Гарантирует видимость изменений этого поля для всех потоков
        private var INSTANCE: AppDatabase? = null

        // --- Определение Миграций Базы Данных ---

        /** Миграция с версии 1 на 2: Добавление таблиц бюджетов и лимитов. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i("AppDatabaseMigration", "Migrating database from version 1 to 2")
                // Создание таблицы бюджетов
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `budgets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `start_date` INTEGER NOT NULL,
                        `end_date` INTEGER NOT NULL
                    )
                """)
                // Создание уникального индекса для периода бюджета
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_start_date_end_date` ON `budgets` (`start_date`, `end_date`)")

                // Создание таблицы лимитов по категориям
                db.execSQL("""
                     CREATE TABLE IF NOT EXISTS `budget_category_limits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `budget_id` INTEGER NOT NULL,
                        `category_id` INTEGER NOT NULL,
                        `limit_amount` REAL NOT NULL,
                        FOREIGN KEY(`budget_id`) REFERENCES `budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                // Создание индексов для таблицы лимитов
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_category_limits_budget_id_category_id` ON `budget_category_limits` (`budget_id`, `category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_category_limits_category_id` ON `budget_category_limits` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_category_limits_budget_id` ON `budget_category_limits` (`budget_id`)")
            }
        }

        /** Миграция с версии 2 на 3: Добавление таблицы финансовых целей. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i("AppDatabaseMigration", "Migrating database from version 2 to 3")
                // Создание таблицы финансовых целей
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `financial_goals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `target_amount` REAL NOT NULL,
                        `current_amount` REAL NOT NULL DEFAULT 0.0,
                        `creation_date` INTEGER NOT NULL
                    )
                """)
                // Можно добавить индексы при необходимости
                // db.execSQL("CREATE INDEX index_financial_goals_name ON financial_goals(name)")
            }
        }

        /** Миграция с версии 3 на 4: Добавление полей иконок и цветов в таблицу категорий. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i("AppDatabaseMigration", "Migrating database from version 3 to 4")
                // Добавляем столбец для имени ресурса иконки
                db.execSQL("ALTER TABLE categories ADD COLUMN icon_res_name TEXT DEFAULT NULL")
                // Добавляем столбец для HEX-кода цвета
                db.execSQL("ALTER TABLE categories ADD COLUMN color_hex TEXT DEFAULT NULL")
            }
        }

        /**
         * Callback, вызываемый при первом создании базы данных.
         * Используется для предзаполнения начальными данными (например, стандартными категориями).
         */
        private val roomCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Используем INSTANCE для доступа к DAO после создания БД
                INSTANCE?.let { database ->
                    // Запускаем корутину в IO потоке для выполнения операций с БД
                    CoroutineScope(Dispatchers.IO).launch {
                        Log.i("AppDatabaseCallback", "Database created. Populating with initial categories...")
                        val categoryDao = database.categoryDao()
                        // Вставляем стандартные категории (примеры)
                        // Убедитесь, что ресурсы drawable с именами ic_category_* существуют!
                        // Расходы
                        categoryDao.insertCategory(CategoryEntity(name = "Еда", type = "expense", iconResName = "ic_category_food", colorHex = "#FF7043"))
                        categoryDao.insertCategory(CategoryEntity(name = "Транспорт", type = "expense", iconResName = "ic_category_transport", colorHex = "#5C6BC0"))
                        categoryDao.insertCategory(CategoryEntity(name = "Жилье", type = "expense", iconResName = "ic_category_housing", colorHex = "#78909C"))
                        categoryDao.insertCategory(CategoryEntity(name = "Развлечения", type = "expense", iconResName = "ic_category_entertainment", colorHex = "#EC407A"))
                        categoryDao.insertCategory(CategoryEntity(name = "Одежда", type = "expense", iconResName = "ic_category_clothing", colorHex = "#AB47BC"))
                        categoryDao.insertCategory(CategoryEntity(name = "Связь", type = "expense", iconResName = "ic_category_communication", colorHex = "#29B6F6"))
                        categoryDao.insertCategory(CategoryEntity(name = "Здоровье", type = "expense", iconResName = "ic_category_health", colorHex = "#EF5350"))
                        categoryDao.insertCategory(CategoryEntity(name = "Другое (Расходы)", type = "expense", iconResName = "ic_category_other", colorHex = "#BDBDBD"))
                        // Доходы
                        categoryDao.insertCategory(CategoryEntity(name = "Зарплата", type = "income", iconResName = "ic_category_salary", colorHex = "#66BB6A"))
                        categoryDao.insertCategory(CategoryEntity(name = "Подработка", type = "income", iconResName = "ic_category_sidejob", colorHex = "#9CCC65"))
                        categoryDao.insertCategory(CategoryEntity(name = "Подарки", type = "income", iconResName = "ic_category_gift", colorHex = "#FFEE58"))
                        categoryDao.insertCategory(CategoryEntity(name = "Другое (Доходы)", type = "income", iconResName = "ic_category_other", colorHex = "#BDBDBD"))
                        Log.i("AppDatabaseCallback", "Initial categories populated.")
                    }
                }
            }
            // Можно добавить onOpen для логирования или других действий при открытии БД
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.d("AppDatabaseCallback", "Database opened.")
                // Можно включить WAL режим для лучшей производительности записи
                // db.enableWriteAheadLogging()
            }
        }

        /**
         * Получает синглтон экземпляр базы данных.
         * Создает базу данных при первом вызове.
         * @param context Контекст приложения.
         * @return Экземпляр AppDatabase.
         */
        fun getDatabase(context: Context): AppDatabase {
            // Если экземпляр уже есть, возвращаем его
            // Если нет, создаем его в синхронизированном блоке для потокобезопасности
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Используем контекст приложения
                    AppDatabase::class.java,    // Класс базы данных
                    "financial_planner_db"      // Имя файла базы данных
                )
                    // --- Выберите ОДИН из следующих вариантов обработки изменений схемы ---
                    // Вариант 1: Добавить все миграции по порядку
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

                    .addCallback(roomCallback) // Добавляем Callback для предзаполнения
                    // .allowMainThreadQueries() // НЕ РЕКОМЕНДУЕТСЯ: разрешает запросы в UI потоке
                    .build() // Строим экземпляр базы данных
                INSTANCE = instance // Сохраняем созданный экземпляр
                instance // Возвращаем созданный экземпляр
            }
        }
    }
}