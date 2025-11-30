package com.example.crimicam.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject

/**
 * AuthGuard - Protects routes that require authentication
 *
 * Usage:
 * ```
 * @Composable
 * fun CameraScreen() {
 *     AuthGuard(
 *         onUnauthenticated = { navController.navigate("login") }
 *     ) {
 *         // Your camera screen content
 *         CameraScreenContent()
 *     }
 * }
 * ```
 */
@Composable
fun AuthGuard(
    userSessionManager: UserSessionManager = koinInject(),
    onUnauthenticated: () -> Unit = {},
    showLoadingWhileChecking: Boolean = true,
    content: @Composable () -> Unit
) {
    val isLoggedIn by userSessionManager.isLoggedIn.collectAsState()
    var hasChecked by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn && hasChecked) {
            onUnauthenticated()
        }
        hasChecked = true
    }

    when {
        !hasChecked && showLoadingWhileChecking -> {
            // Show loading while checking auth state
            AuthCheckingScreen()
        }
        isLoggedIn -> {
            // User is authenticated, show content
            content()
        }
        else -> {
            // User is not authenticated, show message
            UnauthenticatedScreen(onLogin = onUnauthenticated)
        }
    }
}

@Composable
private fun AuthCheckingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color.Cyan,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "VERIFYING CREDENTIALS...",
                color = Color.Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun UnauthenticatedScreen(onLogin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Red
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "AUTHENTICATION REQUIRED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "YOU MUST BE LOGGED IN TO ACCESS THIS FEATURE",
                fontSize = 12.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Cyan
                )
            ) {
                Text(
                    "LOG IN",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * Quick check if user is authenticated (for use in ViewModels)
 */
fun requireAuthentication(userSessionManager: UserSessionManager) {
    if (!userSessionManager.isUserLoggedIn()) {
        throw IllegalStateException("User must be authenticated to perform this action")
    }
}