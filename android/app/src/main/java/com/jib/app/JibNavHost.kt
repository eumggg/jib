package com.jib.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jib.app.auth.AuthViewModel
import com.jib.app.auth.LoginScreen
import com.jib.app.auth.RegisterScreen
import com.jib.app.ui.map.MapScreen
import com.jib.app.ui.station.StationDetailScreen

private const val ROUTE_MAP = "map"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_STATION = "station/{stationId}"

@Composable
fun JibNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

    // Start at login when unauthenticated, map when authenticated.
    val startDestination = if (currentUser != null) ROUTE_MAP else ROUTE_LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(ROUTE_MAP) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(ROUTE_REGISTER) },
            )
        }
        composable(ROUTE_REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(ROUTE_MAP) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
        composable(ROUTE_MAP) {
            MapScreen(onStationClick = { id -> navController.navigate("station/$id") })
        }
        composable(
            route = ROUTE_STATION,
            arguments = listOf(navArgument("stationId") { type = NavType.StringType }),
        ) {
            // stationId is read by StationDetailViewModel via SavedStateHandle.
            // Back navigation pops the back stack so the map keeps its camera position.
            StationDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
