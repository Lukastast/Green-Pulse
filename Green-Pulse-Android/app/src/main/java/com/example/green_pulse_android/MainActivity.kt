package com.example.green_pulse_android

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.green_pulse_android.authorization.LoginScreen
import com.example.green_pulse_android.authorization.SignupScreen
import com.example.green_pulse_android.helpers.GreenPulseAppState
import com.example.green_pulse_android.helpers.HOME_SCREEN
import com.example.green_pulse_android.helpers.LOGIN_SCREEN
import com.example.green_pulse_android.helpers.PLANT_VIEW_SCREEN
import com.example.green_pulse_android.helpers.SIGNUP_SCREEN
import com.example.green_pulse_android.helpers.SPLASH_SCREEN
import com.example.green_pulse_android.helpers.SnackbarManager
import com.example.green_pulse_android.plants.HomeScreen
import com.example.green_pulse_android.plants.PlantViewScreen
import com.example.green_pulse_android.splash.SplashScreen
import com.example.green_pulse_android.ui.theme.GreenPulseAndroidTheme
import kotlinx.coroutines.CoroutineScope

@Composable
fun GreenPulseApp() {
    GreenPulseAndroidTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val snackbarHostState = remember { SnackbarHostState() }
            val appState = rememberAppState(snackbarHostState)

            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (appState.shouldShowBottomBar()) {
                        MainBottomBar(navController = appState.navController)
                    }
                }
            ) { innerPaddingModifier ->
                NavHost(
                    navController = appState.navController,
                    startDestination = SPLASH_SCREEN,
                    modifier = Modifier.padding(innerPaddingModifier)
                ) {
                    GreenPulseGraph(appState)
                }
            }
        }
    }
}

@Composable
fun rememberAppState(
    snackbarHostState: SnackbarHostState,
    navController: NavHostController = rememberNavController(),
    snackbarManager: SnackbarManager = SnackbarManager,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): GreenPulseAppState {
    return remember(snackbarHostState, navController, snackbarManager, coroutineScope) {
        GreenPulseAppState(snackbarHostState, navController, snackbarManager, coroutineScope)
    }
}

fun NavGraphBuilder.GreenPulseGraph(appState: GreenPulseAppState) {
    composable(LOGIN_SCREEN) {
        LoginScreen(
            openScreen = { route -> appState.navigate(route) },
            openAndPopUp = { route, popUp -> appState.navigateAndPopUp(route, popUp) }
        )
    }

    composable(PLANT_VIEW_SCREEN) {
        PlantViewScreen(
            openScreen = { route -> appState.navigate(route) }
        )
    }

    composable(HOME_SCREEN) {
        HomeScreen(
            restartApp = { route -> appState.clearAndNavigate(route) },
            openScreen = { route -> appState.navigate(route) }
        )
    }

    composable(SIGNUP_SCREEN) {
        SignupScreen(openAndPopUp = { route, popUp -> appState.navigateAndPopUp(route, popUp) })
    }

    composable(SPLASH_SCREEN) {
        SplashScreen(
            navController = appState.navController
        )
    }
}

@Composable
private fun MainBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val items = listOf(
        BottomNavItem(
            name = "Home",
            route = HOME_SCREEN,
            icon = Icons.Default.Home
        ),
        BottomNavItem(
            name = "Plants",
            route = PLANT_VIEW_SCREEN,
            icon = Icons.Default.LocalFlorist
        )
    )

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.name) },
                label = { Text(item.name) },
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(HOME_SCREEN) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class BottomNavItem(
    val name: String,
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)