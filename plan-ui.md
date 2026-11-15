# 정차 화면 게임 UI 작업 계획 (plan-ui)

정차(P) 화면을 "경기"처럼 보이게 바꾸는 2차 작업의 계획과 진행 기록입니다. 세션이 끊겨도 다른 사람이 이 문서만 보고 이어서 할 수 있도록 끝난 것과 남은 것을 모두 적습니다.

- 마지막 갱신: 2026-11-15
- 작업 브랜치: `feature/game-ui` (PR-A, develop 기준, PR #82) → `feature/game-ui-lobby` (PR-B, PR-A 위, PR #83) → `feature/game-ui-shelf` (PR-C, PR-B 위, push 안 함)
- **상태(11.15): PR-A · B · C 구현 · 문서 완료.** 남은 것은 아래 10장 "남은 일"
- 디자인 목업: [`docs/mockups/game-ui.html`](docs/mockups/game-ui.html) (브라우저로 열기, 화면 제목 옆 "모션 다시 보기")
  - **목업에는 데이터가 없거나 팀 결정과 다른 요소가 있습니다. 목업과 이 문서가 다르면 이 문서를 따릅니다** (아래 "목업과 다르게 하기로 한 것")
- 화면 기준 문서는 여전히 [`docs/screen-plan.md`](docs/screen-plan.md). 이 작업이 끝나면 그 문서에 반영합니다(PR-A · C 문서 단계)

## 1. 목표와 원칙

"운행 = 경기, 회전 = 플레이, 깜빡이 켬 = 득점, 안 켬 = 실점, 평점 = 등급 도장"이라는 경기 비유를 정차 화면 전체에 일관되게 적용합니다. **데이터와 화면 흐름은 그대로 두고 표현(레이아웃 · 색 · 모션 · 문구)만 바꿉니다.**

지켜야 할 것 (CLAUDE.md 주행 안전 원칙 그대로):
- 연출은 **정차(P) 화면에서만**. `DrivingGuard`와 DRV 덮개 방식은 바꾸지 않음
- 음성은 그대로 **운행당 시동 1회 · 종료 1회**. 주행 중 효과음 없음
- 점멸 없음(3Hz 미만 규칙), 무한 반복 연출은 크기 변화 6% 이내, 탭하면 연출 건너뛰기
- 꽃가루는 최고 등급 · 퀘스트 클리어 때만 1회
- **새 의존성 없음** (Lottie 등 금지). Compose 1.6 기본 API(Animatable, animate*AsState, AnimatedVisibility, rememberInfiniteTransition, Canvas)만
- **앱에 이미 있는 데이터만 사용.** 서버 필드를 지어내지 않음
- 고정 width 대신 `weight` · `fillMaxSize`, 터치 영역 64dp 이상, 문구는 strings.xml, 용어는 "퀘스트"

## 2. 진행 방식

CLAUDE.md "작업 방식" 그대로입니다.
- lead → tester(red) → developer(green · refactor) → reviewer. 서브에이전트 정의는 `.claude/agents/`
- 사이클마다 실패 테스트 1개 → `test:` 커밋, 최소 구현 → `feat:`/`fix:`, 정리 → `refactor:`
- 동작이 바뀌지 않는 일(레이아웃 · 색 · 애니메이션 · Preview · 분기 없는 문구)은 테스트 없이 developer가 바로 고치고 `feat:`/`chore:`/`docs:`
- JDK 17: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`

### 진행 방식 변경 (2026-11-15, 사용량)

사용량을 줄이려고 사용자가 이 작업의 진행 방식을 바꿨습니다. PR-A 화면 작업부터 적용합니다.
1. **PR마다 새 에이전트 하나**가 그 PR을 처음부터 끝까지 맡음
2. TDD 사이클은 **한 에이전트가 red → green을 직접** 함. 커밋은 그대로 나눔(`test:` 다음 `feat:`/`fix:`, 필요하면 `refactor:`)
3. **lead는 더 쓰지 않음**(이 문서의 계획이 lead 결과를 대신함). **reviewer는 PR마다가 아니라 A · B · C가 모두 끝난 뒤 한 번**만 검토

## 3. 결정 사항

| # | 항목 | 결정 | 이유 |
| --- | --- | --- | --- |
| 1 | 득점 · 실점 색 | 기존 파랑(`Accent` · `SuccessContainer`) · 주황 유지. 목업의 초록(`#4ADE80`) 안 씀 | 색각 이상 고려(`ui/theme/Color.kt`, screen-plan 6장) |
| 2 | 포인트 표현 | "+NP 적립" 카운트업만. 목업의 "경기 상금 거리 × 평점" · 보물상자 · "보상 받기" 버튼 · "오늘 상금 n/150P" · 홈 "보유 포인트"는 넣지 않음 | 결정 11.14(포인트는 리그 가산점, 리워드처럼 보이지 않게). 2차 #19와 함께 정함 |
| 3 | 용어 | "퀘스트". 목업의 "오늘의 도전"은 안 씀 | 결정 11.13 |
| 4 | BEST 플레이 · "N초 전 점등" | 이번엔 넣지 않음 | 데이터 없음(아래 5장). 하려면 별도 feat |
| 5 | 종료 음성에서 "퀘스트 클리어! 20포인트, 6연승"을 먼저 말하기 | **보류(사용자 결정 필요)** | 종료 음성은 서버를 기다리지 않음(결정 11.01). 기기가 서버의 퀘스트 판정(D25)을 흉내 내야 해 규칙이 중복됨 |
| 6 | 리그 단계(실버 · 골드) · 승강 구역 · 순위 변동 · "승급까지 N계단" · 선수 경험치 · 레벨 | 넣지 않음. **결정 필요 안건** | 데이터 · 개념 없음. 서버 요청 후보 17(`me.previousRank`) |
| 7 | 배지 진행도 · "가장 가까운 트로피(82/100)" | 넣지 않음. **결정 필요 안건** | 데이터 없음. 서버 요청 후보 16(`BadgeResponse.progress { current, target }`) |
| 8 | 목업 파일 | `docs/mockups/game-ui.html`에 둠 | 동료가 이어서 작업할 수 있게 |
| 9 | 효과음 | 넣지 않음 | 음성 끄기 토글(2차 #15)이 없고 음성 2회 원칙과 별도 결정 |
| 10 | PR 분할 | A · B · C 3개(아래 6장) | 사이클 40개 이상이라 한 PR은 리뷰가 어려움 |

## 4. 목업과 다르게 하기로 한 것

목업을 그대로 구현하면 안 되는 부분입니다.

1. **"N초 전 점등" · BEST 플레이**: 목업은 "앱 판정 결과에 이미 있다"고 했지만 사실이 아님. `SignalJudge.isSignalOn`(`domain/detection/SignalJudge.kt`)은 Boolean만, `TurnRecord`(`domain/model/TripResult.kt`)도 `signalOn`만 있음. 서버 `PlayItem`도 `occurredAt · type · direction · result`뿐
2. **홈 "한 번 더 회전하면 클리어!"는 틀린 안내**: 퀘스트는 운행 1건 = 1라운드로 판정(서버 D25, `FakeMissionApi.kt` 주석). 다음 운행은 0부터 다시 셈. 문구는 **"다음 경기에서 회전 N번 모두"**
3. **목업의 "Compose가 화면 밖 애니메이션을 정지"는 틀림**: `MainActivity`는 NavHost를 그대로 둔 채 DRV로 덮음(결정 11.08). 그래서 주행 중에도 정차 화면이 아래에서 그려짐 → `LocalParked`로 P가 아니면 연출을 멈춤(T1)
4. 결정 1 · 2 · 3 (초록색, 상금 · 보물상자, "도전")

## 5. 데이터가 없어 뺀 것 (나중에 하려면)

| 요소 | 필요한 것 |
| --- | --- |
| "N초 전 점등", BEST 플레이 | 앱: `SignalJudge`가 첫 점등 시각을 돌려주고 `TurnRecord` · Room에 컬럼 추가(exportSchema=false라 마이그레이션 방식부터 결정). 깜빡이 4단계(`signalTiming`) 결정과 묶임 |
| 리그 순위 변동(▲3) | 서버 요청 후보 17: `me.previousRank` |
| 리그 단계 · 승강 구역 · 승급까지 N계단 | 서버 plan에 새 개념 결정 필요 |
| 선수 카드 시즌 경험치 · 레벨 | 서버 plan에 새 개념 결정 필요 |
| 배지 진행 막대 · 가장 가까운 트로피 | 서버 요청 후보 16: `BadgeResponse.progress` (앱이 배지별 기준 100회 · 7일을 하드코딩하지 않으려면 필요) |

## 6. PR 나누기

| PR | 브랜치 | 범위 | 상태 |
| --- | --- | --- | --- |
| A | `feature/game-ui` | 공통 모션 부품, SCR-02 결과, C안 음성(시동 · 종료 중계 톤), SCR-02a 하이라이트 | **구현 · 문서 완료**: TDD 28개, T1~T5 · T10 · T11, 검증(단위 테스트 312 · 빌드 · lint 15). 에뮬레이터 확인 · PR 남음, 리뷰는 마지막 한 번으로 미룸 |
| B | `feature/game-ui-lobby` (A 머지 전이라 `feature/game-ui` 위에서 시작) | SCR-01 홈 로비, SCR-03 퀘스트 | **구현 · 문서 완료**(11.15): TDD 11개(29~39), T6 · T7, 검증(단위 테스트 323 · 빌드 · lint 15). 에뮬레이터 확인 · PR 남음, 리뷰는 마지막 한 번 |
| C | `feature/game-ui-shelf` (B 머지 전이라 `feature/game-ui-lobby` 위에서 시작) | SCR-05 업적 진열장, SCR-04 리그, 문서 마무리 | **구현 · 문서 완료**(11.15): TDD 3개(40~42), T8 · T9, 문서 마무리, 검증(단위 테스트 326 · 빌드 · lint 15). 에뮬레이터 확인 · PR 남음 |

## 7. PR-A 상세

### 7.1 설계

- `domain/model/MatchOutcome.kt`(새): `enum MatchOutcome { PERFECT, WIN, DRAW, TOUGH }`, `of(goals, conceded): MatchOutcome?` (회전 0이면 null). 결과 · 홈 · 음성이 함께 씀
- `voice/VoiceScript.kt`: `tripStart(mission, streakDays: Int? = null)`, `tripEnd(result, minDistanceKm, signalLeadSec)` 중계 톤, 문장 2개 이하
- `service/TripVoiceGuide.kt`: 생성자에 `streak: suspend () -> Streak?`, `turnConfig: () -> TurnConfig`. 시동 때 퀘스트 · 연승을 동시에(`async(UNDISPATCHED)`) 받음. `AppContainer`에 `rewardRepository::streak`, `{ config.turn }` 연결
- `ui/result`: `ResultUiState.Loaded`에 `outcome`, `celebrate`(config.grades에서 minScore가 가장 높은 등급), `tip`(실점이 있으면 `tips["TURN_SIGNAL"]`). `toResultUiState`와 `ResultViewModel`에 `tips` 인자(`ResultScreen`이 `container.config.tips` 전달). `PlayCard.position`(출발~도착 비율 0..1)
- `ui/common/game/`(새, 화면 부품): `CountUpText`, `GradeStamp`(spring dampingRatio 0.45, scale · rotation −6°), `ProgressRing`, `Confetti`(Canvas 파티클 30개, 2초, 1회, 시드 고정), `StreakFlame`(1.6초 주기, 6% 이내), `StaggeredReveal`(순차 등장 + 탭 건너뛰기, rememberSaveable), `HatchedBox`(사선 무늬), `LocalParked`
- `ui/theme/Color.kt`: Gold `#FFCC4D` · Silver · Bronze · Flame `#FF7A45`, 등급색(맨 위 등급 금, 이하 하늘 `#7CC4FF` · 민트 `#8BE0B4` · 보라 `#C9B6FF`, 없음 회색). 등급 이름이 아니라 config.grades 순서로 고름

### 7.2 TDD 사이클 — 모두 완료 (2026-11-15, 마지막 커밋 `9cbb707`)

전체 단위 테스트 234개 통과, `assembleDebug` 성공.

| # | 테스트 (위치) | 상태 |
| --- | --- | --- |
| 1~5 | `MatchOutcomeTest`: 2:1 승리 / 1:1 무승부 / 1:3 아쉬운 경기 / 3:0 완승 / 0:0 승패 없음 | ✅ (+ refactor 분기 정리) |
| 6 | `ResultUiStateTest`: 결과 화면 상태에 득점과 실점으로 정한 승패 | ✅ |
| 7 | 짧은 이동 결과에는 승패를 담지 않음 | ✅ |
| 8 | 기준값에서 가장 높은 등급이면 축하 연출 켬, 아래 등급은 끔 ("S" 하드코딩 없음) | ✅ |
| 9~10 | 실점이 있으면 깜빡이 팁, 없으면 비움 | ✅ (+ refactor 실점 계산 한 곳으로) |
| 11 | `ResultViewModelTest`: 결과 화면은 기준값의 팁을 씀 | ✅ |
| 12~14 | 플레이 위치 = 회전 시각 비율 / 출발 = 도착이면 0 / 범위 밖은 0..1로 자름 | ✅ (+ refactor `position` 함수) |
| 15 | `VoiceScriptTest` 종료: 짧은 이동 → "경기 종료. 짧은 이동이라 경기에 반영되지 않아요." | ✅ |
| 16 | 회전 없음 → "경기 종료. 오늘은 회전 없는 경기였어요." | ✅ |
| 17 | 모두 득점(2:0) → "경기 종료! 2 대 0 퍼펙트 승리입니다!" (TripVoiceGuideTest 1:0도) | ✅ |
| 18 | 3:1 → "경기 종료! 3 대 1 승리입니다. 화면에서 하이라이트를 볼 수 있어요." | ✅ |
| 19 | 1:1 → "경기 종료! 1 대 1 무승부입니다. 화면에서 하이라이트를 볼 수 있어요." | ✅ |
| 20 | 1:3, 7초 → "경기 종료. 1 대 3, 아쉬운 경기였어요. 다음 경기에선 회전 7초 전에 깜빡이!" | ✅ |
| 21 | 0:2, 7초 → "경기 종료. 이번엔 아쉬웠어요. 다음 경기에선 회전 7초 전에 깜빡이!" | ✅ (+ refactor `tripEnd`를 MatchOutcome으로) |
| 22 | `TripVoiceGuideTest`: 종료 안내는 기준값 `turn.signalLeadSec` 사용 | ✅ |
| 23 | 시동: 진행 중 · 지난 날 미달성 → "깜빡이리그, 경기 시작합니다! 오늘의 퀘스트는 회전 깜빡이 완벽, 회전 세 번 모두 득점해 볼까요?" | ✅ |
| 24 | 이미 클리어 → "경기 시작합니다! 오늘의 퀘스트는 이미 클리어했어요, 이번 경기도 깜빡이 잊지 마세요." | ✅ |
| 25 | 퀘스트 못 받음 → "깜빡이리그, 경기 시작합니다! 회전할 때 깜빡이를 미리 켜 주세요." | ✅ |
| 26 | 2일 이상 연승 → "5연승 중, 경기 시작합니다! 오늘의 퀘스트는 …" | ✅ |
| 27 | 연승 1일 이하 · 모름 → 연승을 말하지 않음 | ✅ |
| 28 | `TripVoiceGuideTest`: 시동 안내에 연승을 받아 넣고 못 받으면 뺌 | ✅ |

옛 음성 테스트(`종료 안내 - 결과별 문구`, `시동 안내는 오늘의 퀘스트로 상태별 문구를 말한다`)는 새 테스트로 모두 대체되어 삭제됨.

### 7.3 테스트 없이 고칠 것 — 진행 중

`feature/game-ui`의 최신 커밋으로 어디까지 됐는지 확인할 것(`git log --oneline 9cbb707..origin/feature/game-ui`).

- [x] **T1 `LocalParked`**: `MainActivity`에서 `CompositionLocalProvider(LocalParked provides guard.showParkedScreens)`로 NavHost를 감쌈. 불꽃 · 발광 · `rememberInfiniteTransition`은 false면 정지 상태로 그림. 순차 등장 · 카운트업도 P가 아니면 시작 안 함. DrivingGuard 판정 · 덮는 방식은 그대로
- [x] **T2 `ui/common/game/*`**: 7.1의 부품. 시간: 카운트업 600ms ease-out, 도장 550ms spring(0.45), 링 · 막대 800~1000ms, 카드 400ms 간격 150~300ms, 꽃가루 2초 1회. 점멸 없음(상태줄도). 무한 연출 6% 이내. `StaggeredReveal`은 탭 → 최종 상태, `rememberSaveable`로 하이라이트에 갔다 와도 다시 재생 안 함
- [x] **T3 테마**: 7.1의 색 추가. 득점 · 실점 색은 기존 유지
- [x] **T4 `ResultScreen.kt` 교체**: 왼쪽 스코어보드(득점 : 실점 큰 숫자 카운트업 + outcome 문구 "완승 / 승리! / 무승부 / 아쉬운 경기", 회전 0회면 "오늘은 회전 없는 경기"), 등급 도장(0.9초 뒤), 평점 카운트업, "+NP 적립" 카운트업(0P면 숨김 그대로), `celebrate`일 때만 Confetti. 오른쪽 스탯 막대 채움(ComingSoon · Unsupported 행은 HatchedBox + 기존 문구 "준비 중" · "이 차량 미지원"), "다음 경기 팁"(`tip`) 카드, 퀘스트 배너(QuestChip 확대), 플레이 보기 · 확인 버튼 64dp. 베스트 플레이 카드 없음. Preview: S(꽃가루) · A · Pending · NoTurn · Excluded × 1280×720 · 720 폭
- [x] **T5 `PlaysScreen.kt`**: 출발~도착 막대 위 `position`대로 점(득점 · 실점 색), 점 누르면 `LazyRow` `animateScrollToItem`(터치 64dp), 카드: 큰 판정 단어 "GOAL!" / "MISS" + 방향 화살표 + 근거 "깜빡이 켬 / 안 켬"(기존 strings). N초 전 · BEST 없음
- [x] **T10** 새 문구는 모두 strings.xml(game_ 또는 화면 접두사). 속도 재촉 표현 금지
- [x] **T11** 효과음 없음 (확인만)

### 7.4 PR-A 남은 단계

1. [x] T1~T5 완료 (developer, 11.15). UI 문구 때문에 깨진 기존 테스트 없음. develop(점수 항목 확장 #81)을 병합하며 결과 화면 상태 · ViewModel 충돌을 양쪽 기능 모두 살려 해결
2. [x] 전체 검증 (11.15, 병합 뒤): 단위 테스트 312개 통과, assembleDebug 성공, lint 경고 15(기준선과 같음).
    `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:lintDebug`. lint 경고는 기준선 15건을 넘지 않음
3. [ ] 에뮬레이터 확인: Pleos 에뮬레이터(`docs/emulator-setup.md`), 디버그 홈 "시나리오 재생"으로 결과 · 하이라이트 화면. Pleos는 사용자 10이라 adb로 켤 때 `adb shell am start --user 10 -n com.example.blinkerleague/.MainActivity`
4. [x] 문서 (developer, 11.15. plan.md의 #20은 이미 '점수 항목 확장'이 써서 **#22**로 적음. screen-plan은 결과 = 4.4, 하이라이트 = 4.5): `plan.md` 2차 표에 "#20 정차 화면 게임 UI"(PR-A/B/C 하위 항목, #16 연승 표시 보강은 B · C, #17 VOC-02는 "퍼펙트 문구"만 A), 일정 표 2차 범위에 추가, 결정 기록(3장 결정 + 승패 규칙 + 축하는 맨 위 등급 · 퀘스트 클리어만 + 반복 연출은 P일 때만 + 음성 C안 2회 유지 · 초 단위는 `turn.signalLeadSec`). `docs/screen-plan.md` 4.3(결과) · 4.4(하이라이트) · 6장(모션 규칙 표, 색) · 9장(결정 1 · 2 · 4)
5. [ ] ~~reviewer~~ **미룸**: 진행 방식 변경(2장)에 따라 PR-C까지 끝난 뒤 한 번만 검토. 그때: 브랜치 전체 검토 → `review/<날짜>-feature-game-ui.md`. 확인 포인트는 아래 7.5
6. [ ] 리뷰 파일 커밋 · push · PR(develop). **머지는 사용자 확인 후** (develop은 squash merge)
7. [ ] 시연 녹화 스크립트(`scripts/record_demo.py`, 저장소 밖 로컬 전용)는 화면 글자로 버튼을 찾음. "플레이 보기" · "확인" · "운행을 기다리고 있어요" 등 문구가 바뀌면 녹화가 깨질 수 있으니 확인. PR-A에서 "플레이 보기" · "확인" · "돌아가기"는 그대로. 바뀐 문구: 결과 "항목별 점수" → "이번 경기 스탯", "측정할 회전이 없었어요" 삭제(→ "오늘은 회전 없는 경기"), "득점 N · 실점 M" 줄 → 큰 숫자(접근성 설명으로만 남음), 퀘스트 칩 → 배너(같은 문구), 하이라이트 안내 "좌우로 넘겨 전체 플레이 보기" → "타임라인의 점을 누르면 해당 플레이로 이동". 결과 화면은 진입 연출 동안 글자가 투명하게 시작함(약 3초, 화면을 누르면 바로 최종 상태)

### 7.5 reviewer 확인 포인트 (PR-A)

- 주행 안전: 반복 · 순차 연출이 `LocalParked == false`에서 멈추는지, DrivingGuard · MainActivity 덮는 방식 그대로인지, 다이얼로그 · 토스트 점수 경로 · 효과음 없음
- 음성: 운행당 시작 1 · 종료 1, 퀘스트 · 연승 동시 수신, 문장 2개 이하, 속도 재촉 표현 없음
- 하드코딩: "N초 전"은 `turn.signalLeadSec`, 축하 등급은 config.grades 맨 위
- 데이터 정직성: 경험치 · 리그 단계 · 순위 변동 · 배지 진행 · N초 전 · BEST · 회당 상금을 지어낸 곳 없음
- 모션 접근성: 점멸 없음, 무한 연출 6% 이내, 탭 건너뛰기, rememberSaveable, 꽃가루 1회
- 레이아웃: weight · fillMaxSize, 1280×720 · 720 폭 Preview, 터치 64dp(타임라인 점 포함)
- 기존 테스트 갱신이 맞는지: `VoiceScriptTest`, `TripVoiceGuideTest`
- lint 경고 기준선 15건 이하

## 8. PR-B 계획 (홈 로비 · 퀘스트) — 완료 (2026-11-15)

`feature/game-ui-lobby`(PR-A가 아직 머지 전이라 `feature/game-ui` 위에서 시작). 진행 방식 변경(2장)에 따라 lead 확인 없이 에이전트 하나가 이 장대로 진행함.

설계:
- `ui/home`: `RecentTripRow`에 `goals` · `conceded` · `outcome` · `distanceKm`. `HomeUiState`의 `streakDays` · `streakTodayAchieved`를 `streak: StreakCard?`로. `player: PlayerCard?(nickname, regionLabel)`. `HomeViewModel`에 `profile: () -> DriverProfile?`, `rule: () -> PointConfig`
- `ui/quest`: `QuestHint.kt`(새, 순수): `sealed QuestHint { Cleared; NextTrip(requiredTurns) }`. `QuestUiState.Loaded`에 `streakImpact: StreakImpact?`(클리어하면 N연승 / 오늘 클리어로 N연승 / 보상 포인트). `QuestViewModel`에 `loadStreak` · `rule`
- `ui/achievements`: `streakDotsFilled(days, period)` 순수 함수(`AchievementsUiState.kt`의 `daysToReward` 옆), `StreakCard`에 `period`

TDD 사이클:
| # | 테스트 (위치) | 예상 red | 상태 |
| --- | --- | --- | --- |
| 29 | 최근 경기 행에 득점 · 실점 · 승패 · 거리를 담는다 (`HomeViewModelTest`, `trip()` 헬퍼에 회전 추가) | 새 필드 기본값 0 / null | ✅ |
| 30 | 연승 진행 점 - 연승 3일이면 주기 7칸 중 3칸 (`AchievementsViewModelTest`) | 골격이 0 | ✅ |
| 31 | 연승이 주기의 배수(7, 14)면 모든 칸 | `days % period`가 0 | ✅ |
| 32 | 연승 0일이면 칸을 채우지 않음 | 31 구현이 7 | ✅ (+ refactor 한 식으로) |
| 33 | 홈 연승 카드는 기준값 주기로 남은 날과 보상 포인트 (PointConfig(5, 30), Streak(3, true) → StreakCard(3, true, 2, 30, period=5)). 기존 `streakDays` 단언을 `streak?.days`로 | 기본값 7 · 50 | ✅ |
| 34 | 홈에 등록한 닉네임과 지역 이름 (`Regions.label`) | `player` null | ✅ |
| 35 | 퀘스트를 클리어했으면 클리어 안내 (`QuestHintTest` 새) | 골격 기본값 NextTrip | ✅ |
| 36 | 클리어 전이면 "다음 경기에서 회전 N번 모두" (IN_PROGRESS 2/3, FAILED → NextTrip(3), 남은 횟수가 아니라 requiredTurns 전체) | requiredTurns 대신 0 | ✅ |
| 37 | 오늘 클리어 전이면 클리어 시 이어질 연승 일수 (`QuestViewModelTest` 새, Streak(5, false) → 6) | `streakImpact` null | ✅ |
| 38 | 오늘 이미 클리어면 오늘 클리어로 이어진 일수 (Streak(6, true) → 6) | 37 구현이 7 | ✅ |
| 39 | 다음 클리어가 연속 보상 주기면 보상 포인트 (Streak(6, false), period 7 → 50, 아니면 null) | 기본값 null | ✅ |

전체 단위 테스트 323개 통과(PR-A 312 + 11). 실제로 확인한 red는 모두 어서션 실패(골격 기본값 0 · null · 7 · NextTrip(0)).

구현 메모:
- `QuestViewModel(loadMission, loadStreak, rule)`: 퀘스트와 연승을 동시에 받음. 연승을 못 받으면 `streakImpact` null(그 줄만 숨김). 보상 포인트는 이어질 일수가 `point.streakRewardDays`의 배수일 때(오늘 이미 클리어한 날 포함)
- `HomeViewModel`에 `rule`(기준값 `point`) · `profile`(`AppContainer.profile()` = 토큰 저장소의 등록 정보). `grades`는 화면의 등급 도장 색 때문에 공개
- `StreakCard.period` 추가(업적 화면 ViewModel은 아직 안 채움 → PR-C 사이클 40)

테스트 없이 고칠 것 — 완료 (커밋 `feat: 홈을 경기장 로비로, 퀘스트를 체크포인트 보드로 개편`):
- [x] **T6 `HomeScreen.kt`**: 선수 카드(닉네임 · 지역만. 경험치 · 리그 단계 · 포인트 없음), `QuestPanel`에 `ProgressRing` + 조건 체크 + `QuestHint` 문구, `StreakPanel`에 불꽃 + `period`칸 점 + "N일 더 하면 …", `RecentTripItem`에 등급 도장 + "3 : 1 승리 · 3.9km", 상태줄 `home_status_waiting`을 "출전 대기 중 · 출발하면 경기가 시작돼요"(녹화 스크립트 확인)
- [x] **T7 `QuestScreen.kt` · `MissionCard.kt`**: 조건을 체크포인트로(회전 수는 칸, 사용률은 막대, 달성은 성공색 체크), `streakImpact` 줄, ACHIEVED면 "CLEAR" 도장 + 꽃가루(진입 시 1회), `MissionCard`의 `LinearProgressIndicator`를 링으로. 보물상자 · 보상 받기 · 일일 상한 줄 없음

T6 · T7 결과:
- 홈: 선수 카드(머리글자 · 닉네임 · 지역 칩), 퀘스트 카드 = `MissionCard`(링 "2/3 회전" + 조건 체크 + "다음 경기에서 회전 N번 모두 득점하면 클리어"), 연승 카드(불꽃 · "N연승" · 오늘 달성 여부 · `period`칸 점(마지막 칸 금색 테두리) · "N일 더 이어가면 M연승 · 연속 보상 +XP"), 최근 경기(등급 도장 · "3 : 1 승리" · "오늘 22:43 · 3.9km" · "평점 N"/"결과 집계 중"), 상태줄 문구 변경(점은 깜빡이지 않음). 진입 연출: 카드 순차 등장 · 링 채움 · 도장, 탭 건너뛰기
- 퀘스트: 왼쪽 체크포인트(48dp 체크 원, 회전 수는 칸 — 10칸 넘으면 막대, 사용률은 막대, 채움 연출) · 연승 영향 줄("클리어하면 6연승" / "오늘 클리어로 6연승" + 주기면 " · 연속 보상 +50P"), 클리어면 상태 대신 "CLEAR" 도장 + 꽃가루 1회. 오른쪽 "달성 보상 +20P"(금색) · `QuestHint` 안내 · 버튼(64dp). 보물상자 · 보상 받기 · 일일 상한 없음
- 문구는 strings.xml `game_*`. 쓰지 않게 된 `mission_progress` · `mission_hint_in_progress` · `mission_hint_failed` · `home_streak_label` · `home_score_grade` 삭제
- Preview: 홈 1280×720 · 720(클리어 · 집계 중 · 짧은 이동) · 720 빈 상태, 퀘스트 1280×720(클리어) · 720(진행 중 + 보상 주기) · 720(미달성 · 연승 없음), MissionCard

PR-B 남은 단계:
1. [ ] 에뮬레이터 확인(홈 · 퀘스트, 정차 · 주행 전환 시 연출 멈춤)
2. [ ] 시연 녹화 스크립트(`scripts/record_demo.py`, 저장소 밖 로컬 전용) 확인: **홈 상태줄 "운행을 기다리고 있어요" → "출전 대기 중 · 출발하면 경기가 시작돼요"**. 홈 최근 경기 행 "평점 N · 등급" → 도장 + "3 : 1 승리" + "평점 N", 연승 카드 제목 "연속 달성" 삭제(→ "N연승"), 퀘스트 카드 "회전 N / M · 모두 제때 켜기" → 링 "N/M 회전". 홈 · 퀘스트 화면도 진입 연출 동안 글자가 투명하게 시작함(약 1.6초 · 3초, 누르면 바로 최종 상태). "시나리오 재생" · "돌아가기" · "확인" · "다음 경기에서 재도전"은 그대로
3. [ ] ~~reviewer~~ PR-C 뒤 한 번. PR / 머지는 사용자 확인 후 (A가 먼저 머지되면 develop 기준으로 다시 맞춤)

reviewer 추가 포인트: 홈 다음 행동 문구가 D25(다음 운행은 0부터)와 맞는지, 연승 칸 수 · 보상은 `point.streakRewardDays` · `streakRewardPoint`에서 읽는지. 기존 테스트 `HomeViewModelTest`(streakDays → streak), `AchievementsViewModelTest`(StreakCard 동등 비교, 새 필드 기본값으로 유지).

## 9. PR-C 계획 (업적 · 리그 · 문서 마무리) — 완료 (2026-11-15)

B가 아직 머지 전이라 `feature/game-ui-lobby` 위에서 `feature/game-ui-shelf`로 시작함. 진행 방식 변경(2장)대로 에이전트 하나가 진행.

TDD 사이클:
| # | 테스트 | 예상 red | 상태 |
| --- | --- | --- | --- |
| 40 | 업적 연승 카드에 기준값 주기와 채운 칸 수 (`AchievementsViewModelTest`, period 5, Streak(3, …) → period 5, dotsFilled 3) | period 기본값 7 | ✅ (기존 테스트 "연속 보상 주기와 보상 포인트를 기준값에서 읽는다"의 기대값도 period = 5로 맞춤) |
| 41 | 업적 화면에 획득한 배지 수 | `earnedCount` 기본값 0 | ✅ |
| 42 | 배지 카드에 서버 배지 코드를 담는다 (계획에 없던 사이클: T8 아이콘이 코드를 써야 해서 추가) | `code` 기본값 "" | ✅ |

실제로 확인한 red는 모두 어서션 실패(7 ≠ 5, 0 ≠ 2, "" ≠ "SIGNAL_100"). 전체 단위 테스트 326개 통과(PR-B 323 + 3).

테스트 없이 고칠 것:
- **T8 `AchievementsScreen.kt`**: 진열장 그리드, 잠긴 배지는 실루엣(알파 · 회색) + 🔒 + 서버 `condition` 그대로, 획득 배지는 `awardedOn`, 상단 "획득 N / 전체 M", 배지 코드별 아이콘(FIRST_TRIP · STREAK_7 · SIGNAL_100 · SEASON_CHAMPION, 모르는 코드는 기본 트로피. 이모지 대신 icons-core · Canvas), 연승 카드에 진행 점(2차 #16 · 미구현 7). 진행 막대 · "가장 가까운 트로피"는 넣지 않음(결정 7)
- **T9 `LeagueScreen.kt`**: `MyLeaguePanel`에 큰 "N위"(myRank, 없으면 기존 문구)와 D-day 칩 강조, `RankingPanel`에 1~3위 금 · 은 · 동 색, 내 행 강조(기존 `me`). 승강 구역 · 순위 변동 · 승급까지는 넣지 않음(결정 6)

문서 마무리:
- `plan.md` "현황 점검" 미구현 표 7(SCR-05 연승 7일 점) · 8(SCR-03 연승 영향 문구) · 9(VOC-02 일부) · 10(SCR-01 퀘스트 진행 링)을 완료로
- `docs/screen-plan.md` 4.5 · 4.6 · 4.8 · 4.9 게임 UI 구성과 "데이터가 없어 뺀 것", 10장 표 갱신, 머리말에 목업 출처
- 결정 6 · 7 결과에 따라 `docs/server-requests.md`에 16(배지 진행도) · 17(리그 순위 변동) 추가 여부
- README 스크린샷(`docs/screenshots`)은 화면이 바뀌므로 다시 찍기(사용자)
- 이 문서(`plan-ui.md`) 진행 상태 갱신, 다 끝나면 내용을 plan.md · screen-plan.md로 옮기고 이 문서는 "완료"로 표시

결과 (커밋 `feat: 업적을 트로피 진열장으로, 리그에 큰 순위와 메달 색` 외):
- [x] **T8 업적**: 왼쪽 연승 카드(불꽃 · "N연승" · 오늘 달성 · 주기 칸 진행 점 · "N일 더 달성하면 +XP") + 주간 리포트 버튼, 오른쪽 "트로피 진열장 · 획득 N / 전체 M" + 배지 그리드. 아이콘은 `ui/achievements/BadgeIcon.kt`(Canvas: 깃발 · 불꽃 · 방향지시등 · 금 트로피, 모르는 코드는 파랑 트로피). 잠긴 배지는 회색 실루엣 + 자물쇠(`Icons.Filled.Lock`) + 서버 조건, 획득 배지는 금 테두리 · "MM.dd 획득". 배지 순차 등장. 연승 진행 점은 `ui/common/game/StreakDots.kt`로 옮겨 홈과 같이 씀
- [x] **T9 리그**: "N월 시즌" + D-day 칩, 큰 "N위"(1~3위 메달 색) + "참여자 M명 중"(순위가 없으면 기존 안내 문구), 리그 점수는 한 줄로 작게, 순위표 1~3위 금 · 은 · 동 원, 내 행 파랑 바탕 · 테두리 · "닉네임 (나)"(옆에서 들어옴). 내 리그 패널은 세로 스크롤
- [x] 문구는 strings.xml. 바뀐 문구: 리그 머리말 "11월 시즌 · D-21" → "11월 시즌" + 칩 "시즌 종료 D-21", "3위 / 24명" → 큰 "3위" + "참여자 24명 중", 내 행 "닉네임 (나)". 업적 연승 "5일" → "5연승", 배지 제목 "배지" → "트로피 진열장" + "획득 N / 전체 M", 잠긴 배지 "잠김" 글자 → 자물쇠 아이콘(설명 "잠김"). 쓰지 않게 된 `league_season` · `league_rank` · `achievements_badges` · `achievements_streak_days` 삭제
- [x] Preview: 업적 1280×720 · 720(연승 7일 꽉 참) · 720(연승 없음 · 배지 없음), 리그 1280×720(3위) · 720(7위, 내 행이 4위 아래) · 720(순위 비공개 · 순위표 없음)
- [x] 문서: plan.md "현황 점검" 7 · 8 · 10 완료, 9 일부(퍼펙트 문구만), 2차 #16 · #17 · #22, 결정 기록. screen-plan 머리말(목업 출처) · 1장 · 4.8 리그 · 4.9 업적 · 9장(결정 6 · 7 결정 필요) · 10장
- [ ] `docs/server-requests.md` 16 · 17: 결정 6 · 7이 아직 열려 있어 적지 않음
- 검증(11.15): `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:lintDebug` — 단위 테스트 326 통과, assembleDebug 성공, lint 오류 0 · 경고 15(기준선과 같음)

## 10. 남은 일 (2026-11-15, PR-C 뒤)

1. **리뷰 한 번**: reviewer가 PR-A · B · C 전체를 한 번에 검토 → `review/<날짜>-feature-game-ui.md`(확인 포인트 7.5 + PR-B reviewer 추가 포인트 + PR-C: 결정 6 · 7 요소를 지어낸 곳 없는지, 배지 코드 매핑 · 모르는 코드 기본 트로피, 리그 순위 없을 때 기존 문구)
2. **에뮬레이터 다시 확인**: 결과 · 하이라이트 · 홈 · 퀘스트 · 업적 · 리그, 정차 ↔ 주행 전환 때 연출이 멈추는지(Preview 렌더링도 Android Studio에서 눈으로 확인 안 됨)
3. **PR 머지 순서**: #82(PR-A) → #83(PR-B) → PR-C. 앞 PR이 squash merge되면 다음 브랜치를 develop 기준으로 다시 맞춤. 머지는 사용자 확인 후
4. **README 스크린샷**(`docs/screenshots`) 다시 찍기(사용자)
5. 시연 녹화 스크립트(`scripts/record_demo.py`) 문구 확인: 7.4-7 · 8장 PR-B 남은 단계 2 · 9장 바뀐 문구
6. **사용자 결정**
   - 결정 5: 종료 음성에서 퀘스트 클리어 · 포인트 · 연승을 먼저 말할지 ((a) 기기 추정으로 말함 (b) 말하지 않음)
   - 결정 6: 리그 단계 · 승강 · 순위 변동 · 경험치를 서버 plan 안건으로 올릴지, 서버 요청 17을 적을지
   - 결정 7: 서버 요청 16(배지 진행도)을 적을지
   - BEST 플레이 · "N초 전 점등"을 별도 feat로 할지
   - PR-B에서 남은 질문: 퀘스트를 이미 클리어한 뒤(오늘 클리어로 N연승)에도 연속 보상 포인트(" · 연속 보상 +XP")를 계속 보여 줄지
7. 다 끝나면 이 문서를 "완료"로 표시(내용은 plan.md · screen-plan.md에 옮겨 둠)
