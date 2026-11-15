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

## 재검토 (2026-11-13)

- 기준: 9cb849e..8e5a65c (13개 커밋, 이전 리뷰 이후)
- 범위: 지적 1~3 반영, KDoc · plan.md · screen-plan 4.7 갱신
- 판단: **머지 가능**

### 지적 1 (연속 보상 주기 0 → 크래시): 해결
- `ConfigRepository.kt:89-90` `mergedWith`가 `streakRewardDays`는 `takeIf { it > 0 }`, `streakRewardPoint`는 `takeIf { it >= 0 }`일 때만 서버 값을 쓰고, 아니면 `PointConfig` 기본값(7 · 50)을 씀.
- 캐시 경로: 시작할 때 `base`는 `cache.load()` → 디코드 → `defaults.mergedWith(it)`이고, `refresh()`도 `defaults.mergedWith(response)`임. 저장되는 것은 원본 응답 JSON이라, 이미 저장된 `streakRewardDays: 0` 캐시도 다음 시작 때 같은 검사를 거쳐 기본값으로 복구됨. `daysToReward`로 가는 다른 경로는 없음(`AchievementsViewModel.kt:38`은 `container.config` 값만 읽음). `overrideForDebug`는 debug 전용이라 범위 밖.
- 테스트: 0 · -3 → 기본 주기(a4e7c6d → 7b32bad), 포인트 -10 → 기본값, 0 → 0 그대로(67b48eb → 69d12ce). red · fix 커밋이 나뉘었고 red 커밋에 main 변경 없음.
- 참고(낮음, 머지를 막지 않음): 캐시 복구 경로를 직접 확인하는 테스트(잘못된 캐시 → 새 `ConfigRepository` → 기본값)는 없음. `mergedWith`가 순수 함수이고 두 경로 모두 이를 부르는 것을 코드로 확인했으므로 필수는 아님.

### 지적 2 (적용 가중치 0% · 미측정 항목): 해결
- `MetricDetailUiState.kt:64` `fromServer?.takeIf { it.status == METRIC_SCORED }?.weight?.takeIf { it > 0 }` → SCORED이고 0보다 클 때만 값, 아니면 null(줄 숨김).
- `METRIC_SCORED`를 ui 파일 private 상수로 둔 것은 타당함. `dto.ScoreStatus`는 운행 상태(openapi `TripResult.status` enum: UPLOADED · SCORED · NO_TURN · EXCLUDED_SHORT)이고, 항목 상태(openapi `MetricResult.status`)는 enum 없는 별도 문자열이라 값이 우연히 같을 뿐 개념이 다름. 또 ui 패키지는 `data.remote.dto`를 import하지 않음(이번 확인에서 import 0건). 나중에 항목 상태 비교가 더 생기면 `domain/model/MetricDetail` 쪽으로 옮기는 정도면 됨(지금은 사용처 한 곳).
- 테스트: UNSUPPORTED + 0.3 → null(ebab3fc → 63f2944), SCORED + 0.0 → null(56c96ae → 45fbc93), SCORED + null → null(5d7828e, 특성 테스트로 바로 통과하는 것이 맞음). 9ed2c11은 테스트 이름의 `%`를 "퍼센트"로 바꾼 것뿐.

### 지적 3 (짧은 이동 포함 N): 해결
- `TripRepositoryTest` `짧은 이동도 그날 몇 번째인지 셀 때 포함한다`(8e5a65c): 08:00 0.4km 운행 + 12:00 운행, 고정 시계 18:00(KST), 업로드 뒤 전제 어서션 `scoreStatus == EXCLUDED_SHORT`로 짧은 이동임을 확인하고 정오 운행이 2번째인지 확인. 쿼리에 상태 조건이 들어가면 실패하므로 회귀를 잡음. 특성 테스트라 `test:` 커밋 하나로 충분함.

### 문서
- KDoc: `AchievementsUiState.daysToReward`에 "period는 1 이상(mergedWith에서 보장)", `weightPercent`에 SCORED · 0 초과 조건 — 코드와 일치(0a79eb5).
- `plan.md` 현황 점검 3번 표 갱신, 결정 기록 2건 추가(point 검사, 가중치 표시 조건), 기존 가중치 결정 행 갱신. `docs/screen-plan.md` 4.7 "업로드 전 · 가중치 0 · 미측정 항목은 숨김" — 코드와 일치.
- 서버 형식 변경 없음, `docs/server-requests.md` 추가 필요 없음.

### 새 지적
- 없음. 이번 변경은 위 파일들에 한정되고, 주행 안전 원칙 · 아키텍처 · 권한 · 보안에 영향 없음.

### 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug` 성공. 단위 테스트 207개, 실패 0 · 건너뜀 0 (test-results XML 합계). 새 테스트 6개(연속 보상 2, 가중치 3, 짧은 이동 1)가 결과에 있음.
- lintDebug는 다시 돌리지 않음(tester 보고: 오류 0 · 경고 15, 이전과 같음).
