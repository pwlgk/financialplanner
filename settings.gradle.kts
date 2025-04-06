// Файл: settings.gradle.kts (уровень проекта)

// Блок управления плагинами - здесь объявляются репозитории, где Gradle ищет сами плагины
pluginManagement {
    repositories {
        google() // Репозиторий Google для плагинов Android и т.д.
        mavenCentral() // Центральный репозиторий Maven
        gradlePluginPortal() // Портал плагинов Gradle
    }
}

// Блок управления разрешением зависимостей - здесь объявляются репозитории,
// где Gradle ищет БИБЛИОТЕКИ (зависимости), указанные в build.gradle модулей.
dependencyResolutionManagement {
    // Указываем режим разрешения репозиториев.
    // PREFER_SETTINGS - хороший вариант, сначала ищет здесь, потом (если разрешено) в build.gradle модулей.
    // FAIL_ON_PROJECT_REPOS - строгий режим, требует, чтобы ВСЕ репозитории были только здесь.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // ИЛИ используйте FAIL_ON_PROJECT_REPOS, если уверены

    repositories {
        google() // Репозиторий Google для библиотек AndroidX, Material и т.д.
        mavenCentral() // Центральный репозиторий Maven для многих библиотек Java/Kotlin.

        // !!! ВАЖНО: Репозиторий JitPack для библиотек с GitHub (как MPAndroidChart) !!!
        maven {
            url = uri("https://jitpack.io")
            // Опционально: можно добавить проверку учетных данных, если репозиторий приватный
            // credentials {
            //     username = "your_username"
            //     password = "your_password_or_token"
            // }
        }
    }
}

// Имя корневого проекта
rootProject.name = "FinancialPlanner" // Или ваше название
// Включаем модуль приложения в сборку
include(":app")