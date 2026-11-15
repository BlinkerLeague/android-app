# 리뷰: feature/game-ui · feature/game-ui-lobby · feature/game-ui-shelf (2026-11-15)

- 대상: 정차 화면 게임 UI 묶음 PR 3개를 한 번에 검토함 (plan-ui.md 2장 "리뷰는 마지막 한 번")
  - #82 `feature/game-ui` (PR-A: 결과 · 하이라이트 · 중계 톤 음성 · 공통 모션 부품 · LocalParked), 기준 develop
  - #83 `feature/game-ui-lobby` (PR-B: 홈 로비 · 퀘스트 보드), 기준 feature/game-ui
  - #84 `feature/game-ui-shelf` (PR-C: 업적 트로피 진열장 · 리그), 기준 feature/game-ui-lobby
- 기준: `origin/develop...feature/game-ui-shelf` (PR-A 69개 커밋(develop 병합 1개 포함) · PR-B 25개 · PR-C 8개, 47개 파일 +3084 / −551)
- 범위: 정차 화면 표현(레이아웃 · 색 · 모션 · 문구)을 경기 비유로 개편, `MatchOutcome` · `QuestHint` · `StreakImpact` · `streakDotsFilled` 같은 순수 로직 추가, 시동 · 종료 음성을 중계 톤으로 교체
- 판단: **수정 후 머지**
  - 주행 안전 · 음성 횟수 · 데이터 정직성 · 기준값 사용에서 막는 문제는 없음
  - 다만 화면에 바로 보이는 문구 오류(1번)와 에뮬레이터에서 본 레이아웃 문제(2~4번)는 머지 전에 고치는 편이 좋음. 모두 테스트 없이 고치는 UI · 문구 수정이라 작음

## 지적 사항

### 1. 앞 공백으로 시작하는 문자열 2개가 공백 없이 붙어 나옴 (심각도: 중간) — PR-C · PR-B
- **위치:** `automotive/src/main/res/values/strings.xml:175` (`achievements_total_count` = `" / 전체 %1$d"`, PR-C), `strings.xml:283` (`game_quest_streak_reward` = `" · 연속 보상 +%1$dP"`, PR-B). 쓰는 곳은 `AchievementsScreen.kt:157-159`, `QuestScreen.kt:255-259`
- **문제:** Android 문자열 리소스는 앞뒤 공백을 지움. 빌드된 APK를 `aapt2 dump resources`로 보면 실제 값이 `"/ 전체 %1$d"`, `"· 연속 보상 +%1$dP"`로 공백이 사라져 있음
- **실패 시나리오:** 업적 머리말이 "획득 2/ 전체 5", 퀘스트 연승 줄이 "클리어하면 7연승· 연속 보상 +50P"로 보임
- **제안:** 앞 공백을 ` `으로 쓰거나 문자열 전체를 큰따옴표로 감쌈(`" / 전체 %1$d"`). 더 나은 방법은 한 문자열로 합치고(`획득 %1$d / 전체 %2$d`) 강조는 `indexOf`로 스타일을 입히는 것(퀘스트 연승 줄에서 이미 쓰는 방식)

### 2. 결과 화면 왼쪽 판: 승패 문구와 등급 도장 사이가 크게 빔 (심각도: 중간) — PR-A
- **위치:** `ui/result/ResultScreen.kt:211-217` (Rating을 `weight(1f)` 칸 안에 `Arrangement.spacedBy(8.dp, Alignment.CenterVertically)`로 가운데 정렬), `ResultScreen.kt:285` (도장 `weight(1f, fill = false).heightIn(min = 96.dp, max = 168.dp)`)
- **문제:** 남은 높이를 모두 weight 칸이 가져가는데 도장은 168dp로 막혀 있음. 도장 + 평점(약 210dp)이 그 칸 가운데에 놓여 위아래가 비고, 화면이 높을수록 더 벌어짐. 에뮬레이터에서 본 "승패 문구와 등급 도장 사이 큰 빈칸"과 같은 현상
- **실패 시나리오:** 1280×720에서 위아래로 약 60dp씩, Pleos 오른쪽 영역처럼 더 높은 화면에서는 그보다 크게 비고, 점수판이 판 위쪽으로 쏠려 보임
- **제안:** 도장이 칸을 채우도록 함(`weight(1f)` + `aspectRatio`, 최대 크기는 240dp 안팎으로 올림). 또는 칸을 `Arrangement.SpaceEvenly`로 바꾸거나 Column 전체를 `verticalArrangement = SpaceBetween`으로 나눠 머리말 · 점수판 · 도장 · 포인트가 고르게 퍼지게 함. 바꾼 뒤 720 폭 Preview에서 포인트 카드가 잘리지 않는지 확인

### 3. 업적 진열장: 카드가 작고 빈 곳이 큼 (심각도: 중간) — PR-C
- **위치:** `ui/achievements/AchievementsScreen.kt:167-176` (`GridCells.Adaptive(minSize = 168.dp)`), `:186` (`heightIn(min = 196.dp)`), `:199-204` (아이콘 84dp 원 · 56dp)
- **문제:** 지금 배지 카탈로그는 4개 정도인데, Adaptive 168dp는 진열장 폭이 넓을수록 열을 늘림(1280 폭이면 3열, 그보다 넓으면 4~5열). 카드는 최소 높이로 위에만 한두 줄 놓이고 아래 절반이 빔. 에뮬레이터에서 본 "카드가 작고 빈 곳이 큼"과 같은 현상
- **실패 시나리오:** 배지 4개 → 한두 줄만 차고 그리드 아래 300dp 이상이 빔. 아이콘이 56dp라 "트로피 진열장"치고 작아 보임
- **제안:** 배지가 적을 때는 열 수를 줄임(예: `GridCells.Fixed(if (badges.size <= 4) 2 else 3)`). 또는 `minSize`를 220~240dp로 올리고 카드와 아이콘을 키움(아이콘 원 112dp 이상). 줄이 적으면 그리드 칸 높이를 나눠 카드가 세로로도 채우게 하는 방법도 있음. 진열장 패널의 `Arrangement.spacedBy(12.dp, Alignment.CenterVertically)`는 그리드가 `weight(1f)`라 효과가 없으니 같이 정리

### 4. 하이라이트 타임라인 점이 작고, 가까운 플레이끼리는 터치 영역이 겹침 (심각도: 낮음~중간) — PR-A
- **위치:** `ui/result/PlaysScreen.kt:190-204`
- **문제:** 터치 영역은 64dp지만 보이는 점은 22dp(테두리 5dp 포함)라 차 화면에서 작음(에뮬레이터 관찰과 같음). 또 점 위치가 `travel * position`이라 시간 차가 작은 두 플레이는 64dp 상자가 겹쳐 나중에 그린 점이 앞 점의 터치를 가로챔
- **실패 시나리오:** 720 폭에서 이동 시간 비율 0.88 · 0.97인 두 회전은 중심 간격이 약 50dp → 겹친 부분을 누르면 항상 뒤 플레이로 이동하고 앞 플레이 점은 반쯤만 누를 수 있음
- **제안:** 보이는 점을 32~40dp로 키움(타임라인 줄 높이도 함께). 겹침은 점 사이 최소 간격(64dp)을 두도록 위치를 밀어 주거나, 카드 목록(LazyRow)이 이미 64dp 이상 터치를 주므로 점은 위치 표시만 하고 겹칠 때는 가장 가까운 점을 고르는 방식(누른 x와 가장 가까운 점 하나)으로 바꿈

### 5. 회전 없는 경기 문구가 화면마다 다르고, 한 운행에 "오늘은"을 씀 (심각도: 낮음) — PR-A · PR-B
- **위치:** `ui/home/HomeScreen.kt:330` (`else -> R.string.home_state_no_turn`), `strings.xml:129` (`home_state_no_turn` = "측정할 회전이 없었어요"), `strings.xml:48` (`result_no_turn_match` = "오늘은 회전 없는 경기"), `voice/VoiceScript.kt:38` ("경기 종료. 오늘은 회전 없는 경기였어요.")
- **문제:** 결과 화면에서는 "측정할 회전이 없었어요"를 지우고 "오늘은 회전 없는 경기"로 바꿨는데(plan-ui 7.4-7), 홈 최근 경기 행은 옛 문구가 그대로 남음. 또 "오늘은"은 운행 하나에 대한 말이라 틀릴 수 있음
- **실패 시나리오:** 같은 회전 없는 운행이 홈에서는 "측정할 회전이 없었어요", 결과 화면에서는 "오늘은 회전 없는 경기"로 보임. 어제 운행을 홈에서 열면 "오늘은 회전 없는 경기", 오늘 두 번째 운행에서 회전이 있었어도 음성이 "오늘은 회전 없는 경기였어요"라고 말함
- **제안:** 세 곳을 "이번 경기는 회전이 없었어요"(음성) · "회전 없는 경기"(화면)처럼 날짜 없는 같은 문구로 맞춤. 홈은 `home_state_no_turn`을 그 문구로 바꾸면 됨. 음성을 바꾸면 `VoiceScriptTest`의 기대 문장도 같이 고침

### 6. "회전 N초 전" 수치가 화면 팁과 음성에서 따로 놂 (심각도: 낮음) — PR-A
- **위치:** `domain/config/DetectionConfig.kt:28` (`DEFAULT_TIPS["TURN_SIGNAL"]` = "회전 5초 전에 깜빡이를 켜 보세요."), `ui/result/ResultUiState.kt` `tip = tips[TURN_SIGNAL]`, `voice/VoiceScript.kt:31` (`signalLeadSec: Int = 5` 기본값)
- **문제:** 음성은 `turn.signalLeadSec`을 쓰지만, 결과 화면의 "다음 경기 팁"은 문구에 5가 박힌 기본 팁을 그대로 씀. `VoiceScript.tripEnd`의 기본값 5도 기준값 기본과 같은 숫자를 한 번 더 적은 것
- **실패 시나리오:** 서버가 `turn.signalLeadSec`만 7로 내리고 `tips`는 안 바꾸면, 음성은 "7초 전", 같은 결과 화면 팁은 "5초 전"이라고 말함
- **제안:** 이번 PR에서는 `VoiceScript`의 기본값을 `TurnConfig().signalLeadSec`으로 바꾸거나 빼서 숫자를 한 곳에만 둠. 팁 문구 불일치는 기존 기본 팁에서 오는 것이라, 서버 `tips`도 함께 바꾸도록 `docs/server-requests.md`나 plan 결정 기록에 한 줄 남기면 충분함

### 7. 점수가 없는 스탯 행은 빈 회색 막대로 보여 0점처럼 읽힐 수 있음 (심각도: 낮음) — PR-A
- **위치:** `ui/result/ResultScreen.kt:388-400`
- **문제:** `ComingSoon` · `Unsupported`만 사선 무늬이고, `NotApplicable` · `Excluded` · `NotScored` · `Pending` · `NoTurn`은 채움 0인 회색 막대 + "—"로 그려짐. 상태 문구가 같이 나오긴 하지만 막대 모양은 0점과 같음
- **실패 시나리오:** develop에서 들어온 서버 "해당 없음"(예: 낮 운행의 야간 전조등)이 빈 막대로 보여 0점을 받은 것처럼 보임 (CLAUDE.md "제외로 보여 주기"와 어긋나 보임)
- **제안:** `MetricValue.Score`가 아닌 값은 모두 `HatchedBox`로 그림. 병합 때 추가한 `NotApplicable` 행도 이 분기에 넣음

### 8. 진입 연출 중 투명한 버튼 · 배너도 눌림 (심각도: 낮음) — PR-A · B · C 공통
- **위치:** `ui/common/game/StaggeredReveal.kt:531-550` (`reveal`은 `graphicsLayer` alpha만 바꿈, `skipRevealOnTap`은 이벤트를 소비하지 않음), 예: `ResultScreen.kt:173` 퀘스트 배너는 1.8초 동안 투명
- **문제:** 연출을 건너뛰려고 아무 데나 누르면 아직 보이지 않는 배너 · 카드 · 최근 경기 행이 같이 눌려 다른 화면으로 넘어감
- **실패 시나리오:** 결과 화면 진입 직후 오른쪽 아래를 눌러 연출을 건너뛰려다 보이지 않던 퀘스트 배너가 눌려 퀘스트 화면으로 이동
- **제안:** 연출이 끝나기 전 첫 탭은 건너뛰기만 하고 소비하거나(`clock.elapsedMs < totalMs`일 때 `down.consume()`), 등장 전(alpha 0) 요소는 클릭을 막음. 녹화 스크립트가 진입 직후 버튼을 누르는지도 함께 확인

### 9. 퀘스트 클리어 꽃가루가 화면에 들어갈 때마다 다시 터짐 (심각도: 낮음, 확인 필요) — PR-B
- **위치:** `ui/quest/QuestScreen.kt:109` · `:146`, `ui/common/game/StaggeredReveal.kt:56` (`played`는 백스택 항목마다 따로 저장)
- **문제:** `rememberSaveable`은 같은 화면 항목 안에서만 기억하므로, 클리어한 날에는 홈에서 퀘스트 화면을 열 때마다 꽃가루 · CLEAR 도장이 다시 나옴. 결과 화면도 같은 운행을 다시 열면 맨 위 등급 꽃가루가 다시 나옴
- **실패 시나리오:** 오늘 퀘스트를 클리어한 사용자가 홈 → 퀘스트를 열 때마다 축하가 반복됨 (plan-ui "꽃가루는 1회"를 화면 진입마다 1회로 볼지, 클리어당 1회로 볼지 정해지지 않음)
- **제안:** 의도가 "진입마다 1회"면 plan-ui 결정에 그렇게 적음. "클리어당 1회"면 본 퀘스트(날짜 · 퀘스트) · 운행 ID를 ViewModel이나 앱 범위에 기억해 두 번째부터는 `celebrate`를 끔

### 10. 문서: plan-ui 머리말이 PR-C를 "push 안 함"으로 적음 (심각도: 낮음) — PR-C
- **위치:** `plan-ui.md:6`
- **문제:** PR-C는 이미 `origin/feature/game-ui-shelf`로 push되고 #84가 열려 있음
- **제안:** "PR #84"로 고침. 이 리뷰의 판단(수정 후 머지)과 남은 결정(5 · 6 · 7, 꽃가루 1회 범위)도 10장에 반영

### 참고 (고치지 않아도 됨)
- 종료 음성은 "퍼펙트 승리", 결과 화면 승패 문구는 "완승"으로 서로 다름. 계획(7.2-17 · T4)대로이지만 같은 말로 맞추면 더 자연스러움
- 홈 · 업적 연승 카드가 0일일 때 "0연승", 퀘스트 연승 줄이 연승 0일일 때 "클리어하면 1연승"으로 나옴. 0이면 "연승을 시작해 보세요" 같은 문구로 바꾸는 것을 고려
- 시동 안내는 이제 퀘스트와 연승을 둘 다 받은 뒤 말하므로, 연승 API가 느리면 그만큼 늦어짐(동시에 받으므로 최대 지연은 두 요청 중 긴 쪽). 필요하면 연승만 짧은 `withTimeoutOrNull`로 감쌈
- 결과 스탯 막대 색의 70점 기준(`ResultScreen.kt:393`)은 develop에 있던 하드코딩이라 이번 범위는 아님. 등급 기준값으로 옮길지 나중에 정함

## 확인한 것

- **주행 안전**
  - `MainActivity`는 `CompositionLocalProvider(LocalParked provides guard.showParkedScreens)`만 추가함. DrivingGuard 판정, DRV 덮개, `clearAndSetSemantics` 방식은 그대로
  - 무한 반복 연출은 `StreakFlame` 하나뿐. `motionEnabled()`(정차이고 Preview가 아닐 때)일 때만 `rememberInfiniteTransition`이 돌고, 크기 1.00 → 1.06(6%)에 0.8초씩 왕복(1.6초 주기)이라 점멸이 없음
  - 순차 등장 · 카운트업 · 도장 · 링 · 꽃가루는 모두 `RevealClock` 하나를 봄. `LocalParked == false`면 시작하지 않고, 재생 중에 주행으로 바뀌면 `LaunchedEffect`가 취소되며 멈췄다가 P가 되면 이어서 재생함. `animate*AsState` · `AnimatedVisibility` · 따로 도는 `LaunchedEffect`는 없음
  - 다이얼로그 · 토스트 · 효과음(SoundPool · MediaPlayer · ToneGenerator) 추가 없음. 새 의존성 없음
  - 꽃가루는 `celebrate`(맨 위 등급) · 퀘스트 클리어일 때만 그리고 2초에 한 번 떨어진 뒤 사라짐(시드 고정, 터치를 받지 않음). 탭하면 `skip()`으로 최종 상태로 감. `rememberSaveable`로 하이라이트에 갔다 와도 다시 재생하지 않음
  - 속도를 재촉하는 문구 없음("빨리" · "서둘러" 등 검색). 리그 D-day 칩도 점멸하지 않음
- **음성**: 시동 · 종료 각 1회는 `startAnnounced` · `endAnnounced`(운행 ID 집합) 그대로. 퀘스트 · 연승은 `async(UNDISPATCHED)`로 동시에 받음. 문장은 모두 2개 이하. `TripVoiceGuideTest`의 기존 기대 문장 교체와 새 테스트(signalLeadSec 7, 연승 있음 · 없음)가 맞음
- **기준값**: 종료 음성의 "N초 전"은 `turn.signalLeadSec`(AppContainer에서 `{ config.turn }`). 축하 등급은 `grades.maxByOrNull { minScore }`, 등급색은 순서로 고름(`gradeColor`). 연승 칸 수 · 남은 날 · 보상 포인트는 `point.streakRewardDays` · `streakRewardPoint`에서 읽음(홈 · 퀘스트 · 업적)
- **데이터 정직성**: 경험치 · 레벨 · 리그 단계 · 승강 구역 · 순위 변동 · 배지 진행 막대 · "가장 가까운 트로피" · N초 전 점등 · BEST 플레이 · 회당 상금 · 보물상자 · 보상 받기 · 보유 포인트 모두 없음. 선수 카드는 닉네임 · 지역만, 리그 큰 순위는 `rankPublic && myRank != null`일 때만이고 아니면 기존 안내 문구(`league_not_ranked` · `league_rank_hidden`)
- **D25와 맞음**: `QuestHint.NextTrip(requiredTurns)` → "다음 경기에서 회전 N번 모두 득점하면 클리어"(남은 횟수가 아니라 전체 횟수). 시동 음성도 "회전 세 번 모두"
- **배지 코드**: FIRST_TRIP · STREAK_7 · SIGNAL_100 · SEASON_CHAMPION 매핑, 모르는 코드는 파랑 트로피(`BadgeShape.of`의 `else`). 잠긴 배지는 서버 `condition` 그대로 + 자물쇠(설명 "잠김")
- **develop 병합(bfecbbf)**: 충돌 3곳 모두 양쪽 기능을 살림. `toResultUiState`는 `tips`(게임 UI)와 `serverMetrics`(#81)를 둘 다 받고, 호출하는 곳은 모두 이름 인자라 순서 변경 영향 없음(`MetricDetailUiState.kt:43`, 테스트들). `ResultViewModel`은 `tips` · `loadMetrics`를 둘 다 받고 `ResultScreen` 팩토리가 둘 다 넘김. 스탯 행 `when`에 `NotApplicable`이 들어가 컴파일 · 문구 모두 맞음. `outcome` · `celebrate` · `tip` 계산은 서버 항목 점수와 독립이라 서로 영향 없음. #81의 결과 화면 테스트는 그대로 통과
- **순수 로직 · 테스트**: `MatchOutcome.of`(0:0 → null, 실점 0 → 완승, 그다음 승 · 무 · 아쉬움), 플레이 위치(0 길이 → 0, 범위 밖 → 0..1), `streakDotsFilled`(0 → 0, 주기 배수 → 꽉 참), `StreakImpact`(오늘 전이면 +1, 주기 배수면 보상), `QuestHint` 모두 경계값 테스트가 있음. 테스트 이름은 한글, 대부분 given / when / then. 바뀐 기존 테스트(`VoiceScriptTest` 옛 문구 테스트 대체, `HomeViewModelTest` streakDays → streak, `AchievementsViewModelTest` period = 5)는 계획(7.2 · 8장 · 9장)과 맞음
- **TDD 커밋 순서**: PR-A 28사이클 · PR-B 11 · PR-C 3 모두 `test:` → `feat:`(필요하면 `refactor:`) 순서. 화면 작업은 테스트 없이 `feat:`로 묶음(진행 방식 변경 2장대로)
- **레이아웃**: 화면은 모두 `weight` · `fillMaxSize`. 버튼 · 퀘스트 배너 · 스탯 행 · 타임라인 점 터치 영역 64dp 이상, 최근 경기 행 72dp. 결과 · 홈 · 퀘스트 · 업적 · 리그 · 하이라이트 모두 1280×720 · 720 폭 Preview 있음
- **문구**: 새 문구는 모두 strings.xml. 쓰지 않게 된 문자열은 삭제됨(lint UnusedResources 늘지 않음)
- **아키텍처**: domain(`MatchOutcome`)은 Android 의존성 없음. ViewModel은 Compose · SDK 타입을 import하지 않음. 권한 · Manifest 변경 없음

## 검증

- `./gradlew :automotive:testDebugUnitTest :automotive:lintDebug` (JDK 17): **성공**. 단위 테스트 326개(53개 클래스) 모두 통과, 실패 0. lint 경고 15건(기준선과 같음), 오류 0
- `aapt2 dump resources automotive-debug.apk`로 1번(앞 공백이 사라진 문자열)을 확인함
- 시연 녹화 스크립트 `scripts/record_demo.py`는 이 작업 트리에 없어(gitignore, 로컬 전용) 확인하지 못함. 바뀐 화면 문구는 plan-ui 7.4-7 · 8장 남은 단계 2 · 9장에 정리돼 있음. 특히 홈 상태줄 "운행을 기다리고 있어요" → "출전 대기 중 · 출발하면 경기가 시작돼요"와 진입 연출 동안 글자가 투명한 점(8번 지적 포함)을 녹화 전에 확인해야 함
- 에뮬레이터는 직접 돌리지 않음. 2~4번은 메인 세션이 에뮬레이터에서 본 내용과 코드를 맞춰 본 결과
