# 리뷰: feature/p0-finish (2026-11-10)

- 기준: develop...feature/p0-finish (4개 커밋: 158fdb7, b31ef73, f14761b, b337637)
- 범위: plan.md 9b 마무리(screen-plan 7장 6번). 개발 흐름 규칙 보강(CLAUDE.md · lead.md · developer.md), 시연 시나리오 결과 회귀 테스트, P0 화면 좁은 폭 · 상태별 `@Preview`, plan.md · screen-plan 상태 정리
- 판단: 머지 가능

## 지적 사항

동작을 바꾸는 문제는 없음. 아래 둘은 새 좁은 폭 Preview가 보여 줄 레이아웃 문제를 코드로 계산해 본 것이고, 실제 앱 영역(1682×1210dp, screen-plan 6장)에서는 나타나지 않는다. Preview를 렌더링해 보지 않았으므로 Android Studio에서 확인한 뒤 고치거나 2차 목록에 적으면 된다.

### 1. 온보딩 좁은 폭에서 "이 차량 미지원 · 점수 제외"가 항목 이름 자리를 다 차지함 (심각도: 낮음)
- **위치:** `automotive/src/main/java/com/example/blinkerleague/ui/onboarding/OnboardingScreen.kt:197-217` (`CapabilityLine`), 새 Preview `OnboardingNarrowPreview`(247행)
- **문제:** 행에서 항목 이름은 `weight(1f)`, 상태 문구는 weight 없이 먼저 측정된다. 720dp 폭이면 두 번째 패널 안쪽 폭이 약 147dp(720 − 좌우 패딩 56 − 간격 40 = 624, 1/3.2 비율 → 195, 패널 패딩 48 제외)인데, "이 차량 미지원 · 점수 제외"(bodyMedium 14sp, 한글 11자 + 공백 · 점)는 약 180dp라 상태 문구가 147dp를 모두 가져가고 이름(`핸들 각도`, `기어`)은 폭 0에 가깝게 눌린다.
- **실패 시나리오:** 새 Preview가 일부러 넣은 UNSUPPORTED 두 줄(핸들 각도 · 기어)에서 항목 이름이 안 보이거나 한 글자씩 세로로 쌓임. 미지원 항목이 무엇인지 알 수 없게 되는 것이라 "미지원은 제외로 보여 준다"는 의도가 좁은 폭에서 약해짐.
- **제안:** 상태 문구에 `Modifier.weight(1f, fill = false)` 또는 `widthIn(max = …)`을 주거나, 좁은 폭에서는 이름 아래 줄에 상태를 두는 방식. 미룬다면 plan.md "9b에서 미룬 것"에 적는다.

### 2. 결과 화면 항목 행의 고정 `width(200.dp)` 때문에 좁은 폭에서 상태 문구 칸이 약 50dp (심각도: 낮음)
- **위치:** `automotive/src/main/java/com/example/blinkerleague/ui/result/ResultScreen.kt:236` (`MetricLine`의 이름 `Modifier.width(200.dp)`), 새 Preview `ResultNoTurnPreview` · `ResultExcludedShortPreview`(316, 324행)
- **문제:** 720dp에서 오른쪽 열 패널 안쪽은 약 338dp(664 − 20 = 644의 1.2/2 = 386, 패널 패딩 48 제외)이고, 이름 200 + 간격 16×2 + 숫자 칸 56을 빼면 상태 문구 칸은 약 50dp다. "회전 없음"은 2줄, "이 차량 미지원"은 3줄로 접힌다(행은 `heightIn(min = 64.dp)`라 잘리지는 않음). 이번에 추가한 회전 0회 · 짧은 운행 Preview는 1280 폭만 있어서 이 상태의 좁은 폭은 Preview로 확인되지 않는다. (고정 폭 자체는 이번 브랜치에서 생긴 것이 아님)
- **실패 시나리오:** 앱 영역이 720dp 근처로 좁아지는 차량/모드에서 "회전 없음" · "경기 제외"가 여러 줄로 쪼개져 읽기 어려움.
- **제안:** 이름 칸을 `weight` 또는 `widthIn(max = 200.dp)`로 바꾸거나, 좁은 폭 NO_TURN Preview를 하나 더 두고 확인. 미룬다면 2차 목록에 적는다.

## 확인한 것

- **동작 변경 없음:** `automotive/src/main/**` 변경은 5개 화면 파일의 `@Preview` 함수 추가와 `ResultScreen.kt`의 private `previewState`에 기본값 있는 `turnValue` 파라미터 추가뿐. `previewState`는 private이고 Preview 세 곳에서만 불림. 기본값이 이전 식(`score != null`이면 `Score`, 아니면 `Pending`)과 같아 기존 Preview 출력도 그대로. `ResultUiState.kt`, `HomeViewModel.kt`, ViewModel · Repository · 리소스는 변경 없음.
- **Preview 상태가 실제 매핑과 일치:** `toResultUiState`(ResultUiState.kt:92-100)는 `EXCLUDED_SHORT → MetricValue.Excluded`, `NO_TURN → MetricValue.NoTurn`이고 두 상태에서 득점 · 실점이 0이면 `Goals`를 그리지 않음. 새 Preview도 같은 값(`Excluded`, `NoTurn`, goals 0 · conceded 0). MetricDetail 좁은 폭 Preview의 `TurnCounts(0, 0, 0, 0, false)`도 서버 counts가 없을 때의 실제 매핑(MetricDetailUiState.kt:45-46)과 같음.
- **미지원 · 회전 0회는 0점이 아니라 제외:** 결과 행은 "회전 없음" / "경기 제외" / "이 차량 미지원"과 "—"로 표시(점수 막대 없음), 온보딩은 "이 차량 미지원 · 점수 제외", 플레이 빈 상태는 "측정한 회전이 없어요".
- **주행 안전 원칙:** DRV 화면(`ui/driving`)과 가드는 변경 없음. 새 Preview는 모두 정차 후 화면(SCR-00/02/02a/02b/03). 위험 운전 유도 문구 없음.
- **터치 영역:** 새 Preview가 그리는 버튼 · 행은 모두 기존 코드의 `heightIn(min = 64.dp)`(완료 · 플레이 보기 · 돌아가기 · 재도전 · 리그 입장 · 속도 권한 · 결과 항목 행 · 퀘스트 칩 · 상세 회전 행). 온보딩 지역 칩은 56dp, 기능 행은 48dp로 기존과 같음(이번 변경 아님).
- **온보딩 지역 목록:** `Regions.all`은 서울 1개라 시 · 도 칩 Row(가로 스크롤 없음)는 그려지지 않고, 구 목록은 `LazyVerticalGrid(Adaptive 120dp)` + `weight(1f)`라 좁은 폭에서 1열로 스크롤됨. 잘림 위험 없음.
- **회귀 테스트(`TripRecorderTest.kt:99-123`):** 한글 이름, `// given` · `// when` · `// then`, 가상 시간(`runTest` + `advanceTimeBy`, recorder 시계는 `testScheduler.currentTime`, Repository 시계 `{ 0L }`, `ZoneId.of("Asia/Seoul")`). 실제 `delay`나 `System.currentTimeMillis()` 없음. 기록 → 업로드 → `toResultUiState`/`toPlaysUiState`까지 이어서 C · 50 · 득점 1 · 실점 1 · 카드 2장(득점, 실점 순)을 확인. 동작이 바뀌지 않는 회귀 테스트라 `test:` 하나로 끝나는 것이 맞음(green 커밋 불필요).
- **plan.md PR 번호:** `git log develop`에서 #31(주행 안전 가드) · #32(홈) · #33(결과 · 플레이) · #34(퀘스트) · #35(온보딩) · #58(SCR-02b) · #61(퀘스트 보상 P1 표시 제거)이 모두 확인되고 plan.md 남은 작업 4~8번 · 목업 차이 표 · screen-plan 4.5 문장과 맞음.
- **lint 기록:** `automotive/build/reports/lint-results-debug.xml` 기준 Error 0, Warning 16 = GradleDependency 12 · InlinedApi 2 · DataExtractionRules 1 · ObsoleteLintCustomCheck 1(Compose lint 검사가 라이브러리 버전 때문에 동작하지 않음). 결정 기록의 분류와 일치. InlinedApi 2건 중 1건은 `src/debug`의 `ProbeService.kt:46`이라 "9b에서 미룬 것"에는 `TripCollectionService`만 적힌 것이 맞음(조사용 코드). 근거("Pleos 대상은 API 34")는 `targetSdk = 34`, `minSdk = 28`과 맞음.
- **screen-plan 정리:** 5장에 추가된 홈 최근 경기 행 문장은 `strings.xml`의 `home_state_pending`("결과 집계 중") · `home_state_no_turn`("측정할 회전이 없었어요")과 HomeScreen.kt:201-202 동작과 일치. 4.3 · 4.5 · 4.6의 "→ 2차" 갱신도 develop 커밋(#58, #61)과 맞음. 예외 문구 7종은 5장 표 7행과 같음.
- **CLAUDE.md 규칙 보강:** 기존 규칙과 모순 없음. "동작이 바뀌는 코드는 TDD"로 범위를 좁히고 "테스트 없이 고칠 것"(lint · 문구 · 문서 · Preview)을 lead.md(목록 작성) · developer.md(바로 고침, 기존 테스트 통과 조건)에 같은 정의로 넣음. 리뷰 파일 커밋은 메인 세션(reviewer.md "커밋은 메인 세션이 한다"와 일치), squash merge 언급은 README 206행과 일치, 에이전트는 브랜치 변경 · push 안 함은 tester.md(red 커밋 push 안 함)와 일치. lead.md에 추가된 `lintDebug` · `testDebugUnitTest` 실행은 소스를 바꾸지 않는 검사로 한정됨.
- **보안:** 비밀값 · 토큰 · 원시 좌표 관련 변경 없음.

## 검증

- tester 실행 결과(lead 전달): `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:lintDebug` 성공, 단위 테스트 186개 실패 0, lint Error 0 · Warning 16(기준과 같음).
- reviewer 재실행: `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:lintDebug --console=plain -q` 성공(출력 없음). `build/test-results/testDebugUnitTest` 합계 186개 · 실패 0 · 오류 0, 새 테스트 `시연 시나리오 결과는 C · 평점 50 · 득점 1 · 실점 1, 플레이 카드 2장` 포함. lint 보고서 Error 0 · Warning 16.
- Preview 렌더링은 하지 않음(명령줄에서 확인 수단 없음). 지적 1 · 2는 코드의 폭 계산으로 판단한 것.
