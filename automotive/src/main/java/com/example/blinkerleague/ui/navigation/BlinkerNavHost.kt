package com.example.blinkerleague.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.blinkerleague.ui.demo.DemoScreen
import com.example.blinkerleague.ui.summary.SummaryScreen

private object Routes {
    const val DEMO = "demo"
    const val SUMMARY = "summary/{tripId}"
    fun summary(tripId: String) = "summary/$tripId"
}

@Composable
fun BlinkerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DEMO) {
        composable(Routes.DEMO) {
            DemoScreen(onTripFinished = { tripId -> navController.navigate(Routes.summary(tripId)) })
        }
        composable(
            route = Routes.SUMMARY,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) {
            SummaryScreen(onDone = { navController.popBackStack() })
        }
    }
}
