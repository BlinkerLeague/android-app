package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.TurnConfig
import com.example.blinkerleague.domain.model.Gear
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.SignalSample
import com.example.blinkerleague.domain.model.SteeringSample
import com.example.blinkerleague.domain.model.Turn
import com.example.blinkerleague.domain.model.TurnDirection
import com.example.blinkerleague.domain.model.TurnSignalState
import com.example.blinkerleague.domain.model.VehicleStateSample
import kotlin.math.abs
import kotlin.math.sign

/**
 * 핸들 각도로 회전을 찾음 (MVP 기본 감지 방식).
 *
 * 1. 핸들 각도 절댓값이 [TurnConfig.steeringExitDeg]를 넘은 때부터 다시 그 아래로 내려온 때까지를 한 번의 조향으로 보고,
 *    그동안 [TurnConfig.steeringEnterDeg] 이상까지 꺾였으면 회전. 회전 구간은 조향 시작~끝
 *    (진입·해제 기준을 나눠서 90도 근처 흔들림이 여러 회전으로 찍히지 않음. 조향 중 좌우가 바뀌면 거기서 끊음)
 * 2. 제외: 회전 중 기어가 P·R이었거나, 비상등이 켜져 있었거나(※ 아래 참고),
 *    속도를 알 수 있는데 회전 동안 [TurnConfig.minSpeedKmh]를 한 번도 넘지 않은 경우(주차장 조작)
 * 3. 간격이 [TurnConfig.mergeGapSec] 이내인 연속 회전은 묶음. 방향이 같으면 하나로 합치고,
 *    좌우가 섞이면(회전교차로, S자 도로) 제외
 *
 * 핸들 각도로는 유턴을 구분하지 못하므로 [Turn.uTurn]은 null.
 *
 * ※ 비상등 제외 규칙은 현재 동작하지 않음: 비상등은 TurnSignal로 감지 불가
 *   (Vehicle SDK TurnSignal 값은 OFF/RIGHT/LEFT뿐, Light 도메인에도 비상등 API 없음, Safety 경고등 목록도 에뮬레이터에선 비어 있음).
 *   비상등을 읽을 출처가 생기면 [TurnSignalState.HAZARD] 샘플로 넣어주면 됨. 규칙과 테스트는 그때를 위해 남겨 둠
 */
class SteeringTurnDetector(private val config: TurnConfig) {

    fun detect(
        steering: List<SteeringSample>,
        vehicleStates: List<VehicleStateSample> = emptyList(),
        signals: List<SignalSample> = emptyList(),
        locations: List<LocationSample> = emptyList(),
    ): List<Turn> {
        val gears = vehicleStates.sortedBy { it.timestampMs }
        val sortedSignals = signals.sortedBy { it.timestampMs }
        return findSteeringTurns(steering.sortedBy { it.timestampMs })
            .filterNot { overlapsGear(it, gears, Gear.P, Gear.R) }
            .filterNot { overlapsHazard(it, sortedSignals) }
            .filterNot { tooSlow(it, locations) }
            .let(::mergeConsecutive)
    }

    private fun findSteeringTurns(samples: List<SteeringSample>): List<Turn> {
        val turns = mutableListOf<Turn>()
        var startMs: Long? = null
        var side = 0f // 오른쪽 +1, 왼쪽 -1
        var peak = 0f

        fun close(atMs: Long) {
            val start = startMs ?: return
            if (peak >= config.steeringEnterDeg) {
                val direction = if (side > 0) TurnDirection.RIGHT else TurnDirection.LEFT
                turns += Turn(start, atMs, direction, angleDeg = peak, uTurn = null)
            }
            startMs = null
            peak = 0f
        }

        for (s in samples) {
            val angle = if (config.steeringRightPositive) s.angleDeg else -s.angleDeg
            val magnitude = abs(angle)
            if (startMs != null && (magnitude < config.steeringExitDeg || sign(angle) != side)) close(s.timestampMs)
            if (startMs == null && magnitude >= config.steeringExitDeg) {
                startMs = s.timestampMs
                side = sign(angle)
            }
            if (startMs != null) peak = maxOf(peak, magnitude)
        }
        samples.lastOrNull()?.let { close(it.timestampMs) } // 기록이 회전 도중에 끝난 경우
        return turns
    }

    /** 회전 구간 [startMs, endMs] 동안 기어가 [gears] 중 하나였던 적이 있는지 */
    private fun overlapsGear(turn: Turn, sorted: List<VehicleStateSample>, vararg gears: Gear): Boolean =
        sorted.indices.any { i ->
            val s = sorted[i]
            val until = sorted.getOrNull(i + 1)?.timestampMs ?: Long.MAX_VALUE
            s.gear in gears && s.timestampMs <= turn.endMs && until > turn.startMs
        }

    private fun overlapsHazard(turn: Turn, sorted: List<SignalSample>): Boolean =
        sorted.indices.any { i ->
            val s = sorted[i]
            val until = sorted.getOrNull(i + 1)?.timestampMs ?: Long.MAX_VALUE
            s.state == TurnSignalState.HAZARD && s.timestampMs <= turn.endMs && until > turn.startMs
        }

    /** 속도 정보가 회전 구간에 있을 때만 판단. 없으면 제외하지 않음 */
    private fun tooSlow(turn: Turn, locations: List<LocationSample>): Boolean {
        val speeds = locations
            .filter { it.timestampMs in turn.startMs..turn.endMs }
            .mapNotNull { it.speedKmh }
        return speeds.isNotEmpty() && speeds.max() < config.minSpeedKmh
    }

    private fun mergeConsecutive(turns: List<Turn>): List<Turn> {
        if (turns.isEmpty()) return emptyList()
        val groups = mutableListOf(mutableListOf(turns.first()))
        for (t in turns.drop(1)) {
            val group = groups.last()
            if (t.startMs - group.last().endMs <= config.mergeGapMs) group.add(t) else groups.add(mutableListOf(t))
        }
        return groups.mapNotNull { group ->
            if (group.map { it.direction }.toSet().size > 1) return@mapNotNull null // 좌우가 섞인 연속 회전은 제외
            group.first().copy(endMs = group.last().endMs, angleDeg = group.maxOf { it.angleDeg })
        }
    }
}
