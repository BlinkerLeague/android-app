package com.example.blinkerleague.domain.config

/**
 * 측정 기준값. 서버 `GET /api/config/thresholds`의 `trip`, `turn`과 같은 이름·단위를 씀.
 * 서버 값을 받지 못하면 [DEFAULT]를 사용함.
 */
data class DetectionConfig(
    /** 서버 기준값 버전. 0은 앱 기본값 */
    val version: Long = 0,
    val trip: TripConfig = TripConfig(),
    val turn: TurnConfig = TurnConfig(),
    val guard: GuardConfig = GuardConfig(),
    val grades: List<GradeThreshold> = GradeThreshold.DEFAULT,
    /** 항목별 팁 문구 (항목 상세 SCR-02b). 서버 기준값 `tips`가 오면 항목별로 덮어씀 */
    val tips: Map<String, String> = DEFAULT_TIPS,
    val league: LeagueConfig = LeagueConfig(),
    val point: PointConfig = PointConfig(),
) {
    companion object {
        /** 서버 기본 기준값의 팁 문구. 깜빡이는 앱 판정 기준(회전 5초 전)에 맞춘 문구 */
        val DEFAULT_TIPS = mapOf(
            "TURN_SIGNAL" to "회전 5초 전에 깜빡이를 켜 보세요.",
            "SMOOTH_DRIVING" to "출발은 부드럽게, 정지는 미리 천천히 해 보세요.",
            "SAFETY_WARNING" to "앞차와 거리를 넉넉히 두고 주변을 살펴 보세요.",
            "NIGHT_HEADLIGHT" to "해가 지면 전조등을 꼭 켜 주세요.",
        )

        val DEFAULT = DetectionConfig()
    }
}

data class TripConfig(
    /** 이보다 짧은 운행은 점수에서 제외. 1km (기획서 MVP 기준, 11.09 결정. 서버 기준값은 서버 요청 10번으로 1.0 요청) */
    val minDistanceKm: Double = 1.0,
    val parkEndMinutes: Int = 3,
    /** 시연 녹화용(디버그 빌드 `--ei parkEndSec N`): 있으면 [parkEndMinutes] 대신 이 초를 씀. 서버 설정에는 없음 */
    val parkEndSec: Int? = null,
) {
    val parkEndMs: Long get() = parkEndSec?.let { it * 1_000L } ?: (parkEndMinutes * 60_000L)
}

data class TurnConfig(
    /** 핸들 각도 절댓값이 이 이상이 되면 회전 (진입 기준) */
    val steeringEnterDeg: Float = 90f,
    /** 회전 중 핸들 각도 절댓값이 이 아래로 내려오면 회전 끝 (해제 기준). 진입 기준과 나눠서 90도 근처 흔들림이 여러 회전으로 찍히지 않게 함 */
    val steeringExitDeg: Float = 20f,
    /**
     * 핸들 각도가 양수일 때 우회전인지. 에뮬레이터 실측: Virtual Console에서 오른쪽(+)으로 꺾으면
     * Vehicle SDK `onSteeringWheelAngleUpdated`는 음수를 줌 → 기본값 false (docs/spikes/vehicle-background-speed.md).
     * 실차 부호는 미확인이라 서버 설정으로 바꿀 수 있게 둠
     */
    val steeringRightPositive: Boolean = false,
    /** 속도를 알 수 있을 때, 회전 동안 이 속도를 한 번도 넘지 않으면 주차장 조작으로 보고 제외 */
    val minSpeedKmh: Float = 5f,
    // 아래 windowSec ~ uTurnAngleDeg, headingNoiseDeg는 진행 방향 기반 감지(HeadingTurnDetector, 보조)용

    val windowSec: Int = 10,
    val minAngleDeg: Float = 60f,
    val uTurnAngleDeg: Float = 150f,
    val signalLeadSec: Int = 5,
    // 아래는 앱 내부 튜닝값 (서버 설정에는 아직 없음)
    /** 이보다 작은 heading 변화는 직진 중 흔들림으로 보고 회전 시작·끝 계산에서 무시함 */
    val headingNoiseDeg: Float = 2f,
    /** 회전 사이 간격이 이 안이면 연속 회전으로 묶음. 방향이 섞이면(회전교차로, S자) 제외 */
    val mergeGapSec: Int = 3,
) {
    val windowMs: Long get() = windowSec * 1_000L
    val signalLeadMs: Long get() = signalLeadSec * 1_000L
    val mergeGapMs: Long get() = mergeGapSec * 1_000L
}

/** 지역 리그 기준 (서버 기준값 `league`, 서버 D11 · D13). 앱은 화면 안내 문구와 Fake 판정에만 씀 */
data class LeagueConfig(
    /** 권역 참여자가 이 수 미만이면 순위 비공개 */
    val minParticipants: Int = 10,
    /** 순위 자격: 시즌 누적 거리 */
    val minSeasonDistanceKm: Double = 50.0,
    /** 순위 자격: 시즌 운행 횟수 (거리와 둘 중 하나만 채우면 됨) */
    val minSeasonTrips: Int = 10,
)

/** 서버 기준값 `point`. 연속 보상 주기(일)와 보상 포인트 */
data class PointConfig(
    /** 이 일수만큼 연속으로 운행하면 연속 보상 */
    val streakRewardDays: Int = 7,
    /** 연속 보상 포인트 */
    val streakRewardPoint: Int = 50,
)

/** 주행 안전 가드 기준 (docs/screen-plan.md 3장) */
data class GuardConfig(
    /** 기어 P에서 이 속도 이하면 정차로 봄. 센서 잡음 때문에 0이 아니라 약간의 여유를 둠 */
    val parkedMaxSpeedKmh: Float = 1f,
    /** 앱 시작 후 이 시간 안에 기어 값을 못 받으면 "차량 상태를 확인할 수 없어요" */
    val gearTimeoutSec: Int = 3,
) {
    val gearTimeoutMs: Long get() = gearTimeoutSec * 1_000L
}

/**
 * 점수 → 등급 기준 (서버 plan.md 3.3 `grades`와 같은 값). 높은 기준부터 비교해 처음 만족하는 등급.
 * 50점 미만은 등급 없음 (screen-plan 9장: 등급 칸 비움)
 */
data class GradeThreshold(val grade: String, val minScore: Int) {
    companion object {
        val DEFAULT = listOf(GradeThreshold("S", 95), GradeThreshold("A", 85), GradeThreshold("B", 70), GradeThreshold("C", 50))

        fun gradeOf(score: Int?, thresholds: List<GradeThreshold>): String? =
            score?.let { s -> thresholds.sortedByDescending { it.minScore }.firstOrNull { s >= it.minScore }?.grade }
    }
}
