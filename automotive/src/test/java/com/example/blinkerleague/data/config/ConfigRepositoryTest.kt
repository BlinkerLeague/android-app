package com.example.blinkerleague.data.config

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.ConfigApi
import com.example.blinkerleague.data.remote.dto.ThresholdsResponse
import com.example.blinkerleague.domain.config.DetectionConfig
import com.example.blinkerleague.domain.config.GradeThreshold
import com.example.blinkerleague.domain.config.HarshConfig
import com.example.blinkerleague.domain.config.LeagueConfig
import com.example.blinkerleague.domain.config.PointConfig
import com.example.blinkerleague.domain.config.RainConfig
import com.example.blinkerleague.domain.config.SignalCancelConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRepositoryTest {

    /** 서버 저장소 default-thresholds.json + version (ThresholdController 응답). 핸들 각도 항목은 아직 없음 */
    private val serverJson = """
        {"version":1,
         "trip":{"minDistanceKm":3.0,"parkEndMinutes":3},
         "turn":{"minSpeedKmh":5,"windowSec":10,"minAngleDeg":60,"uTurnAngleDeg":150,"signalLeadSec":5},
         "harsh":{"deltaKmhPerSec":10},"safetyWarning":{"mergeWindowSec":10},
         "weights":{"TURN_SIGNAL":0.4,"SMOOTH_DRIVING":0.3,"SAFETY_WARNING":0.2,"NIGHT_HEADLIGHT":0.1},
         "point":{"tripMaxKm":50,"dailyCap":150},
         "grades":[{"grade":"S","minScore":95},{"grade":"A","minScore":85},{"grade":"B","minScore":70},{"grade":"C","minScore":50}],
         "tips":{"TURN_SIGNAL":"회전 30m 전에 깜빡이를 켜 보세요.","SEAT_BELT":"출발 전에 안전벨트를 매 주세요."}}
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    private class MemoryCache(var value: String? = null) : ConfigCache {
        override fun load() = value
        override fun save(json: String) {
            value = json
        }
    }

    private class FakeConfigApi(var result: ApiResult<ThresholdsResponse>) : ConfigApi {
        var calls = 0
        override suspend fun thresholds(): ApiResult<ThresholdsResponse> {
            calls++
            return result
        }
    }

    private val response = json.decodeFromString<ThresholdsResponse>(serverJson)

    @Test
    fun `서버 응답 형식을 읽고, 서버가 주는 항목만 덮어씀 - 핸들 각도는 기본값 유지`() {
        val merged = DetectionConfig.DEFAULT.mergedWith(response)
        assertEquals(1L, merged.version)
        assertEquals(3.0, merged.trip.minDistanceKm, 0.0)
        assertEquals(5, merged.turn.signalLeadSec)
        assertEquals(DetectionConfig.DEFAULT.turn.steeringEnterDeg, merged.turn.steeringEnterDeg)
        assertEquals(DetectionConfig.DEFAULT.turn.steeringRightPositive, merged.turn.steeringRightPositive)
        assertEquals(GradeThreshold.DEFAULT, merged.grades)
        assertEquals(DetectionConfig.DEFAULT.guard, merged.guard)
        // 팁은 서버 것이 항목별로 덮어쓰고, 서버에 없는 항목은 기본 문구 유지
        assertEquals("회전 30m 전에 깜빡이를 켜 보세요.", merged.tips["TURN_SIGNAL"])
        assertEquals("출발 전에 안전벨트를 매 주세요.", merged.tips["SEAT_BELT"])
        assertEquals(DetectionConfig.DEFAULT_TIPS["NIGHT_HEADLIGHT"], merged.tips["NIGHT_HEADLIGHT"])
    }

    @Test
    fun `서버 기준값의 league 그룹을 순위 자격 기준으로 읽는다`() {
        // given
        // 서버가 순위 자격을 30km 또는 5회, 공개 인원을 20명으로 바꿈
        val changed = json.decodeFromString<ThresholdsResponse>(
            """{"version":2,"league":{"commonItems":["TURN_SIGNAL"],"minParticipants":20,"minSeasonDistanceKm":30.0,"minSeasonTrips":5,"topN":10}}""",
        )

        // when
        val merged = DetectionConfig.DEFAULT.mergedWith(changed)

        // then
        assertEquals(LeagueConfig(minParticipants = 20, minSeasonDistanceKm = 30.0, minSeasonTrips = 5), merged.league)
    }

    @Test
    fun `서버 기준값 point의 연속 보상 주기를 읽고 보상 포인트가 없으면 기본값을 쓴다`() {
        // given
        // 서버가 연속 보상 주기를 5일로 바꾸고 보상 포인트는 주지 않음
        val changed = json.decodeFromString<ThresholdsResponse>(
            """{"version":2,"point":{"dailyCap":150,"streakRewardDays":5,"tripMaxKm":50}}""",
        )

        // when
        val merged = DetectionConfig.DEFAULT.mergedWith(changed)

        // then
        assertEquals(PointConfig(streakRewardDays = 5, streakRewardPoint = 50), merged.point)
    }

    @Test
    fun `서버 기준값 point의 연속 보상 포인트를 읽는다`() {
        // given
        // 서버가 연속 보상 주기를 5일, 보상 포인트를 30으로 바꿈
        val changed = json.decodeFromString<ThresholdsResponse>(
            """{"version":3,"point":{"streakRewardDays":5,"streakRewardPoint":30}}""",
        )

        // when
        val merged = DetectionConfig.DEFAULT.mergedWith(changed)

        // then
        assertEquals(PointConfig(streakRewardDays = 5, streakRewardPoint = 30), merged.point)
    }

    @Test
    fun `서버 기준값 point의 연속 보상 주기가 0 이하이면 기본값을 쓴다`() {
        // given
        // 서버가 연속 보상 주기를 0일, -3일로 잘못 보냄
        val zero = json.decodeFromString<ThresholdsResponse>(
            """{"version":2,"point":{"streakRewardDays":0,"streakRewardPoint":30}}""",
        )
        val negative = json.decodeFromString<ThresholdsResponse>(
            """{"version":3,"point":{"streakRewardDays":-3,"streakRewardPoint":30}}""",
        )

        // when
        val mergedZero = DetectionConfig.DEFAULT.mergedWith(zero)
        val mergedNegative = DetectionConfig.DEFAULT.mergedWith(negative)

        // then
        val expected = PointConfig(streakRewardDays = PointConfig().streakRewardDays, streakRewardPoint = 30)
        assertEquals(expected, mergedZero.point)
        assertEquals(expected, mergedNegative.point)
    }

    @Test
    fun `서버 기준값 point의 연속 보상 포인트가 음수이면 기본값을 쓰고 0은 그대로 쓴다`() {
        // given
        // 서버가 연속 보상 포인트를 -10으로 잘못 보낸 경우와 0(보상 없음)으로 보낸 경우
        val negative = json.decodeFromString<ThresholdsResponse>(
            """{"version":2,"point":{"streakRewardDays":5,"streakRewardPoint":-10}}""",
        )
        val zero = json.decodeFromString<ThresholdsResponse>(
            """{"version":3,"point":{"streakRewardDays":5,"streakRewardPoint":0}}""",
        )

        // when
        val mergedNegative = DetectionConfig.DEFAULT.mergedWith(negative)
        val mergedZero = DetectionConfig.DEFAULT.mergedWith(zero)

        // then
        assertEquals(PointConfig(streakRewardDays = 5, streakRewardPoint = PointConfig().streakRewardPoint), mergedNegative.point)
        assertEquals(PointConfig(streakRewardDays = 5, streakRewardPoint = 0), mergedZero.point)
    }

    @Test
    fun `서버 기준값 signalCancel의 forgetSec와 minSpeedKmh로 깜빡이 끄기 기준을 덮어쓴다`() {
        // given
        // 서버가 깜빡이를 15초 이상 켜 두면 잊은 것으로, 판정 속도를 40km/h 이상으로 바꿈
        val changed = json.decodeFromString<ThresholdsResponse>(
            """{"version":2,"signalCancel":{"forgetSec":15,"minSpeedKmh":40,"penalty":5}}""",
        )

        // when
        val merged = DetectionConfig.DEFAULT.mergedWith(changed)

        // then
        assertEquals(SignalCancelConfig(forgetSec = 15, minSpeedKmh = 40), merged.signalCancel)
    }

    @Test
    fun `서버 기준값 harsh의 deltaKmhPerSec로 급가감속 기준을 덮어쓴다`() {
        // given
        // 서버가 1초 사이 속도 변화 기준을 8km/h로 바꿈
        val changed = json.decodeFromString<ThresholdsResponse>(
            """{"version":2,"harsh":{"deltaKmhPerSec":8,"penaltyPer10Km":5.0}}""",
        )

        // when
        val merged = DetectionConfig.DEFAULT.mergedWith(changed)

        // then
        assertEquals(HarshConfig(deltaKmhPerSec = 8, maxGapSec = HarshConfig().maxGapSec), merged.harsh)
    }

    @Test
    fun `서버 기준값 rain의 minWiperSec로 우천 구간 기준을 덮어쓴다`() {
        // given
        // 서버가 와이퍼를 45초 넘게 켜야 우천 구간으로 보도록 바꿈
        val changed = json.decodeFromString<ThresholdsResponse>(
            """{"version":2,"rain":{"minWiperSec":45,"speedRatio":0.8}}""",
        )

        // when
        val merged = DetectionConfig.DEFAULT.mergedWith(changed)

        // then
        assertEquals(RainConfig(minWiperSec = 45), merged.rain)
    }

    @Test
    fun `version이 바뀐 경우에만 갱신하고 저장, 다시 켜면 저장 값으로 시작`() = runTest {
        val cache = MemoryCache()
        val api = FakeConfigApi(ApiResult.Success(response))
        val repository = ConfigRepository(api, cache)
        assertEquals(0L, repository.config.value.version)

        assertTrue(repository.refresh())
        assertEquals(3.0, repository.config.value.trip.minDistanceKm, 0.0)
        assertFalse(repository.refresh()) // 같은 version

        val restarted = ConfigRepository(FakeConfigApi(ApiResult.NetworkError(Exception())), cache)
        assertEquals(1L, restarted.config.value.version)
        assertFalse(restarted.refresh())
        assertEquals(3.0, restarted.config.value.trip.minDistanceKm, 0.0)
    }

    @Test
    fun `받지 못하거나 저장 값이 깨졌거나 Fake 모드면 기본값`() = runTest {
        assertEquals(DetectionConfig.DEFAULT, ConfigRepository(null, MemoryCache()).config.value)
        assertEquals(DetectionConfig.DEFAULT, ConfigRepository(null, MemoryCache("{broken")).config.value)
        val offline = ConfigRepository(FakeConfigApi(ApiResult.HttpError(500)), MemoryCache())
        assertFalse(offline.refresh())
        assertEquals(DetectionConfig.DEFAULT, offline.config.value)
    }
}
