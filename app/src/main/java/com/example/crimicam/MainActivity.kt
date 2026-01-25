package com.example.crimicam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.crimicam.presentation.main.Home.EmergencyReportViewModel
import com.example.crimicam.presentation.main.Home.HomeViewModel
import com.example.crimicam.presentation.login.LoginScreen
import com.example.crimicam.presentation.main.Admin.AdminScreen
import com.example.crimicam.presentation.main.BottomNav.BottomNavItem
import com.example.crimicam.presentation.main.BottomNav.BottomNavigationBar
import com.example.crimicam.presentation.main.Home.ActivityDetail.ActivityDetailScreen
import com.example.crimicam.presentation.main.Home.Camera.CameraScreen
import com.example.crimicam.presentation.main.Home.HomeScreen
import com.example.crimicam.presentation.main.Home.Monitor.MonitorScreen
import com.example.crimicam.presentation.main.Home.Monitor.StreamViewerScreen
import com.example.crimicam.presentation.main.KnownPeople.KnownPeopleScreen
import com.example.crimicam.presentation.main.Map.MapScreen
import com.example.crimicam.presentation.main.Profile.LocationLabel.LocationLabelScreen
import com.example.crimicam.presentation.main.Profile.ProfileScreen
import com.example.crimicam.presentation.main.Profile.ViewProfile.ViewProfileScreen
import com.example.crimicam.presentation.signup.SignupScreen
import com.example.crimicam.ui.theme.CrimicamTheme
import com.example.crimicam.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import timber.log.Timber
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.d("✅ Notification permission GRANTED")
            initializeFCM()
        } else {
            Timber.w("❌ Notification permission DENIED")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("🚀 MainActivity onCreate")

        NotificationHelper.createNotificationChannel(this)

        handleNotificationIntent(intent)

        requestNotificationPermission()

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

    override fun onResume() {
        super.onResume()
        Timber.d("📱 MainActivity onResume")

        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("🎯 MainActivity onNewIntent")

        handleNotificationIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Timber.d("✅ Notification permission already granted")
                    initializeFCM()
                }

                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    Timber.d("ℹ️ Showing notification permission rationale")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                else -> {
                    Timber.d("📝 Requesting notification permission")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            Timber.d("📱 Android <13, no permission needed")
            initializeFCM()
        }
    }

    private fun initializeFCM() {
        try {
            Timber.d("🔄 Initializing FCM...")

            // Debug: Check current token first
            debugFCMToken()

            // Get FCM token
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Timber.d("✅ FCM Token: ${token?.take(20)}...")
                    Timber.d("📱 Token length: ${token?.length}")

                    // Store token in user document
                    storeFCMTokenInUserDocument(token)

                } else {
                    Timber.e(task.exception, "❌ Failed to get FCM token")
                }
            }

            // Subscribe to broadcast topic (ALL users will get activity notifications)
            FirebaseMessaging.getInstance().subscribeToTopic("activity_broadcast")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Timber.d("✅ SUCCESS: Subscribed to 'activity_broadcast' topic")
                        Timber.d("📢 This device will now receive activity notifications from ALL users!")
                    } else {
                        Timber.e(task.exception, "❌ FAILED to subscribe to activity broadcast topic")
                    }
                }

            // Subscribe to emergency reports topic for admin notifications
            FirebaseMessaging.getInstance().subscribeToTopic("emergency_reports")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Timber.d("✅ SUCCESS: Subscribed to 'emergency_reports' topic")
                        Timber.d("📢 Will receive emergency reports via topic")
                    } else {
                        Timber.e(task.exception, "❌ FAILED to subscribe to emergency reports topic")
                    }
                }

        } catch (e: Exception) {
            Timber.e(e, "❌ Error initializing FCM")
        }
    }

    private fun debugFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Timber.d("🎯 FCM Token Debug:")
                Timber.d("   Token exists: ${!token.isNullOrEmpty()}")
                Timber.d("   Token length: ${token?.length}")
                Timber.d("   Token preview: ${token?.take(20)}...")

                if (token.isNullOrEmpty()) {
                    Timber.e("⚠️ FCM token is empty or null!")
                } else if (token.length < 100) {
                    Timber.w("⚠️ FCM token seems short (${token.length} chars)")
                }
            } else {
                Timber.e(task.exception, "❌ Failed to get FCM token in debug")
            }
        }
    }

    private fun storeFCMTokenInUserDocument(token: String?) {
        token?.let { fcmToken ->
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                Timber.d("💾 Storing FCM token in user document: ${currentUser.uid}")
                Timber.d("📱 Token to store: ${fcmToken.take(20)}...")

                // Use set() with merge option instead of update()
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.uid)
                    .set(
                        mapOf(
                            "fcmToken" to fcmToken,
                            "fcmTokenUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        Timber.d("✅ FCM token stored in user document")

                        // Verify it was stored
                        verifyStoredToken(currentUser.uid, fcmToken)

                        // Also store in user_tokens collection as backup
                        storeBackupToken(fcmToken)
                    }
                    .addOnFailureListener { e ->
                        Timber.e(e, "❌ Failed to store FCM token in user document")
                        // Try alternative approach
                        forceStoreFCMToken(currentUser.uid, fcmToken)
                    }
            }
        }
    }

    private fun storeBackupToken(fcmToken: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val deviceId = "${Build.BRAND}_${Build.MODEL}_${Build.SERIAL}"

            val tokenData = mapOf(
                "token" to fcmToken,
                "userId" to currentUser.uid,
                "platform" to "android",
                "deviceId" to deviceId,
                "active" to true,
                "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            FirebaseFirestore.getInstance()
                .collection("user_tokens")
                .document("${currentUser.uid}_$deviceId")
                .set(tokenData)
                .addOnSuccessListener {
                    Timber.d("✅ Backup token stored in user_tokens collection")
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "❌ Failed to store backup token")
                }
        }
    }

    private fun verifyStoredToken(userId: String, expectedToken: String) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val storedToken = doc.getString("fcmToken")
                Timber.d("🔍 Verifying stored token:")
                Timber.d("   Expected: ${expectedToken.take(20)}...")
                Timber.d("   Stored: ${storedToken?.take(20)}...")
                Timber.d("   Match: ${storedToken == expectedToken}")

                if (storedToken.isNullOrEmpty()) {
                    Timber.e("❌ Token verification failed: No token stored")
                } else if (storedToken != expectedToken) {
                    Timber.w("⚠️ Token mismatch, forcing update...")
                    forceStoreFCMToken(userId, expectedToken)
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "❌ Failed to verify stored token")
            }
    }

    private fun forceStoreFCMToken(userId: String, fcmToken: String) {
        Timber.d("🔄 Force storing FCM token for user: $userId")

        val userData = hashMapOf<String, Any>(
            "fcmToken" to fcmToken,
            "fcmTokenUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update(userData)
            .addOnSuccessListener {
                Timber.d("✅ FCM token force updated in existing document")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "❌ Failed to force update token")
            }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        Timber.d("🔍 Checking for notification intent...")
        intent?.extras?.let { extras ->
            Timber.d("📦 Intent extras found: ${extras.keySet().joinToString(", ")}")

            val type = extras.getString("type")
            Timber.d("📋 Notification type: $type")

            when (type) {
                "ACTIVITY_BROADCAST" -> {
                    Timber.d("🎯 RECEIVED BROADCAST NOTIFICATION!")
                    showToast("Activity notification received!")
                }

                "EMERGENCY_REPORT" -> {
                    Timber.d("🚨 RECEIVED EMERGENCY REPORT NOTIFICATION!")
                    showToast("Emergency report notification received!")

                    // Trigger UI update via LiveData or similar
                    EmergencyReportNotificationManager.hasNewEmergencyReport = true
                }

                else -> {
                    Timber.d("ℹ️ Unknown notification type: $type")
                }
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        Timber.d("⏸️ MainActivity onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("❌ MainActivity onDestroy")
    }

    companion object {
        // Static flag to track if we should open emergency reports
        var shouldOpenEmergencyReports = false
    }
}

// Helper object to manage emergency report notifications
object EmergencyReportNotificationManager {
    var hasNewEmergencyReport = false
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()
    val emergencyViewModel: EmergencyReportViewModel = viewModel()

    // Check for notification intent when app starts
    LaunchedEffect(Unit) {
        // Check if we should open emergency reports from notification
        if (MainActivity.shouldOpenEmergencyReports) {
            MainActivity.shouldOpenEmergencyReports = false
            // You can trigger a state change here to show emergency reports dialog
        }
    }

    NavHost(
        navController = navController,
        startDestination = "login",
        enterTransition = { fadeIn(animationSpec = tween(50)) },
        exitTransition = { fadeOut(animationSpec = tween(50)) }
    ) {
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

        composable("signup") {
            SignupScreen(navController = navController)
        }

        composable("main") {
            MainScreen(
                mainNavController = navController,
                homeViewModel = homeViewModel,
                emergencyViewModel = emergencyViewModel
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScreen(
    mainNavController: androidx.navigation.NavHostController,
    homeViewModel: HomeViewModel,
    emergencyViewModel: EmergencyReportViewModel
) {
    val bottomNavController = rememberNavController()
    val context = LocalContext.current

    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var isAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkAdminStatus { adminStatus ->
            isAdmin = adminStatus
        }
    }

    // Handle notification data when app opens from notification
    LaunchedEffect(Unit) {
        // Process any pending notification data
        // This would come from MainActivity's intent extras
    }

    val allItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Map,
        BottomNavItem.KnownPeople,
        BottomNavItem.Admin,
        BottomNavItem.Profile
    )

    val bottomNavRoutes = listOf(
        BottomNavItem.Home.route,
        BottomNavItem.Map.route,
        BottomNavItem.KnownPeople.route,
        BottomNavItem.Profile.route,
        BottomNavItem.Admin.route
    )

    val isBottomNavRoute = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                navController = bottomNavController,
                items = allItems,
                isAdmin = isAdmin,
                shouldShowBottomNav = isBottomNavRoute
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = bottomNavController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { fadeIn(animationSpec = tween(50)) },
            exitTransition = { fadeOut(animationSpec = tween(50)) }
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    navController = bottomNavController,
                    homeViewModel = homeViewModel,
                    emergencyViewModel = emergencyViewModel
                )
            }
            composable(BottomNavItem.Map.route) {
                MapScreen()
            }
            composable(BottomNavItem.KnownPeople.route) {
                KnownPeopleScreen()
            }
            composable(BottomNavItem.Profile.route) {
                ProfileScreen(
                    navController = bottomNavController,
                    onLogout = {
                        // Unsubscribe from topics on logout
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("activity_broadcast")
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Timber.d("✅ Unsubscribed from broadcast topic")
                                }
                            }

                        FirebaseMessaging.getInstance().unsubscribeFromTopic("emergency_reports")
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Timber.d("✅ Unsubscribed from emergency reports topic")
                                }
                            }

                        val userId = FirebaseAuth.getInstance().currentUser?.uid
                        userId?.let {
                            FirebaseMessaging.getInstance().unsubscribeFromTopic("user_$it")
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Timber.d("✅ Unsubscribed from user topic: user_$it")
                                    }
                                }
                        }

                        mainNavController.navigate("login") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                )
            }
            composable(BottomNavItem.Admin.route) {
                AdminScreen()
            }

            // Home nested routes
            composable("camera") {
                CameraScreen(navController = bottomNavController)
            }
            composable("monitor") {
                MonitorScreen(navController = bottomNavController)
            }

            composable(
                route = "activity_detail/{captureId}",
                arguments = listOf(
                    navArgument("captureId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val captureId = backStackEntry.arguments?.getString("captureId")
                ActivityDetailScreen(
                    navController = bottomNavController,
                    captureId = captureId
                )
            }

            composable("activity_detail") {
                ActivityDetailScreen(
                    navController = bottomNavController,
                    captureId = null
                )
            }

            // Profile nested routes
            composable("view_profile") {
                ViewProfileScreen(navController = bottomNavController)
            }
            composable("location_label") {
                LocationLabelScreen(navController = bottomNavController)
            }

            // Stream viewer route
            composable("stream_viewer/{sessionId}") { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                StreamViewerScreen(
                    sessionId = sessionId,
                    navController = bottomNavController
                )
            }
        }
    }
}

private fun checkAdminStatus(onResult: (Boolean) -> Unit) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val db = FirebaseFirestore.getInstance()

    if (currentUser != null) {
        db.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                val isAdmin = document.getBoolean("admin") ?: false
                onResult(isAdmin)
            }
            .addOnFailureListener {
                Timber.e(it, "Failed to check admin status")
                onResult(false)
            }
    } else {
        onResult(false)
    }
}