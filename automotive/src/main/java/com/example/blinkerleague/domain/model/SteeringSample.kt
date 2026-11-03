package com.example.blinkerleague.domain.model

/**
 * 핸들 각도가 [angleDeg]로 바뀐 시각. SDK 값 그대로 담음 (에뮬레이터 실측: 오른쪽이 음수).
 * 좌우 해석은 [com.example.blinkerleague.domain.config.TurnConfig.steeringRightPositive]로 함.
 */
data class SteeringSample(
    val timestampMs: Long,
    val angleDeg: Float,
)
