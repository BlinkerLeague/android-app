package com.example.blinkerleague.data.repository

import com.example.blinkerleague.data.remote.ApiResult
import com.example.blinkerleague.data.remote.RewardApi
import com.example.blinkerleague.data.remote.dto.WeeklyMissionDto
import com.example.blinkerleague.domain.model.Badge
import com.example.blinkerleague.domain.model.MetricChange
import com.example.blinkerleague.domain.model.PointEntry
import com.example.blinkerleague.domain.model.PointReason
import com.example.blinkerleague.domain.model.PointWallet
import com.example.blinkerleague.domain.model.Streak
import com.example.blinkerleague.domain.model.WeeklyMission
import com.example.blinkerleague.domain.model.WeeklyMissionStatus
import com.example.blinkerleague.domain.model.WeeklyReport
import java.time.OffsetDateTime

/** 2차 보상 데이터. 받지 못하면 null (화면은 "불러오지 못했어요") */
class RewardRepository(private val api: RewardApi) {

    suspend fun wallet(): PointWallet? = (api.points() as? ApiResult.Success)?.body?.let { body ->
        PointWallet(
            balance = body.balance,
            todayEarned = body.todayEarned,
            dailyCap = body.dailyCap,
            entries = body.entries.mapNotNull { e ->
                val at = epochMs(e.createdAt) ?: return@mapNotNull null
                PointEntry(
                    amount = e.amount,
                    reason = PointReason.entries.firstOrNull { it.name == e.reason } ?: PointReason.OTHER,
                    distanceKm = e.description?.distanceKm,
                    score = e.description?.score,
                    createdAtMs = at,
                )
            },
        )
    }

    suspend fun streak(): Streak? = (api.streak() as? ApiResult.Success)?.body?.let { Streak(it.streakDays, it.todayAchieved) }

    suspend fun badges(): List<Badge>? = (api.badges() as? ApiResult.Success)?.body?.map {
        Badge(it.code, it.name, it.condition, it.earned, it.awardedAt?.let(::epochMs))
    }

    suspend fun weeklyReport(week: String? = null): WeeklyReport? =
        (api.weeklyReport(week) as? ApiResult.Success)?.body?.let { body ->
            WeeklyReport(
                week = body.week,
                trips = body.trips,
                distanceKm = body.distanceKm,
                metrics = body.metrics.map { MetricChange(it.metric, it.thisWeek, it.lastWeek, it.change) },
                weakestMetric = body.weakestMetric,
                lastMission = body.lastMission?.toDomain(),
                nextMission = body.nextMission?.toDomain(),
            )
        }

    private fun WeeklyMissionDto.toDomain(): WeeklyMission? {
        val status = WeeklyMissionStatus.entries.firstOrNull { it.name == status } ?: return null
        return WeeklyMission(week, metric, title, target, minDistanceKm, status)
    }

    private fun epochMs(iso: String): Long? = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
}
