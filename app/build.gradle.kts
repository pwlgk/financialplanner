// Плагины, применяемые к модулю приложения
plugins {
    id("com.android.application") // Применяем плагин Android приложения
    id("org.jetbrains.kotlin.android") // Применяем плагин Kotlin
    id("com.google.devtools.ksp") // Применяем KSP для Room (альтернатива - kotlin-kapt)
    id("androidx.navigation.safeargs.kotlin") // Применяем Safe Args
}

android {
    // Пространство имен вашего приложения (ОБЯЗАТЕЛЬНО ЗАМЕНИТЕ)
    namespace = "com.diplom.financialplanner"
    // Версия SDK, с которой компилируется приложение
    compileSdk = 34 // Рекомендуется использовать последнюю стабильную версию

    defaultConfig {
        // Уникальный идентификатор вашего приложения в Google Play
        applicationId = "com.diplom.financialplanner" // ОБЯЗАТЕЛЬНО ЗАМЕНИТЕ
        // Минимальная версия Android, на которой будет работать приложение
        minSdk = 24 // API 24 (Android 7.0 Nougat) - хороший баланс
        // Целевая версия Android, под которую оптимизировано приложение
        targetSdk = 34 // Рекомендуется использовать последнюю стабильную версию
        // Версия кода приложения (увеличивается при каждом релизе)
        versionCode = 1
        // Версия приложения, видимая пользователю
        versionName = "1.0.0" // Используйте семантическое версионирование (MAJOR.MINOR.PATCH)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Включаем поддержку векторной графики для старых версий Android
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // Включает уменьшение кода (удаление неиспользуемого)
            isMinifyEnabled = true // Включите для релизных сборок
            // Включает обфускацию (переименование классов, методов, полей)
            isShrinkResources = true // Включите для удаления неиспользуемых ресурсов
            // Файлы с правилами ProGuard/R8 для сохранения нужного кода
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), // Стандартные правила Android
                "proguard-rules.pro" // Ваши кастомные правила (нужно создать файл)
            )
            // Можно добавить подпись для релиза
            // signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Для отладочных сборок обычно минификация отключена
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // Настройки совместимости Java и Kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    // Включение функций сборки
    buildFeatures {
        viewBinding = true // Включаем ViewBinding
        // dataBinding = true // Включите, если используете DataBinding
    }
    // Настройки упаковки (могут понадобиться для исключения конфликтов META-INF)
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Зависимости приложения
dependencies {

    // --- Core & AppCompat ---
    implementation("androidx.core:core-ktx:1.12.0") // Kotlin Extensions для Core
    implementation("androidx.appcompat:appcompat:1.6.1") // Поддержка старых версий Android
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Для ConstraintLayout

    // --- Material Design ---
    implementation("com.google.android.material:material:1.11.0") // Компоненты Material Design

    // --- Lifecycle (ViewModel, LiveData/Flow, SavedState) ---
    val lifecycleVersion = "2.7.0" // Используйте последнюю стабильную версию
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion") // ViewModel с Kotlin Coroutines
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion") // LiveData (если используете)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion") // lifecycleScope для корутин
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycleVersion") // Сохранение состояния ViewModel

    // --- Navigation Component ---
    val navVersion = "2.7.7" // Используйте последнюю стабильную версию (совместимую с Safe Args)
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion") // Основная библиотека навигации для фрагментов
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion") // Для интеграции с UI (ActionBar, BottomNav)

    // --- Room (База данных) ---
    val roomVersion = "2.6.1" // Используйте последнюю стабильную версию
    implementation("androidx.room:room-runtime:$roomVersion") // Основная библиотека Room
    ksp("androidx.room:room-compiler:$roomVersion") // KSP Annotation Processor для Room
    // kapt("androidx.room:room-compiler:$roomVersion") // Kapt Annotation Processor (альтернатива KSP)
    implementation("androidx.room:room-ktx:$roomVersion") // Поддержка Kotlin Coroutines и Flow для Room

    // --- Coroutines ---
    // Версии должны быть совместимы
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") // Ядро корутин
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Корутины для Android

    // --- Графики (MPAndroidChart) ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // Библиотека для графиков

    // --- Тестирование ---
    // Unit тесты
    testImplementation("junit:junit:4.13.2")
    // Инструментальные тесты Android
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // Дополнительные библиотеки для тестов (опционально)
    // testImplementation "androidx.arch.core:core-testing:2.2.0" // Для тестирования LiveData/ViewModel
    // testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3" // Для тестирования корутин
    // androidTestImplementation "androidx.navigation:navigation-testing:$navVersion" // Для тестирования навигации
    // androidTestImplementation "androidx.room:room-testing:$roomVersion" // Для тестирования Room
}