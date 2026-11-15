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

    /** 디버그 빌드 시연용 덮어쓰기 ([overrideForDebug]). 서버 값을 받아도 계속 적용 */
    private var debugOverride: ((DetectionConfig) -> DetectionConfig)? = null

    private var base: DetectionConfig =
        cache.load()?.let { runCatching { json.decodeFromString<ThresholdsResponse>(it) }.getOrNull() }
            ?.let { defaults.mergedWith(it) }
            ?: defaults

    private val _config = MutableStateFlow(base)
    val config: StateFlow<DetectionConfig> = _config.asStateFlow()

    /** 디버그 빌드에서만 부름 (`DebugTools`). 예: 시연 녹화 때 P단 종료 대기를 줄임 */
    fun overrideForDebug(transform: (DetectionConfig) -> DetectionConfig) {
        debugOverride = transform
        _config.value = transform(base)
    }

    /** @return 값이 바뀌었으면 true */
    suspend fun refresh(): Boolean {
        val response = (api?.thresholds() as? ApiResult.Success)?.body ?: return false
        if (response.version == base.version) return false
        base = defaults.mergedWith(response)
        _config.value = debugOverride?.invoke(base) ?: base
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
        tips = tips + response.tips.orEmpty(),
        league = response.league?.let { l ->
            league.copy(
                minParticipants = l.minParticipants ?: league.minParticipants,
                minSeasonDistanceKm = l.minSeasonDistanceKm ?: league.minSeasonDistanceKm,
                minSeasonTrips = l.minSeasonTrips ?: league.minSeasonTrips,
            )
        } ?: league,
        point = point.copy(
            streakRewardDays = response.point?.streakRewardDays?.takeIf { it > 0 } ?: point.streakRewardDays,
            streakRewardPoint = response.point?.streakRewardPoint?.takeIf { it >= 0 } ?: point.streakRewardPoint,
        ),
        signalCancel = signalCancel.copy(
            forgetSec = response.signalCancel?.forgetSec ?: signalCancel.forgetSec,
            minSpeedKmh = response.signalCancel?.minSpeedKmh ?: signalCancel.minSpeedKmh,
        ),
        harsh = harsh.copy(
            deltaKmhPerSec = response.harsh?.deltaKmhPerSec ?: harsh.deltaKmhPerSec,
        ),
        rain = rain.copy(
            minWiperSec = response.rain?.minWiperSec ?: rain.minWiperSec,
        ),
    )
}

interface ConfigCache {
    fun load(): String?
    fun save(json: String)
}
