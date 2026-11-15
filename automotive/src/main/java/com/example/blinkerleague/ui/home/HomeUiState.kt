package com.example.blinkerleague.ui.home

import com.example.blinkerleague.domain.model.MatchOutcome
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.service.RecordingStatus
import com.example.blinkerleague.ui.achievements.StreakCard

data class HomeUiState(
    /** 오늘의 퀘스트. 불러오기 전이거나 못 받으면 null */
    val quest: TodayMission? = null,
    val questLoading: Boolean = true,
    val recentTrips: List<RecentTripRow> = emptyList(),
    /** 퀘스트 연승 (2차, 서버 D6에 따라 다시 보임). 못 받으면 null → 카드 숨김 */
    val streak: StreakCard? = null,
    val recordingStatus: RecordingStatus = RecordingStatus.OFF,
    val notices: List<HomeNotice> = emptyList(),
    /** 시나리오 재생 진입 버튼 (디버그 빌드만, screen-plan 1장) */
    val showDemoEntry: Boolean = false,
)

data class RecentTripRow(
    val tripId: String,
    /** 끝난 날: 오늘·어제·그 밖(날짜 [date] 표시) */
    val day: TripDay,
    /** 예: "11.06" */
    val date: String,
    /** 예: "08:42" */
    val time: String,
    val scoreState: ScoreState,
    val score: Int?,
    /** 50점 미만이거나 점수가 없으면 null */
    val grade: String?,
    /** 득점: 깜빡이를 켠 회전 수 */
    val goals: Int = 0,
    /** 실점: 깜빡이를 켜지 않은 회전 수 */
    val conceded: Int = 0,
    /** 회전이 없으면 null */
    val outcome: MatchOutcome? = null,
    val distanceKm: Double = 0.0,
)

enum class TripDay { TODAY, YESTERDAY, OTHER }

/** 홈 상단 안내 (screen-plan 5장 예외 상태) */
enum class HomeNotice {
    /** 속도 권한이 없어 판정 정확도가 제한돼요 */
    NO_SPEED_PERMISSION,

    /** 기록이 꺼져 있어요 */
    RECORDING_OFF,
}
