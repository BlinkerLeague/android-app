# 리뷰: feature/score-metrics-expansion (2026-11-15)

- 기준: develop...feature/score-metrics-expansion (128개 커밋)
- 범위: 서버 점수 항목 7개(SIGNAL_CANCEL · TURN_SPEED · SMOOTH_DRIVING · NIGHT_HEADLIGHT · SEAT_BELT · SAFETY_WARNING · RAIN_DRIVING) 앱 판정 · 업로드, Pleos SDK 브레이크 · 하향등 · 안전벨트 · 경고등 · 와이퍼 연결, 결과 행을 서버 metrics로 채움
- 판단: 수정 후 머지

## 지적 사항

### 1. 경고등이 켜져 있는 동안 속도가 기준을 넘나들 때마다 SAFETY_WARNING을 새로 세고, 이벤트 상한도 없음 (심각도: 높음)
- **위치:** `domain/detection/SafetyWarningDetector.kt:35-41`, `domain/detection/TripAssembler.kt:153-158`, `:193`, `data/repository/TripMapper.kt:126`
- **문제:** "대상 경고등 켜짐 && 속도 ≥ movingMinSpeedKmh"가 거짓에서 참으로 바뀔 때마다 1건을 셉니다. 그래서 경고등이 계속 켜져 있어도 속도가 10km/h 아래로 떨어졌다 다시 올라가면 또 셉니다. 속도는 `SENSOR_RATE_FASTEST`(약 10Hz) 샘플을 모두 씁니다. 운행 전체 이벤트 수를 서버 `trip.maxEvents`(기본 2,000) 아래로 묶는 장치도 앱 어디에도 없습니다.
- **실패 시나리오:** TPMS 경고등이 며칠째 켜진 차가 정체 구간에서 8~12km/h로 가다 서다 합니다. 10Hz 속도에 잡음이 있어 10km/h를 넘을 때마다 SAFETY_WARNING이 1건씩 생기고, 1시간 정체면 수백~수천 건이 됩니다. 2,000건을 넘으면 서버가 400(INVALID_INPUT)을 주고, 앱은 그 운행을 재전송하지 않고 `REJECTED`로 둡니다(server-requests 11번). 결국 깜빡이를 포함한 운행 전체가 채점되지 않습니다. 2,000 아래여도 서버 `mergeWindowSec`(10초)보다 간격이 길면 하나씩 따로 세어져 감점이 과해집니다. 지금 테스트는 속도가 일정한 경우만 다룹니다(`SafetyWarningDetectorTest`).
- **제안:** (a) 경고등마다 "운행 중 켜짐 → 꺼짐" 한 주기에 1건만 셉니다. 같은 주기 안에서 속도를 다시 넘는 것은 세지 않습니다. 정차 중에 켜졌으면 처음 달리기 시작할 때 1건이고, 이것은 지금 테스트를 그대로 유지합니다. 아니면 속도 판정에 히스테리시스나 최소 지속 시간을 두고, 그 값은 `SafetyWarningConfig`에 둡니다. (b) "속도가 넘나들어도 경고등이 켜져 있는 동안은 1건" red 테스트를 추가합니다. (c) `TripAssembler`나 업로드 직전에 이벤트 총량 상한을 둡니다. `trip.maxEvents`를 기준값으로 읽고, 넘치면 TURN을 남기고 횟수형 이벤트(HARSH_* · SAFETY_WARNING)부터 자릅니다. 상한을 어떻게 처리할지는 plan.md 결정 기록에 남깁니다.

### 2. 판정 결과에 반영되는 supported와 실제로 모은 데이터가 어긋날 수 있음: 샘플이 0개여도 항목이 측정됨으로 나가 만점 처리됨 (심각도: 중간)
- **위치:** `data/vehicle/PleosVehicleSignalSource.kt:251-268`(poll), `:206-208` · `:220-222` · `:284-287`(리스너 onFailed → close), `service/TripRecorder.kt:207`, `domain/detection/TripAssembler.kt:177-182`
- **문제:** 폴링 Flow는 처음 2번 연속 실패하면 끝나고, 서비스가 다시 시작될 때까지 재시도하지 않습니다. 리스너 Flow도 `onFailed`가 오면 닫힙니다. 반면 `supported`는 운행이 끝날 때 `vehicle.supportedSignals()`(capability 확인)로 새로 계산합니다. 그래서 수집은 멈췄는데 운행 종료 시점의 capability 확인은 성공하는 경우가 생깁니다. 이 경우를 SEAT_BELT만 막고 있습니다("출발 상태 모름 → 제외").
- **실패 시나리오:** 서비스 시작 직후 SDK가 아직 연결되지 않아 `getTelltalesInfo`가 두 번 실패하면 경고등 폴링이 영구히 멈춥니다. 운행 종료 때 capability 확인은 성공해 `SAFETY_WARNING`이 supported에 들어가고, 이벤트 0건이 만점으로 채점됩니다. 브레이크 리스너가 `onFailed`로 닫혔을 때도 같아서 SMOOTH_DRIVING이 급감속 0건으로 채점됩니다. plan.md "주의"에 적힌 "지원만 보내면 0건이 위반 없음으로 채점됨"과 같은 문제입니다.
- **제안:** `TripAssembler`에서 운행 기록에 그 신호 샘플이 하나도 없으면(운행 전 이어받은 상태도 없음) supported에서 그 신호를 뺍니다. 대상은 BRAKE · HEADLIGHT · SAFETY_WARNING · WIPER이고 SEAT_BELT는 이미 이렇게 합니다. 인터페이스 계약상 각 Flow는 구독 직후 현재 값을 한 번 보내므로, 정상 수집이면 샘플이 최소 1개는 있습니다. 조립 테스트를 신호마다 하나씩 red로 추가합니다. 폴링은 처음 실패해도 끝내지 말고 주기를 늘려 계속 시도하는 쪽도 검토합니다.

### 3. 새 판정기가 10Hz 속도 시계열에서 O(n²)라 긴 운행의 조립이 수 초~수십 초 걸릴 수 있음 (심각도: 중간)
- **위치:** `domain/detection/HarshDrivingDetector.kt:34`, `domain/detection/SafetyWarningDetector.kt:36-37`, `service/TripRecorder.kt:162`(폴링 샘플을 값이 같아도 매초 저장)
- **문제:** `List.lastOrNull { … }`는 목록 끝에서부터 거꾸로 훑습니다. 그래서 샘플마다 이것을 부르면 전체가 O(n²)입니다. 속도는 `SENSOR_RATE_FASTEST`(약 10Hz) 샘플을 모두 저장하므로 2시간 운행이면 약 72,000개입니다. HarshDrivingDetector는 약 2.6×10⁹번 비교하고, SafetyWarningDetector는 시각 수(속도 + 경고등) × 두 목록을 훑어 비슷한 규모가 됩니다. 경고등 · 벨트 폴링도 값이 같아도 매초 1개씩 쌓입니다.
- **실패 시나리오:** 장거리 운행을 마치고 P단에 두면 `finish()`가 `Dispatchers.Default`에서 수십 초 동안 조립합니다. 그동안 recorder의 `collect`는 막혀 있어 다른 입력을 처리하지 못합니다. callbackFlow 기본 버퍼가 차면 속도 샘플을 버리고, 바로 다시 출발하면 다음 운행 시작이 늦게 잡힙니다. 그 사이 서비스가 종료되면 저장 전인 운행을 잃습니다.
- **제안:** 두 판정기를 정렬된 목록 위의 투 포인터(인덱스 전진)로 바꿔 O(n)로 만듭니다. 동작이 바뀌지 않으므로 refactor 커밋으로 하고 기존 테스트로 회귀를 확인합니다. 폴링 샘플은 data 계층에서 값이 바뀔 때만 보내도록(`distinctUntilChanged`로 타임스탬프 제외 비교) 하는 것도 검토합니다. 다만 SeatBeltJudge의 "출발 직전 상태"는 이어받은 마지막 값으로 유지되는지 확인해야 합니다.

### 4. 급가속 · 급감속이 10Hz 잡음에 다시 걸려 한 번의 동작이 여러 건이 될 수 있음 (심각도: 낮음)
- **위치:** `domain/detection/HarshDrivingDetector.kt:39-42`
- **문제:** 기준 초과 상태가 한 샘플(0.1초)이라도 풀리면 바로 다시 셀 수 있게 됩니다. 1초 전 대비 변화량이 기준값 근처에서 오르내리면(예: 10.2 → 9.8 → 10.1) 한 번의 급가속이 2~3건이 됩니다. plan.md 후속에도 "가상 콘솔의 계단식 속도 변화로 급가속이 과다 판정됨"이 적혀 있습니다.
- **실패 시나리오:** 실차에서 강하게 가속하면 HARSH_ACCEL이 여러 건 올라가고, `harsh.penaltyPer10Km`로 SMOOTH_DRIVING이 과하게 깎입니다.
- **제안:** 한 건을 센 뒤 일정 시간(예: 2초) 안의 같은 종류는 세지 않습니다. 이 값은 `HarshConfig`에 앱 내부 값으로 둡니다. 아니면 기준값 아래로 일정 시간 머물러야 다시 셀 수 있게 합니다. 잡음 시나리오 red 테스트를 추가합니다.

### 5. 대표 좌표가 없으면 NIGHT_HEADLIGHT를 supported로 보내면서 야간 구간은 만들지 않음 (심각도: 낮음)
- **위치:** `domain/detection/TripAssembler.kt:125`, `domain/model/ScoreItem.kt`(`HEADLIGHT in signals → NIGHT_HEADLIGHT`)
- **문제:** 지역 프로필이 없거나 목록에 없는 시 · 도 코드면 `coordinate == null`이 되어 NIGHT_SEGMENT를 만들지 않습니다. 그런데 HEADLIGHT 신호는 그대로라 업로드 supported에는 NIGHT_HEADLIGHT가 들어갑니다.
- **실패 시나리오:** 등록 전이거나 코드를 매핑하지 못한 상태에서 밤에 운행하면, 서버는 야간 구간 0건으로 보고 해당 없음이나 만점으로 처리합니다. 실제로는 "측정 못 함"입니다.
- **제안:** 좌표가 없으면 그 운행에서 NIGHT_HEADLIGHT를 측정하지 않은 것으로 표시합니다. 예를 들어 Trip에 "제외 항목"을 두거나 업로드 때 빼서 SEAT_BELT 제외와 같은 방식으로 처리합니다. HEADLIGHT 신호 자체는 RAIN_DRIVING에도 쓰므로 신호를 빼면 안 됩니다. 실제로 일어날 가능성이 낮으므로 plan.md 후속에 기록만 해도 됩니다.

### 6. 운행 종료 때 capability 확인이 5개 늘어 미지원 차량에서 종료 처리가 늦어짐 (심각도: 낮음)
- **위치:** `data/vehicle/PleosVehicleSignalSource.kt:100-124`(`check`를 차례로 호출), `service/TripRecorder.kt:207`
- **문제:** 미지원이면 `check`가 실패한 뒤 1초 쉬고 한 번 더 시도하고, 응답이 없으면 3초 타임아웃을 두 번 기다립니다. 새 신호 5개를 차례로 확인하므로 미지원 차량에서는 운행 종료마다 약 5초, SDK가 응답하지 않으면 최대 약 35초 동안 recorder가 막힙니다.
- **실패 시나리오:** 3번 지적과 같아서, 종료 직후 들어오는 입력이 밀리거나 버려집니다.
- **제안:** capability 결과를 수집 시작 때 한 번 구해 캐시하거나, `coroutineScope { async }`로 병렬로 확인합니다.

### 7. 주석 · 문서의 기준 표현이 코드와 다름 (심각도: 낮음)
- **위치:** `domain/config/DetectionConfig.kt:102`("이 초 이상"), `domain/detection/SignalCancelDetector.kt:66`(`durationSec > forgetSec`), `domain/model/SignalType.kt:22`
- **문제:** forgetSec 주석은 "이상"인데 코드와 scoring-criteria는 "넘게"(초과)입니다. `SignalType.SPEED` 주석은 "signalCancel 판정에 씀"이라고만 되어 있지만, 실제로는 TURN_SPEED · SMOOTH_DRIVING · SEAT_BELT · SAFETY_WARNING에도 씁니다.
- **제안:** 주석을 코드에 맞춥니다(테스트 없이 `docs:`/`chore:`).

## 확인한 것
- **400 위험(형식):** 이벤트 type 8종(`TURN` · `SIGNAL_LEFT_ON` · `HARSH_ACCEL` · `HARSH_BRAKE` · `NIGHT_SEGMENT` · `SEAT_BELT` · `SAFETY_WARNING` · `RAIN_SEGMENT`)과 payload 키(`durationSec`, `headlightOffSec`, `buckledAtDeparture`, `unbuckledMovingSec`, `knownLimitSec`, `overRainLimitSec`, TURN `maxSpeedKmh`)가 `docs/api/openapi.json` `EventRequest`와 같습니다. 업로드 `supported`는 `ScoreItem.measurable`로 서버 enum 8개 이름만 나갑니다. SPEED · BRAKE · WIPER 같은 신호 이름은 나가지 않습니다.
- **occurredAt이 운행 구간 안에 있는지:** SIGNAL_LEFT_ON은 `countFromMs ∈ [tripStart, countUntil ≤ tripEnd]`, HARSH는 `window` 필터, NIGHT · RAIN은 `max(start)`/`min(end)`로 자름, SEAT_BELT는 운행 안 출발 시각이나 운행 시작, SAFETY_WARNING은 `times`가 `[tripStart, tripEnd]` 안에 있습니다. `isoTime`이 초 단위로 내림하더라도 start · end · event에 같은 규칙을 적용하므로 구간 밖으로 나가지 않습니다.
- **supported와 이벤트 정합:** measurable 조건과 TripAssembler의 이벤트 생성 조건이 항목마다 같습니다(2번 · 5번 예외 제외). 출발 시점 벨트 상태를 모르면 SEAT_BELT를 제외하고 테스트도 있습니다. 미지원 · 권한 없음 → Flow 종료(SecurityException catch) → 측정 제외이며, 크래시나 0점으로 이어지지 않습니다.
- **기준값:** `signalCancel.forgetSec · minSpeedKmh`, `harsh.deltaKmhPerSec`, `rain.minWiperSec`를 서버 이름 그대로 `ThresholdsResponse`에서 받아 `mergedWith`로 덮어씁니다(테스트 있음). 앱 내부 값(maxGapSec · movingMinSpeedKmh · pollIntervalSec · 경고등 목록 · 시 · 도 좌표)은 `domain/config`에 있습니다.
- **순수 함수:** `domain/` 아래에서 `currentTimeMillis` · `systemDefault` · `now()`를 부르지 않습니다. 시간대는 `ZoneId`로 주입받습니다(AppContainer.zone → Service · Demo).
- **SDK 경계:** SDK import는 `data/vehicle/PleosVehicleSignalSource.kt`에만 있습니다. 경고등은 SDK enum 대신 문자열 이름으로 넘깁니다. javap(`classes.jar`)로 확인한 결과는 다음과 같습니다. `Brake.registerBrakePedalStatus`/`checkBrakePedalStatusCapabilities`=CAR_DRIVE, `Light.registerLowBeamLightState`=CAR_LIGHTS, `Seat.getCurrentSeatBeltBuckledState`/`checkSeatBeltBuckledCapabilities`=CAR_SEATS, `Safety.getTelltalesInfo`=CAR_SAFETY(`registerTelltales` · `checkTelltalesCapabilities`는 PRIVATE_CAR_SAFETY이며 쓰지 않음), `Wiper.registerWiperState`/`checkWiperStateCapabilities`=CAR_WINDOWS. `TelltaleType` ABS · ESC · TPMS · PARKING_BRAKE · ELECTRONIC_PARKING_BRAKE, `TelltaleState` ON · BLINK, `SeatValue.ROW_1_LEFT`, `WindowValue.FRONT_WINDSHIELD`가 실제로 있습니다. `src/main` 매니페스트에는 PRIVATE_CAR_SAFETY가 없고 `src/debug`에만 있습니다. 새 권한 3개는 모두 실제로 씁니다.
- **취소 · 누수:** 새 리스너 3개는 모두 `awaitClose`에서 해제합니다. 폴링은 `flow { delay }` + `withTimeoutOrNull` + `suspendCancellableCoroutine`(`isActive` 확인)이라 수집을 취소하면 함께 멈추고, 늦게 온 콜백은 무시됩니다.
- **원시 좌표:** NIGHT_SEGMENT payload에는 시간만 들어가고, 대표 좌표는 기기 안 일출 · 일몰 계산에만 씁니다. 경고등 이름도 업로드하지 않습니다. 로그에 토큰이나 개인정보가 없습니다.
- **주행 안전:** 새 항목 때문에 늘어난 주행 중 화면 · 알림 · 음성은 없습니다. 결과 화면은 기존처럼 정차 뒤에만 나옵니다. 팁 문구 4개("깜빡이가 꺼졌는지 확인", "회전할 때는 속도를 충분히 줄여", "출발 전에 안전벨트", "비가 오면 전조등을 켜고 속도를 줄여")는 안전 방향입니다. 새 문구 "이번 운행엔 해당 없음"은 `strings.xml`에 있습니다.
- **결과 화면:** ViewModel은 `StateFlow<ResultUiState>` 하나만 노출합니다. 서버 `UNSUPPORTED` → 미지원, `NOT_APPLICABLE` → 해당 없음, 점수 → 항목 점수, 결과 없음 → 깜빡이는 기기 판정이고 나머지는 준비 중입니다. `MetricDetail.status` · `score`는 nullable이라 서버 null에도 안전합니다. 8행 확장은 plan.md 21번에 보류로 적혀 있습니다.
- **TDD:** 판정기 · 조립 · 매핑 · 수집 · 화면 사이클이 모두 `test:` → `feat:`/`fix:` 순서이고, refactor 2개(SpeedDuration · HeadlightOff)도 따로 커밋했습니다. 새 테스트 70개 모두 한글 이름이고 `// given` · `// when` · `// then`이 있으며 시각은 고정값입니다. SDK 래퍼 커밋 4개(`4f81de0` · `b9e5a2a` · `d41e289` · `50da823`)와 연결 커밋 `697afd5`에는 테스트가 없는데, SDK · 컨테이너 연결이라 단위 테스트 대상이 아닙니다.
- **문서:** plan.md 2차 11~14 · 20 · 21, 사용 필드 표, 결정 기록 9건, screen-plan 4.4, api/README, platform-capabilities(SDK 확인표), pleos-inquiries(권한 표)가 변경 내용과 맞습니다. 서버 형식을 바꿀 필요가 없어 server-requests는 바꾸지 않았고, 이것도 맞습니다.

## 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:lintDebug` → BUILD 성공(exit 0)
  - 단위 테스트 278개 통과(실패 0, 건너뜀 0)
  - lint 0 errors, 15 warnings. 모두 기존 경고(GradleDependency 12, API 34 필드 2, 라이브러리 lint 1)이고 이 브랜치에서 생긴 경고는 없습니다.
- 로컬 서버 E2E(8개 항목 SCORED)는 메인 세션의 보고를 따랐습니다. 리뷰어가 다시 돌리지는 않았습니다.

## 재검토 (2026-11-15)

- 기준: e8b9298(첫 리뷰 HEAD)...HEAD(0ee2434), 수정 커밋 16개
- 판단: **머지 가능**
- gradle은 tester가 같은 작업 트리에서 돌리고 있어 리뷰어가 따로 실행하지 않았음. 코드 · 테스트 diff만 읽고 판단함

### 지적별 해결 여부

| # | 지적 | 결과 | 근거 |
| --- | --- | --- | --- |
| 1 | SAFETY_WARNING을 다시 셈 · 이벤트 상한 없음 (높음) | 해결 | 경고등 판정이 `counted` 집합을 씀(`SafetyWarningDetector`). 경고등 샘플에서 그 종류가 빠질 때만(`retainAll(active)`) 주기를 끝내고, 속도가 기준 아래로 내려가면 `continue`만 하므로 다시 세지 않음. red 테스트 `de4d305`에서 속도가 넘나드는 경우를 확인함. 운행 시작 전에 켜진 경고등과 운행이 끝난 뒤의 샘플을 다루는 회귀 테스트(`0dff4b4`)도 있음. 상한은 `TripConfig.maxEvents`(기본 2000)에 두고 서버 `trip.maxEvents`가 양수일 때만 덮어씀(`ConfigRepository`, DTO 선택값). `TripAssembler.capEvents`로 자르고, 조립 단계에서 자르므로 Room 저장본과 업로드가 같음 |
| 2 | supported와 실제 수집이 어긋남 (중간) | 해결 | `recorded` 맵에서 샘플이 0개인 BRAKE · HEADLIGHT · SAFETY_WARNING · WIPER · SPEED를 `measurable`에서 빼고, 모든 판정 조건과 `Trip.supported`가 줄어든 집합을 씀. sdk-test-log 204행("리스너는 등록 즉시 현재 값을 한 번 보냄")과 TripRecorder의 운행 전 상태 이어받기 덕분에 정상 수집이면 샘플이 최소 1개 있음. 브레이크처럼 값이 바뀔 때만 오는 신호도 첫 값은 옴 |
| 3 | O(n²) 판정 (중간) | 해결 | `cbdef29` 투 포인터. 아래 "동작 동일성" 참고. 폴링 샘플 중복 제거는 2차 후속으로 기록함 |
| 4 | 급가감속 쿨다운 (낮음) | 2차로 미룸 | plan.md "점수 항목 확장 리뷰 후속"에 기록. 메인 세션 결정 |
| 5 | 좌표가 없을 때 NIGHT_HEADLIGHT (낮음) | 2차로 미룸 | 위와 같음 |
| 6 | capability를 차례로 확인함 (낮음) | 2차로 미룸 | 위와 같음 |
| 7 | 주석이 코드와 다름 (낮음) | 해결 | forgetSec 주석을 "넘게"로 고침. `SignalType.SPEED` 주석에 실제 용도를 다 적음(`4d0776a`) |

### 새로 확인한 것

- **C3 refactor 동작 동일성 (HarshDrivingDetector)**
  - `beforeIndex`는 "시각 ≤ windowStartMs인 마지막 인덱스"까지만 전진함. windowStartMs는 샘플 시각 순이라 줄어들지 않으므로, 예전 `lastOrNull`과 같은 샘플을 고름.
  - 같은 시각 샘플이 여럿이면 둘 다 그중 마지막 샘플(정렬은 안정 정렬)을 고름. 자기 자신은 `ts − 1000 < ts`라 고르지 않음.
  - 브레이크는 `heldIndex`(≤ from 마지막)와 `[fromIndex, toIndex)`(from ≤ ts ≤ to)의 눌림 누적 차이로 계산함. 예전 `heldAtStart || any { pressed && ts in from..to }`와 같은 결과임.
  - `moveTo`는 `continue`(기준 샘플 없음 · 샘플 끊김)보다 앞에서 매번 호출되므로 포인터가 뒤처지지 않음.
- **C3 동작 동일성 (SafetyWarningDetector)**
  - `times`는 오름차순이고, 두 포인터는 "≤ t인 마지막 샘플"이라 예전 `lastOrNull`과 같음. 같은 시각 샘플 규칙도 같음.
  - 판정 의미가 바뀐 것(켜짐 주기 1건)은 `93bef38` fix에서 테스트와 함께 바뀌었고, refactor 커밋에서는 동작이 바뀌지 않음.
- **상한이 항상 maxEvents 이하인지**
  - `capEvents`는 횟수형을 늦은 것부터 `excess`개까지 빼고, 마지막에 `take(maxEvents)`를 하므로 결과는 항상 maxEvents 이하임.
  - maxEvents는 기본 2000이고, 서버 값이 0 이하이면 무시하므로(`1e32fbd`/`b7f994e` 테스트) `take`에 음수가 들어가지 않음.
  - 이벤트는 시각순 정렬 뒤에 자르므로 남은 이벤트의 occurredAt도 운행 구간 안에 있음.
- **C2 이후 SEAT_BELT 제외 · measurable 정합**
  - `measuredSupported`가 `measurable`을 기준으로 바뀜. SEAT_BELT 판정 조건(SEAT_BELT · SPEED가 measurable에 있음)과 이벤트가 없을 때 빼는 조건이 같은 집합을 봄.
  - SPEED 샘플이 0개면 SEAT_BELT 신호는 supported에 남음. 하지만 SPEED가 빠지므로 업로드 때 `ScoreItem.measurable`이 SEAT_BELT · SAFETY_WARNING · SMOOTH_DRIVING · SIGNAL_CANCEL · TURN_SPEED 항목을 넣지 않음. 결과적으로 항목과 이벤트가 맞음.
  - 판정 조건과 `ScoreItem.measurable` 조건은 항목마다 그대로 일치함.
- **TDD · 테스트 규칙:** 수정 사이클마다 `test:` → `fix:`/`feat:` 순서이고, refactor와 chore · docs는 따로 커밋함. 새 테스트 8개 모두 한글 이름이고 given / when / then이 있음.
- **문서:** plan.md에 결정 기록 4건(경고등 주기 · 이벤트 상한 · 샘플 0개 신호 제외 · REJECTED 운행 재전송 안 함), 사용 필드 표(`trip.maxEvents` 씀), 2차 후속(4 · 5 · 6 · 폴링 재시도 · 중복 제거)을 넣었고, api/README의 읽는 항목도 갱신함. 코드와 맞음.

### 참고 (머지를 막지 않음)

- `capEvents`의 `dropped`는 data class 동등성으로 비교하는 `Set`임. 실제로는 UUID라 겹치지 않지만, 고정 id를 주입하는 테스트에서 같은 id · 같은 시각 · 같은 종류의 이벤트가 둘이면 함께 빠질 수 있음. 동등성 대신 인덱스나 id로 비교하면 더 분명함.
- 횟수형을 모두 빼도 넘치면 이른 것부터 maxEvents개만 남기므로, 이론상 늦은 TURN · 구간 이벤트가 잘릴 수 있음. SEAT_BELT는 출발 시각이라 거의 항상 남음. 그러려면 비횟수형 이벤트만 2,000개를 넘어야 해서 실제로는 생기기 어려움.
- 상한 때문에 SAFETY_WARNING · HARSH_*를 자르면 그만큼 감점이 줄어듦(운행 전체가 거부되는 것보다 나음). 결정 기록에 상한 처리 방식은 적혀 있음.

### 검증

- 리뷰어는 gradle을 실행하지 않음(메인 세션 지시: tester가 같은 작업 트리에서 실행 중). 단위 테스트 · 빌드 결과는 tester 보고로 확인해야 함.
