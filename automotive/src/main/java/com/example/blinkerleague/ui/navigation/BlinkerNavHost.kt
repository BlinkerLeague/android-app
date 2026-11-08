package com.example.blinkerleague.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blinkerleague.BlinkerLeagueApplication
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.blinkerleague.ui.demo.DemoScreen
import com.example.blinkerleague.ui.home.HomeScreen
import com.example.blinkerleague.ui.onboarding.OnboardingScreen
import com.example.blinkerleague.ui.quest.QuestScreen
import com.example.blinkerleague.ui.result.PlaysScreen
import com.example.blinkerleague.ui.result.ResultScreen

/** 화면 경로 (screen-plan 4.1) */
private object Routes {
    const val ONBOARDING = "onboarding"
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
    val driver = (LocalContext.current.applicationContext as BlinkerLeagueApplication).container.driverRepository
    val registered by driver.registered.collectAsStateWithLifecycle()
    // 시작 화면: 등록 전이면 온보딩, 등록돼 있으면 홈 (처음 한 번만 정함)
    val start = remember { if (driver.registered.value) Routes.HOME else Routes.ONBOARDING }
    // 토큰이 거부돼 등록이 풀리면(재발급 실패) 온보딩으로 돌아감
    LaunchedEffect(registered) {
        val current = navController.currentDestination?.route
        if (!registered && current != null && current != Routes.ONBOARDING) {
            navController.navigate(Routes.ONBOARDING) { popUpTo(0) }
        }
    }
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onRegistered = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
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
            QuestScreen(onBack = { navController.popBackStack() })
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
