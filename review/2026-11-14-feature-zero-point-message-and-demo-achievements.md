# 리뷰: feature/zero-point-message-and-demo-achievements (2026-11-14)

- 기준: develop(953c929)...feature/zero-point-message-and-demo-achievements(e4f4452) (5개 커밋)
- 범위: 결과 화면에서 0P이면 포인트 줄 숨김("적립 한도" 문구 삭제), 시연 녹화에 업적 화면 단계 추가 · 주행 약 2.2km, 관련 문서 갱신
- 판단: 머지 가능

## 지적 사항

머지를 막는 문제는 없음. 아래는 모두 낮음이고 이번 PR에서 고치지 않아도 됨.

### 1. `scenario_demo` 주석의 거리 · 기본값이 녹화 스크립트와 다름 (심각도: 낮음)
- 위치: `scripts/vehicle_probe_sim.py:135-136, 147`
- 문제: `scenario_demo`의 기본값은 여전히 `DEMO_CRUISE_SEC` 45초("약 1.25km")이고, 녹화 스크립트만 `os.environ.setdefault("DEMO_CRUISE_SEC", "70")`으로 70초를 넘긴다. 이번 브랜치는 이 파일을 건드리지 않았다.
- 실패 시나리오: `python scripts/vehicle_probe_sim.py demo`를 따로 실행하면 약 1.55km(회전 구간 0.3km + 1.25km)만 달린다. 평점 50 기준 floor(1.55 × 50 / 100) = 0이라 결과 화면에 포인트 줄이 보이지 않는다. 녹화 스크립트로 실행하면 문제없다.
- 제안: 2차에 sim 기본값도 70으로 맞추거나, 주석에 "record_demo.py는 70을 넘김"을 적는다.

### 2. 하루 상한에 걸린 0P도 이제 아무 안내가 없음 (심각도: 낮음, 의도된 결정)
- 위치: `automotive/src/main/java/com/example/blinkerleague/ui/result/ResultUiState.kt:117`
- 문제: 0 하나로는 버림과 상한을 구분할 수 없어서 둘 다 숨긴다. 상한 안내는 지갑의 `wallet_cap_reached`에만 남는다.
- 실패 시나리오: 실제로 하루 상한을 채운 사용자는 결과 화면에서 포인트가 왜 없는지 알 수 없다. plan.md 결정 기록(11.14)과 서버 요청 12번에 이유가 적혀 있어 받아들일 수 있다.
- 제안: 다시 구분해서 보여 줘야 하면 서버 요청 12번에 상한 여부 필드(예: `pointsCapped`)를 따로 요청한다(2차 19번 검토 때 같이 정한다).

## 확인한 것
- **한도 문구가 남은 경로 없음:** `result_points_capped`는 strings.xml과 ResultScreen에서 모두 지워졌고 참조가 남지 않았다. "적립 한도"는 지갑의 `wallet_cap_reached`(실제 상한 정보가 있는 화면)와 plan.md에만 남는다.
- **판단은 UiState에 있음:** `earnedPoints?.takeIf { scoreState == ScoreState.SCORED && it > 0 }`. 0과 음수 모두 null이 되고, Screen은 `earnedPoints?.let`로 "+NP 적립"만 그린다. Screen에 남았던 0 분기는 이 변경 뒤에는 도달할 수 없는 코드였으므로, 8c3d195를 테스트 없이 `fix:`로 지운 것도 맞다.
- **테스트:** `적립 포인트가 0이면 포인트 줄을 보이지 않는다`(ResultUiStateTest)가 한글 이름 · given / when / then을 따른다. 기존 테스트 `적립 포인트는 점수가 난 운행만 표시`(12 표시, EXCLUDED_SHORT는 null)도 그대로 통과한다. 고정 시계는 이 테스트에 필요 없다.
- **TDD 커밋 순서:** 822f707 `test:`(테스트 파일만) → b607052 `fix:`(ResultUiState 한 줄) → 8c3d195 `fix:`(Screen · strings · KDoc) → 3adc363 `chore:` → e4f4452 `docs:`. red → green 순서가 맞다.
- **녹화 스크립트와 주행 안전:** 업적 · 홈 탭을 누르는 단계는 `TripCollection: 운행 끝` 로그와 종료 음성(`PleosTtsAnnouncer: 안내`)을 기다린 뒤에만 실행된다. 주행 중에는 화면을 누르지 않는다. 앱 코드의 음성 · 화면 규칙은 바뀌지 않았다.
- **`tap_text` 사용:** 기존 시그니처 `tap_text(match, label)`와 맞는다. `^업적$`, `^홈$`는 strings.xml의 `tab_achievements` · `tab_home`과 정확히 일치하고, 같은 글자를 쓰는 다른 문자열은 없다. 앞 단계에서 BACK 두 번으로 홈에 돌아온 상태라 탭 바가 보인다. 찾지 못해도 로그만 남기고 `finally`에서 녹화를 멈추므로 녹화 파일은 남는다.
- **거리 계산:** `scenario_demo`에서 40km/h 구간은 sleep 합계 4+3+4+1+8+4+4 = 28초, 약 0.31km. 100km/h × 70초 ≈ 1.94km. 합계 약 2.26km라 docstring의 "약 2.2km"와 맞다. Fake 적립식 `floor(min(km, 50) × 평점 / 100)`(FakeRewardApi.kt:80)로 평점 50이면 1P다. `os.environ.setdefault`는 sim 서브프로세스가 시작되기 전에 실행되고 환경을 물려주므로, 사용자가 지정한 값이 있으면 그 값을 덮어쓰지 않는다.
- **ADB 환경 변수:** record_demo.py의 `ADB`는 vconsole.py:15와 같은 변수 이름을 써서 하나로 설정할 수 있다.
- **커밋 위생:** 커밋에 `.idea/*`, `__pycache__`, `*.pyc`, `local.properties`가 없다(작업 트리의 `.idea` 변경은 커밋되지 않음). `.gitignore`에 `__pycache__/`, `*.pyc`를 추가했다. 비밀값은 없다.
- **문서 일관성:** server-requests.md는 갱신일이 2026-11-14(12번 수정)이고, 12번 본문을 "0이면 포인트 줄을 표시하지 않음"으로 고쳤다. plan.md에는 결정 기록 2행(0P 숨김, 포인트는 리그 가산점 · 리워드 없음)과 2차 19번(상태 "검토"), 4장 체크박스 문구, 5장 녹화 항목(업적 · 2.2km · 기본 70)이 들어갔다. screen-plan.md SCR-02 구현 상태와 README 두 곳도 변경과 맞다.
- **아키텍처 · 규칙:** domain · ViewModel에 Android · SDK import가 추가되지 않았다. 권한 · 매니페스트 변경도 없다. 문구는 strings.xml에 있다.

## 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug`: 성공. 단위 테스트 208개 중 실패 0, 오류 0(결과 XML 40개를 합산)
- `python -m py_compile scripts/record_demo.py`: 성공. 이때 생긴 `scripts/__pycache__`는 새 .gitignore 규칙으로 무시된다.
- lintDebug는 다시 돌리지 않음(tester 결과: 오류 0, 경고 15로 develop과 같음. 이번 변경은 문자열 하나 삭제와 Kotlin 두 줄이라 새 경고 요인이 없음)
- 녹화 스크립트를 에뮬레이터에서 실제로 실행하지는 않음(리뷰 범위 밖)
