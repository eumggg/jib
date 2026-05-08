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
import androidx.navigation.navDeepLink
import com.jib.app.auth.AuthViewModel
import com.jib.app.auth.LoginScreen
import com.jib.app.auth.RegisterScreen
import com.jib.app.ui.map.MapScreen
import com.jib.app.ui.profile.ProfileScreen
import com.jib.app.ui.station.StationDetailScreen
import com.jib.app.ui.submit.SubmitStationScreen

private const val ROUTE_MAP = "map"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_STATION = "station/{stationId}"
private const val ROUTE_SUBMIT = "submit-station"
private const val ROUTE_PROFILE = "profile"

@Composable
fun JibNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

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
            MapScreen(
                onStationClick = { id -> navController.navigate("station/$id") },
                onAddStation = { navController.navigate(ROUTE_SUBMIT) },
                onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
            )
        }
        composable(
            route = ROUTE_STATION,
            arguments = listOf(navArgument("stationId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "jib://station/{stationId}" }),
        ) {
            StationDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SUBMIT) {
            SubmitStationScreen(
                onClose = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
            )
        }
        composable(ROUTE_PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onStationClick = { id ->
                    navController.navigate("station/$id")
                },
            )
        }
    }
}
