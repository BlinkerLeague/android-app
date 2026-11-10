# 리뷰: feature/league-visibility (2026-11-10)

- 기준: feature/league-server-format...feature/league-visibility (14개 커밋)
- 범위: 설정(SCR-08) "리그 순위 공개" 토글. 현재 값은 리그 조회 `me.visible`, 저장은 `PUT /api/drivers/me/league-visibility`, 실패 시 토글 유지 + 안내, Fake 구현
- 판단: 수정 후 머지 (1번만 고치면 됨. 2~5번은 낮음이라 후속으로 처리해도 됨)

## 지적 사항

### 1. 저장 중 표시·중복 요청 막기가 없어 연속 토글 때 화면과 서버 값이 어긋날 수 있음 (심각도: 중간)
- **위치:** `ui/settings/SettingsViewModel.kt:56-60` (`setLeagueVisible`), `ui/settings/SettingsScreen.kt:106` (`toggleable`)
- **문제:** 낙관적 갱신이 없어서 저장이 끝날 때까지 토글이 그대로이고, 저장 중임을 보여 주거나 토글을 막지도 않는다. 누를 때마다 `viewModelScope.launch`로 PUT을 새로 보내고, 응답이 오는 순서대로 `league.value`를 덮어쓴다.
- **실패 시나리오:** 느린 차량 네트워크에서 공개(true) 상태로 토글을 누름 → 반응이 없어 한 번 더 누름(두 번째 요청도 `false`) → 첫 요청 성공으로 꺼짐 표시 → 다시 켜려고 누름(`true` 요청). 이때 늦게 도착한 두 번째 `false` 요청이 `true` 요청보다 나중에 처리되면 서버는 꺼짐인데 화면은 켜짐(또는 그 반대)으로 끝난다. 사용자는 순위 공개 여부를 잘못 알게 된다.
- **제안:** 저장 중 상태(`saving`)를 두어 그동안 토글을 비활성화하거나 입력을 무시한다(또는 이전 `Job`을 취소하고 마지막 요청만 반영). 성공 시에는 요청 값 대신 응답 `visible`을 반영하면 더 안전하다. "저장 중에 다시 누르면 요청을 한 번만 보낸다" 같은 ViewModel 테스트를 추가한다(`saveLeagueVisible`을 `CompletableDeferred`로 막아 두고 두 번 호출).

### 2. 순위 공개를 끈 상태가 리그 화면에 반영되지 않음 (심각도: 낮음)
- **위치:** `domain/model/League.kt:12` (`visible` 추가), `ui/league/LeagueScreen.kt:104-115`
- **문제:** `League.visible`을 읽어 오지만 리그 화면 어디에서도 쓰지 않는다. 서버는 공개를 끄면 `me.rank`를 null로 준다(openapi `MeResponse` 설명).
- **실패 시나리오:** 순위가 공개된 리그(10명 이상)에서 자격을 채운 운전자가 공개를 끄면 리그 화면에 "아직 순위가 없어요 · 참여자 N명"과 "이번 시즌 순위 자격을 채웠어요"가 함께 보여, 왜 순위가 없는지 알 수 없다.
- **제안:** `visible == false`면 "순위 공개를 껐어요 (설정에서 바꿀 수 있어요)" 같은 문구를 보여 준다. 이 브랜치 범위 밖이면 plan.md에 후속으로 적어 둔다.

### 3. 오프라인으로 설정을 열면 토글이 이유 없이 사라지고 다시 불러오지 않음 (심각도: 낮음)
- **위치:** `ui/settings/SettingsViewModel.kt:35` (`init`의 한 번 로드), `ui/settings/SettingsScreen.kt:102` (`leagueVisible?.let`)
- **문제:** 리그 조회가 실패하면 `leagueVisible = null`이라 토글 영역 전체가 숨겨지고, 로드는 ViewModel 생성 때 한 번뿐이다.
- **실패 시나리오:** 지하 주차장 등 오프라인에서 설정을 열면 "리그" 항목 자체가 없다. 연결이 돌아와도 ViewModel이 살아 있는 동안(탭 상태 보존 시) 토글이 나타나지 않는다.
- **제안:** "불러오지 못했어요" 안내를 보여 주거나, 화면에 다시 들어올 때(또는 안내를 누를 때) 다시 불러온다. 의도된 동작이면 그 테스트(`loadLeagueVisible = { null }`이면 `leagueVisible`이 null)를 추가해 고정한다.

### 4. PUT 응답의 `visible`이 명세상 선택값인데 DTO는 필수값 (심각도: 낮음)
- **위치:** `data/remote/dto/LeagueDtos.kt:78` (`data class LeagueVisibilityDto(val visible: Boolean)`)
- **문제:** openapi `LeagueVisibilityResponse`는 `required`가 없다(요청 `LeagueVisibilityRequest`만 `visible` 필수). 같은 DTO를 응답에도 쓰므로 기본값이 없다.
- **실패 시나리오:** 서버가 200에 빈 본문이나 `{}`를 주면 `SerializationException` → `apiCall`이 `HttpError(UNREADABLE_RESPONSE)`로 바꿔, 서버에는 저장됐는데 앱은 "저장하지 못했어요"를 띄운다.
- **제안:** 응답용 DTO를 분리해 `visible: Boolean? = null`로 두거나, 지금 서버가 항상 준다면 그대로 두고 주석으로 근거를 남긴다.

### 5. 설정 화면을 떠나면 진행 중인 저장이 취소됨 (심각도: 낮음)
- **위치:** `ui/settings/SettingsViewModel.kt:57` (`viewModelScope.launch`)
- **문제:** 저장 요청이 화면 ViewModel 범위에서 돈다.
- **실패 시나리오:** 토글을 누르고 곧바로 다른 탭으로 이동해 ViewModel이 정리되면 요청이 취소될 수 있고(서버 도달 여부는 불확실), 사용자는 결과를 보지 못한다. 다시 들어오면 서버 값으로 다시 불러오므로 화면은 결국 맞는다.
- **제안:** 현재 동작으로도 데이터가 어긋나지는 않으므로 문서화 정도로 충분하다. 1번을 고칠 때 함께 고려한다.

## 확인한 것
- **TDD 커밋 순서:** 7쌍 모두 `test:`(red) → `feat:`(green) 순서. `8b6f5d8` red는 이전 구현(`4e801a6`: 실패 시 아무것도 안 함)에서 `leagueVisibilityFailed` 어서션으로 실패하는 올바른 red. `1fc483a` refactor는 인터페이스 기본 구현 제거로, 테스트용 익명 `LeagueApi`(`FakeLeagueApiTest.kt`)에도 `setVisibility`가 추가됨.
- **테스트 작성 규칙:** 새 테스트 6개 모두 한글 이름, `// given` / `// when` / `// then`, `StandardTestDispatcher` + `runCurrent()`. HTTP 테스트는 MockWebServer로 메서드(PUT) · 경로 · `Authorization` 헤더 · 본문 `{"visible":false}`를 확인. `SERVER_LEAGUE` 예시에 `"visible": true`가 있어 `replace`가 실제로 적용됨.
- **서버 형식:** 경로 `PUT /api/drivers/me/league-visibility`, 요청 `{ visible }` 필수 boolean, `MeResponse.visible`은 선택값이라 `LeagueMeDto.visible = true` 기본값(D8 기본 공개)과 `me == null`일 때 `true` 처리 모두 명세와 맞음. 400 · 401 · 404는 `apiCall`이 `HttpError`로 바꾸어 실패 안내로 이어짐. 401 토큰 재발급은 기존 HTTP 클라이언트 경로를 그대로 탐.
- **오프라인 저장 실패:** `IOException` → `NetworkError` → `setVisible` false → 토글 유지 + `settings_league_failed` 안내(테스트 있음). 다음 성공 시 안내가 지워짐.
- **로드와 저장 경합:** 토글은 `leagueVisible != null`일 때만 보이므로 초기 로드가 끝나기 전에는 누를 수 없어, 로드 결과가 저장 결과를 덮어쓰는 경합은 없음.
- **터치 영역:** 토글 행 전체가 `fillMaxWidth().heightIn(min = 64.dp).toggleable(role = Role.Switch)`이고 `Switch(onCheckedChange = null)`로 중복 처리 없음. 64dp 기준 충족.
- **주행 안전 원칙:** 설정 화면에 점수 · 알림을 새로 띄우지 않고 음성도 없음. 문구는 `strings.xml`에 있고 위험 운전을 유도하는 표현 없음. 원시 위치 좌표 전송 없음, 토큰 · 개인정보 로그 없음.
- **아키텍처:** ViewModel은 Android/SDK 타입 import 없이 함수 주입(`loadLeagueVisible`, `saveLeagueVisible`)으로 테스트 가능, `StateFlow<SettingsUiState>` 하나, Screen / Content 분리 유지.
- **Fake:** `FakeLeagueApi`가 메모리에 `visible`을 기억해 다음 조회에 반영(테스트 있음). 앱 재시작 시 기본 공개로 돌아가는 것은 주석에 명시.
- **문서:** `plan.md` 7번 항목과 결정 기록(11-10, 현재 값은 `me.visible`에서 읽고 실패 시 토글 유지), `docs/api/README.md` 연결 상태 갱신. 서버 변경 요청은 필요 없음.

## 검증
- 메인 세션이 실행: 단위 테스트 179개 통과, assembleDebug 성공 (이 브랜치 시점).
- 리뷰어는 `./gradlew`를 직접 실행하지 않았다(같은 작업 트리를 다른 리뷰어가 동시에 쓰고 있어 브랜치를 바꾸지 않고 `git show` · `git diff`로만 검토함).
