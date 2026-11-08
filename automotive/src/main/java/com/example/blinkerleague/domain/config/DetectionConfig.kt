package com.example.blinkerleague.domain.config

/**
 * 측정 기준값. 서버 `GET /api/config/thresholds`의 `trip`, `turn`과 같은 이름·단위를 씀.
 * 서버 값을 받지 못하면 [DEFAULT]를 사용함.
 */
data class DetectionConfig(
    val version: Int = 0,
    val trip: TripConfig = TripConfig(),
    val turn: TurnConfig = TurnConfig(),
    val guard: GuardConfig = GuardConfig(),
    val grades: List<GradeThreshold> = GradeThreshold.DEFAULT,
) {
    companion object {
        val DEFAULT = DetectionConfig()
    }
}

data class TripConfig(
    val minDistanceKm: Double = 1.0,
    val parkEndMinutes: Int = 3,
) {
    val parkEndMs: Long get() = parkEndMinutes * 60_000L
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
