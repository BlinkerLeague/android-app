package com.example.blinkerleague.domain.model

/**
 * 핸들 각도가 [angleDeg]로 바뀐 시각. 부호(어느 쪽이 양수인지)는 SDK 확인 전이라
 * [com.example.blinkerleague.domain.config.TurnConfig.steeringRightPositive]로 맞춤.
 */
data class SteeringSample(
    val timestampMs: Long,
    val angleDeg: Float,
)
