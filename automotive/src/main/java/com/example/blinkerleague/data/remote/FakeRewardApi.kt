package com.example.blinkerleague.data.remote

import com.example.blinkerleague.data.remote.dto.BadgeDto
import com.example.blinkerleague.data.remote.dto.MetricChangeDto
import com.example.blinkerleague.data.remote.dto.PointDescriptionDto
import com.example.blinkerleague.data.remote.dto.PointEntryDto
import com.example.blinkerleague.data.remote.dto.PointsResponse
import com.example.blinkerleague.data.remote.dto.ScoreStatus
import com.example.blinkerleague.data.remote.dto.StreakResponse
import com.example.blinkerleague.data.remote.dto.WeeklyMissionDto
import com.example.blinkerleague.data.remote.dto.WeeklyReportResponse
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 개발·시연용 2차 보상 API. 서버 plan.md 4.2·4.3·4.5 규칙을 흉내 냄 (서버 기본 데이터 파일과 같은 이름·문구):
 * - 운행 적립 `floor(min(거리, 50) × 평점 ÷ 100)`, 미션 달성 20P, 7일 연속마다 50P, 하루 상한 150P
 * - 연속 달성: 오늘 달성했으면 오늘부터, 아니면 어제부터 거꾸로 센 달성 일수
 * - 배지: 첫 경기(점수가 난 첫 운행), 7일 연속, 깜빡이 100회(켠 회전 누적)
 * - 주간 리포트: 지난주(ISO 주) 깜빡이 평균 점수와 그 전 주 비교, 다음 주 맞춤 미션
 */
class FakeRewardApi(
    private val tripApi: FakeTripApi,
    private val missionApi: FakeMissionApi,
    private val clock: () -> Long,
    private val zone: ZoneId,
) : RewardApi {

    private data class Credit(
        val at: Instant,
        val amount: Int,
        val reason: String,
        val description: PointDescriptionDto?,
        val tripId: String? = null,
    )

    private data class ScoredTrip(val id: String, val at: Instant, val km: Double, val score: Int)

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    private fun dayOf(at: Instant): LocalDate = at.atZone(zone).toLocalDate()

    private fun iso(at: Instant): String = OffsetDateTime.ofInstant(at, zone).toString()

    /** 점수가 난 운행 */
    private fun scoredTrips() = tripApi.uploadedTrips()
        .filter { (_, result) -> result.status == ScoreStatus.SCORED && result.score != null }
        .map { (request, result) -> ScoredTrip(request.tripId, OffsetDateTime.parse(request.endedAt).toInstant(), request.distanceKm, result.score!!) }
        .sortedBy { it.at }

    /**
     * 이 운행으로 적립된 운행 포인트(상한 적용 후, 서버 요청 12번 `earnedPoints`를 흉내 냄).
     * 점수가 나지 않은 운행은 null, 상한에 걸려 못 받았으면 0
     */
    fun earnedPointsFor(tripId: String): Int? {
        if (scoredTrips().none { it.id == tripId }) return null
        return credits().firstOrNull { it.tripId == tripId }?.amount ?: 0
    }

    /** 연속 달성 일수 (day로 끝나는) */
    private fun streakEndingAt(day: LocalDate, achieved: Set<LocalDate>): Int {
        var n = 0
        var d = day
        while (d in achieved) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    private fun credits(): List<Credit> {
        val raw = mutableListOf<Credit>()
        scoredTrips().forEach { t ->
            raw += Credit(t.at, floor(min(t.km, TRIP_MAX_KM) * t.score / 100.0).toInt(), "TRIP", PointDescriptionDto(t.km, t.score), t.id)
        }
        val achievements = missionApi.achievements()
        achievements.forEach { (day, at) ->
            raw += Credit(at, MISSION_POINT, "DAILY_MISSION", null)
            if (streakEndingAt(day, achievements.keys) % STREAK_DAYS == 0) raw += Credit(at, STREAK_POINT, "STREAK_7", null)
        }
        // 하루 상한: 그날 남은 만큼만 적립
        val usedByDay = mutableMapOf<LocalDate, Int>()
        return raw.sortedBy { it.at }.mapNotNull { c ->
            val used = usedByDay.getOrDefault(dayOf(c.at), 0)
            val amount = min(c.amount, DAILY_CAP - used)
            if (amount <= 0) return@mapNotNull null
            usedByDay[dayOf(c.at)] = used + amount
            c.copy(amount = amount)
        }
    }

    override suspend fun points(): ApiResult<PointsResponse> {
        val credits = credits()
        val today = today()
        return ApiResult.Success(
            PointsResponse(
                balance = credits.sumOf { it.amount },
                todayEarned = credits.filter { dayOf(it.at) == today }.sumOf { it.amount },
                dailyCap = DAILY_CAP,
                entries = credits.sortedByDescending { it.at }.take(20)
                    .map { PointEntryDto(it.amount, it.reason, it.description, iso(it.at)) },
            ),
        )
    }

    override suspend fun streak(): ApiResult<StreakResponse> {
        val achieved = missionApi.achievements().keys
        val today = today()
        val todayAchieved = today in achieved
        return ApiResult.Success(StreakResponse(streakEndingAt(if (todayAchieved) today else today.minusDays(1), achieved), todayAchieved))
    }

    override suspend fun badges(): ApiResult<List<BadgeDto>> {
        val scored = scoredTrips()
        val achievements = missionApi.achievements()
        val bestStreak = achievements.keys.maxOfOrNull { streakEndingAt(it, achievements.keys) } ?: 0
        val signalOn = tripApi.uploadedTrips().sumOf { it.second.turns?.success ?: 0 }
        return ApiResult.Success(
            listOf(
                BadgeDto("FIRST_TRIP", "첫 경기", "점수가 난 첫 운행", scored.isNotEmpty(), scored.firstOrNull()?.at?.let(::iso)),
                BadgeDto("STREAK_7", "7일 연속", "오늘의 미션 7일 연속 달성", bestStreak >= STREAK_DAYS, null),
                BadgeDto("SIGNAL_100", "깜빡이 100회", "깜빡이를 켠 회전 누적 100회", signalOn >= 100, null),
            ),
        )
    }

    override suspend fun weeklyReport(week: String?): ApiResult<WeeklyReportResponse> {
        val thisWeekStart = today().minusDays((today().dayOfWeek.value - 1).toLong()) // 이번 주 월요일
        val reportStart = week?.let(::parseWeek) ?: thisWeekStart.minusWeeks(1)
        if (!reportStart.isBefore(thisWeekStart)) return ApiResult.HttpError(400) // 아직 끝나지 않은 주
        fun tripsIn(start: LocalDate) = scoredTrips().filter { dayOf(it.at) >= start && dayOf(it.at) < start.plusWeeks(1) }
        val thisWeek = tripsIn(reportStart)
        val lastWeek = tripsIn(reportStart.minusWeeks(1))
        fun avg(trips: List<ScoredTrip>) = trips.takeIf { it.isNotEmpty() }?.map { it.score }?.average()?.roundToInt()
        val now = avg(thisWeek)
        val before = avg(lastWeek)
        return ApiResult.Success(
            WeeklyReportResponse(
                week = weekKey(reportStart),
                trips = thisWeek.size,
                distanceKm = thisWeek.sumOf { it.km },
                metrics = listOf(MetricChangeDto("TURN_SIGNAL", now, before, if (now != null && before != null) now - before else null)),
                weakestMetric = if (now != null) "TURN_SIGNAL" else null,
                lastMission = null,
                nextMission = WeeklyMissionDto(weekKey(reportStart.plusWeeks(1)), "TURN_SIGNAL", "회전 깜빡이 주간 90점", 90, 30.0, "ASSIGNED"),
            ),
        )
    }

    private fun weekKey(monday: LocalDate) = "%d-W%02d".format(monday.get(IsoFields.WEEK_BASED_YEAR), monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    private fun parseWeek(key: String): LocalDate? {
        val m = Regex("""(\d{4})-W(\d{2})""").matchEntire(key) ?: return null
        return LocalDate.of(m.groupValues[1].toInt(), 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, m.groupValues[2].toLong())
            .let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    }

    private companion object {
        const val TRIP_MAX_KM = 50.0
        const val MISSION_POINT = 20
        const val STREAK_DAYS = 7
        const val STREAK_POINT = 50
        const val DAILY_CAP = 150
    }
}
