package com.example.blinkerleague.domain.region

/**
 * 시군구 → 리그 권역 (서버 D31 초기 매핑: 서울 생활권 5개, 나머지 시·도).
 * 매핑표의 원본은 서버(league_region_mapping). 앱은 Fake 모드와 화면 이름에만 씀
 */
object LeagueRegions {
    private val SEOUL = mapOf(
        "도심권" to listOf("11110", "11140", "11170"),
        "동북권" to listOf("11200", "11215", "11230", "11260", "11290", "11305", "11320", "11350"),
        "서북권" to listOf("11380", "11410", "11440"),
        "서남권" to listOf("11470", "11500", "11530", "11545", "11560", "11590", "11620"),
        "동남권" to listOf("11650", "11680", "11710", "11740"),
    )

    /** 권역 이름. 서울 25개 구 밖이면 null (시·도 단위 매핑은 서버 기준) */
    fun of(regionCode: String): String? = SEOUL.entries.firstOrNull { regionCode in it.value }?.key?.let { "서울 $it" }
}
