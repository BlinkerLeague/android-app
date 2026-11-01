package com.example.blinkerleague.domain.detection

import com.example.blinkerleague.domain.config.TurnConfig
import com.example.blinkerleague.domain.model.LocationSample
import com.example.blinkerleague.domain.model.Turn
import com.example.blinkerleague.domain.model.TurnDirection
import kotlin.math.abs
import kotlin.math.sign

/**
 * 위치 시계열에서 회전을 찾음.
 *
 * 1. 속도가 [TurnConfig.minSpeedKmh] 이상이고 heading이 있는 샘플만 이어서 구간으로 나눔 (저속 구간은 제외)
 * 2. 구간 안에서 [TurnConfig.windowSec] 이내에 heading이 [TurnConfig.minAngleDeg] 이상 누적으로 바뀌면 회전.
 *    같은 방향으로 계속 도는 동안은 끝을 늘리고, 변화가 시작되기 전 직진 부분은 시작에서 잘라냄
 * 3. 간격이 [TurnConfig.mergeGapSec] 이내인 연속 회전은 묶음. 방향이 같으면 하나로 합치고,
 *    좌우가 섞이면(회전교차로, S자 도로) 판정 대상에서 제외
 *
 * heading은 북=0, 시계 방향이므로 변화량이 양수면 우회전, 음수면 좌회전.
 */
class TurnDetector(private val config: TurnConfig) {

    fun detect(samples: List<LocationSample>): List<Turn> {
        val raw = splitMovingRuns(samples.sortedBy { it.timestampMs }).flatMap(::detectInRun)
        return mergeConsecutive(raw)
    }

    private fun splitMovingRuns(samples: List<LocationSample>): List<List<LocationSample>> {
        val runs = mutableListOf<List<LocationSample>>()
        var current = mutableListOf<LocationSample>()
        for (s in samples) {
            val moving = s.headingDeg != null && s.speedKmh != null && s.speedKmh >= config.minSpeedKmh
            if (moving) {
                current.add(s)
            } else if (current.isNotEmpty()) {
                runs.add(current)
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) runs.add(current)
        return runs.filter { it.size >= 2 }
    }

    /** 부호 있는 회전 각도를 가진 중간 결과 */
    private data class RawTurn(val startMs: Long, val endMs: Long, val signedAngle: Float)

    private fun detectInRun(run: List<LocationSample>): List<RawTurn> {
        val n = run.size
        val t = LongArray(n) { run[it].timestampMs }
        // delta[k] = k-1 → k 사이 heading 변화 (-180, 180]
        val delta = FloatArray(n) { k ->
            if (k == 0) 0f else headingDelta(run[k - 1].headingDeg!!, run[k].headingDeg!!)
        }

        val result = mutableListOf<RawTurn>()
        var i = 0
        while (i < n - 1) {
            val hit = findThresholdHit(i, t, delta)
            if (hit < 0) {
                i++
                continue
            }
            val sign = sign(sumRange(delta, i + 1, hit))

            // 시작: i 이후 처음으로 같은 방향 변화가 나타난 직전 샘플
            var start = i
            for (k in i + 1..hit) {
                if (delta[k] * sign >= config.headingNoiseDeg) {
                    start = k - 1
                    break
                }
            }
            // 끝: 같은 방향 변화가 이어지는 동안 늘림
            var end = hit
            while (end + 1 < n && delta[end + 1] * sign >= config.headingNoiseDeg) end++

            result.add(RawTurn(t[start], t[end], sumRange(delta, start + 1, end)))
            i = end
        }
        return result
    }

    /** i에서 시작해 window 안에서 누적 변화가 기준을 넘는 첫 인덱스. 없으면 -1 */
    private fun findThresholdHit(i: Int, t: LongArray, delta: FloatArray): Int {
        var cumulative = 0f
        for (j in i + 1 until t.size) {
            if (t[j] - t[i] > config.windowMs) break
            cumulative += delta[j]
            if (abs(cumulative) >= config.minAngleDeg) return j
        }
        return -1
    }

    private fun mergeConsecutive(raw: List<RawTurn>): List<Turn> {
        if (raw.isEmpty()) return emptyList()
        val groups = mutableListOf(mutableListOf(raw.first()))
        for (r in raw.drop(1)) {
            val group = groups.last()
            if (r.startMs - group.last().endMs <= config.mergeGapMs) group.add(r) else groups.add(mutableListOf(r))
        }
        return groups.mapNotNull { group ->
            val signs = group.map { sign(it.signedAngle) }.toSet()
            if (signs.size > 1) return@mapNotNull null // 좌우가 섞인 연속 회전은 제외
            val angle = group.sumOf { it.signedAngle.toDouble() }.toFloat()
            Turn(
                startMs = group.first().startMs,
                endMs = group.last().endMs,
                direction = if (angle > 0) TurnDirection.RIGHT else TurnDirection.LEFT,
                angleDeg = abs(angle),
                uTurn = abs(angle) >= config.uTurnAngleDeg,
            )
        }
    }

    private fun sumRange(values: FloatArray, from: Int, to: Int): Float {
        var sum = 0f
        for (k in from..to) sum += values[k]
        return sum
    }

    companion object {
        /** from → to 로 바뀐 heading 변화량. 범위 (-180, 180] */
        fun headingDelta(from: Float, to: Float): Float {
            var d = (to - from) % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }
    }
}
