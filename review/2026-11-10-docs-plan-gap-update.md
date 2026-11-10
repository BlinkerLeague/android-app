# 리뷰: docs/plan-gap-update (2026-11-10)

- 기준: develop...docs/plan-gap-update (1개 커밋, `3c390b7`)
- 범위: 기획서 · 코드 대조 현황을 `plan.md` · `docs/screen-plan.md` · `docs/api/README.md`에 반영 (문서만, 코드 변경 없음)
- 판단: 수정 후 머지 (지적은 모두 문서 문구 수준, 중간 1 · 낮음 3)

## 지적 사항

### 1. `point.streakRewardPoint`를 "안 씀(서버 계산용)"으로 분류했지만 같은 문서가 "+50P" 하드코딩 제거를 계획함 (심각도: 중간)
- **위치:** `plan.md:254` ("사용하지 않는 API · 필드" 표, `point.dailyCap` · `tripMaxKm` · `streakRewardPoint` … 안 씀) ↔ `plan.md:215` (미구현 4 "화면 문구의 '7일' · '+50P'도 하드코딩", `strings.xml` 156 · 220) ↔ `plan.md:179` (남은 작업 11은 `point.streakRewardDays`만 적음) ↔ `docs/screen-plan.md:212` · `:321`, `docs/api/README.md:21` (모두 `streakRewardDays`만)
- **문제:** 미구현 4가 "+50P"(`strings.xml:156` `achievements_reward_in`)를 하드코딩 문제로 지적하는데, 그 값을 대신할 출처인 `point.streakRewardPoint`는 "안 씀"으로 분류돼 있어 문서 안에서 모순임. 코드 확인: `ConfigDtos.kt`의 `ThresholdsResponse`에는 `point` 필드 자체가 없음(앱은 `streakRewardDays` · `streakRewardPoint` 둘 다 읽지 않음). openapi `Point`에는 `dailyCap` · `streakRewardDays` · `streakRewardPoint` · `tripMaxKm`가 있음.
- **실패 시나리오:** 남은 작업 11을 문서대로 하면 주기(7)만 기준값으로 바뀌고 "+50P"는 그대로 남음. 서버가 보상을 바꾸면 SCR-05 문구가 틀림. 또 "서버 계산용"이라는 분류를 근거로 다음 작업자가 `streakRewardPoint`를 DTO에 넣지 않을 수 있음.
- **제안:** `streakRewardPoint`를 "안 씀" 행에서 빼서 `streakRewardDays` 행과 합침 → "`point.streakRewardDays` · `streakRewardPoint` | 쓸 계획 | 미구현 4 (하드코딩 7 · +50P 제거)". `plan.md:179` 남은 작업 11, `screen-plan.md` 4.9 · 10장, `docs/api/README.md:21`도 "주기와 보상 포인트(`streakRewardDays` · `streakRewardPoint`)"로 맞춤. `strings.xml:220`("7일 연속 달성")은 서버 사유 코드 `STREAK_7`의 이름표라 주기 기준값과 성격이 다름 → 미구현 4에서 같이 바꿀지(주기를 넣은 문구로) 아니면 사유 코드 이름표로 두고 목록에서 뺄지 한 줄로 정해 적을 것. 코드의 `AchievementsUiState.kt:19` 주석 "(7일마다 50P)"도 같은 작업 범위로 적어 두면 좋음.

### 2. "의도적으로 다름"이 결정 기록 없는 "적용 중" 항목을 결정처럼 묶음 (심각도: 낮음)
- **위치:** `plan.md` "현황 점검 › 의도적으로 다름" (회전 0회 평점 없음, 50점 미만 등급 비움, 정차 = P단만 … "자세한 내용은 위 '목업 개정과의 차이' 표와 결정 기록에 있습니다"), `docs/screen-plan.md` 10장 마지막 줄
- **문제:** screen-plan 9장은 이 세 가지를 "적용 중"(결정 아님)으로 바르게 적었는데, plan.md 절은 "의도적으로 다름"으로 묶고 근거를 "목업 차이 표와 결정 기록"으로 안내함. 실제로 결정 기록 표와 목업 차이 표에는 회전 0회 · 50점 미만 · 정차 = P단 항목이 없음(확인함).
- **실패 시나리오:** 읽는 사람이 세 항목을 확정 결정으로 오해해 결정 필요 목록에서 다시 다루지 않음.
- **제안:** 해당 세 항목에 "(적용 중, 결정 기록 없음 · screen-plan 9장)"을 붙이거나 안내 문장을 "목업 차이 표 · 결정 기록 · screen-plan 9장(적용 중 항목)"으로 고침.

### 3. screen-plan 9장 "회전 0회"에서 기존 제안 "P1부터 나머지 항목으로 재분배"가 기록 없이 사라짐 (심각도: 낮음)
- **위치:** `docs/screen-plan.md` 9장 diff: `- | 회전 0회 문구 | P0은 평점 없음, P1부터 나머지 항목으로 재분배 | SCR-02 |` → `+ | 회전 0회 | 적용 중 | 평점 없이 "측정할 회전이 없었어요" | SCR-02 |`
- **문제:** 이전 제안의 뒷부분(P1 재분배)이 삭제됐고, 폐기 결정이나 이동처가 없음. 이 커밋의 다른 곳은 철회를 취소선으로 남겼는데(서버와 맞출 항목 표) 여기만 지워짐.
- **실패 시나리오:** 2차 항목(급가속 등)이 생긴 뒤 회전 0회 운행의 평점 처리를 다시 논의할 때 이전 제안을 잃음. 점수 계산은 서버 몫이라 앱 결정이 아닐 수도 있음.
- **제안:** "내용"에 "이전 제안: P1부터 나머지 항목으로 재분배(점수 계산은 서버, 2차 항목 연결 때 서버와 확인)"를 남기거나, 결정 필요로 옮김.

### 4. 미구현 1(용어 통일)의 범위가 문자열 목록과 조금 어긋남 (심각도: 낮음)
- **위치:** `plan.md:212` — 범위는 "'오늘의 미션' 계열", 목록은 `strings.xml` 64 · 70 · 72 · 73 · 152 · 154 · 155 · 219
- **문제:** 152 `achievements_streak`("미션 연속 달성")는 "오늘의 미션" 계열이 아닌데 들어 있고, 172 · 176(`report_last_mission` "이 주의 맞춤 미션", `report_next_mission` "다음 주 맞춤 미션")은 "미션"이지만 빠져 있음. 의도(주간 맞춤 미션은 서버 용어라 유지)는 짐작되지만 적혀 있지 않음.
- **제안:** "주간 리포트의 '맞춤 미션'(172 · 176)은 서버 주간 미션 용어라 유지"처럼 제외 이유를 한 줄 적거나 목록에 넣음. 범위 표현을 "'오늘의 미션' · 일일 미션 계열"로 넓힘.

## 확인한 것

- **화면 구현 현황 · PR 번호:** `git log develop`와 대조해 모두 맞음 — DRV #31, SCR-01 #32(연승 #54), SCR-02/02a #33(적립 #56), SCR-03 #34(#61), SCR-00 #35, SCR-02b #58, SCR-04 #60 · #64 · #66, SCR-05 #54, SCR-06 #53, SCR-07 #55, SCR-08 #59 · #65, 마무리 #67(`ad8c2c7`), `TripApi.getRecentTrips` · `getScore` #28, 시동 없는 운행 구분 #22.
- **코드 위치 · 줄 번호:** `strings.xml` 15(`result_header` "경기 종료 · %1$.1f km") · 64 · 70 · 72 · 73 · 152 · 154 · 155 · 156 · 219 · 220, `voice/VoiceScript.kt:15-18`(시동 음성 "오늘의 미션"), `AchievementsUiState.kt:32`(`STREAK_REWARD_DAYS = 7`), `ResultUiState.kt:110-112`(`ComingSoon` 3행), `ui/quest/MissionCard.kt:65`(`LinearProgressIndicator`), `ui/league/LeagueScreen.kt` `RegionsPanel`, `TripRepository.metricDetails`(`weight` · `value` 버림) 모두 일치.
- **4.1 경로:** `ui/navigation/BlinkerNavHost.kt` `Routes`와 일치(`onboarding` · `home` · `result/{tripId}` · `result/{tripId}/plays` · `result/{tripId}/metric/{metric}` · `quest` · `league` · `achievements` · `wallet` · `settings` · `report` · `demo`). 하단 탭 5개 순서(홈 · 리그 · 업적 · 지갑 · 설정)도 `TABS`와 일치.
- **4.7~4.12 화면 위치:** `ui/result/MetricDetail*`, `ui/league`, `ui/achievements`, `ui/wallet`, `ui/report`, `ui/settings` 존재. 4.11 리포트가 `lastWeek` · 미션 `week` · `metric`을 표시하지 않는 것도 `WeeklyReportViewModel`(`MetricRow(metric, thisWeek, change, …)`, `MissionRow(title, target, minKm, status)`)로 확인.
- **"사용하지 않는 API · 필드" 표 (지적 1 제외):** `rewardPoint`는 `MissionRepository:25`에서 쓰여 표에서 빠진 것이 맞음. `metrics[].weight` · `value`, `TripResult.grade` · `turns` · `summary` · `plays` · `mission`(결과 화면은 `ResultViewModel.loadMission()`), `RecentTripDto.grade` · `endedAt` · `distanceKm`(`refreshRecentScores`는 `status` · `score`만), `missionId` · `periodKey`, `league.safetyIndex` · `league.rank` · `rankings[].averageScore` · `points` · `season.startsAt`(`LeagueRepository` 미사용, `me.averageScore`는 사용) 분류가 코드와 맞음. `turn.windowSec` · `minAngleDeg` · `uTurnAngleDeg`는 `ConfigRepository:75-77`에서 읽고 `HeadingTurnDetector`에서만 씀(운행 수집 경로에서 미사용) → "읽기만 함" 맞음. 엔드포인트 13개(openapi 경로 12 + `/api/trips` GET · POST)도 맞음.
- **`supported` 주의 문구:** `CapabilityReport.supportedSignals`는 `TURN_SIGNAL` · `STEERING_ANGLE`만, `ScoreItem.measurable`은 `HEADLIGHT` · `SAFETY_WARNING` 신호가 있으면 항목을 넣는 구조라, "지원 신호와 판정 이벤트를 같은 PR에서" 경고가 실제 위험을 정확히 짚음.
- **서버와 맞출 항목 표:** "반영됨(서버 #43, 서버 요청 1 · 7번)", "서버 요청 2번 철회"가 `docs/server-requests.md` 처리 상태 표(10 · 11줄)와 일치. 바뀐 내용은 취소선으로 남겨 기존 내용을 지우지 않음.
- **문서 간 참조:** plan.md 2차 앱 작업 11~13 ↔ 미구현 12, 14 ↔ payload 확인, 15 ↔ 미구현 5 · 6, 16 ↔ 7 · 8 · 10, 17 ↔ 9, 18 ↔ 11 일치. screen-plan 7장 8 ↔ 남은 작업 11(미구현 1~4), 9 ↔ 2차 11~14, 10 ↔ 15~18 일치. 결정 필요 신규 5개(깜빡이 4단계 · 카드 색 · `CAR_SPEED` · 터치 영역 · 백그라운드 누락) ↔ screen-plan 9장 "결정 필요" 5개 일치, 5장 예외 표 새 행도 9장을 가리킴. screen-plan 10장 ↔ plan.md 미구현 · 막힘 표 일치.
- **결정을 지어내지 않음:** 50점 미만 등급 · 정차 = P단 · 회전 0회는 screen-plan 9장에서 "적용 중"으로만 표기(plan.md 쪽 표현은 지적 2). "결정"으로 적은 D6 · P 단위 · 반칙 용어는 각각 #54 · #61 · 목업 차이 표에 근거가 있음. 깜빡이 2단계(결정 11.01), 등급 앱 계산 · 최근 경기(결정 11.08), `supported` = `ScoreItem`(결정 11.08)도 결정 기록에 있음.
- **삭제 줄:** diff의 삭제 줄은 상태 갱신(“없음” → “완료 #NN”, 11.05 당시 설명을 “11.05 당시”로 바꿈), 7장 "선행" 열 → "상태" 열, 완료된 SDK 병행 메모 등이며 기존 결정이 사라진 곳은 지적 3 하나뿐.
- **마크다운 표:** 세 문서의 모든 표에서 행별 열 수가 머리행과 같음(스크립트로 확인).
- **CLAUDE.md 원칙:** 주행 중 화면 · 알림을 추가하는 계획 없음. VOC-02(2차 17)는 "종료 시 1회 원칙 유지"를 명시, 음성 끄기 토글은 설정(정차) 화면. 위험 운전 유도 문구 없음. 2차 판정 계획은 기준값(`harsh.deltaKmhPerSec`, `safetyWarning.mergeWindowSec`, 야간 기준)을 config에서 읽고 순수 함수 + 테스트로 명시. 서버 변경은 `docs/server-requests.md` 새 항목으로 전달(2차 14).
- **확인 못 한 것:** 서버 D21(`docs/scoring-criteria.md:118`이 서버 plan.md D21을 인용)은 서버 저장소에 있어 이 저장소에서 원문을 확인하지 못함. 문서 간 인용은 일관됨.

## 검증

- 문서만 변경, 빌드 · 테스트 해당 없음 (`git diff develop...HEAD --stat`: `plan.md`, `docs/screen-plan.md`, `docs/api/README.md` 3개)
- 실행한 확인 명령
  - `git log develop..HEAD --oneline`, `git diff develop...HEAD`, `git log develop --oneline` (PR 번호 대조)
  - `sed -n` / `grep -n`: `strings.xml` 해당 줄, `VoiceScript.kt`, `AchievementsUiState.kt`, `ResultUiState.kt`, `MissionCard.kt`, `BlinkerNavHost.kt`(`Routes` · `TABS`), `LeagueScreen.kt`
  - `data/remote/dto/*.kt`(Config · Trip · Mission · Reward · League · Auth) 읽기, 필드 사용처 grep(`LeagueRepository`, `RewardRepository`, `MissionRepository`, `TripRepository`, `TripMapper`, `ConfigRepository`, `WeeklyReport*`, `ResultViewModel`, `CapabilityReport`, `ScoreItem`)
  - `docs/api/openapi.json` 스키마 · 경로 목록을 python으로 출력
  - 세 문서의 마크다운 표 열 수 검사(python)
  - `docs/server-requests.md` 처리 상태 · 2번 철회, `plan.md` 결정 기록 · 목업 차이 표 확인
