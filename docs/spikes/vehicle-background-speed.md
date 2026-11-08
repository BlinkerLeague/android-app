# 차량 신호 백그라운드 수신·속도 출처 조사 (2026-11-03)

Pleos Connect 에뮬레이터에서 아래 항목을 확인한 기록입니다. 방식은 [NaviHelper 이벤트 콜백 조사](navi-helper-events.md)와 같습니다.
1. Vehicle SDK의 방향지시등·조향각 콜백이 백그라운드에서도 오는지
2. CarPropertyManager로 차량 속도·기어를 읽을 수 있는지
3. NaviHelper `getCurrentLocationInfo()` 폴링으로 위치를 받을 수 있는지
4. Vehicle SDK Odometer로 트립 주행 거리·시간을 받을 수 있는지 (속도 적분과 비교)
5. 비상등 상태를 읽을 수 있는 API가 있는지 (Light 도메인, Safety 경고등)
6. 포그라운드 서비스를 어떤 경로로 시작할 수 있는지 (백그라운드, 부팅, Android 14 제한, Pleos 심사 기준)

확인하지 못한 항목은 "미확인"으로 적었습니다.

## 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator, Android 14 (API 34), AAOS 사용자 u10(운전자) / u0(시스템) |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` / 차량 서비스 `ai.pleos.playground.service.vehicle` 2.3.22 |
| NaviHelper SDK | `ai.pleos.playground:NaviHelper:3.0.5` / 내비 서비스 3.0.4 |
| android.car | `useLibrary("android.car")`, compileSdk 34의 `optional/android.car.jar` |

### API·권한 확인 근거

**Vehicle SDK**
- 문서의 권한 표: TurnSignal → `pleos.car.permission.CAR_LIGHTS`
- 조향각·기어는 문서 표에 없습니다. 그래서 SDK 클래스의 `@RequiresPermission`(javap)으로 확인했습니다.
  - `registerSteeringWheelAngle`, `registerGearSelection` → `pleos.car.permission.CAR_DRIVE`

| 도메인 | 사용한 API (SDK 2.3.2 javap) | 권한 |
| --- | --- | --- |
| TurnSignal | `checkTurnSignalStateCapabilities`, `getCurrentTurnSignalState`, `registerTurnSignalState(TurnSignalStateListener)` → `onTurnSignalStateUpdated(TurnSignalValue?)` | `CAR_LIGHTS` |
| SteeringWheel | `checkSteeringWheelAngleCapabilities`, `getCurrentSteeringWheelAngle`, `registerSteeringWheelAngle(SteeringWheelAngleListener)` → `onSteeringWheelAngleUpdated(Float?)` | `CAR_DRIVE` |
| Gear | `getCurrentGearSelectionState`, `registerGearSelection(GearSelectionListener)` → `onSelected(VehicleGear?)`. capability 확인 API는 **없음** | `CAR_DRIVE` |

- `Vehicle.initialize()`에는 연결 완료 콜백이 없습니다.
  - 문서 본문이 비어 있고, 튜토리얼에는 "초기화 표시만으로 서비스 연결을 보장하지 않는다"고만 적혀 있습니다.
  - 그래서 capability 확인이 실패하면 1초 간격으로 최대 5번 다시 시도하게 했습니다. 실제로는 매번 첫 시도에 성공했습니다(초기화 후 약 0.6초).

**android.car**
- Android Developers 문서(parked app 가이드: `useLibrary("android.car")`, `VehiclePropertyIds.PERF_VEHICLE_SPEED`는 m/s이고 `Car.PERMISSION_SPEED` 필요)
- `android.car.jar` javap
- 기기의 `dumpsys package permission`(보호 수준)

| 상수 | 값 | 보호 수준(기기) | 결과 |
| --- | --- | --- | --- |
| `Car.PERMISSION_SPEED` | `android.car.permission.CAR_SPEED` | **dangerous** → 런타임 허용 필요 | 선언 + 허용하면 속도 읽힘 |
| `Car.PERMISSION_POWERTRAIN` | `android.car.permission.CAR_POWERTRAIN` | normal | 선언만으로 기어 읽힘 |
| `Car.PERMISSION_READ_STEERING_STATE` | `android.car.permission.READ_CAR_STEERING` | signature\|privileged | 일반 앱은 못 받음 |
| (TURN_SIGNAL_STATE 읽기) | `android.car.permission.CAR_EXTERIOR_LIGHTS` | signature\|privileged | 일반 앱은 못 받음 |

`TURN_SIGNAL_STATE`의 읽기 권한이 `CAR_EXTERIOR_LIGHTS`라는 매핑은 미확인입니다(문서 페이지가 길어서 해당 부분을 읽지 못함). 실측으로 확인한 것은 일반 앱 권한으로는 이 속성이 목록에 없다는 점뿐입니다.

## 측정 방법

**앱 쪽: 조사용 프로브 (디버그 빌드 전용)**
- `VehicleProbe` → Logcat `VehicleTest`, `CarPropertyProbe` → `SpeedTest`, `NaviEventLogger` → `NaviTest`
- 로그 한 줄 형식(`data/debug/ProbeLog.kt`):
  `시각 +경과초 dt=같은이름직전간격 #누적 [출처] 이름 값`
  - 출처: Vehicle / CarProperty / Navi
  - 값은 리플렉션으로 필드를 모두 펼쳐서 찍음
- 시작 경로는 두 가지입니다.
  - 앱 프로세스에서 등록: `adb shell am start -n com.example.blinkerleague/.MainActivity --es probe vehicle,speed`
  - 포그라운드 서비스에서 등록: `adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService -a com.example.blinkerleague.PROBE_START --es targets vehicle,speed`
  - 서비스는 자동으로 시작하지 않습니다. 일반 백그라운드 실험이 오염되지 않게 하기 위해서입니다.

**값 주입: Virtual Console API** (`scripts/vconsole.py`, `scripts/vehicle_probe_sim.py`)
- 엔드포인트는 콘솔 웹 화면의 요청과 `/vehicle/catalog`로 확인했고, 모두 `PUT`입니다.

| 신호 | 엔드포인트 |
| --- | --- |
| 기어 | `/vehicle/gear?gear=Park\|Reverse\|Neutral\|Drive` |
| 속도·조향 | `/vehicle/controls?speed=<km/h>&accelerator=<%>&brake=<%>&steering=<deg>` (카탈로그 조향 범위 -470~500, 콘솔 슬라이더 -540~540) |
| 방향지시등 | `/vehicle/turn-signal?mode=Off\|BlinkLeft\|BlinkRight\|FlashLeft\|FlashRight` |
| 비상등 | `/vehicle/lights?feature=hazard&value=true\|false` |

- **세션 신호**: 콘솔은 `POST /browser/session?client=…` 세션 신호(3초 타임아웃)가 살아 있어야 쓰기를 받습니다. 스크립트가 1초마다 보냅니다.
- **주입 시각 기록**: 주입할 때마다 기기 시각으로 환산한 주입 시각을 Logcat `ProbeInject`에 남깁니다. 호스트↔기기 시계 차이는 시작할 때 `adb shell date +%s%3N`으로 5번 재서 왕복 시간이 가장 짧은 값을 씁니다(왕복 22~24ms).
- **지연 계산**: `scripts/probe_log_summary.py`가 주입마다 관련 콜백 중 값이 처음 바뀐 콜백까지의 시간을 계산합니다.
  - 시계 오차 때문에 주입 50ms 전부터 그 주입의 반응으로 봅니다.
  - 그래서 **-5~+15ms는 "오차 범위 안에서 즉시"** 로 읽습니다.
- **프로세스 상태**: `dumpsys activity processes`의 `curProcState`·`cached`와 이벤트 로그의 `am_freeze`·`am_unfreeze`로 판단했습니다.

### 재현 명령

```bash
# 공통: 디버그 빌드 설치, 속도 런타임 권한 허용 (AAOS는 사용자별 권한 → --user current 필수)
./gradlew :automotive:installDebug
adb shell pm grant --user current com.example.blinkerleague android.car.permission.CAR_SPEED
adb logcat -c; adb logcat -b events -c

# A) 앱 포그라운드
adb shell am start -n com.example.blinkerleague/.MainActivity --es probe vehicle,speed
python3 scripts/vehicle_probe_sim.py turn speed range

# B) 일반 백그라운드: 앱에서 등록 후 하단 독의 내비 아이콘으로 내비를 앞으로, 20초 대기
adb shell am start -n com.example.blinkerleague/.MainActivity --es probe vehicle,speed
#   (내비 아이콘 탭 → adb shell dumpsys activity activities | grep -m1 topResumedActivity 로 내비가 앞인지 확인)
python3 scripts/vehicle_probe_sim.py turn speed

# C) 포그라운드 서비스에서 등록 + 내비 앞
adb shell am start -n com.example.blinkerleague/.MainActivity
adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService \
  -a com.example.blinkerleague.PROBE_START --es targets vehicle,speed
#   (내비를 앞으로, 20초 대기)
python3 scripts/vehicle_probe_sim.py turn speed

# 요약 (각 실행 후)
adb logcat -d -s VehicleTest:I SpeedTest:I NaviTest:I ProbeInject:I > run.log
python3 scripts/probe_log_summary.py run.log
adb logcat -d -b events | grep -E "am_freeze|am_unfreeze|am_proc_died" | grep blinkerleague

# 실험 3: 위치 폴링 (5초, 0이면 중지)
adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService \
  -a com.example.blinkerleague.NAVI_POLL_LOCATION --el intervalMs 5000
adb emu geo fix 127.0276 37.4979
python3 scripts/navi_drive_sim.py --seconds 25 --speed 40 --bearing 90 --start 37.4979,127.0276
```

## 실험 1. 백그라운드에서 Vehicle SDK 콜백이 오는지

시나리오(`turn`, 약 27초)는 다음 순서입니다.
1. 기어 D, 40km/h
2. 오른쪽 깜빡이 켬 → 3초 뒤 조향 +120° → 3초 뒤 0° → 깜빡이 끔
3. 왼쪽으로 같은 순서
4. 깜빡이 없이 조향 +120° → 0°

이어서 실험 2의 `speed` 시나리오도 같은 상태에서 돌렸습니다.

### 상태별 비교

| | A) 앱 포그라운드 | B) 일반 백그라운드(내비 앞) | C) 포그라운드 서비스 + 내비 앞 |
| --- | --- | --- | --- |
| 프로세스 상태 | `curProcState=2`(TOP), cached=false | 내비를 앞으로 띄운 직후 15, 약 10초 뒤 **`am_freeze`(동결)**, 이후 16(cached=true) | `curProcState=4`(포그라운드 서비스), 동결 없음 |
| 깜빡이 콜백 (주입 4회) | 4/4, 즉시(-4~-3ms) | **0/4** (동결 이후 전혀 안 옴) | 4/4, 즉시(-2~-1ms) |
| 조향각 콜백 (주입 6회) | 6/6, -2~+170ms | **0/6** | 6/6, -4~+153ms |
| 기어 콜백 | 모두 즉시(-5~+1ms) | **0회** (주입 8회) | 모두 즉시(-4~+10ms) |
| 누락 | 없음 | 동결된 동안 전부 누락. 앱을 다시 띄워도 밀린 콜백이 한꺼번에 오지 않음 | 없음 |

**B의 추가 관찰**
- 동결된 동안 CarPropertyService는 속도 이벤트(10Hz)를 계속 보냈습니다. 약 65초 뒤부터 binder 버퍼가 차서 전송 실패가 876번 났습니다(`FAILED BINDER TRANSACTION`, `DeadObjectException … running out of binder buffer space`).
- 앱을 다시 앞으로 가져오자 동결이 풀린 지 74ms 만에 **프로세스가 종료되고 새로 시작**됐습니다(`am_unfreeze` → `am_proc_died` → `am_proc_start`).

**조향 0°로 되돌릴 때의 지연**: A·C 모두 50~170ms가 걸렸습니다. 다른 변화는 시계 오차 범위 안이었습니다. 콘솔이나 차량 서비스가 0으로 돌아오는 값을 다듬는 것으로 보이지만 미확인입니다.

### 값의 의미

| 항목 | 결과 |
| --- | --- |
| capability | `checkTurnSignalStateCapabilities` → `CapabilityConfig{accessType=READ}`, `checkSteeringWheelAngleCapabilities` → `READ`, `AreaBounds(minValue=-470.0, maxValue=500.0)` |
| 깜빡이 값 | SDK enum `TurnSignalValue`가 `OFF(id=0)`, `RIGHT(id=1)`, `LEFT(id=2)` 세 개뿐임. **비상등 값은 없음** |
| 깜빡이 좌·우 구분 | 된다. BlinkRight → RIGHT, BlinkLeft → LEFT |
| Flash 모드 | FlashRight → RIGHT, FlashLeft → LEFT (Blink와 구분되지 않음) |
| 비상등 | 비상등을 켜도 TurnSignal 콜백이 오지 않음(OFF 유지). 비상등을 켠 채 BlinkRight를 넣으면 RIGHT. → **TurnSignal로는 비상등을 알 수 없음** |
| 조향각 단위 | 도(°). 콘솔 값과 크기가 같음(120 → 120.0, 470 → 470.0) |
| 조향각 부호 | **콘솔 +값 → SDK 음수.** 콘솔 +120 → `-120.0`, -120 → `120.0`, +500 → `-500.0`. 콘솔 화면은 +값을 시계 방향(`rotate(+deg)`, 오른쪽 조향)으로 그림 → 이 에뮬레이터에서는 **오른쪽 = 음수**. 실차 부호는 미확인 |
| 조향각 범위 | capability는 -470~500이지만, 콘솔에 700을 넣으면 `-540.0`이 옴(콘솔 슬라이더 범위 ±540에서 잘림). 범위 밖 값도 그대로 전달됨 |
| 조향각 갱신 주기 | 바뀔 때 즉시 + **변화가 없어도 약 3.1초마다 같은 값이 다시 옴**(중앙값 3.10초, A에서 42회/2분) |

## 실험 2. CarPropertyManager로 차량 속도를 읽을 수 있는지

### 지원 여부 (`getPropertyList`, `getCarPropertyConfig`)

| 속성 | CAR_SPEED 허용 전 | 허용 후 |
| --- | --- | --- |
| 목록 크기 | 6 | 9 |
| `PERF_VEHICLE_SPEED` | 목록에 없음, config null, `registerCallback` false | 목록에 있음, 등록 true |
| `PERF_VEHICLE_SPEED_DISPLAY` | 위와 같음 | 위와 같음 |
| `GEAR_SELECTION` | 등록 true (`CAR_POWERTRAIN`, normal) | 같음 |
| `CURRENT_GEAR` | 등록 true | 같음 |
| `PERF_STEERING_ANGLE` | 목록에 없음, 등록 false | 같음 (signature 권한) |
| `TURN_SIGNAL_STATE` | 목록에 없음, 등록 false | 같음 (signature 권한) |

VHAL 덤프(`dumpsys car_service`)상 `PERF_VEHICLE_SPEED`는 연속값(changeMode 2)이고 샘플링 범위는 10~20Hz입니다.

**런타임 권한**: `CAR_SPEED`는 dangerous라 선언만으로는 허용되지 않습니다.
- `pm grant`에 `--user`를 주지 않으면 시스템 사용자(u0)에만 허용됩니다. 앱은 u10에서 돌기 때문에 여전히 `granted=false`였습니다.
- 앱에서는 런타임 권한 요청 화면이 필요합니다. 아직 구현하지 않았습니다.

### 속도 (콘솔 0 → 20 → 40 → 60 → 30 → 0 km/h, 5초 간격)

| 주입 (km/h) | `PERF_VEHICLE_SPEED` 값 | 지연 A / C |
| --- | --- | --- |
| 20 | 5.555556 | +85ms / -4ms |
| 40 | 11.111112 | +77ms / +93ms |
| 60 | 16.666668 | +63ms / +82ms |
| 30 | 8.333334 | +52ms / +70ms |
| 0 | 0.0 | -4ms / +58ms |

- **단위는 m/s**입니다(값 × 3.6 = km/h).
- 주입한 값으로 **곧바로 바뀝니다.** 중간값 없이 한 번에 목표값이 됩니다(에뮬레이터 특성).
- `PERF_VEHICLE_SPEED_DISPLAY`도 같은 값과 같은 시각으로 옵니다.
- **갱신 주기는 약 10Hz**입니다(간격 중앙값 0.10초). 값이 변하지 않아도 계속 옵니다. `SENSOR_RATE_FASTEST`로 구독했고, 2분에 약 1,200회입니다.
- **Vehicle SDK 2.3.2에는 차량 속도 API가 없습니다.** 클래스 목록 기준이며, 속도 관련은 크루즈 목표 속도·속도 표시 단위·공조 팬 속도뿐입니다.

### 기어 (P → R → N → D → P, 3초 간격)

| 출처 | 결과 |
| --- | --- |
| Vehicle SDK `onSelected` | `GEAR_PARK`, `GEAR_REVERSE`, `GEAR_NEUTRAL`, `GEAR_DRIVE`. 즉시(-5~+10ms) |
| CarProperty `GEAR_SELECTION` | 값 P=4, R=2, N=1, D=8. 즉시(-5~+9ms) |
| CarProperty `CURRENT_GEAR` | **처음 값 4(P)에서 바뀌지 않음.** 콘솔 기어 기능은 벤더 속성 `VENDOR_GEAR_SELECTION`(0x21400400)을 쓰는데, `GEAR_SELECTION`만 따라옴 |

### 백그라운드
속도·기어도 실험 1과 결과가 같습니다.
- A(포그라운드): 모두 옵니다.
- B(일반 백그라운드): 동결 이후 전혀 오지 않고, 동결이 풀릴 때 프로세스가 종료됩니다.
- C(포그라운드 서비스): 모두 옵니다.

## 실험 3. `getCurrentLocationInfo()` 폴링 (목적지 없음, 5초 간격)

| 항목 | 결과 |
| --- | --- |
| 대응 콜백 | `onCurrentLocationInfo`가 호출할 때마다 옴(14/14, 호출 후 4~30ms) |
| 필드 | `latitude`, `longitude`, `heading`, `address`, `routeId`, `currentRouteIndex`. **속도 필드는 없음** |
| 값 | 항상 `address="수정구 시흥동"`, 37.4127212, 127.094681, `heading=0.0`, `routeId=""` |
| GPS를 옮겼을 때 | `adb emu geo fix`로 강남(37.4979, 127.0276)으로 옮기고, 기어 D·40km/h로 25초 동쪽 주행을 시켜도 **값이 바뀌지 않음** |
| 주소 형식 | "구 + 동"(예: `수정구 시흥동`). 시군구 코드는 없음 |

## 실험 4. Vehicle SDK Odometer: 트립 주행 거리·시간

### API·권한 (SDK 2.3.2 javap)

`Vehicle.getOdometer()`의 API는 모두 `pleos.car.permission.CAR_DRIVE`가 필요합니다.

| 값 | capability | get | 리스너 |
| --- | --- | --- | --- |
| 누적 주행거리(오도미터) | `checkCurrentDistanceDrivenCapabilities` | `getCurrentDistanceDriven` | `registerDistanceDriven` → `DistanceDrivenListener.onDistanceDrivenUpdated(Float?)` |
| 트립 현재 주행 거리 | `checkTripCurrentDrivingDistanceCapabilities` | `getTripCurrentDrivingDistance` | `registerTripCurrentDrivingDistance` (같은 리스너) |
| 트립 누적 주행 거리 | `checkTripCumulativeDrivingDistanceCapabilities` | `getTripCumulativeDrivingDistance` | `registerTripCumulativeDrivingDistance` |
| 트립 현재 주행 시간 | `checkTripCurrentDrivingTimeCapabilities` | `getTripCurrentDrivingTime` | `registerTripCurrentDrivingTime` → `DrivingTimeListener.onDrivingTimeUpdated(Int?)` |
| 트립 누적 주행 시간 | `checkTripCumulativeDrivingTimeCapabilities` | `getTripCumulativeDrivingTime` | `registerTripCumulativeDrivingTime` |

- 그 밖에 순간(`Instantaneous`)·주유 후(`SinceRefill`) 거리와 시간 API도 있습니다. 이번에는 시험하지 않았습니다.

### 결과 (앱 포그라운드)

| 값 | 결과 |
| --- | --- |
| 트립 현재·누적 거리, 현재·누적 시간 | capability, get, 리스너 모두 **`ERROR_NOT_SUPPORTED_PROPERTY`** (에뮬레이터) |
| 누적 주행거리(오도미터) | capability `READ`. 단, 콘솔로 속도를 2km만큼(60km/h×60초 + 120km/h×30초) 넣어도 **0.0에서 늘지 않음** |
| 오도미터를 콘솔에서 직접 바꿈 (`/vehicle/odometer?traveled_distance_km=`) | 12.5 → `12.5`, 100 → `100.0`이 즉시 옴. **단위 km**. 0으로 되돌리는 요청은 콘솔이 거부함 |
| 오도미터 갱신 주기 | 값이 바뀔 때 즉시 + 약 4초마다 같은 값 재전송(간격 3~5초, 중앙값 4.0초) |
| 같은 구간의 `PERF_VEHICLE_SPEED` 적분 | **2.001km** (넣은 값 2km와 일치) |

- 콘솔 카탈로그상 오도미터는 `PERF_ODOMETER`(km)를 직접 쓰는 기능입니다. 에뮬레이터는 속도로 거리를 쌓지 않습니다.
- 그래서 실차에서 오도미터가 속도를 따라 늘어나는지, 소수점 몇 자리까지 오는지는 미확인입니다.

```bash
adb shell am start -n com.example.blinkerleague/.MainActivity --es probe vehicle,odometer,speed
python3 scripts/vehicle_probe_sim.py distance odometer
adb logcat -d -s VehicleTest:I SpeedTest:I ProbeInject:I > run.log
python3 scripts/probe_log_summary.py run.log --integrate   # 트립 거리 콜백마다 속도 적분(km)을 나란히 보여줌
```

## 실험 5. 비상등 상태 API (Light 도메인, Safety 경고등)

| 확인한 곳 | 결과 |
| --- | --- |
| `Vehicle.getLight()` (Light 도메인, javap) | 상향등·하향등·미등·안개등·실내등·독서등·앰비언트 상태·스위치 API만 있음. **비상등 API 없음** |
| SDK 2.3.2 전체 클래스 | "hazard" 문자열이 들어간 클래스·상수가 **없음** |
| Safety 경고등 `TelltaleType` | `TURN_SIGNAL_LEFT`, `TURN_SIGNAL_RIGHT`는 있지만 비상등 값은 없음. 상태는 `OFF/ON/BLINK` |
| 경고등 API 권한 | `getTelltalesInfo`: `CAR_SAFETY`. `checkTelltalesCapabilities`·`registerTelltales`: **`PRIVATE_CAR_SAFETY`** (에뮬레이터 보호 수준 normal) |
| 경고등 실측 | capability `READ`. 하지만 `getTelltalesInfo`와 리스너 모두 **빈 목록**(`telltales=[]`) |
| 비상등 on/off 주입 | TurnSignal(`onTurnSignalStateUpdated`)도, 경고등(`onTelltaleInfoUpdated`)도 **반응 없음**. 비상등을 켠 채 BlinkRight를 넣으면 TurnSignal만 RIGHT |

- 결론적으로 비상등은 Vehicle SDK 2.3.2로 감지할 수 없습니다(에뮬레이터 기준).
- CarProperty `HAZARD_LIGHTS_STATE`는 시험하지 않았습니다. 읽기 권한이 `CAR_EXTERIOR_LIGHTS`(signature)이면 일반 앱은 쓸 수 없지만, 이 매핑은 미확인입니다.
- 경고등 등록 권한 이름이 `PRIVATE_…`입니다. Pleos 심사 기준상 Vehicle SDK 권한은 사전 승인이 필요하므로, 이 권한이 승인될지는 미확인입니다.

```bash
adb shell am start -n com.example.blinkerleague/.MainActivity --es probe vehicle,odometer
python3 scripts/vehicle_probe_sim.py hazard   # 비상등 on/off, 깜빡이, 비상등+깜빡이
```

## 실험 6. 포그라운드 서비스 시작 경로

### Android 14 규칙 (Android Developers 문서)

**백그라운드 시작 제한**
- Android 12 이상을 대상으로 하는 앱은 백그라운드에서 포그라운드 서비스를 시작하면 `ForegroundServiceStartNotAllowedException`이 납니다.
- 예외가 몇 가지 있습니다. 화면에서 막 내려간 경우, `BOOT_COMPLETED`·`LOCKED_BOOT_COMPLETED`·`MY_PACKAGE_REPLACED` 수신, 사용자 요청에 따른 정확한 알람, 알림·위젯 조작, 배터리 최적화 해제 등입니다.

**BOOT_COMPLETED 수신기 제한**
- 수신기에서 시작할 수 없는 type을 정한 제한(`dataSync`, `mediaPlayback`, `phoneCall`, `mediaProjection` 등)은 **Android 15부터**입니다.
- `camera`·`microphone`·`location`처럼 사용 중에만 허용되는 권한이 필요한 type은 Android 14에서도 백그라운드에서 만들 수 없습니다.

**type 선언**
- Android 14부터 포그라운드 서비스마다 type을 선언하고, type별 권한을 함께 선언해야 합니다.
- 차량 신호를 계속 읽는 용도에 맞는 표준 type은 없습니다.
  - `location`은 위치 권한이 필요하고, 우리는 위치를 쓰지 않습니다.
  - `connectedDevice`는 BT·NFC·USB 같은 외부 장치용이고 런타임 조건이 있습니다.
  - `dataSync`는 Android 15부터 시간 제한과 부팅 시 시작 제한이 있습니다.
- 그래서 **`specialUse` + `FOREGROUND_SERVICE_SPECIAL_USE` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`**(용도 설명)가 해당합니다. Google Play는 이 설명을 심사합니다.

### 에뮬레이터 실측 (target SDK 34)

| 시작 경로 | 결과 | 시스템 로그(`Background started FGS`) |
| --- | --- | --- |
| 앱이 보이는 상태(Activity 실행 중)에서 시작 | 허용 | `code:PROC_STATE_TOP` |
| 앱이 뒤로 간 지 약 5초 뒤 앱 스스로 시작(내비가 앞, procState 15) | 허용 | `code:ACTIVITY_STARTER` (화면에서 막 내려간 경우의 유예) |
| 앱이 동결된 뒤(내비 앞 20초) 일반 브로드캐스트로 깨워 시작 | **거부** — `ForegroundServiceStartNotAllowedException` | `Disallowed … code:DENIED` |
| `MY_PACKAGE_REPLACED` 수신기에서 시작 (앱 재설치) | 허용 | `code:PACKAGE_REPLACED` |
| `BOOT_COMPLETED` 수신기에서 시작 (에뮬레이터 재부팅) | 허용. 부팅 완료 후 약 1초 뒤 Vehicle SDK capability 확인 성공 | `code:BOOT_COMPLETED` |
| `force-stop`된 앱의 `MY_PACKAGE_REPLACED` | **수신 안 됨** (앱이 stopped 상태) | — |

- `BOOT_COMPLETED`는 운전자 사용자(u10)에게 전달됐습니다. 수신기 프로세스는 `u10a174`로 시작됐습니다.
- **시동 시점**은 미확인입니다.
  - Pleos 문서(Suspend 모드 오디오 포커스 가이드)에 따르면 시동(IGN)을 끄면 시스템을 완전히 끄지 않고 저전력 Suspend 상태로 들어갑니다.
  - 그래서 시동을 다시 켤 때(Resume) `BOOT_COMPLETED`가 오지 않을 가능성이 높습니다. 문서에 언급은 없습니다.
  - 에뮬레이터는 user 빌드라 `cmd car_service`의 Suspend 시뮬레이션 명령이 막혀 있어(`requires non-user build`) 확인하지 못했습니다.
  - Resume을 알리는 `CarPowerManager`는 `android.car.permission.CAR_POWER` 권한이 필요한데, 이 권한은 signature|privileged|vendorPrivileged라 일반 앱은 받을 수 없습니다.

### Pleos 문서의 심사·가이드 기준

**앱 심사 가이드와 기능·성능 안정성 상세**
- 포그라운드 서비스, 백그라운드 실행, 자동 시작, `BOOT_COMPLETED`에 대한 규정은 **없습니다**.
- 관련 항목은 다음과 같습니다.
  - 백그라운드에서 돌아왔을 때 마지막 상태를 복원해야 함
  - 과점유로 시스템 성능을 떨어뜨리면 안 됨
  - **Vehicle SDK 퍼미션은 Pleos App Market과 사전 협의·승인된 경우에만 선언할 수 있고, 미승인 퍼미션을 선언하면 심사를 통과할 수 없음**
  - 운전자 주의 분산 요소

**개발 가이드 FAQ**
- 백그라운드·포그라운드 서비스·Suspend 관련 Q&A는 없습니다.
- 차량 속도는 `CarPropertyManager`로 읽으라는 안내가 있습니다.

### 재현 명령

```bash
# 앱이 뒤로 간 뒤 앱 스스로 시작 (7초 뒤, 그 사이 내비를 앞으로)
adb shell am start -n com.example.blinkerleague/.MainActivity --el fgsDelayMs 7000
# 동결된 앱을 일반 브로드캐스트로 깨워 시작 시도 (앱을 뒤로 보낸 뒤 20초 이상 지나서)
adb shell am broadcast -n com.example.blinkerleague/.service.ProbeStartReceiver -a com.example.blinkerleague.PROBE_TRIGGER
# MY_PACKAGE_REPLACED: force-stop 하지 않은 상태에서 다시 설치
./gradlew :automotive:installDebug
# BOOT_COMPLETED: 에뮬레이터 재부팅
adb reboot
# 결과
adb logcat -d -s VehicleTest:I | grep "\[Probe\]"
adb logcat -d | grep "Background started FGS"
```

## 한계와 다음에 확인할 것

- **모든 결과는 에뮬레이터 기준입니다.** 실차의 갱신 주기, 조향각 부호, 비상등 표현은 미확인입니다.
- **조향각 부호**: 콘솔 기준 오른쪽(+)이 SDK에서 음수로 옵니다. 콘솔의 + 방향 정의는 화면 그림(시계 방향 회전)으로만 판단했습니다. 실차에서 확인해야 합니다.
- **비상등**: TurnSignal로는 알 수 없고(실험 1), Light 도메인에도 API가 없으며, Safety 경고등은 에뮬레이터에서 빈 목록입니다(실험 5).
  - 실차에서 경고등에 `TURN_SIGNAL_LEFT`와 `TURN_SIGNAL_RIGHT`가 동시에 `BLINK`로 오는지는 미확인입니다.
  - CarProperty `HAZARD_LIGHTS_STATE`는 시험하지 않았습니다.
- **조향 0° 복귀 지연(50~170ms)의 원인**은 미확인입니다.
- **B의 동결**: 동결까지 약 10초가 걸렸습니다. 이 시간은 Android 14의 cached app freezer 설정에 따른 것이고, 실차 설정은 미확인입니다.
- **B의 프로세스 종료**: 동결이 풀릴 때 프로세스가 종료된 원인은 binder 버퍼 초과로 추정합니다. 종료 사유 코드(`am_proc_died ... 100,2`)의 의미는 미확인입니다.
- **포그라운드 서비스 시작 경로**(실험 6): 동결된 백그라운드에서는 시작할 수 없고, 부팅·앱 교체 수신기에서는 시작할 수 있습니다. 시동 Resume 때 앱이 깨어나는 경로는 미확인입니다.
- **트립 거리·시간**(실험 4): 에뮬레이터에서는 지원하지 않습니다. 실차 지원 여부는 미확인입니다.
- **`CAR_SPEED` 런타임 권한 요청 화면**: 차량에서 권한 대화상자가 어떻게 뜨는지 미확인입니다.
- **NaviHelper 위치**: 에뮬레이터에서는 고정값이라, 실제 위치 갱신 주기와 heading 정확도는 미확인입니다.

## 앱 설계에 주는 의미

- **백그라운드 수집은 포그라운드 서비스가 필요합니다**(스파이크 2번).
  - 일반 백그라운드에서는 약 10초 뒤 동결되어 콜백을 모두 잃습니다.
  - 동결된 상태로 10Hz 속도 구독을 유지하면 binder 버퍼가 넘쳐 프로세스가 종료됩니다.
  - 수집은 포그라운드 서비스(`TripRecorder`를 감싸는 서비스)로 만들고, 서비스가 아닐 때는 고빈도 구독을 하지 않아야 합니다.
- **조향각 부호**: `TurnConfig.steeringRightPositive` 기본값을 **`false`로 바꿨습니다**(SDK 음수 = 우회전). 실차 부호가 다르면 서버 설정으로 바꿀 수 있습니다. 시나리오 생성기도 같은 부호로 핸들 값을 만듭니다.
- **조향각은 변화가 없어도 약 3초마다 같은 값이 다시 옵니다.** 회전 감지(진입 90°, 해제 20°)는 값 기준이라 영향이 없습니다. 다만 "콜백이 왔다 = 변화"로 가정하면 안 됩니다.
- **비상등 제외 규칙**(`SteeringTurnDetector`의 HAZARD)은 TurnSignal로 감지할 수 없어 현재 동작하지 않습니다. 코드 주석(`TurnSignalState`, `SteeringTurnDetector`)과 plan.md에 그렇게 적었습니다. 출처가 생기면 HAZARD 샘플로 넣으면 되게 규칙은 남겨 뒀습니다.
- **운행 거리**는 `PERF_VEHICLE_SPEED` 적분으로 계산할 수 있습니다(에뮬레이터 2km 구간 오차 0.05%). 오도미터·트립 거리는 에뮬레이터에서 검증할 수 없어, 실차 확인 전까지 속도 적분을 기본으로 둡니다.
- **수집 서비스 시작**은 두 경로를 함께 씁니다.
  1. 앱이 보일 때(첫 실행, 사용자가 앱을 열 때) 시작합니다.
  2. `BOOT_COMPLETED` 수신기에서 시작합니다.
  - 동결된 상태에서 차량 이벤트로 깨어나 시작하는 것은 안 됩니다.
  - type은 `specialUse`이고, 서비스는 시동이 꺼질 때까지 유지합니다.
  - 시동 Resume 경로는 Pleos에 문의가 필요합니다.
- **기어는 Vehicle SDK와 CarProperty `GEAR_SELECTION` 둘 다 됩니다.** `CURRENT_GEAR`는 쓰지 않습니다.
- **속도는 CarProperty `PERF_VEHICLE_SPEED`(m/s, 약 10Hz)만 가능합니다.** `CAR_SPEED` 런타임 권한 요청 화면이 필요합니다.
- **NaviHelper 위치는 에뮬레이터에서 고정값이라 운행 거리 계산에 쓸 수 없습니다.** 거리는 `PERF_VEHICLE_SPEED` 적분이나 다른 위치 출처(FusedLocation SDK)를 검토해야 합니다.

## 결론

- **회전 판정을 조향각 기준으로 할 때 쓸 수 있는 값**: Vehicle SDK `SteeringWheel.registerSteeringWheelAngle` → `onSteeringWheelAngleUpdated(Float?)`. 단위는 도(°), 에뮬레이터 기준 오른쪽이 음수, 변화 시 즉시 + 약 3초 주기 재전송, 권한 `pleos.car.permission.CAR_DRIVE`, 포그라운드 서비스에서 등록해야 백그라운드에서도 옴. CarProperty `PERF_STEERING_ANGLE`은 signature 권한이라 쓸 수 없음.
- **운행 거리 출처**: CarPropertyManager `PERF_VEHICLE_SPEED` 적분. Vehicle SDK 트립 거리는 에뮬레이터에서 미지원이고, 오도미터는 콘솔이 직접 쓰는 값이라 속도를 따라 늘지 않아 검증할 수 없음.
- **급가감속 판정에 쓸 속도 출처**: CarPropertyManager `PERF_VEHICLE_SPEED`. 단위는 m/s, 약 10Hz, 권한 `android.car.permission.CAR_SPEED`(dangerous, 런타임 허용 필요), 포그라운드 서비스에서 구독. Vehicle SDK 2.3.2에는 속도 API가 없음.
