# Pleos SDK 실행 기록

Pleos SDK를 실제 에뮬레이터나 차량에서 실행해 확인한 내용을 기록합니다.
문서에 적힌 내용이 아니라 **직접 실행해서 확인한 결과**만 적습니다.
스파이크 체크리스트(`plan.md` 0장)의 근거 자료로 씁니다.

## 요약

| SDK | 버전 | 환경 | 결과 | 확인한 날 | 상세 |
|---|---|---|---|---|---|
| FusedLocation | 1.1.3 | 에뮬레이터 | ✅ 현재 위치 조회·1초 갱신 됨 | 2026-11-03 | [보기](#fusedlocation-113--에뮬레이터-2026-11-03) |
| FusedLocation | 1.1.3 | 실차 | ❓ 미확인 (구현 가이드에는 미지원) | — | — |
| TextToSpeech | 3.3.1 | 에뮬레이터 | ✅ `ON_DEVICE` + `ko-KR` 재생됨 | 2026-11-01 | [보기](#texttospeech-331--에뮬레이터-2026-11-01) |
| Vehicle | 2.3.2 | 에뮬레이터 | ✅ 21개 도메인 읽기 값 전수 조사, 브레이크·전조등·경고등·크루즈 변경 콜백 확인 | 2026-11-04 | [보기](#vehicle-232--에뮬레이터-2026-11-04) |
| Vehicle | 2.3.2 | 에뮬레이터 | ✅ 방향지시등·조향각·기어·백그라운드 수신 (팀원 조사, 미병합 브랜치) | 2026-11-03 | `feature/vehicle-background-speed`의 `docs/spikes/vehicle-background-speed.md` |
| Vehicle | 2.3.2 | 실차 | ❓ 미확인 | — | — |
| NaviHelper 외 | 3.0.5 등 | 문서·소스 | 📄 점수화 후보 데이터 조사 (과속·단속 카메라·어린이보호구역·안전벨트) | 2026-11-04 | [보기](#점수화에-쓸-수-있는-다른-sdk-데이터-2026-11-04-문서sdk-소스-조사) |

## 공통 실행 환경

- 에뮬레이터: Pleos Connect v2.1.0 (API 34), AVD 이름 `Pleos_Connect_v2.1.0`
- 앱은 **user 10**으로 실행됨 (`adb shell am get-current-user` → `10`).
  권한을 허용하거나 테스트를 실행할 때 `--user 10`을 붙여야 함. user 0에만 허용하면 앱에는 적용되지 않음
- Windows 사용자 경로가 한글이면 에뮬레이터를 `C:\android\sdk`(SDK 연결 폴더)로 실행해야 함 → [emulator-setup.md](emulator-setup.md)

---

## FusedLocation 1.1.3 — 에뮬레이터 (2026-11-03)

**목적**: 스파이크 4번. 위치를 FusedLocation SDK로 받을 수 있는지, 갱신 주기와 받는 필드 확인

### 설정

```kotlin
// automotive/build.gradle.kts
implementation("ai.pleos.playground:FusedLocation:1.1.3")
```

```xml
<!-- AndroidManifest.xml. protectionLevel dangerous → 앱에서 런타임 요청 필요 -->
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION" />
```

- SDK가 바인딩하는 서비스: `ai.pleos.playground.service.location.fused`
  (에뮬레이터 설치 버전 1.0.0, SDK가 요구하는 최소 버전 0.5.0)

### API (SDK 클래스를 javap로 확인)

```
FusedLocation(Context)
  boolean isSupported()
  void initialize()
  void release()
  void getLastKnownLocation(FusedLocationListener)
  void registerFusedLocationCallback(FusedLocationListener)
  void unregisterFusedLocationCallback(FusedLocationListener)

interface FusedLocationListener
  void onChanged(LocationData?)   // null일 수 있음
  void onError(Exception)

LocationData(latitude: Double, longitude: Double, altitude: Double,
             heading: Float, confidence: Float, timestampMillis: Long)
```

- 갱신 주기를 지정하는 파라미터는 없음
- **속도 필드가 없음** → 속도는 Vehicle SDK에서 따로 받아야 함

### 실행 방법

테스트 코드: `automotive/src/androidTest/java/com/example/blinkerleague/FusedLocationSpikeTest.kt`

```powershell
$adb = 'C:\android\sdk\platform-tools\adb.exe'
.\gradlew.bat :automotive:installDebug :automotive:installDebugAndroidTest
& $adb shell pm grant --user 10 com.example.blinkerleague pleos.car.permission.FUSED_LOCATION
& $adb logcat -c
& $adb shell am instrument -w --user 10 -e class com.example.blinkerleague.FusedLocationSpikeTest `
    com.example.blinkerleague.test/androidx.test.runner.AndroidJUnitRunner
& $adb logcat -d -s FusedLocationSpike:*
```

### 결과: 테스트 2개 통과

| 확인 항목 | 결과 |
|---|---|
| `isSupported()` | `true` |
| `getLastKnownLocation()` | 약 20ms 안에 응답. 오류 없음 |
| `registerFusedLocationCallback()` | 15초 동안 13번 받음. 간격은 대부분 1000ms이고 약 2000ms가 두 번 있었음. 오류 없음 |
| 위치 바꾸기 (`adb emu geo fix 126.9780 37.5665 30`) | SDK 값이 바로 37.5665, 126.9780, 고도 30으로 바뀜 |

받은 값 예시:

```
LocationData(latitude=37.421998333333335, longitude=-122.084, altitude=5.0,
             heading=0.0, confidence=5.0, timestampMillis=1793717725436)
갱신 간격(ms): [1001, 1014, 1992, 1996, 1001, 1000, 1001, 1001, 1001, 1000, 1000, 1000]
```

- 처음 위치는 에뮬레이터 기본 좌표(미국 Google 본사)이고, 고도는 5m
- 정지 상태라서 `heading`은 0.0. 움직일 때 값이 바뀌는지는 아직 확인 안 함
- `confidence` 5.0의 단위와 의미는 문서에 없음

### 남은 확인

- [ ] 실차에서 `isSupported()` 결과 (구현 가이드에는 미지원으로 적혀 있음. false이면 NaviHelper로 대신 받음)
- [ ] 위치가 움직일 때 `heading` 값 (Virtual Console 경로 재생 또는 `geo fix` 연속 입력)
- [ ] 앱이 백그라운드에 있을 때도 콜백을 계속 받는지 (스파이크 2번)
- [ ] 권한 거부 시 `onError`로 오는 예외 종류

---

## Vehicle 2.3.2 — 에뮬레이터 (2026-11-04)

**목적**: 스파이크 5(브레이크)·6(전조등)·7(안전 경고)·8(주행 상태·시동)·9(capability 전체). Vehicle SDK의 어느 도메인에서 어떤 값을 읽을 수 있는지 전부 확인

방향지시등·조향각·기어·백그라운드 수신·속도(CarProperty)·오도미터·비상등은 팀원이 먼저 조사했음
→ `feature/vehicle-background-speed` 브랜치의 `docs/spikes/vehicle-background-speed.md` (아직 develop에 병합 전). 여기서는 겹치지 않는 부분 위주로 적음.

### 설정

- 의존성: `ai.pleos.playground:Vehicle:2.3.2` (이미 있음). 차량 서비스 `ai.pleos.playground.service.vehicle`
- 권한: 읽기 권한 12개를 **디버그 빌드 전용** `automotive/src/debug/AndroidManifest.xml`에 선언
  (`CAR_DRIVE`, `CAR_LIGHTS`, `CAR_SAFETY`, `PRIVATE_CAR_SAFETY`, `CAR_INFO`, `CAR_ENERGY`, `CAR_SEATS`, `CAR_CLIMATE`, `CAR_WINDOWS`, `CAR_DOORS`, `CAR_MIRRORS`, `CAR_TOLL`, 모두 `pleos.car.permission.*`)
  - 에뮬레이터에서는 모두 보호 수준 **normal** → 설치만 하면 허용됨 (런타임 요청 불필요)
  - Pleos 심사 기준상 Vehicle SDK 권한은 사전 승인된 것만 선언할 수 있음 → 릴리스 매니페스트에는 실제로 쓰는 것만 옮길 것
- 앱에 쓸 도메인별 권한 (SDK 소스의 `@RequiresPermission`): Brake·Gear·SteeringWheel·DrivingMode·Odometer → `CAR_DRIVE`, Light·TurnSignal → `CAR_LIGHTS`, Safety → `CAR_SAFETY` (경고등 등록은 `PRIVATE_CAR_SAFETY`)

### 실행 방법

테스트 코드: `automotive/src/androidTest/java/com/example/blinkerleague/VehicleSdkSurveyTest.kt`
- SDK 메서드 이름을 손으로 적지 않고 **리플렉션으로 모든 도메인을 훑음** (`Vehicle.getXxx()` 21개)
- `dumpReadableValues`: `check*`/`get*`/`has*`/`is*` 중 인자가 `(onComplete, onFailed)` 두 개뿐인 메서드를 모두 호출 (쓰기 `set*`는 호출 안 함)
- `listenForChanges`: 인자가 리스너 하나뿐인 `register*`에 로그용 Proxy 리스너를 등록하고 `listenSeconds`(기본 30초) 동안 대기
- 영역(Area) 인자가 필요한 메서드(좌석·창문·도어별 값 등 136개)는 건너뜀
- 값 주입: `scripts/vehicle_survey_sim.py` (Virtual Console `PUT`, 엔드포인트는 `/vehicle/catalog`로 확인)

```powershell
$adb = 'C:\android\sdk\platform-tools\adb.exe'
.\gradlew.bat :automotive:installDebug :automotive:installDebugAndroidTest
$runner = 'com.example.blinkerleague.test/androidx.test.runner.AndroidJUnitRunner'

# 1) 읽기 값 전수 조사
& $adb logcat -c
& $adb shell am instrument -w --user 10 -e class 'com.example.blinkerleague.VehicleSdkSurveyTest#dumpReadableValues' $runner
& $adb logcat -d -v raw -s VehicleSurvey:I

# 2) 변경 콜백: 리스너 60초 + 다른 터미널에서 주입
& $adb shell am instrument -w --user 10 -e listenSeconds 60 -e class 'com.example.blinkerleague.VehicleSdkSurveyTest#listenForChanges' $runner
python scripts/vehicle_survey_sim.py      # 리스너 등록 후(약 5초 뒤) 실행
& $adb logcat -d -v time -s VehicleSurvey:I SurveyInject:I
```

### 결과 1: 앱에 필요한 값 (읽기 + 변경 콜백)

| 값 | API | 에뮬레이터 결과 | 앱에서 쓰임 |
|---|---|---|---|
| 브레이크 페달 | `Brake.getCurrentBrakePedalStatus` / `registerBrakePedalStatus` → `Boolean?` | ✅ capability `READ`. 콘솔 brake 30% → `true`, 100% → (변화 없음), 0% → `false`. **눌림 여부(Boolean)만 옴, 밟은 정도(%)는 없음** | 급감속 보조 (2차) |
| 하향등 | `Light.getCurrentLowBeamLightState` / `registerLowBeamLightState` → `LightState` | ✅ 콘솔 전조등 모드 LowBeam → `ON`, Auto·Off → `OFF` | 야간 전조등 (2차) |
| 미등 | `Light.getCurrentTailLampLightState` / `registerTailLampLightState` | ✅ Position → `ON`, LowBeam이어도 `ON` 유지, Auto·Off → `OFF` | 야간 전조등 (2차) |
| 상향등 | `Light.getCurrentHighBeamLightState` / `registerHighBeamLightState` | ✅ 콘솔 high_beam on/off → `ON`/`OFF` | — |
| 전조등 스위치 위치 | `Light.getLowBeamLightSwitch` 등 → `LightSwitch` | 값은 오지만(`OFF`) 콘솔 전조등 모드와 연동 안 됨. 스위치 리스너 없음 | ✕ (상태 값을 씀) |
| 오토라이트 | — | ❌ **API 없음.** `LightState`는 `OFF/ON/DAYTIME_RUNNING`뿐. 콘솔 Auto를 넣으면 하향등·미등 모두 `OFF`가 됨 | 오토라이트 여부는 판정 불가 → 상태(ON/OFF)로만 판정 |
| 안개등 | `Light.getCurrentFront/RearFogLightState` | ❌ 미지원 (`IllegalArgumentException … FRONT_FOG_LIGHTS_STATE`) | — |
| 제동등 | — | ❌ API 없음 (콘솔 stop_light on/off에 아무 콜백 없음) | 브레이크 페달로 대신 |
| 비상등 | — | ❌ API 없음. 콘솔 hazard on/off에 TurnSignal·Light·경고등 모두 반응 없음 (팀원 조사와 같음) | 회전 제외 규칙 HAZARD 불가 |
| 경고등(Telltale) | `Safety.getTelltalesInfo` / `registerTelltales` → `Telltales(List<TelltaleData>)` | ✅ 평소엔 빈 목록. **콘솔 경고등 시뮬레이터로 넣으면 옴**: `TelltaleData(type=TURN_SIGNAL_LEFT, state=BLINK, color=1, …)`, `SEAT_BELT ON`. 끄면 다시 빈 목록 (켜진 것만 담김) | 안전 경고 (2차). 실차에서 비상등이 `TURN_SIGNAL_LEFT`+`RIGHT` 동시 BLINK로 오는지 확인 필요 |
| 기타 경고 | `Safety.hasPowerDownWarningIndicator` | ✅ `false` | — |
| 엔진오일·브레이크액·변속기오일·워셔액 | `Safety.get…LevelState`, `hasLowWasherFluidWarning` | ❌ 미지원 | — |
| 주행 모드 | `DrivingMode.getCurrentDrivingModeState` → `NORMAL/ECO/SPORT` | ❌ 미지원 (`ERROR_NOT_SUPPORTED_PROPERTY`). 어차피 에코·스포츠 모드이지 **주행/정차 상태가 아님** | ✕ |
| 크루즈 상태·목표 속도 | `DrivingMode.getCruiseControlState` / `getCruiseControlTargetSpeed` | ✅ `ENABLED`, `13.8889`(m/s = 50km/h). 콘솔 목표 20 → `20.0`. 콘솔 상태 `not_ready` → **`null`** (매핑 안 되는 값) | ✕ |
| 시동(Ignition) | — | ❌ **API 없음.** SDK에 `IgnitionState` enum(`LOCK/OFF/ACC/ON/START`)은 있지만 이를 돌려주는 메서드가 없음 | 운행 시작·종료는 기어·속도로 판정해야 함 |
| 기어 | `Gear.getCurrentGearSelectionState` | ✅ `GEAR_PARK` (변경 콜백은 팀원 조사 참고) | P단 판정 |
| 조향각 | `SteeringWheel.getCurrentSteeringWheelAngle` | ✅ `0.0`. 변화가 없어도 약 3.1초마다 같은 값 재전송 (`-0.0`으로도 옴) | 회전 감지 |
| 방향지시등 | `TurnSignal.getCurrentTurnSignalState` | ✅ `OFF` (`OFF/RIGHT/LEFT`, 팀원 조사 참고) | 깜빡이 판정 |
| 오도미터 | `Odometer.getCurrentDistanceDriven` | ✅ `0.0`(km). 약 4초마다 재전송 | 운행 거리 후보 |
| 남은 주행 가능 거리 | `Odometer.getRangeRemainingDistance` / `registerRangeRemainingListener` | ✅ 처음 `500000.0` → 콘솔 연결 순간 `0.0`. 약 4초마다 재전송 | ✕ |
| 트립 거리·시간 (9종) | `Odometer.getTrip…` | ❌ 모두 미지원 | — |
| 차량 속도 | — | ❌ Vehicle SDK에 없음 → CarPropertyManager `PERF_VEHICLE_SPEED` (팀원 조사) | 저속 조향 제외, 급가감속 |

### 결과 2: 그 밖의 도메인 (앱과 무관, 지원 여부만)

| 도메인 | 지원 (값) | 미지원 |
|---|---|---|
| CarInfo | VIN·CRN 조회 → 둘 다 `"invalid"` | `getCapabilityInfo`는 `PRIVATE_CAR_INFO` 필요 (`SecurityException`) |
| Display | 온도 단위 `CELSIUS` (READ_WRITE) | — |
| Door | 도어 잠금·위치(영역 2개), 글러브박스 잠금 `false` | — |
| EvBattery | 배터리 용량 `77400.0`, 잔량 `77400.0`, SOC `100`(%), 충전 전류 제한 `12.5`, 충전 스위치·충전구 `false` | 전비(누적·현재·순간·충전 후) |
| Fuel | — | 연비 전부, 연료 잔량(%) → **전기차 에뮬레이터** |
| HVAC | 에어컨·자동·듀얼·내기순환·제상·풍량(1~8)·온도(15~30) 등 capability | 최대 냉방 |
| Seat | 좌석 위치·각도·열선·통풍·안전벨트·착석 capability (영역별) | — |
| SideMirror·SteeringWheel 열선 | 열선 0~3 | — |
| Tire | 바퀴 영역 4개 | — |
| Toll | — | ETCS 음성 안내 볼륨 |
| Window | 창문 잠금·위치(0~10) | 스마트 글래스 |
| Wiper | 상태 `OFF/ON/SERVICE`, 스위치 `OFF/CONTINUOUS_LEVEL_1/3/AUTO` | — |

### 관찰한 점 (구현할 때 주의)

- **미지원 오류가 두 가지 모양으로 옴.** 같은 "미지원"인데
  - `check*`와 일부 `get*`: SDK 예외, 메시지 `ERROR_NOT_SUPPORTED_PROPERTY` (런타임 클래스 이름이 난독화돼 `a`로 찍힘)
  - 나머지 `get*`: 일반 `Exception`, 메시지 `java.lang.IllegalArgumentException: property ID is not in carPropertyConfig list …`
  - → 클래스로 구분하지 말고 **`onFailed`가 오면 미지원으로 보고 `supported` 목록에서 빼는** 방식이어야 함 (CLAUDE.md 규칙과 같음)
- **리스너는 등록 즉시 현재 값을 한 번 보냄** (`registerXxx` 직후 0.2~1초 안에 첫 콜백). 미지원 리스너는 등록 직후 `onFailed`가 옴
- **콘솔 세션이 연결되기 전 값은 믿을 수 없음.** 처음에 상향등 `ON`·미등 `ON`·남은 거리 500000이었다가, Virtual Console 세션이 붙는 순간 콘솔 상태(전부 꺼짐, 0)로 바뀜. 에뮬레이터 부팅 직후 값으로 판단하지 말 것
- 브레이크는 이미 눌린 상태에서 더 밟아도(30% → 100%) 콜백이 오지 않음 → **값이 바뀔 때만** 옴. 반대로 조향각·오도미터·남은 거리는 **변화 없어도 3~4초마다** 다시 옴
- 콘솔 PUT 응답보다 콜백이 먼저 찍힐 만큼 빠름 (정밀한 지연 측정은 팀원 조사 참고: 대부분 즉시~170ms)

### 결과 3: 좌석별 값 (안전벨트·착석)

`dumpSeatAreaValues` 테스트. 좌석 영역은 `ROW_1_LEFT`(운전석), `ROW_1_RIGHT`, `ROW_2_LEFT/CENTER/RIGHT` 5개

| 값 | API (권한 `CAR_SEATS`) | 결과 |
|---|---|---|
| 안전벨트 착용 | `Seat.getCurrentSeatBeltBuckledState(area)` / `registerSeatBeltBuckledState(area, listener)` → `Boolean?` | ✅ 5개 좌석 모두 `false` |
| 착석 여부 | `Seat.getCurrentSeatOccupancy(area)` / `registerSeatOccupancy` | ✅ 운전석 `false`, 나머지 `true` (콘솔 기본값으로 보임) |
| 안전벨트 미착용 경고 | `Safety.hasSeatBeltNotBuckledWarning(area)` | ❌ 미지원 |
| 에어백 전개 | `Safety.hasSeatAirbagBeenDeployed(area)` | ❌ 미지원 |

- Virtual Console에는 안전벨트를 바꾸는 기능이 없어(경고등 SeatBelt만 있음) **변경 콜백은 확인 못 함**
- 좌석마다 지원 여부가 다름. 같은 API라도 `ROW_2_CENTER`처럼 없는 영역은 `area ID … not supported`로 실패함

### 남은 확인

- [ ] 실차에서 운전석 안전벨트 착용·해제 시 `registerSeatBeltBuckledState` 콜백이 오는지
- [ ] 실차에서 같은 덤프를 한 번 돌려 지원 목록 비교 (특히 주행 모드, 트립 거리, 안개등, 경고등)
- [ ] 실차 비상등이 경고등 `TURN_SIGNAL_LEFT`·`RIGHT` 동시 `BLINK`로 오는지
- [ ] 오토라이트 상태에서 실차 하향등 `LightState`가 실제 점등을 따라가는지 (야간 전조등 판정 근거)
- [ ] 시동 on/off를 앱이 알 방법 (Vehicle SDK에 없음. CarProperty `IGNITION_STATE` 권한 확인 필요) → 스파이크 8번
- [ ] Virtual Console `power-system`(Off/Ready/On/Driving) 주입 시 앱에 오는 신호 — 에뮬레이터 상태를 흔들 수 있어 이번엔 하지 않음

---

## 점수화에 쓸 수 있는 다른 SDK 데이터 (2026-11-04, 문서·SDK 소스 조사)

**목적**: 가이드(https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)의 SDK 5종(Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation) 중에서 방향지시등 말고도 안전 습관 점수에 쓸 수 있는 데이터가 있는지 확인

- 근거: 문서 본문 + **SDK 소스 jar** (Pleos Nexus `NaviHelper-3.0.5-sources.jar`, `navi-data-3.0.4-sources.jar`)
  - 문서의 API Reference 페이지는 본문이 스크립트로 그려져서 필드를 읽을 수 없었음 → 소스로 확인
- NaviHelper 실행 결과는 팀원 조사(`feature/vehicle-background-speed`의 `docs/spikes/navi-helper-events.md`)를 인용함. 이번에 새로 실행하지는 않음

### NaviHelper 3.0.5: 가장 쓸모가 큼

권한: 조회 `pleos.car.permission.NAVI_ROUTE`, 경로 설정 `NAVI_ROUTE_SEARCH`. 이벤트는 `NaviHelperEventListener`로 받음 (`addListener`)

| 콜백 | 데이터 (소스 기준) | 점수화 후보 | 에뮬레이터에서 |
|---|---|---|---|
| `onCurrentRoadSpeedLimit(speedLimit: Int)` | 현재 도로 제한속도 (km/h로 추정, 소스에 단위 없음) | **과속 비율**: 차량 속도(CarProperty `PERF_VEHICLE_SPEED`)와 비교해 제한속도 초과 시간·거리 | 1초마다 옴. 단 **항상 `0`** (목적지 유무 무관) |
| `onCameraAlert(List<CameraAlert>)` | `type`(고정·이동·구간 시작/끝·신호·**어린이보호구역** 카메라 등 10종), `speedLimit`(km/h), `remainDistance`(m), `sectionAvrSpeed`(구간 평균 km/h), **`isOverSpeed`**, **`isOverSectionAvrSpeed`** | **단속 구간 준수**: 카메라 통과 시 과속 여부를 내비가 직접 판정해 줌. 구간단속 평균속도 준수 | 오지 않음 |
| `onCautionPointInfo(List<CautionPoint>)` | `type`(**SCHOOL_ZONE**, SPEED_BUMP, ACCIDENT_PRONE_AREA, RAILROAD_CROSSING, WILDLIFE_CROSSING 등 23종), `remainDistance`(m) | **어린이보호구역·과속방지턱 감속**: 지점 진입 전후 속도 | 오지 않음 |
| `onTBTInfo(List<TBTInfo>)` | `type: TurnType`(좌·우회전, 유턴, IC 진출입, 회전교차로 등), `distance`(m), `description` | **회전 전 미리 깜빡이** (목업의 "내비 안내 회전 미리 켜기"), 회전 감지 보정 | 경로 시작 때 **전체 회전 목록**이 한 번 옴. 주행 중 갱신은 확인 못 함 |
| `onNavigationStatus(NavigationStatus)` | `INACTIVE / INITIALIZING / FREE_DRIVE / ROUTE_DRIVE` | 안내 중 운행만 TBT 판정 대상으로 | 경로 시작 시 `ROUTE_DRIVE` |
| `onDestinationArrived`, `onDrivingInfo`, `onTripInfo` | 도착, 남은 거리(m)·시간(s), 경로 총거리·ETA | 운행 구분 보조 (도착 = 운행 종료 후보) | `onDrivingInfo`만 경로 시작 시 1회 |
| `onCurrentLocationInfo` | 위도·경도·heading·주소("구 + 동") | 지역 리그의 지역 판단 보조 | `getCurrentLocationInfo()` 호출 시 옴. 값이 고정됨 |

- **과속·단속·주의 지점은 실차(또는 실제 지도 위치 진행)에서만 확인 가능**
  - 에뮬레이터 내비는 주입한 GPS를 따라 진행하지 않음(팀원 조사). 그래서 제한속도 0, 카메라·주의 지점 이벤트 없음
  - Pleos에 에뮬레이터 모의 주행 방법을 문의하거나 실차 확인 필요
- 카메라·주의 지점은 **내비가 안내 중일 때만** 오는지, 목적지 없이(FREE_DRIVE)도 오는지 미확인

### Vehicle 2.3.2: 추가 후보

| 데이터 | 점수화 후보 | 상태 |
|---|---|---|
| 안전벨트 착용 `Seat.registerSeatBeltBuckledState(운전석)` | **출발 전 안전벨트 착용** (기어 D 전환 시점에 착용 여부) | 조회 됨. 변경 콜백은 실차 확인 필요 (위 결과 3) |
| 브레이크 페달 `Brake.registerBrakePedalStatus` | 급정거 보조 (속도 급감 + 페달) | 됨 (Boolean만) |
| 하향등·미등 `Light.register…LightState` | 야간 전조등 (2차에 이미 있음) | 됨 |
| 경고등 `Safety.registerTelltales` | 경고등 켜진 채 주행 (ABS·ESC·타이어 공기압 등) | 콘솔 주입 시 옴 |
| 크루즈 상태·목표 속도 `DrivingMode` | 점수화에 쓰기 어려움 (크루즈 사용을 보상하면 안전과 무관) | 됨 |

### 나머지 SDK

| SDK | 점수화 관련 | 비고 |
|---|---|---|
| FusedLocation 1.1.3 | 위치·heading만 있음. 속도 없음. `addProximityAlert`(지정 영역 진입·이탈 알림)가 있음 → 지역 리그의 지역 경계 판단에 쓸 수 있으나 지역 경계 데이터는 따로 필요 | 문서 기준 "에뮬레이터에서 개발·검증". 실차 지원은 `isSupported()`로 확인 |
| TextToSpeech 3.3.1 | 데이터 없음 (음성 출력) | 이미 사용 중 |
| SpeechToText 3.3.1 | 데이터 없음 (음성 → 텍스트) | 점수화와 무관 |

- 가이드의 SDK 목록에는 **ADAS SDK가 없음** (plan.md "이후" 항목의 차로 변경 측정은 이 5종으로는 불가)

### 정리: 점수화에 추가할 수 있는 후보 (우선순위 제안)

1. **과속 비율**: NaviHelper 제한속도 + CarProperty 차량 속도. 운행 중 제한속도를 넘은 시간 비율
2. **단속 카메라·구간단속 준수**: `CameraAlert.isOverSpeed` / `isOverSectionAvrSpeed`를 내비가 직접 줌 → 판정 로직이 단순함
3. **어린이보호구역 감속**: `CautionPoint(SCHOOL_ZONE)` 또는 `CameraType.SPEED_CAMERA_SCHOOL_ZONE`
4. **출발 전 안전벨트**: Vehicle Seat
5. **내비 안내 회전 미리 깜빡이**: TBT 목록 + 기존 깜빡이 판정 (목업에 이미 있음)

1~3은 에뮬레이터에서 값이 오지 않아 **실차 확인 전까지 MVP에 넣기 어려움**. 모두 P0 밖이라 `plan.md` "결정 필요"에 적어 둠.

---

## TextToSpeech 3.3.1 — 에뮬레이터 (2026-11-01)

**목적**: 스파이크 10번. 시동·종료 음성 안내를 재생할 수 있는지 확인

- `Mode.ON_DEVICE` + `ko-KR`로 재생됨. 권한 `pleos.car.permission.TTS_SERVICE` 필요
- Android 표준 `TextToSpeech`는 엔진(caas SpeechService) 연결에 실패함 → Pleos SDK를 씀
- 에뮬레이터 caas 버전 4.2.10 (SDK 최소 요구 4.1.29)
- 구현: `voice/PleosTtsAnnouncer.kt`

### 남은 확인

- [ ] 내비 안내 음성이 나오는 중일 때 오디오 포커스 동작 (지연·거절)

---

## 기록 양식

새로 실행하면 위 요약 표에 한 줄을 추가하고, 아래 양식으로 절을 추가합니다.

```markdown
## <SDK> <버전> — <에뮬레이터|실차> (<YYYY-MM-DD>)

**목적**: 스파이크 N번. 무엇을 확인하려 했는지

### 설정
의존성, 권한, 필요한 서비스

### 실행 방법
테스트 코드 위치와 명령어

### 결과
확인 항목별 결과, 실제 로그 일부

### 남은 확인
- [ ] ...
```
