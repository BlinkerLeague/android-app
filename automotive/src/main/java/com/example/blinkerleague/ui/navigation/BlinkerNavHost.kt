package com.example.blinkerleague.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.ui.achievements.AchievementsScreen
import com.example.blinkerleague.ui.demo.DemoScreen
import com.example.blinkerleague.ui.home.HomeScreen
import com.example.blinkerleague.ui.onboarding.OnboardingScreen
import com.example.blinkerleague.ui.quest.QuestScreen
import com.example.blinkerleague.ui.report.WeeklyReportScreen
import com.example.blinkerleague.ui.result.MetricDetailScreen
import com.example.blinkerleague.ui.result.PlaysScreen
import com.example.blinkerleague.ui.result.ResultScreen
import com.example.blinkerleague.ui.settings.SettingsScreen
import com.example.blinkerleague.ui.wallet.WalletScreen

/** 화면 경로 (screen-plan 4.1) */
private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val WALLET = "wallet"
    const val ACHIEVEMENTS = "achievements"
    const val REPORT = "report"
    const val SETTINGS = "settings"
    const val DEMO = "demo"
    const val QUEST = "quest"
    const val RESULT = "result/{tripId}"
    const val PLAYS = "result/{tripId}/plays"
    const val METRIC = "result/{tripId}/metric/{metric}"
    fun metric(tripId: String, metric: String) = "result/$tripId/metric/$metric"
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
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
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
                composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onOpenReport = { navController.navigate(Routes.REPORT) }) }
                composable(Routes.REPORT) { WeeklyReportScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.WALLET) { WalletScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
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
                        onOpenMetric = { item -> navController.navigate(Routes.metric(tripId, item.name)) },
                        onOpenQuest = { navController.navigate(Routes.QUEST) },
                        onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }
                composable(
                    route = Routes.PLAYS,
                    arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
                ) { entry ->
                    val tripId = checkNotNull(entry.arguments?.getString("tripId"))
                    PlaysScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCard = { navController.navigate(Routes.metric(tripId, "TURN_SIGNAL")) },
                    )
                }
                composable(
                    route = Routes.METRIC,
                    arguments = listOf(
                        navArgument("tripId") { type = NavType.StringType },
                        navArgument("metric") { type = NavType.StringType },
                    ),
                ) {
                    MetricDetailScreen(onBack = { navController.popBackStack() })
                }
            }
        }
        // 하단 탭: 맨 위 화면(홈·업적·지갑·설정)에서만 보임. 상세 화면·온보딩·시나리오 재생에는 없음
        if (currentRoute in TABS.map { it.route }) BottomTabs(navController, currentRoute)
    }
}

private data class Tab(val route: String, val label: Int)

/** 2차 하단 탭 (목업 하단 바의 홈·리그·업적·지갑·설정 중 지금 있는 것만) */
private val TABS = listOf(
    Tab(Routes.HOME, R.string.tab_home),
    Tab(Routes.ACHIEVEMENTS, R.string.tab_achievements),
    Tab(Routes.WALLET, R.string.tab_wallet),
    Tab(Routes.SETTINGS, R.string.tab_settings),
)

@Composable
private fun BottomTabs(navController: NavHostController, currentRoute: String?) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            TABS.forEach { tab ->
                val selected = tab.route == currentRoute
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Tab) {
                            if (!selected) {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
