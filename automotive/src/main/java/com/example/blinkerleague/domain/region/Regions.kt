package com.example.blinkerleague.domain.region

/** 시군구 (법정동 코드 앞 5자리, 서버 plan.md 3.2) */
data class District(val code: String, val name: String)

data class Province(val name: String, val districts: List<District>)

/**
 * 앱에 넣은 지역 목록 (서버에 좌표를 보내지 않음, screen-plan 4.2).
 * P0은 서울 25개 구만 넣음. 나머지 시·도는 행정표준코드관리시스템의 법정동 코드 파일로 채울 것 (plan.md)
 */
object Regions {
    /** 지역 코드 → "서울 마포구". 목록에 없으면 null */
    fun label(code: String): String? = all.firstNotNullOfOrNull { p -> p.districts.firstOrNull { it.code == code }?.let { "${p.name} ${it.name}" } }

    val all: List<Province> = listOf(
        Province(
            "서울",
            listOf(
                District("11110", "종로구"), District("11140", "중구"), District("11170", "용산구"),
                District("11200", "성동구"), District("11215", "광진구"), District("11230", "동대문구"),
                District("11260", "중랑구"), District("11290", "성북구"), District("11305", "강북구"),
                District("11320", "도봉구"), District("11350", "노원구"), District("11380", "은평구"),
                District("11410", "서대문구"), District("11440", "마포구"), District("11470", "양천구"),
                District("11500", "강서구"), District("11530", "구로구"), District("11545", "금천구"),
                District("11560", "영등포구"), District("11590", "동작구"), District("11620", "관악구"),
                District("11650", "서초구"), District("11680", "강남구"), District("11710", "송파구"),
                District("11740", "강동구"),
            ),
        ),
    )
}
