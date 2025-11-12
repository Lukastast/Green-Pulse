package com.example.green_pulse_android.helpers

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

    @Stable
    class GreenPulseAppState(
        private val snackbarHostState: SnackbarHostState,
        val navController: NavHostController,
        private val snackbarManager: SnackbarManager,
        coroutineScope: CoroutineScope
    ) {
        init {
            coroutineScope.launch {
                snackbarManager.snackbarMessages.filterNotNull().collect { message ->
                    snackbarHostState.showSnackbar(message)
                    snackbarManager.clearSnackbarState()
                }
            }
        }

        fun popUp() {
            navController.popBackStack()
        }

        fun navigate(route: String) {
            navController.navigate(route) { launchSingleTop = true }
        }
        @Composable
        fun shouldShowBottomBar(): Boolean {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val currentRoute = currentDestination?.route
            return currentRoute !in listOf(SPLASH_SCREEN, LOGIN_SCREEN, SIGNUP_SCREEN)
        }

        fun navigateAndPopUp(route: String, popUp: String) {
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo(popUp) { inclusive = true }
            }
        }

        fun clearAndNavigate(route: String) {
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo(0) { inclusive = true }
            }
        }
    }
