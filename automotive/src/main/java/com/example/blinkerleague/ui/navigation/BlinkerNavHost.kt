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
import com.example.blinkerleague.ui.summary.SummaryScreen

/** 화면 경로 (screen-plan 4.1). 온보딩·결과 화면 이름 변경은 해당 단계에서 */
private object Routes {
    const val HOME = "home"
    const val DEMO = "demo"
    const val QUEST = "quest"
    const val SUMMARY = "summary/{tripId}"
    fun summary(tripId: String) = "summary/$tripId"
}

@Composable
fun BlinkerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenQuest = { navController.navigate(Routes.QUEST) },
                onOpenTrip = { tripId -> navController.navigate(Routes.summary(tripId)) },
                onOpenDemo = { navController.navigate(Routes.DEMO) },
            )
        }
        // 시나리오 재생: 디버그 빌드 홈에서만 들어옴. 재생이 끝나면 결과로 이동(예외, screen-plan 4.1)
        composable(Routes.DEMO) {
            DemoScreen(
                onTripFinished = { tripId -> navController.navigate(Routes.summary(tripId)) },
                onOpenMissions = { navController.navigate(Routes.QUEST) },
            )
        }
        composable(Routes.QUEST) {
            MissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.SUMMARY,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) {
            SummaryScreen(onDone = { navController.popBackStack() })
        }
    }
}
