package com.diplom.financialplanner.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController // Импортируем NavController
import androidx.navigation.fragment.NavHostFragment // Импортируем NavHostFragment
// import androidx.navigation.findNavController // Этот импорт больше не нужен напрямую
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.diplom.financialplanner.R
import com.diplom.financialplanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController // Объявляем переменную для NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navView: BottomNavigationView = binding.navView

        // --- ИЗМЕНЕННЫЙ СПОСОБ ПОЛУЧЕНИЯ NavController ---
        // 1. Находим NavHostFragment по ID из layout'а
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        // 2. Получаем NavController из NavHostFragment
        navController = navHostFragment.navController
        // ----------------------------------------------------

        // Конфигурация AppBar (оставляем как было, но проверяем ID)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_dashboard, R.id.navigation_expenses, R.id.navigation_income,
                R.id.navigation_budget, R.id.navigation_reports // Убедитесь, что здесь нет navigation_goals
            )
        )

        // Настройка ActionBar и BottomNavigationView с полученным navController
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    // Метод onSupportNavigateUp теперь использует переменную класса navController
    override fun onSupportNavigateUp(): Boolean {
        // Используем navController, полученный в onCreate
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    fun navigateToBottomNavItem(itemId: Int) {
        binding.navView.selectedItemId = itemId
    }
}