package com.example.blinkerleague.ui.report

import java.time.LocalDate
import java.time.temporal.IsoFields

/** ISO 주 키("2026-W45")와 그 주 월요일 사이 변환. 서버 `GET /api/reports/weekly?week=` 형식 */
internal object IsoWeek {
    private val PATTERN = Regex("""(\d{4})-W(\d{2})""")

    fun key(monday: LocalDate): String =
        "%d-W%02d".format(monday.get(IsoFields.WEEK_BASED_YEAR), monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun monday(key: String): LocalDate? {
        val m = PATTERN.matchEntire(key) ?: return null
        return LocalDate.of(m.groupValues[1].toInt(), 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, m.groupValues[2].toLong())
            .let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    }

    fun mondayOf(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())

    fun shift(key: String, weeks: Long): String? = monday(key)?.plusWeeks(weeks)?.let(::key)
}
