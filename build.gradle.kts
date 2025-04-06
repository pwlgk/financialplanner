// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Плагин для сборки Android приложений
    id("com.android.application") version "8.2.2" apply false // Используйте актуальную версию Android Gradle Plugin (AGP)
    // Плагин Kotlin для Android
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false // Используйте актуальную версию Kotlin
    // Плагин KSP (Kotlin Symbol Processing) для генерации кода (например, для Room)
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false // Версия KSP должна соответствовать версии Kotlin
    // Плагин Safe Args для безопасной передачи аргументов между экранами Navigation Component
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false // Версия Safe Args должна соответствовать версии Navigation Component
}