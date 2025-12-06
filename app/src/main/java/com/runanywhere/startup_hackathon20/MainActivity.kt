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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.runanywhere.startup_hackathon20.ui.theme.Startup_hackathon20Theme

class MainActivity : ComponentActivity() {

    // Notification permission launcher (Android 13+)
    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _granted: Boolean ->
        // Optional: handle result, show rationale, etc.
    }

    // RECORD_AUDIO permission launcher — call when user intentionally opens voice features
    private val requestAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        // Optional: handle audio permission result (granted==true -> start voice, else show message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Startup_hackathon20Theme {
                AppNavigation()
            }
        }

        // Request notification permission for Android 13+ (optional, UX decision)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Public helper you can call from UI (e.g. Dashboard or ChatScreen) to ask for RECORD_AUDIO
     * before starting the Voice Coach. This keeps permission flow inside the Activity.
     *
     * Example usage from a composable:
     *   (context as? MainActivity)?.requestAudioPermissionIfNeeded()
     *
     * Or better: expose an event to Activity from NavController or your Activity->ViewModel wiring.
     */
    fun requestAudioPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Show welcome screen EVERY TIME app opens (not using SharedPreferences)
    var showWelcome by remember {
        mutableStateOf(true)  // Always starts as true
    }

    // route constants
    val ROUTE_DASH = "dashboard"
    val ROUTE_CHAT = "chat"
    val ROUTE_ANALYSIS = "analysis"
    val ROUTE_CASH = "cash_flow"
    val ROUTE_MODELS = "models"

    LaunchedEffect(Unit) {
        // Load persisted data on app start
        chatViewModel.loadPersistedData(context)
        chatViewModel.initializeVoice(context)
    }

    // Show welcome screen every time app opens
    if (showWelcome) {
        WelcomeScreen(
            onComplete = {
                showWelcome = false
                // Don't save to SharedPreferences - will show again on next app open
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            BottomBar(
                navController,
                routes = listOf(ROUTE_DASH, ROUTE_CHAT, ROUTE_ANALYSIS, ROUTE_CASH, ROUTE_MODELS)
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_DASH,
            modifier = Modifier.padding(innerPadding)
        ) {

            composable(ROUTE_DASH) {
                FintechDashboard(navController = navController, viewModel = chatViewModel)
            }

            // Plain chat route (no param)
            composable(ROUTE_CHAT) {
                LaunchedEffect(Unit) {
                    android.util.Log.d("NAV", "Composed ChatScreen for route: $ROUTE_CHAT")
                }
                ChatScreen(viewModel = chatViewModel) // default voice mode = false
            }

            // SMS analysis
            composable(ROUTE_ANALYSIS) {
                SmsAnalysisScreen(viewModel = chatViewModel)
            }

            composable(ROUTE_CASH) {
                CashFlowScreen(viewModel = chatViewModel)
            }

            composable(ROUTE_MODELS) {
                ModelManagementScreen(viewModel = chatViewModel)
            }

            // backward-compatible alias
            composable("chatbot") {
                ChatScreen(viewModel = chatViewModel)
            }

            // Chat route WITH voice param
            composable(
                route = "chat?start_voice={start_voice}",
                arguments = listOf(navArgument("start_voice") {
                    defaultValue = "false"
                })
            ) { backStackEntry ->
                val startVoice = backStackEntry.arguments?.getString("start_voice")?.toBoolean() ?: false

                // If you want to request audio permission automatically here (not recommended),
                // you could do that by casting LocalContext to MainActivity and calling the helper:
                // (LocalContext.current as? MainActivity)?.requestAudioPermissionIfNeeded()
                //
                // I avoided automatic permission prompts to respect UX best-practices; instead
                // ChatScreen/startVoiceCoach checks permissions and prompts user or sets status.

                ChatScreen(viewModel = chatViewModel, startInVoiceMode = startVoice)
            }
        }
    }
}

@Composable
fun BottomBar(
    navController: NavHostController,
    routes: List<String>
) {
    val items = listOf(
        NavigationItem(route = routes[0], label = "Home", icon = Icons.Default.Home),
        NavigationItem(route = routes[1], label = "AI Chat", icon = Icons.Default.Chat),
        NavigationItem(route = routes[2], label = "SMS\nAnalysis", icon = Icons.Default.Analytics),
        NavigationItem(route = routes[3], label = "Cash Flow", icon = Icons.Default.MonetizationOn),
        NavigationItem(route = routes[4], label = "Models", icon = Icons.Default.Settings)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                },
                selected = currentRoute?.startsWith(item.route) == true,
                onClick = {
                    // Navigate to the selected route
                    if (item.route == routes[0]) {
                        // HOME button - always clear back stack and go to dashboard fresh
                        navController.navigate(item.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        // Other tabs - normal navigation
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
