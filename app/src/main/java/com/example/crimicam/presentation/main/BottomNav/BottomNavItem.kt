package com.example.crimicam.presentation.main.BottomNav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : BottomNavItem("home", Icons.Default.Home, "Home")
    object Map : BottomNavItem("map", Icons.Default.Place, "Map")
    object KnownPeople : BottomNavItem("known_people", Icons.Default.People, "Known People")
    object Profile : BottomNavItem("profile", Icons.Default.Person, "Profile")
    object Admin : BottomNavItem("admin", Icons.Default.Security, "Admin")
}