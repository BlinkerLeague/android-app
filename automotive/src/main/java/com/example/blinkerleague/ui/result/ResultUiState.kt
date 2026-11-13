package com.example.blinkerleague.ui.result

import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.model.ScoreState
import com.example.blinkerleague.domain.model.SignalType
import com.example.blinkerleague.domain.model.TodayMission
import com.example.blinkerleague.domain.model.TripResult
import com.example.blinkerleague.domain.model.TurnDirection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 경기 결과 (SCR-02, screen-plan 4.4) */
sealed interface ResultUiState {
    data object Loading : ResultUiState

    data object NotFound : ResultUiState

    data class Loaded(
        /** 결과 집계 중 / 점수 / 회전 없음 / 짧은 이동 / 업로드 거부 */
        val scoreState: ScoreState,
        val score: Int?,
        /** 50점 미만·점수 없음이면 null (등급 칸 비움) */
        val grade: String?,
        val distanceKm: Double,
        /** 득점 = 깜빡이를 켠 회전, 실점 = 안 켠 회전 (반칙은 쓰지 않음, screen-plan 9장) */
        val goals: Int,
        val conceded: Int,
        val metrics: List<MetricRow>,
        /** 이번 운행을 반영한 오늘 퀘스트. 못 받으면 null (카드 숨김) */
        val mission: TodayMission? = null,
        /** 이 운행으로 적립된 운행 포인트(상한 적용 후). 모르면 null (표시 안 함) */
        val earnedPoints: Int? = null,
        /** 머리말 "오늘의 N번째 경기 종료"의 N. 오늘 운행이 아니거나 모르면 null */
        val dayOrdinal: Int? = null,
    ) : ResultUiState
}

enum class MetricItem { TURN_SIGNAL, SMOOTH_DRIVING, SAFETY_WARNING, NIGHT_HEADLIGHT }

/** 항목별 점수 한 줄. P0은 깜빡이만 값이 있고 나머지는 "준비 중" */
data class MetricRow(val item: MetricItem, val value: MetricValue)

sealed interface MetricValue {
    data class Score(val score: Int) : MetricValue

    /** 서버 점수를 기다리는 중 */
    data object Pending : MetricValue

    /** 측정할 회전이 없었음 */
    data object NoTurn : MetricValue

    /** 짧은 이동이라 경기에서 제외됨 */
    data object Excluded : MetricValue

    /** 업로드가 거부돼 점수가 없음 */
    data object NotScored : MetricValue

    /** 이 차량 미지원 (0점이 아니라 제외) */
    data object Unsupported : MetricValue

    /** P1 항목 */
    data object ComingSoon : MetricValue
}

/** 플레이 타임라인 (SCR-02a) */
data class PlaysUiState(
    val loading: Boolean = true,
    /** 예: "08:10" */
    val startedAt: String = "",
    val endedAt: String = "",
    val distanceKm: Double = 0.0,
    val goals: Int = 0,
    val conceded: Int = 0,
    val plays: List<PlayCard> = emptyList(),
)

data class PlayCard(
    val time: String,
    val direction: TurnDirection,
    /** 득점(깜빡이 켬) / 실점(안 켬) */
    val goal: Boolean,
)

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun TripResult.toResultUiState(
    mission: TodayMission?,
    grades: List<GradeThreshold>,
    earnedPoints: Int? = null,
): ResultUiState.Loaded {
    val goals = turns.count { it.signalOn }
    val canMeasureTurns = SignalType.TURN_SIGNAL in supported && SignalType.STEERING_ANGLE in supported
    val turnValue: MetricValue = when {
        !canMeasureTurns -> MetricValue.Unsupported
        scoreState == ScoreState.EXCLUDED_SHORT -> MetricValue.Excluded
        scoreState == ScoreState.REJECTED -> MetricValue.NotScored
        // 회전이 없으면 다른 항목으로 점수가 나도(SCORED, 서버 D28) 깜빡이 항목은 "회전 없음"
        scoreState == ScoreState.NO_TURN || turns.isEmpty() -> MetricValue.NoTurn
        scoreState == ScoreState.SCORED && score != null -> MetricValue.Score(score)
        else -> MetricValue.Pending
    }
    return ResultUiState.Loaded(
        scoreState = scoreState,
        score = score,
        grade = if (scoreState == ScoreState.SCORED) GradeThreshold.gradeOf(score, grades) else null,
        distanceKm = distanceKm,
        goals = goals,
        conceded = turns.size - goals,
        metrics = listOf(
            MetricRow(MetricItem.TURN_SIGNAL, turnValue),
            MetricRow(MetricItem.SMOOTH_DRIVING, MetricValue.ComingSoon),
            MetricRow(MetricItem.SAFETY_WARNING, MetricValue.ComingSoon),
            MetricRow(MetricItem.NIGHT_HEADLIGHT, MetricValue.ComingSoon),
        ),
        mission = mission,
        earnedPoints = earnedPoints?.takeIf { scoreState == ScoreState.SCORED && it > 0 },
    )
}

internal fun TripResult.toPlaysUiState(zone: ZoneId): PlaysUiState {
    fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(TIME)
    val goals = turns.count { it.signalOn }
    return PlaysUiState(
        loading = false,
        startedAt = time(startedAtMs),
        endedAt = time(endedAtMs),
        distanceKm = distanceKm,
        goals = goals,
        conceded = turns.size - goals,
        plays = turns.sortedBy { it.occurredAtMs }.map { PlayCard(time(it.occurredAtMs), it.direction, it.signalOn) },
    )
}
