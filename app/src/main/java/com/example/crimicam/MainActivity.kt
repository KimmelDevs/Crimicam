package com.example.crimicam

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.crimicam.login.LoginScreen
import com.example.crimicam.main.BottomNav.BottomNavItem
import com.example.crimicam.main.BottomNav.BottomNavigationBar
import com.example.crimicam.main.Home.HomeScreen
import com.example.crimicam.main.KnownPeople.KnownPeopleScreen
import com.example.crimicam.main.Map.MapScreen
import com.example.crimicam.main.Profile.ProfileScreen
import com.example.crimicam.signup.SignupScreen
import com.example.crimicam.ui.theme.CrimicamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CrimicamTheme {
                val view = LocalView.current
                val statusBarColor = MaterialTheme.colorScheme.background
                val navBarColor = MaterialTheme.colorScheme.background

                SideEffect {
                    val window = (view.context as ComponentActivity).window

                    window.statusBarColor = statusBarColor.toArgb()
                    window.navigationBarColor = navBarColor.toArgb()
                    window.setBackgroundDrawable(ColorDrawable(statusBarColor.toArgb()))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val lightStatusIcons = statusBarColor.luminance() > 0.5f
                        window.decorView.systemUiVisibility =
                            if (lightStatusIcons) {
                                window.decorView.systemUiVisibility or
                                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            } else {
                                window.decorView.systemUiVisibility and
                                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                            }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val lightNavIcons = navBarColor.luminance() > 0.5f
                        window.decorView.systemUiVisibility =
                            if (lightNavIcons) {
                                window.decorView.systemUiVisibility or
                                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                            } else {
                                window.decorView.systemUiVisibility and
                                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                            }
                    }
                }

                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login",
        enterTransition = { fadeIn(animationSpec = tween(50)) },
        exitTransition = { fadeOut(animationSpec = tween(50)) }
    ) {
        // Login
        composable("login") {
            LoginScreen(
                homeClick = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                signupClick = { navController.navigate("signup") }
            )
        }

        // Signup
        composable("signup") {
            SignupScreen(navController = navController)
        }

        // Main screen with bottom navigation
        composable("main") {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Map,
        BottomNavItem.KnownPeople,
        BottomNavItem.Profile
    )

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController, items = items)
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(navController = navController)
            }
            composable(BottomNavItem.Map.route) {
                MapScreen()
            }
            composable(BottomNavItem.KnownPeople.route) {
                KnownPeopleScreen()
            }
            composable(BottomNavItem.Profile.route) {
                ProfileScreen()
            }
        }
    }
}