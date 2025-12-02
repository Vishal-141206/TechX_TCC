package com.runanywhere.startup_hackathon20

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.runanywhere.startup_hackathon20.ui.theme.Startup_hackathon20Theme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Makes the app draw edge-to-edge
        enableEdgeToEdge()

        setContent {
            Startup_hackathon20Theme {
                AppNavigation()
            }
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()

    // ROUTES: explicit constants to avoid typos
    val ROUTE_DASH = "dashboard"
    val ROUTE_CHAT = "chat"        // chatbot
    val ROUTE_ANALYSIS = "analysis"// sms analysis
    val ROUTE_CASH = "cash_flow"
    val ROUTE_MODELS = "models"

    // Initialize voice when app starts
    LaunchedEffect(Unit) {
        // Voice will be initialized when needed in ChatViewModel
    }

    // Scaffold with BottomBar (keeps UI consistent)
    Scaffold(
        bottomBar = {
            // show bottom bar on all screens (you can add logic to hide on specific routes)
            BottomBar(navController, routes = listOf(
                ROUTE_DASH,
                ROUTE_CHAT,
                ROUTE_ANALYSIS,
                ROUTE_CASH,
                ROUTE_MODELS
            ))
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_DASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_DASH) {
                Dashboard(navController = navController, viewModel = chatViewModel)
            }

            // CRITICAL: chat must map to ChatScreen
            composable(ROUTE_CHAT) {
                LaunchedEffect(Unit) {
                    android.util.Log.d("NAV", "Composed ChatScreen for route: $ROUTE_CHAT")
                }
                ChatScreen(viewModel = chatViewModel)
            }

            // SMS analysis is a DIFFERENT route
            composable(ROUTE_ANALYSIS) {
                LaunchedEffect(Unit) {
                    android.util.Log.d("NAV", "Composed SmsAnalysisScreen for route: $ROUTE_ANALYSIS")
                }
                SmsAnalysisScreen(viewModel = chatViewModel)
            }

            composable(ROUTE_CASH) {
                CashFlowScreen(viewModel = chatViewModel)
            }

            composable(ROUTE_MODELS) {
                ModelManagementScreen(viewModel = chatViewModel)
            }

            // backward-compatible alias (if other code still uses it)
            composable("chatbot") {
                ChatScreen(viewModel = chatViewModel)
            }
        }
    }
}

/**
 * BottomBar implementation for all 5 main screens.
 */
@Composable
fun BottomBar(
    navController: NavHostController,
    routes: List<String>
) {
    // Define navigation items with proper icons and labels
    val items = listOf(
        NavigationItem(
            route = routes[0],
            label = "Dashboard",
            icon = Icons.Default.Home
        ),
        NavigationItem(
            route = routes[1],
            label = "Chat",
            icon = Icons.Default.Chat
        ),
        NavigationItem(
            route = routes[2],
            label = "SMS",
            icon = Icons.Default.Analytics
        ),
        NavigationItem(
            route = routes[3],
            label = "Cash Flow",
            icon = Icons.Default.MonetizationOn
        ),
        NavigationItem(
            route = routes[4],
            label = "Models",
            icon = Icons.Default.Settings
        )
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            // Keep navigation simple for demo: singleTop behavior
                            launchSingleTop = true
                            // Clear back stack to avoid deep navigation issues
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

// Data class for navigation items
data class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)