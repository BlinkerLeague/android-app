package com.example.blinkerleague.data.config

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.ConfigApi
import com.example.blinkerleague.data.remote.dto.ThresholdsResponse
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.config.GradeThreshold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 측정 기준값 (plan.md 3장 `GET /api/config/thresholds`).
 * - 앱 시작 때 [refresh]. `version`이 바뀐 경우에만 갱신하고, 받지 못하면 저장해 둔 값 → 로컬 기본값 순으로 씀
 * - 서버가 주는 항목만 덮어씀. 서버에 아직 없는 항목(핸들 각도 등)은 기본값 그대로
 * - 바뀐 값은 다음 운행부터 적용됨 (운행 중에는 시작할 때의 값을 씀)
 *
 * @param api null이면(Fake 모드) 서버에서 받지 않음
 * @param cache 받은 응답 JSON 보관 (앱을 다시 켜도 서버 값 유지)
 */
class ConfigRepository(
    private val api: ConfigApi?,
    private val cache: ConfigCache,
    private val defaults: DetectionConfig = DetectionConfig.DEFAULT,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _config = MutableStateFlow(
        cache.load()?.let { runCatching { json.decodeFromString<ThresholdsResponse>(it) }.getOrNull() }
            ?.let { defaults.mergedWith(it) }
            ?: defaults,
    )
    val config: StateFlow<DetectionConfig> = _config.asStateFlow()

    /** @return 값이 바뀌었으면 true */
    suspend fun refresh(): Boolean {
        val response = (api?.thresholds() as? ApiResult.Success)?.body ?: return false
        if (response.version == _config.value.version) return false
        _config.value = defaults.mergedWith(response)
        cache.save(json.encodeToString(response))
        return true
    }
}

/** 서버 값으로 덮어쓴 기준값 (순수 함수) */
internal fun DetectionConfig.mergedWith(response: ThresholdsResponse): DetectionConfig {
    val trip = response.trip
    val turn = response.turn
    return copy(
        version = response.version,
        trip = this.trip.copy(
            minDistanceKm = trip?.minDistanceKm ?: this.trip.minDistanceKm,
            parkEndMinutes = trip?.parkEndMinutes ?: this.trip.parkEndMinutes,
        ),
        turn = this.turn.copy(
            steeringEnterDeg = turn?.steeringEnterDeg ?: this.turn.steeringEnterDeg,
            steeringExitDeg = turn?.steeringExitDeg ?: this.turn.steeringExitDeg,
            steeringRightPositive = turn?.steeringRightPositive ?: this.turn.steeringRightPositive,
            minSpeedKmh = turn?.minSpeedKmh ?: this.turn.minSpeedKmh,
            signalLeadSec = turn?.signalLeadSec ?: this.turn.signalLeadSec,
            mergeGapSec = turn?.mergeGapSec ?: this.turn.mergeGapSec,
            windowSec = turn?.windowSec ?: this.turn.windowSec,
            minAngleDeg = turn?.minAngleDeg ?: this.turn.minAngleDeg,
            uTurnAngleDeg = turn?.uTurnAngleDeg ?: this.turn.uTurnAngleDeg,
        ),
        grades = response.grades?.takeIf { it.isNotEmpty() }?.map { GradeThreshold(it.grade, it.minScore) } ?: grades,
    )
}

interface ConfigCache {
    fun load(): String?
    fun save(json: String)
}
