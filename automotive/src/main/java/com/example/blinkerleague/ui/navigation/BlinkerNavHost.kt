package com.example.blinkerleague.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.blinkerleague.ui.demo.DemoScreen
import com.example.blinkerleague.ui.home.HomeScreen
import com.example.blinkerleague.ui.missions.MissionsScreen
import com.example.blinkerleague.ui.result.PlaysScreen
import com.example.blinkerleague.ui.result.ResultScreen

/** 화면 경로 (screen-plan 4.1). 온보딩은 해당 단계에서 */
private object Routes {
    const val HOME = "home"
    const val DEMO = "demo"
    const val QUEST = "quest"
    const val RESULT = "result/{tripId}"
    const val PLAYS = "result/{tripId}/plays"
    fun result(tripId: String) = "result/$tripId"
    fun plays(tripId: String) = "result/$tripId/plays"
}

@Composable
fun BlinkerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenQuest = { navController.navigate(Routes.QUEST) },
                onOpenTrip = { tripId -> navController.navigate(Routes.result(tripId)) },
                onOpenDemo = { navController.navigate(Routes.DEMO) },
            )
        }
        // 시나리오 재생: 디버그 빌드 홈에서만 들어옴. 재생이 끝나면 결과로 이동(예외, screen-plan 4.1)
        composable(Routes.DEMO) {
            DemoScreen(
                onTripFinished = { tripId -> navController.navigate(Routes.result(tripId)) },
                onOpenMissions = { navController.navigate(Routes.QUEST) },
            )
        }
        composable(Routes.QUEST) {
            MissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            val tripId = checkNotNull(entry.arguments?.getString("tripId"))
            ResultScreen(
                onOpenPlays = { navController.navigate(Routes.plays(tripId)) },
                onOpenQuest = { navController.navigate(Routes.QUEST) },
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(
            route = Routes.PLAYS,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) {
            PlaysScreen(onBack = { navController.popBackStack() })
        }
    }
}
