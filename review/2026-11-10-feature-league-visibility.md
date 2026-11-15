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

## 재검토 (2026-11-10)

- 기준: ca391cc..95e7eb0 (7개 커밋: red/green 2쌍 + red/green 1쌍 + docs 1개)
- 범위: 지적 1번(연속 토글 경합)과 4번(PUT 응답 `visible` 선택값) 수정, 2·3·5번 후속 기록
- 판단: **머지 가능**

### 1번 해결 여부: 해결됨
- `SettingsViewModel.setLeagueVisible`이 `league.value.saving`이면 바로 반환하고, 코루틴을 띄우기 전에 메인 스레드에서 동기로 `saving = true`를 세운다. 두 번째 탭은 첫 저장이 끝나기 전까지 요청을 만들지 않으므로 PUT은 늘 하나이고, 응답 도착 순서 경합이 생기지 않는다.
- `saving` 복귀: `try { saved = saveLeagueVisible(visible) } finally { ... }`에서 성공이면 `LeagueToggle(visible, failed = false)`(saving 기본값 false), 실패 · 예외 · 취소면 `copy(failed = true, saving = false)`로 모든 경로에서 false로 돌아온다. `saveLeagueVisible`은 `LeagueRepository.setVisible` → `apiCall`이라 HTTP · 직렬화 · IO 오류는 `false`로 오고, 취소는 ViewModel이 정리될 때뿐이라 남는 상태가 화면에 보이지 않는다.
- UI: `SettingsUiState.leagueVisibilitySaving`을 `toggleable(enabled = !saving)`과 `Switch(enabled = !saving)`에 연결. 행 Modifier `fillMaxWidth().heightIn(min = 64.dp)`는 그대로라 비활성 상태에서도 64dp 행이 유지된다. 저장 중 미리보기(`SettingsSavingPreview`) 추가. 새 문구는 없음(미리보기 인자만 하드코딩, 기존 미리보기와 같은 방식).
- 테스트: `저장 중에 토글을 다시 누르면 서버에 한 번만 저장을 요청한다`(`CompletableDeferred`로 저장을 붙잡고 두 번 호출 → `saved == [false]`), `저장하는 동안 토글을 저장 중으로 표시하고, 저장이 끝나면 푼다`(저장 중 true → 성공 후 false, 값 반영).

### 4번 해결 여부: 해결됨
- 요청 DTO `LeagueVisibilityDto(val visible: Boolean)`는 기본값이 없어 `encodeDefaults` 설정과 상관없이 true/false 모두 본문에 항상 들어간다(openapi `LeagueVisibilityRequest.required = [visible]`과 일치).
- 응답 DTO `LeagueVisibilityResponseDto(val visible: Boolean? = null)`를 분리(openapi `LeagueVisibilityResponse`에 required 없음과 일치). `LeagueApi.setVisibility`, `FakeLeagueApi`, `BlinkerService.setLeagueVisibility`, `HttpLeagueApi` 반환 타입이 모두 `LeagueVisibilityResponseDto`로 맞음. 성공 판정은 `is ApiResult.Success`라 응답의 `visible` 값을 쓰지 않음.
- 테스트: `순위 공개 저장 응답에 visible이 없어도 저장 성공으로 본다`(MockWebServer `{}` 응답 → true, 본문 `{"visible":false}`).

### 남은 낮음 2 · 3 · 5번
- `plan.md` "리그 순위 공개 후속 (리뷰 낮음, 11.10)" 줄에 세 항목 모두 기록됨. 결정 기록에 1번(저장 중 토글 막음)과 4번(응답 visible 선택값) 근거도 추가됨.

### 새 지적 사항

#### R1. 저장 실패 뒤 `saving`이 풀리는지 고정하는 테스트가 없음 (심각도: 낮음)
- **위치:** `automotive/src/test/java/com/example/blinkerleague/ui/settings/SettingsViewModelTest.kt:96` (`서버 저장에 실패하면 토글은 그대로 두고 실패를 알린다`)
- **문제:** 실패 경로의 `saving = false` 복귀는 코드(`finally`)로는 맞지만, 성공 경로만 `leagueVisibilitySaving == false`를 확인하고 실패 테스트는 `leagueVisible` · `leagueVisibilityFailed`만 본다.
- **실패 시나리오:** 나중에 `finally` 분기를 손보다 실패 쪽 `saving = false`가 빠져도 테스트가 통과한다. 그러면 오프라인에서 한 번 저장에 실패한 뒤 토글이 영구 비활성이 되어 "다시 눌러 주세요" 안내를 따를 수 없다.
- **제안:** 기존 실패 테스트에 `assertFalse(vm.uiState.value.leagueVisibilitySaving)` 한 줄을 추가한다(후속 처리로 충분, 머지를 막지 않음).

### 확인한 것
- **TDD 커밋 순서:** `888504a` red → `4564ae9` green, `ba6dbc0` red → `a8632b1` green, `001d9ee` red → `757db98` green, 이후 `95e7eb0` docs. 각 red는 이전 구현에서 실제로 실패하는 어서션 실패다(가드 없음 → 저장 2회, `leagueVisibilitySaving` 미연결 → false, 필수 `visible`에 `{}` → `SerializationException` → `HttpError` → false). `ba6dbc0`의 `SettingsUiState` 필드 추가는 tester 규칙상 허용되는 기본값 있는 골격.
- **테스트 작성 규칙:** 새 테스트 3개 모두 한글 이름, `// given` / `// when` / `// then`(두 번째 when/then 단계 포함), 입력 설명은 구역 안 별도 주석, `StandardTestDispatcher` + `runCurrent()`로 실제 대기 없음. HTTP 응답은 명세 스키마(`LeagueVisibilityResponse`)에서 허용되는 `{}`.
- **아키텍처:** ViewModel에 Android/SDK import 추가 없음, `StateFlow<SettingsUiState>` 하나, Screen / Content 분리 유지.
- **주행 안전 원칙:** 설정 화면에 점수 · 알림 · 음성 추가 없음, 위험 운전 유도 문구 없음. 원시 위치 전송 · 토큰 로그 없음.

### 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug --console=plain -q` (리뷰어 직접 실행, 결과 폴더를 지운 뒤 실행): 종료 코드 0.
- `automotive/build/test-results/testDebugUnitTest/*.xml` 37개 합계: tests 182, failures 0, errors 0, skipped 0 (첫 검토 179개 + 새 테스트 3개).
- `automotive/build/outputs/apk/debug/automotive-debug.apk` 생성 확인.

### 최종 판단
- **머지 가능.** 1번(중간)과 4번(낮음)이 테스트와 함께 해결됐고, 2 · 3 · 5번은 `plan.md` 후속으로 기록됨. 새 지적 R1은 낮음(테스트 보강)이라 후속으로 처리해도 된다.
