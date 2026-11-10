# 서버 API 명세 (화면 개발 참고)

- `openapi.json`: 서버 `develop`(`ede9630`, 2026-11-10)을 `local` 프로필로 띄워 `/v3/api-docs`에서 받은 명세입니다. 서버 저장소의 `docs/api/openapi.json`과 같은 내용임을 확인했습니다.
  - 받는 방법: 서버 저장소 README "Swagger 접속 방법"대로 띄운 뒤 `curl -s http://localhost:8080/v3/api-docs`. `servers`는 지우고 키를 정렬해 저장합니다.
  - 열어 보기: [Swagger Editor](https://editor.swagger.io/), Postman, Android Studio의 OpenAPI 플러그인
- 앱 DTO(`data/remote/dto`)는 이 명세에 맞춥니다. 명세와 다르면 명세가 기준이고, 서버에 바꿔 달라고 할 것은 `docs/server-requests.md`에 적습니다.
- 인증: `POST /api/drivers`, `POST /api/auth/refresh`, `GET /api/config/thresholds`를 뺀 나머지는 `Authorization: Bearer <access>`가 필요합니다. 오류 응답은 모두 `ErrorResponse { code, message }`입니다.

## 공개 API 13개와 화면

| API | 응답 스키마 | 쓰는 화면 | 앱 상태 (11.10) |
| --- | --- | --- | --- |
| `POST /api/drivers` 운전자 등록 | `RegisterDriverResponse` (201 · 400 · 409) | SCR-00 온보딩 | 연결됨 |
| `POST /api/auth/refresh` 토큰 재발급 | `TokenResponse` (200 · 400 · 401) | 공통(401 때 자동) | 연결됨 |
| `GET /api/config/thresholds` 판정 기준값 | `ThresholdsResponse`: `trip` · `turn` · `grades` · `tips` · `league` 등 | 판정, SCR-02b 팁, SCR-04 순위 자격 | 연결됨. `league` 그룹은 앱이 아직 안 읽음 |
| `POST /api/trips` 운행 업로드 | `TripResult` (200 재전송 · 201 · 400 · 401 · 409 · 413) | 업로드, SCR-02 | 연결됨. 400 · 413은 재시도하지 않음(`REJECTED`) |
| `GET /api/trips/{tripId}/score` 운행 점수 | `TripResult`: `status` · `score` · `grade` · `metrics` · `plays` · `mission` | SCR-02 · 02a · 02b | 연결됨. `earnedPoints`는 명세에 아직 없음(서버 요청 12번) |
| `GET /api/trips?limit=N` 최근 경기 | `RecentTripResult[]` | SCR-01 홈 | 연결됨 |
| `GET /api/missions/today` 오늘의 미션 | `TodayMissionResponse`: `conditions` · `progress` · `status` · `rewardPoint` | SCR-01 카드, SCR-03 | 연결됨 |
| `GET /api/points` 포인트 | `PointsResponse`: `balance` · `todayEarned` · `dailyCap` · `entries` | SCR-06 지갑 | 연결됨 |
| `GET /api/streak` 연속 달성 | `StreakView`: `streakDays` · `todayAchieved` | SCR-01 연승 카드, SCR-05 | 연결됨 |
| `GET /api/badges` 배지 진열장 | `BadgeResponse[]`: 4종(`FIRST_TRIP` · `STREAK_7` · `SIGNAL_100` · `SEASON_CHAMPION`) | SCR-05 업적 | 연결됨. Fake는 3종 |
| `GET /api/reports/weekly?week=` 주간 리포트 | `WeeklyReportView` | SCR-07 | 연결됨 |
| `GET /api/leagues/current` 이번 시즌 리그 | `CurrentLeagueResponse`: `season` · `league` · `me` · `regions` | SCR-04 리그 | **형식 다름**: 앱 DTO는 서버 요청 14번 제안 형식(`myLeague`)이라 실서버 응답을 읽지 못함 |
| `PUT /api/drivers/me/league-visibility` 순위 공개 설정 | `LeagueVisibilityRequest/Response { visible }` (200 · 400 · 401 · 404) | SCR-08 설정 | **미연결**: 설정의 "리그 순위 공개" 토글 없음 |

## 리그 응답 (`CurrentLeagueResponse`) 요약

```json
{
  "season": { "seasonId": "2026-11", "startsAt": "2026-11-01T00:00:00+09:00", "endsAt": "2026-12-01T00:00:00+09:00" },
  "league": { "leagueCode": "SEOUL_NORTHWEST", "name": "서울 서북권", "participants": 10, "rankingPublished": true,
              "safetyIndex": 90.0, "rank": 1,
              "rankings": [{ "rank": 1, "nickname": "깜빡왕", "averageScore": 100.0, "points": 50, "score": 150.0 }] },
  "me": { "visible": true, "eligible": true, "averageScore": 100.0, "points": 50, "score": 150.0,
          "rank": 1, "trips": 1, "distanceKm": 60.0,
          "championships": [{ "seasonId": "2026-10", "leagueCode": "SEOUL_NORTHWEST", "name": "서울 서북권" }] },
  "regions": [{ "leagueCode": "SEOUL_NORTHWEST", "name": "서울 서북권", "safetyIndex": 90.0, "rank": 1 }]
}
```

- `league`: 등록 지역이 권역 매핑표에 없으면 `null`. 참여자 10명 미만이면 `rankingPublished: false`, `rankings`는 빈 목록
- 리그 순위 점수 `score` = 평균 점수 `averageScore` + 이번 시즌 포인트 `points`(서버 D34). 같은 점수는 같은 순위
- `me.rank`: 순위 공개를 껐거나 자격(시즌 50km 또는 10회, 기준값 `league.minSeasonDistanceKm` · `minSeasonTrips`)이 없으면 `null`
- `regions`: 리그 점수가 없는 권역은 `safetyIndex` · `rank`가 `null`
- 출처: 서버 저장소 `docs/app-team-changes-2026-11-10.md`
