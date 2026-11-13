# 리뷰: feature/badge-quest-wording (2026-11-13)

- 기준: develop...feature/badge-quest-wording (2개 커밋, HEAD e653d49)
- 범위: 배지 `STREAK_7` 설명 Fake · Preview · 테스트 픽스처 문구 "오늘의 미션" → "오늘의 퀘스트", 서버 요청 15번 · plan.md 결정 기록 · screen-plan 한 줄
- 판단: 머지 가능 (아래 두 건은 낮음. 머지 전에 문서만 손보면 더 좋음)

## 지적 사항

### 1. 15번 예시 `SEASON_CHAMPION`의 "미션" 포함 근거가 없음 (심각도: 낮음)
- **위치:** `docs/server-requests.md:304`
- **문제:** "다른 배지의 `condition`에도 '미션'이 들어 있으면(예: `SEASON_CHAMPION`)"이라고 적었는데, 배지 코드 자체의 근거는 `docs/api/README.md:22`(서버 배지 4종 `FIRST_TRIP` · `STREAK_7` · `SIGNAL_100` · `SEASON_CHAMPION`)에 있지만, 그 배지의 `condition` 문구에 "미션"이 들어 있다는 근거는 저장소 어디에도 없음. 앱 코드 · Fake(3종) · `openapi.json`(`BadgeResponse`는 필드 형식만 있음) · plan.md에 `SEASON_CHAMPION` 문구가 없음.
- **실패 시나리오:** 서버 팀이 예시를 "이 배지 문구에도 미션이 있다"는 앱 팀의 확인으로 읽고 필요 없는 문구를 찾거나 바꿈. 시즌 챔피언 배지는 이름으로 볼 때 리그 1위 조건일 가능성이 커서 예시가 맞지 않을 수 있음.
- **제안:** 예시를 빼거나 "앱은 `STREAK_7`만 확인함. 다른 배지(`FIRST_TRIP` · `SIGNAL_100` · `SEASON_CHAMPION`, `docs/api/README.md` 기준)의 `condition`에 '미션'이 있으면 같은 방식으로"처럼 근거와 확인 범위를 분명히 적음.

### 2. plan.md 기존 결정 문장이 새 결정과 겉보기에 어긋남 (심각도: 낮음)
- **위치:** `plan.md:390`(기존) · `plan.md:391`(새 결정)
- **문제:** 390행은 "서버가 주는 배지 문구는 바꾸지 않음", 391행은 그 문구를 서버에 바꿔 달라고 요청함. 의미상으로는 "앱이 덮어쓰지 않음"이라 모순은 아니지만 두 줄만 읽으면 결정이 뒤집힌 것처럼 보임.
- **실패 시나리오:** 다음 작업자가 390행을 보고 15번 요청이 결정과 어긋난다고 판단하거나, 반대로 앱에서 배지 문구를 치환하는 코드를 넣음.
- **제안:** 391행에 "앱은 여전히 서버 문구를 덮어쓰지 않음(11.13 위 결정 유지)"을 덧붙이거나, 221행처럼 390행 뒤에 "→ 서버에 요청(server-requests 15)"을 붙임. 기존 결정 행을 고치기 싫으면 391행 보강만으로 충분함.

## 확인한 것
- **식별자 · API 필드 · 리소스 키 미변경:** diff는 문자열 리터럴 3곳뿐. `STREAK_7`, `DAILY_MISSION`, `mission*` 리소스 키 · 경로 · 필드 이름 변경 없음. `strings.xml` 미변경이라 주간 리포트 "맞춤 미션"(172 · 176) 그대로.
- **테스트 어서션 미변경:** `RewardRepositoryTest.kt:22`는 픽스처 JSON의 `condition` 문자열만 바뀜. 해당 문자열을 비교하는 어서션 변경 없음.
- **서버 저장소 · `docs/api/openapi.json` 미변경:** 변경 파일 6개 중 해당 없음. 서버 변경은 `docs/server-requests.md` 15번으로만 요청함(CLAUDE.md 규칙과 일치).
- **server-requests 15번 일관성:** 갱신일(2026-11-13, 15번 추가), 처리 상태 표 15행(굵게 "새로 추가(11.13)" — 12 · 13번과 같은 표기), 요약 표 15행(API `GET /api/badges`, 종류 **변경**, 서버 plan.md "—"), 본문 15절 제목 "(변경)"이 서로 맞음. "바꾸지 않을 것"과 "맞춤 미션 제외"가 plan.md 221 · 390과 일치.
- **"오늘의 미션 7일" 잔존:** 저장소 전체에서 0건(15절 배경 설명의 인용만 있음). 남은 "오늘의 미션"은 KDoc 주석 · openapi 설명 · 과거 기록 문서뿐이고 화면 문구 아님.
- **screen-plan:** 4.9 SCR-05에 "서버 `condition`을 그대로 표시"와 15번 요청 한 줄이 추가돼 동작과 맞음.
- **TDD:** 동작 변경이 없는 문구(Fake · Preview · 픽스처)와 문서 변경이라 red 커밋 없이 `fix:` · `docs:`로 나눈 것이 CLAUDE.md 규칙(동작이 바뀌지 않는 일은 테스트 없이 developer가 `fix:` · `docs:`)과 맞음.
- **주행 안전 · 보안:** 화면 흐름 · 음성 · 권한 · 비밀값 변경 없음.

## 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug` — 성공(EXIT 0). 단위 테스트 207개, 실패 0 · 오류 0 · 건너뜀 0.
- `lintDebug`는 직접 돌리지 않음(tester 보고: 오류 0 · 경고 15, develop과 동일). 리소스 · 코드 구조 변경이 없어 재실행 필요성 낮음.

## 재검토 (2026-11-13)

- 기준: develop...feature/badge-quest-wording (3개 커밋, HEAD 8d3b834)
- 대상 커밋: `8d3b834 docs: 배지 퀘스트 용어 요청 확인 범위 · 서버 문구 비덮어쓰기 명시 (리뷰 지적)`. 변경 파일 `docs/server-requests.md`, `plan.md` 각 1줄
- 판단: **머지 가능**

### 지적 1 (15번 `SEASON_CHAMPION` 예시 근거): 해결
- `docs/server-requests.md:304`에서 근거 없는 "(예: `SEASON_CHAMPION`)"을 뺐음. 대신 "앱은 `STREAK_7` 문구만 확인했다"는 확인 범위와 나머지 배지 3종(`FIRST_TRIP` · `SIGNAL_100` · `SEASON_CHAMPION`)의 근거(`docs/api/README.md` 기준)를 적었음.
- `docs/api/README.md:22`의 배지 4종 목록과 맞음. 이제 서버 팀이 앱이 확인한 문구로 오해할 여지가 없음.

### 지적 2 (plan.md 390 · 391 겉보기 충돌): 해결
- `plan.md:391`에 "앱은 여전히 서버 문구를 덮어쓰지 않음(위 결정 유지)"을 덧붙였음. 390행 "서버가 주는 배지 문구는 바꾸지 않음"이 앱 쪽 치환 금지라는 뜻이 분명해져 두 결정이 이어져 읽힘. 기존 결정 행(390)은 고치지 않아 기록도 그대로 남음.

### 새 문제
- 없음. 문서 2줄만 바뀌었고 코드 · 리소스 · 테스트 · `openapi.json` · 서버 저장소 변경은 없음. 커밋 종류(`docs:`)도 맞음.

### 검증
- 문서만 바뀐 커밋이라 빌드 · 테스트를 다시 돌리지 않음. 코드는 처음 검토 때(HEAD e653d49)와 같고, 그때 `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug`가 성공함(207개, 실패 0).
