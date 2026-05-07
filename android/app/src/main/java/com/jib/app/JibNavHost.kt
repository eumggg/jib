package com.jib.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun JibNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "map") {
        composable("map") { Text("Map — coming soon") }
        composable("login") { Text("Login — coming soon") }
    }
}
