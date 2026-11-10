# 리뷰: feature/league-server-format (2026-11-10)

- 기준: docs/agents-and-openapi...feature/league-server-format (11개 커밋)
- 범위: 리그 응답 DTO를 서버 `CurrentLeagueResponse` 형식(`season` · `league` · `me` · `regions`)으로 교체, 평균 점수 + 시즌 포인트, 지난 시즌 우승 칭호, 순위 목록의 내 행(닉네임 비교), 화면 표시, 서버 요청 14번 해소 기록
- 판단: 머지 가능 (아래 지적은 모두 낮음. 머지 후 또는 다음 스택 브랜치에서 처리해도 됨)

## 지적 사항

높음 · 중간 심각도 문제는 찾지 못했다.

### 1. 서버 요청 요약 표의 14번이 아직 "제안"으로 남아 있음 (심각도: 낮음)
- **위치:** `docs/server-requests.md` "## 요약" 표의 14번 행 ("지역 리그 응답 형식 제안 … | 제안")
- **문제:** 상단 상태 표와 14장 본문에는 "해소"로 바꿨지만 요약 표의 종류 열은 그대로 "제안"이다.
- **실패 시나리오:** 서버 팀이 요약 표만 보면 14번이 아직 열린 요청으로 읽힌다.
- **제안:** 요약 표의 14번도 "해소"(또는 취소선)로 맞춘다.

### 2. AppContainer 주석이 "서버 4.4 미구현"으로 남아 있음 (심각도: 낮음)
- **위치:** `automotive/src/main/java/com/example/blinkerleague/AppContainer.kt:124` (`/** 지역 리그 (서버 4.4 미구현: 서버 모드에서는 API가 생길 때까지 불러오지 못함) */`)
- **문제:** 이 브랜치에서 서버 형식에 연결했으므로 주석이 사실과 다르다(`LeagueApi.kt` 쪽 주석은 "(서버 4.4)"로 고쳤음).
- **제안:** 주석에서 "미구현" 문구를 지운다.

### 3. null 분기(`league: null`, `me: null`, 기록 없음 `averageScore`/`score: null`)에 대한 테스트가 없음 (심각도: 낮음)
- **위치:** `automotive/src/test/java/com/example/blinkerleague/data/repository/LeagueRepositoryTest.kt` (네 테스트 모두 같은 `SERVER_LEAGUE` 응답 사용)
- **문제:** 명세상 `league`는 권역 매핑이 없으면 null, `me.averageScore` · `me.score`는 시즌 기록이 없으면 null인데, 이 경우의 매핑(`myLeague == null`, `myScore == null`, `averageScore == null`, `qualified == false`)을 확인하는 테스트가 없다. 코드(`LeagueRepository.kt`)는 `?.` · `?:`로 처리하고 있어 현재 동작은 맞아 보인다.
- **실패 시나리오:** 이후 DTO나 매핑을 바꿀 때 시즌 첫날(기록 0건) 응답에서 회귀가 생겨도 테스트가 잡지 못한다.
- **제안:** "시즌 기록이 없으면 리그 점수를 비운다", "등록 지역이 권역에 없으면 내 리그가 없다" 같은 테스트를 추가한다.

### 4. MockWebServer 테스트가 요청 경로 · 메서드 · 헤더를 확인하지 않음 (심각도: 낮음)
- **위치:** `LeagueRepositoryTest.kt` 전체
- **문제:** tester.md는 HTTP 테스트에서 `takeRequest()`로 경로 · 메서드 · 헤더를 확인하라고 하는데, 이 테스트는 파싱 결과만 본다. `GET api/leagues/current`(`BlinkerService.kt:50`)와 `Authorization: Bearer` 헤더를 확인하는 테스트가 저장소 어디에도 없다(`HttpApisTest`에 리그 항목 없음).
- **제안:** 첫 테스트에 `server.takeRequest()`로 `GET /api/leagues/current`와 `Authorization: Bearer access-1`을 확인하는 then 구역을 추가한다.

### 5. 순위 목록 점수를 반올림해 서로 다른 순위가 같은 점수로 보일 수 있음 (심각도: 낮음)
- **위치:** `LeagueRepository.kt` `top = l.rankings.map { LeagueEntry(it.rank, it.nickname, it.score.roundToInt(), …) }`
- **문제:** 서버 순위는 소수 점수 기준(같은 점수만 같은 순위)인데 앱은 정수로 반올림해 보여 준다.
- **실패 시나리오:** 138.4(1위)와 137.6(2위)이 둘 다 "138"로 보여 순위 차이의 근거가 화면에서 사라진다.
- **제안:** 시연에서 문제 되면 순위 목록만 소수 한 자리로 표시하는 것을 검토한다(필수 아님).

### 6. 닉네임을 Keystore 복호화로 매번 읽음 (심각도: 낮음)
- **위치:** `AppContainer.kt:125` `LeagueRepository(leagueApi) { tokens.profile()?.nickname }`, `TokenStore.kt:87-90` (`profile()`이 `cipher::decrypt` 호출)
- **문제:** `LeagueRepository.current()`는 `LeagueViewModel`의 `viewModelScope`(Main)에서 불리므로 새로고침마다 메인 스레드에서 Keystore 복호화가 한 번 일어난다.
- **제안:** 지금 규모에서는 체감 문제가 없을 가능성이 높다. 신경 쓰이면 닉네임을 한 번 읽어 캐시하거나 `withContext(Dispatchers.IO)`로 감싼다.

### 참고 (이 브랜치 범위 밖, 다음 스택 브랜치에서 다룸)
- `LeagueMeDto.visible`은 읽기만 하고 쓰이지 않는다. 순위 공개를 끈 사용자는 `me.rank == null`이라 화면에 "아직 순위가 없어요"(`league_not_ranked`)가 보인다 → `feature/league-visibility`에서 처리 예정으로 보임.
- 순위 자격 문구의 `QUALIFY_KM` · `QUALIFY_TRIPS`(`LeagueScreen.kt`)와 Fake의 `km >= 50 || trips.size >= 10`(`LeagueApi.kt`)은 하드코딩 기준값이다(이 브랜치에서 새로 생긴 것은 아님) → `feature/league-qualify-config`에서 처리 예정으로 보임.

## 확인한 것
- **서버 형식:** `LeagueDtos.kt`의 필드 이름과 타입이 openapi.json `CurrentLeagueResponse` · `LeagueResponse` · `MeResponse` · `RankingResponse` · `RegionResponse` · `ChampionshipResponse`와 맞다. 소수(`format: double`)인 `safetyIndex` · `averageScore` · `score`를 `Double`로 받았고, 명세상 null이 올 수 있는 `league` · `me` · `averageScore` · `score` · `rank` · `safetyIndex` · `name`은 nullable이다. 나머지도 기본값이 있고 `HttpClientFactory`의 Json이 `ignoreUnknownKeys` · `explicitNulls = false` · `coerceInputValues = true`라 누락 · null에 안전하다. non-null 필수인 것은 식별자(`leagueCode`, `seasonId`, `rank`, `nickname`)뿐이고 명세 예시상 항상 온다.
- **점수 표시:** `points`가 정수라 `round(averageScore) + points == round(score)`가 유지되어 "평균 N점 + 시즌 포인트 MP"와 큰 숫자가 서로 어긋나지 않는다. `myScore == null`이면 분해 문구를 숨긴다.
- **내 행 표시:** 닉네임이 없으면(`null`) 어떤 행도 나로 표시하지 않는다. 닉네임은 등록 시 중복 409로 막히고(`DriverRepository`), 결정 기록(plan.md 2026-11-10)에 근거가 적혀 있다.
- **TDD 커밋 순서:** red(`test:`) → green(`feat:`) 쌍 5개 + docs 1개. red 커밋의 프로덕션 변경은 기본값 있는 모델 필드(`averageScore`, `points`, `championships`, `Championship`), 기본값 있는 생성자 인자(`myNickname = { null }`), 기본값 있는 UiState 필드뿐으로 tester.md의 골격 예외 범위 안이다.
- **테스트 작성 규칙:** 새 테스트 5개 모두 한글 백틱 이름, `// given` · `// when` · `// then`, 입력 설명 주석. `LeagueViewModelTest`는 `StandardTestDispatcher` + `setMain`, `runCurrent()`, `Asia/Seoul` 고정 시각 주입. 서버 응답 JSON은 openapi 필드 이름을 그대로 썼다.
- **앱 규칙:** 리그 화면은 기존 정차 탭(SCR-04) 안에만 표시를 더했고 주행 중 화면 · 음성 변화 없음. 위험 운전 유도 문구 없음. 새 문구 3개는 `strings.xml`. domain 모델(`Championship`)에 Android/SDK import 없음. ViewModel은 `StateFlow<LeagueUiState>` 하나, Screen/Content 분리 유지. 새로 추가한 것은 텍스트뿐이라 터치 영역 변화 없음.
- **보안:** 비밀값 · 원시 위치 좌표 · 토큰 로그 추가 없음. 테스트의 `access-1` 등은 가짜 값.
- **문서:** `plan.md` 2차 작업 9번 행과 결정 기록, `docs/api/README.md` 연결 상태, `docs/server-requests.md` 14번 해소 기록이 변경과 맞다(요약 표 하나만 예외, 지적 1).

## 검증
- 메인 세션이 실행: 단위 테스트 173개 통과, assembleDebug 성공 (이 브랜치 시점).
- 리뷰어는 `./gradlew`를 직접 실행하지 않았다(다른 리뷰어와 같은 작업 트리를 쓰므로 빌드 · 테스트는 메인 세션이 담당). 검토는 `git log` · `git diff` · `git show`로만 했다.
