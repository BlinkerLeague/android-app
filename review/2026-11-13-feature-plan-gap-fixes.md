# 리뷰: feature/plan-gap-fixes (2026-11-13)

- 기준: develop...feature/plan-gap-fixes (24개 커밋)
- 범위: plan.md 남은 작업 11(현황 점검 미구현 1~4: 용어 퀘스트, SCR-02 "오늘의 N번째 경기", SCR-02b 적용 가중치, 연속 보상 기준값) + 문서 반영
- 판단: 수정 후 머지 (지적 1만 고치면 됨. 2~3은 같은 사이클에서 함께 고치길 권하지만 머지를 막지 않음)

## 지적 사항

### 1. 서버 기준값 `point.streakRewardDays`가 0이면 업적 화면에서 크래시 (심각도: 중간)
- **위치:** `data/config/ConfigRepository.kt:88-91`(검증 없이 덮어씀), `ui/achievements/AchievementsUiState.kt:36` `period - days % period`, `ui/achievements/AchievementsViewModel.kt:38`
- **문제:** 서버 값이 0(또는 음수)이어도 그대로 `PointConfig`에 들어가고, `daysToReward`가 0으로 나머지 연산을 함.
- **실패 시나리오:** 서버 설정 실수로 `"point":{"streakRewardDays":0}`이 오면 `ArithmeticException: / by zero`가 `viewModelScope` 안에서 나서 업적(SCR-05)을 열 때마다 앱이 죽음. `refresh()`가 응답을 `cache.save`로 저장하므로 서버가 고칠 때까지 재시작해도 계속 남음. 음수면 "-3일 더 달성하면" 같은 문구가 나옴. CLAUDE.md "앱이 크래시하면 안 됨". 이번 브랜치에서 하드코딩 7을 서버 값으로 바꾸면서 생긴 위험임.
- **제안:** `mergedWith`에서 `response.point?.streakRewardDays?.takeIf { it > 0 }`(포인트는 `>= 0`)로 받고 아니면 기본값을 씀. 0 · 음수 입력에 기본값을 쓰는 `ConfigRepositoryTest` 하나를 추가함(red → green).

### 2. 적용 가중치 "0% 숨김"이 서버가 null을 준다는 가정에만 기댐, 테스트 없음 (심각도: 낮음)
- **위치:** `ui/result/MetricDetailUiState.kt:61` `weightPercent = fromServer?.weight?.let { (it * 100).roundToInt() }`, `ui/result/MetricDetailTest.kt`
- **문제:** 요구는 "서버 값이 없으면 줄 숨김, 0%로 보이면 안 됨(UNSUPPORTED 포함)"인데, 코드는 `weight`가 null일 때만 숨기고 이를 확인하는 테스트도 없음(추가된 테스트는 0.4 → 40 한 건).
- **실패 시나리오:** 서버가 UNSUPPORTED · NOT_APPLICABLE 항목에 `weight: 0.0`을 주면(`TripDtos.kt:104` 주석은 null이라고 하지만 openapi `MetricResult.weight`는 선택 여부를 정하지 않음) SCR-02b에 "적용 가중치 0%"가 보임. 또 null → 숨김 동작이 나중에 바뀌어도 테스트가 잡지 못함.
- **제안:** `status`가 `SCORED`가 아니거나 값이 0 이하이면 null로 함(`takeIf { it > 0 }`). "UNSUPPORTED 항목은 가중치를 보이지 않는다", "가중치가 없으면 보이지 않는다" 테스트를 추가함.

### 3. "짧은 이동도 N에 포함" 규칙을 확인하는 테스트 없음 (심각도: 낮음)
- **위치:** `data/repository/TripRepositoryTest.kt` `오늘 운행이 그날 몇 번째인지 시작 시각 순으로 센다`, `data/local/TripDao.kt` `countStartedBetween`
- **문제:** 사용자 결정은 "그날 운행을 짧은 이동 포함해 셈"인데 테스트의 운행은 모두 일반 운행임. 지금 쿼리는 상태를 거르지 않아 맞게 동작하지만, 누가 `scoreStatus` 조건을 넣어도 테스트가 실패하지 않음.
- **실패 시나리오:** 나중에 쿼리에 `scoreStatus != 'EXCLUDED_SHORT'`가 들어가면 짧은 이동 뒤 경기의 N이 하나 줄어도 회귀를 잡지 못함.
- **제안:** 오전 짧은 이동 1건 + 정오 운행 1건이면 정오 운행이 2번째인지 확인하는 테스트를 추가함.

## 확인한 것

- **TDD 커밋 순서:** 동작이 바뀐 9개 사이클 모두 `test:` → `feat:`/`fix:` 순서(8e5fe2a→83b2796, 050ecd5→355f123, 5848bc8→fc03132, d3d62b2→ecaa345, 9d2fd96→ce76ccf, a0b9a24→8270200, 90a3679→c77346b, 3a9c03f→036fca1, 1a58dae→8374e70, e410238→5569e48), refactor 4ff7f43 분리. red 커밋의 main 변경은 컴파일용 필드 · 파라미터 추가뿐. 문구(a6d711a) · README(25eebfd) · 문서(22fb8a7)는 테스트 없는 `fix:` · `docs:`.
- **테스트 작성 규칙:** 새 테스트는 한글 이름, `// given` · `// when` · `// then`, 고정 시계(`now = kst(...)`, `Asia/Seoul`) 사용. VoiceScriptTest도 given/when/then으로 정리됨.
- **용어 범위:** strings.xml 남은 "미션"은 172 · 176(주간 리포트 맞춤 미션)뿐. `wallet_reason_streak` "7일 연속 달성"은 유지(STREAK_7 이름표). `AchievementsScreen.kt:155` Preview와 `FakeRewardApi.kt:127`의 "오늘의 미션 7일 연속 달성"은 서버 배지 조건 문구를 흉내 낸 것이라 제외 범위. 식별자 · 리소스 키(`mission_*`) · 서버 API 이름 유지. diff가 작고 범위에 맞음.
- **음성 시동 · 종료 각 1회:** `TripVoiceGuide` 구조 변경 없음(KDoc 문구만), `TripVoiceGuideTest`가 두 문장만 나오는지 확인.
- **위험 운전 유도 문구:** 추가된 문구 없음.
- **기준값 하드코딩:** `STREAK_REWARD_DAYS` 삭제, "+50P" → `%2$dP`, 기본값은 `domain/config/PointConfig`(7 · 50) 한 곳. `FakeRewardApi`의 7 · 50은 범위 밖(Fake 서버 규칙 흉내)이며 기본값과 일치. 업적 ViewModel은 `container.config`(현재 `StateFlow` 값)를 매 갱신마다 읽음.
- **"오늘의 N번째 경기":** `dayOrdinal`은 주입된 `clock()`과 시작 시각을 같은 zone 날짜로 비교, 시작 날짜 0시 ~ 이 운행 시작 시각(포함)을 Room에서 셈. 오늘이 아닌 운행 null, 짧은 이동 결과는 ViewModel에서 N 제거. 자정을 넘긴 운행(어제 23:50 시작)을 오늘 0시대에 열면 N 없이 기존 머리말이 나오는데, 이는 사용자 결정("시작한 날" + "오늘이 아닌 운행은 N 없이")과 일치함. 업로드 · 네트워크와 무관하게 init에서 따로 읽으므로 오프라인에도 열림. 짧은 이동도 Room에 저장됨(`TripRepositoryTest` 기존 테스트).
- **좁은 폭:** 머리말에 고정 width 없음, `textAlign = Center`로 줄바꿈 허용, 720dp Preview 추가.
- **서버 형식:** `PointThresholdsDto.streakRewardDays` · `streakRewardPoint`는 openapi `Point`(int32)와 이름 · 타입이 같고 선택값. `MetricDto.weight: Double?`은 openapi `MetricResult.weight`(double)와 일치, 서버 `weights` 예시(0.4 등)와 백분율 변환이 맞음. 서버 요청 추가는 필요 없음.
- **아키텍처:** ViewModel · domain에 Android/SDK import 없음(`java.time`만). Screen/Content 분리 유지, 문구는 strings.xml, 터치 영역 변경 없음. 권한 · 매니페스트 변경 없음.
- **보안:** 비밀값 · 로그 · 위치 좌표 관련 변경 없음.
- **문서:** plan.md(남은 작업 11 완료, 현황 점검 1~4, 사용하지 않는 필드 표, 결정 기록 3건), screen-plan(1장 표, 4.3 · 4.4 · 4.6 · 4.7 · 4.9 · 4.10, 7장 8, 10장), docs/api/README(`point`, `metrics[].weight`, streak)가 코드와 일치.

## 검증

- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug` 성공. 단위 테스트 201개, 실패 0 · 건너뜀 0 (test-results XML 합계).
- lint · assembleRelease는 다시 돌리지 않음(tester 보고: lintDebug 오류 0 · 경고 15(develop과 같음), assembleRelease(서명 없음) 성공).
