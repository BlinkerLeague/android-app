# 리뷰: feature/league-qualify-config (2026-11-10)

- 기준: feature/league-visibility...feature/league-qualify-config (7개 커밋)
- 범위: 리그 순위 자격(50km 또는 10회)·순위 공개 인원(10명) 하드코딩을 기준값 `league`(`LeagueConfig`)로 교체. ConfigRepository 병합, LeagueViewModel·화면 문구, FakeLeagueApi 판정, AppContainer 연결
- 판단: 머지 가능 (아래 지적은 모두 낮음. 1번은 머지 뒤 따로 처리해도 됨)

## 지적 사항

### 1. 이 브랜치 전에 저장된 기준값 캐시에는 `league`가 없고, 같은 version이면 다시 받지 않음 (심각도: 낮음)
- **위치:** `data/config/ConfigRepository.kt:33-36`(캐시 복원), `:47-54`(`refresh`의 `response.version == base.version` 조기 반환)
- **문제:** 캐시는 `json.encodeToString(response)`로 DTO를 다시 직렬화한 JSON이다. 이전 브랜치의 `ThresholdsResponse`에는 `league` 필드가 없었으므로, 이전 빌드가 저장한 캐시에는 `league`가 빠져 있다. 업데이트 뒤 캐시에서 복원하면 `league`는 로컬 기본값이고, 서버 version이 그대로면 `refresh()`가 false를 돌려 서버의 `league` 값이 반영되지 않는다.
- **실패 시나리오:** 서버가 version N에서 `minSeasonTrips`를 5로 둔 상태에서 이전 빌드가 version N을 캐시함 → 새 빌드 설치 → 화면은 "10번 운행"으로 안내. 서버가 version을 올릴 때까지 계속됨. 새 빌드가 캐시를 쓰는 경우(신규 설치·이후 저장)는 `league`가 정상 저장·복원된다(`encodeDefaults=false`라도 `league`가 null이 아니면 직렬화됨, 복원은 `ignoreUnknownKeys`로 디코드). `commonItems`·`topN`은 DTO에 없어 캐시에서 빠지지만 앱이 읽지 않으므로 문제없음.
- 지금 서버 기본값(D11·D13)이 앱 기본값과 같고 아직 배포 전이라 실제 영향은 작다. 같은 문제는 앞으로 DTO에 필드를 추가할 때마다 반복된다.
- **제안:** 캐시에 원본 응답 문자열을 저장하거나, 캐시 형식 버전(앱 DTO 버전)을 함께 저장해 다르면 version 비교를 건너뛰고 다시 받는다. 최소한 `ConfigRepositoryTest`에 "league를 받은 뒤 다시 켜면 league가 유지된다" 복원 테스트를 하나 두면 저장 경로가 고정된다(현재 복원 테스트는 `trip.minDistanceKm`만 확인).

### 2. 순위 자격 거리를 화면에서 반올림해 판정과 다른 숫자가 나올 수 있음 (심각도: 낮음)
- **위치:** `ui/league/LeagueScreen.kt:114` `state.rule.minSeasonDistanceKm.roundToInt()`
- **문제:** 명세상 `minSeasonDistanceKm`는 double이다. 서버가 `7.5`를 주면 화면은 "8km"로 안내하지만 판정(서버, Fake `km >= rule.minSeasonDistanceKm`)은 7.5km 기준이다.
- **제안:** 정수가 아니면 소수 한 자리로 보이는 포맷(예: `%1$s`에 정리한 문자열)을 쓰거나, 정수 기준임을 서버와 합의해 둔다. 현재 기본값 50.0에서는 문제없음.

### 3. Fake 판정의 횟수 조건(`minSeasonTrips`)은 테스트가 없음 (심각도: 낮음)
- **위치:** `test/.../data/remote/FakeLeagueApiTest.kt:113` (거리 조건만 확인), `data/remote/LeagueApi.kt:67`
- **문제:** `km >= rule.minSeasonDistanceKm || trips.size >= rule.minSeasonTrips` 중 거리 쪽만 기준값으로 검증된다. 횟수 쪽을 실수로 상수로 되돌려도 테스트가 잡지 못한다.
- **제안:** "거리는 못 채웠지만 기준값 횟수를 채우면 자격"을 확인하는 테스트 하나를 추가한다.

### 4. 사소한 정리 (심각도: 낮음)
- `data/remote/LeagueApi.kt:60` `val rule = rule()`이 같은 이름의 생성자 프로퍼티를 가린다. 동작은 맞지만 읽을 때 헷갈린다(`val league = rule()` 등).
- `data/remote/LeagueApi.kt:27` KDoc의 "참여자 10명 미만이라 순위 비공개"는 여전히 숫자를 적고 있다. Fake의 `rankingPublished = false`·`participants = 1`도 `minParticipants`를 보지 않는데, 1명이 기준값 이상일 일은 없으므로 동작 문제는 아님.
- `docs/server-requests.md:285,287` 화면 문구 표가 "10명"·"50km"를 고정 문구로 적고 있다. 제안 당시 기록이라면 그대로 둬도 되지만, 기준값에서 읽는다고 한 줄 덧붙이면 이 변경과 맞는다.

## 확인한 것
- **문자열 인자 순서:** `league_rank_hidden`이 `%1$d`(기준 인원)·`%2$d`(현재 인원)로 바뀌었고, 유일한 호출부 `LeagueScreen.kt:108`이 `(minParticipants, participants)` 순서로 맞게 넘긴다. 리소스는 `src/main/res/values/strings.xml` 하나뿐이고(다른 언어 `values-*` 없음, `src/debug/res/values/strings.xml`에는 이 키가 없음) 어긋날 번역이 없다. `league_qualify_rule`도 `(km, trips)` 순서 일치.
- **ViewModel 파라미터 순서:** `rule`을 기본값과 함께 `loadLeague`와 `clock` 사이에 넣었다. 호출부는 `LeagueScreen.kt:50`(세 인자 위치 전달로 갱신됨), 테스트의 `LeagueViewModel(x) { now }`(후행 람다는 `clock`에 붙음) 모두 맞다. 위치로 두 번째 람다를 넘기는 호출이 있었다면 반환 타입이 달라 컴파일 오류가 나므로 조용히 깨질 수 없다.
- **AppContainer:** Fake 모드의 `{ config.league }`는 `config` 프로퍼티(`configRepository` 초기화 뒤 선언)를 지연 호출하므로 초기화 순서 문제 없음. Fake 모드는 `ConfigRepository(api = null)`이라 기본값을 씀.
- **명세 일치:** `LeagueThresholdsDto`의 `minParticipants`(int32)·`minSeasonDistanceKm`(double)·`minSeasonTrips`(int32)가 `openapi.json`의 `League` 스키마와 이름·타입이 같고, 모두 nullable + 기본값 null. `commonItems`·`topN`은 `ignoreUnknownKeys`로 무시. `ThresholdsResponse.league`도 nullable.
- **병합:** `league`가 없거나 일부 필드만 오면 나머지는 기존 값 유지(`?: league`). 기존 병합 규칙(서버가 주는 항목만 덮어씀)과 같다.
- **앱 규칙:** 기준값을 `domain/config`의 `LeagueConfig`에서 읽고 화면·ViewModel에 하드코딩 상수(`QUALIFY_KM`·`QUALIFY_TRIPS`) 제거. `LeagueConfig`·ViewModel에 Android/SDK import 없음. ViewModel은 `StateFlow<LeagueUiState>` 하나, 문구는 strings.xml. 주행 안전 원칙과 관련된 변경 없음.
- **TDD:** 커밋 순서가 red(`test:`) → green(`feat:`) 세 쌍 → docs. red 커밋의 프로덕션 변경은 골격(`LeagueConfig` 기본값 클래스, 기본값 파라미터, 호출부 맞춤)뿐. 새 테스트 세 개 모두 한글 이름, `// given` / `// when` / `// then`, 고정 시계(`{ now }`) 사용. 서버 JSON은 명세 필드 이름 그대로.
- **문서:** `plan.md` 2차 표 10번, `docs/api/README.md`의 thresholds 행이 변경과 맞음.
- **보안:** 비밀값·토큰 로그·위치 좌표 관련 변경 없음.

## 검증
- 메인 세션이 실행: 단위 테스트 182개 통과, assembleDebug 성공 (이 브랜치 시점).
- 리뷰어는 같은 작업 트리를 다른 리뷰어와 함께 쓰는 제약 때문에 `./gradlew`를 직접 실행하지 않았다. 코드는 `git show feature/league-qualify-config:<경로>`와 `git diff feature/league-visibility...feature/league-qualify-config`로만 읽었다.
