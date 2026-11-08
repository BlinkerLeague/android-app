# 위치 수신 조사: Pleos FusedLocation SDK (2026-11-03 ~ 11-04)

Pleos Connect 에뮬레이터에서 FusedLocation SDK로 위치를 받을 수 있는지, 갱신 주기와 백그라운드 동작, 근접 알림을 확인한 기록입니다. 같은 경로에서 NaviHelper `getCurrentLocationInfo()`와도 비교했습니다. 방식은 [차량 신호 조사](vehicle-background-speed.md)와 같습니다.

확인하지 못한 항목은 "미확인"으로 적었습니다.

## 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator, Android 14 (API 34), 운전자 사용자 u10 |
| FusedLocation SDK | `ai.pleos.playground:FusedLocation:1.1.3` (문서 Project setup 기준 버전) |
| 위치 서비스 | `ai.pleos.playground.service.location.fused` 1.0.0 (SDK 최소 요구 0.5.0) |
| NaviHelper SDK | `ai.pleos.playground:NaviHelper:3.0.5` / 내비 서비스 3.0.4 |
| 권한 | `pleos.car.permission.FUSED_LOCATION` — 기기 보호 수준 **dangerous** → 런타임 허용 필요 |

### API 확인 근거

문서(FusedLocation > Initialization and release, Location data access)와 SDK 1.1.3 클래스(javap)로 확인했습니다.

| API | 시그니처 (javap) | 권한(`@RequiresPermission`) |
| --- | --- | --- |
| 생성 | `FusedLocation(Context)` | - |
| 지원 여부 | `isSupported(): Boolean` | - |
| 초기화·해제 | `initialize()`, `release()` | - |
| 마지막 위치 | `getLastKnownLocation(FusedLocationListener)` | `FUSED_LOCATION` |
| 구독 | `registerFusedLocationCallback(FusedLocationListener)` / `unregisterFusedLocationCallback(…)` | `FUSED_LOCATION` |
| 리스너 | `FusedLocationListener.onChanged(LocationData?)`, `onError(Exception)` | |
| 근접 알림 | `addProximityAlert(ProximityAlertRequest, ProximityAlertListener)`, `removeProximityAlert(alertId: String)` | `FUSED_LOCATION` |
| 근접 리스너 | `ProximityAlertListener.onRegistered(alertId: String)`, `onTransition(ProximityAlertEvent)`, `onError(Exception)` | |
| 차단 안내 | `showLocationAccessBlockedPopup()` | 이번에 시험 안 함 |

- `LocationData`(javap): `latitude: Double`, `longitude: Double`, `altitude: Double`, `heading: Float`, `confidence: Float`, `timestampMillis: Long`. **속도 필드는 없습니다.**
- `ProximityAlertRequest(latitude: Double, longitude: Double, radiusMeters: Float, transitionTypes: Set<ProximityTransition>)` → **원형 영역(중심 + 반경)** 입니다. 다각형을 받는 API는 없습니다.
  - `ProximityTransition`은 `ENTER`, `EXIT` 두 가지입니다.
  - 검증 규칙(클래스 문자열): 위도 -90~90, 경도 -180~180, `radiusMeters must be greater than 0`, `transitionTypes must not be empty`. **최대 반경과 등록 개수 한도는 미확인**입니다.
- `ProximityAlertEvent`: `alertId: String`, `transition: ProximityTransition`
- SDK manifest가 `queries`(위치 서비스 패키지)와 `…FUSED_LOCATION_BINDING` 권한을 자동으로 합칩니다.

## 측정 방법

**앱 쪽: `FusedLocationProbe`** (`data/location`, 디버그 빌드 전용 조사 코드)
- 로그는 Logcat `LocationTest`의 `[Fused]` 출처로 남깁니다.
- 시작하면 `isSupported`·`initialize`를 기록하고, `getLastKnownLocation`을 바로 1번 + 5초마다 부르고, `registerFusedLocationCallback`을 등록합니다.
- 근접 알림은 경로 중간점(37.55589, 126.93501), 반경 100m, `ENTER`+`EXIT`로 등록했습니다.
- 같은 프로세스에서 NaviHelper `getCurrentLocationInfo()`를 5초마다 폴링합니다(Logcat `NaviTest`).

**위치 주입: `scripts/location_route_sim.py`**
- 공개 OSRM 도로 경로를 30km/h 간격(약 8.3m)으로 나눠, `adb emu geo fix 경도 위도 고도`로 **1초마다** 넣습니다.
- 고도는 20m에서 60m로 서서히 올립니다.
- 출발 전·도착 후 5초는 정지합니다.
- 기어 D와 속도 30km/h도 Virtual Console로 함께 넣습니다.
- 주입 값은 Logcat `ProbeInject`에 `geo=위도,경도,고도`로 남깁니다.

**경로**: 신촌역 남쪽 **마포구 노고산동**(37.5535, 126.9373) → 신촌로를 건너 **서대문구 창천동**(37.5580, 126.9330), 약 750m·91초.
- 처음에는 연세대 정문까지 가려 했지만, 연세로가 차량 통행 제한 구역이라 OSRM이 2km 넘게 우회해서 목적지를 바꿨습니다.
- **경계를 넘는 정확한 지점(시각)은 미확인**입니다. FusedLocation에는 주소가 없고, NaviHelper 주소는 아래처럼 바뀌지 않았기 때문입니다.

**요약: `scripts/probe_log_summary.py --compare-location`**
- 주입마다 같은 좌표(±2e-6°)의 `onChanged`가 처음 온 시각까지를 반영 지연으로 계산합니다(시계 오차 ±수십 ms).
- Navi 폴링 시각마다 직전 주입·Fused 값과의 거리(m)와 주소를 나란히 보여줍니다.

### 재현 명령

```bash
./gradlew :automotive:installDebug
adb shell pm grant --user current com.example.blinkerleague pleos.car.permission.FUSED_LOCATION
adb emu geo fix 126.9373 37.5535 20      # 출발점
adb logcat -c; adb logcat -b events -c

# A) 앱 포그라운드 (위치 + 내비 폴링 5초 + 근접 알림)
adb shell am start -n com.example.blinkerleague/.MainActivity --es probe location,navi --el naviPollMs 5000 \
  --ed proxLat 37.55589 --ed proxLng 126.93501 --ef proxRadius 100
python3 scripts/location_route_sim.py

# B) 일반 백그라운드: 위와 같이 시작 → 하단 독에서 내비 아이콘 → 20초 대기 → 주입
# B2) 동결되는 백그라운드: 시작 → 내비 → 전화 → 내비 (우리 앱이 '직전 앱'이 아니게) → 20초 대기 → 주입

# C) 포그라운드 서비스(specialUse) + 내비 앞
adb shell am start -n com.example.blinkerleague/.MainActivity
adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService \
  -a com.example.blinkerleague.PROBE_START --es targets location,navi --el naviPollMs 5000
adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService \
  -a com.example.blinkerleague.LOCATION_PROXIMITY --ed lat 37.55589 --ed lng 126.93501 --ef radius 100
#   → 내비 → 전화 → 내비, 20초 대기
python3 scripts/location_route_sim.py

# 요약
adb logcat -d -s LocationTest:I NaviTest:I ProbeInject:I > run.log
python3 scripts/probe_log_summary.py run.log --compare-location
adb logcat -d -b events | grep -E "am_freeze|am_unfreeze|am_proc_died" | grep blinkerleague
```

## 결과 1. 지원 여부와 권한

| 상황 | 결과 |
| --- | --- |
| `isSupported()` | `true` |
| 권한을 허용하지 않음 | `initialize()`는 성공. 하지만 `getLastKnownLocation`·`registerFusedLocationCallback`·`addProximityAlert`가 **바로 `SecurityException: need Permission pleos.car.permission.FUSED_LOCATION`** 을 던짐 (`onError`가 아니라 호출 자체에서 예외) |
| `pm grant --user current …FUSED_LOCATION` 후 | 모두 동작 |

## 결과 2. 위치값이 주입을 따라오는지 (A: 앱 포그라운드)

| 항목 | 결과 |
| --- | --- |
| 위도·경도 | 주입 좌표를 그대로 따라옴. 주입 96회 중 94회가 같은 좌표로 옴, **반영 지연 중앙값 12ms**(최소 -6ms, 최대 1,116ms). 직전 주입과의 거리 0.0~0.2m |
| 고도 | 주입한 고도(20 → 60m)를 그대로 따라옴 |
| heading | **항상 0.0** (진행 방향이 바뀌는 경로에서도). 에뮬레이터 GPS에 방위가 없어서로 보이며, 실차 값은 미확인 |
| confidence | 항상 5.0 (에뮬레이터 GPS 정확도 값으로 추정) |
| timestampMillis | 위치를 잡은 시각(epoch ms). 콜백 시각보다 4~11ms 앞섬 |
| `onChanged` 주기 | **약 1초**(113회, 간격 중앙값 1.00초, 최대 1.78초). 주입을 멈춘 뒤에도 마지막 좌표로 1초마다 계속 옴 |
| `getLastKnownLocation` | 부를 때마다 콜백으로 최신 위치를 줌(5초 폴링 23회 모두). 처음 시작할 때도 null이 아니었음(직전 GPS 값이 있었기 때문) |

## 결과 3. 앱 상태별 비교

| | A) 앱 포그라운드 | B) 일반 백그라운드 — 우리 앱이 직전 앱 | B2) 일반 백그라운드 — 다른 앱을 거침 | C) specialUse 포그라운드 서비스 + 내비 앞 |
| --- | --- | --- | --- | --- |
| 프로세스 | TOP (procState 2) | `LAST`(직전 앱) procState 15, oom adj 700, cpuset background, **동결 안 됨** | procState 16(cached), **다른 앱으로 넘어간 지 약 11초 뒤 `am_freeze`** | procState 4, 동결 안 됨 |
| `onChanged` (주입 중) | 1초마다, 반영 지연 중앙 **12ms**, 94/96 | 1초마다(간격 최대 2초), 반영 지연 중앙 **548ms**, 84/96 | 동결 이후 **0회** | 1초마다, 반영 지연 중앙 **37ms**, 89/96 |
| 근접 알림 ENTER/EXIT | 둘 다 옴 | 둘 다 옴 | 동결 동안 안 옴 | 둘 다 옴 |
| Navi 폴링 | 5초마다 응답 | 5초마다 응답 | 동결 이후 0회 | 5초마다 응답 |
| 앱을 다시 앞으로 가져왔을 때 | - | - | 동결 해제 직후 1초 안에 **밀린 콜백이 한꺼번에 옴**: `onChanged` 118개(2분 전 타임스탬프부터), `ENTER`→`EXIT`, 내비 콜백 132개. 프로세스는 종료되지 않음 | - |

**"안 옴"의 의미**: 주입한 좌표와 같은 값이 한 번도 오지 않은 횟수입니다. 간격이 2초로 벌어진 구간에서 주입 1개가 건너뛰어진 것으로, 위치가 멈추지는 않았습니다.

**일반 백그라운드의 두 결과**: 우리 앱이 "직전 앱"이면 동결되지 않지만 지연이 커지고(B), 다른 앱을 하나 더 쓰면 cached로 내려가 동결됩니다(B2). 운전 중 사용자가 내비·음악·전화를 오가는 상황을 생각하면 **B2가 일반적**이라고 봐야 합니다.

**차량 신호 조사와 비교**
- CarProperty 속도(10Hz)는 동결 중에 binder 버퍼가 넘쳐 동결 해제 시 프로세스가 종료됐습니다.
- 위치(1Hz)는 2분 동안 버퍼가 넘치지 않아, 해제 시 밀린 콜백이 모두 전달됐습니다.
- 동결 시간이 더 길면 위치도 넘칠 수 있지만, 그 한계는 미확인입니다.

## 결과 4. 근접 알림 (`addProximityAlert`)

| 항목 | 결과 |
| --- | --- |
| 영역 지정 | **원형**: 중심 위도·경도 + `radiusMeters`. 다각형·사각형 API 없음 |
| 등록 | `onRegistered(alertId)`가 등록 약 50ms 뒤 옴(alertId는 UUID 문자열) |
| ENTER | 주입 궤적이 중심에서 100m 안으로 들어온 첫 주입(92m 지점) **16ms 뒤** |
| EXIT | 100m 밖으로 나간 첫 주입(108m 지점) **13ms 뒤** |
| 경계 오차 | 1초 주입 간격(약 8m) 안에서 판정됨. 히스테리시스(경계 근처 흔들림 방지)는 미확인 |
| 해제 | `removeProximityAlert(alertId)`. 해제 후 동작은 시험하지 않음 |

시군구 경계 같은 **다각형 영역은 근접 알림으로 직접 표현할 수 없습니다.**

## 결과 5. NaviHelper `getCurrentLocationInfo()`와 비교 (같은 경로)

| 항목 | FusedLocation | NaviHelper `getCurrentLocationInfo` |
| --- | --- | --- |
| 값 | 주입 좌표를 따라옴 | **항상 37.412721, 127.094681, `address="수정구 시흥동"`, heading 0.0** (주입 좌표와 20~21km 차이) |
| 응답 | 구독 1초 + get 즉시 | 호출 후 5~30ms에 `onCurrentLocationInfo` |
| 필드 | 위도·경도·고도·heading·confidence·timestamp | 위도·경도·heading·주소(구+동)·routeId·currentRouteIndex |
| 주소(행정구역) | **없음** | 있음("구 + 동" 형식). 다만 에뮬레이터에서는 고정값이라 경계를 넘어도 바뀌지 않음 |

- 마포구 → 서대문구 경계를 넘는 것을 앱이 알 수 있는 출처는 에뮬레이터에서 찾지 못했습니다.
- FusedLocation 좌표를 시군구로 바꾸는 방법(Android `Geocoder`, 서버 역지오코딩 등)은 미확인입니다.
- 원시 좌표는 서버로 보내지 않는다는 원칙이 있으므로, 앱 안에서 바꿀 방법이 필요합니다.

## 한계와 다음에 확인할 것

- **에뮬레이터 GPS 기준**입니다.
  - heading이 항상 0이고 confidence가 항상 5.0인 것은 에뮬레이터 값일 가능성이 큽니다. 실차의 heading·confidence 범위와 의미는 미확인입니다.
  - 실차 갱신 주기(1초인지), 터널·지하 주차장에서의 추정 위치 여부("Fused"가 무엇을 융합하는지)도 미확인입니다.
- **NaviHelper 위치가 고정이라 두 출처의 실제 차이**(지도 매칭 여부 등)는 비교하지 못했습니다.
- **근접 알림**: 최대 반경, 동시에 등록할 수 있는 개수, 앱이 종료된 뒤에도 유지되는지는 미확인입니다.
- **동결 중 버퍼**: 위치 콜백이 몇 분까지 넘치지 않고 쌓이는지 미확인입니다.
- **`showLocationAccessBlockedPopup()`**: 언제 써야 하는지(사용자가 위치 접근을 막은 상태의 감지 방법 포함)는 미확인입니다.
- **경계를 넘은 정확한 지점**: 시군구 판별 수단이 없어 확인하지 못했습니다.

## 앱 설계에 주는 의미

- **위치 출처는 FusedLocation SDK**가 맞습니다.
  - NaviHelper 위치는 에뮬레이터에서 고정이고, 목적지가 없으면 푸시도 없습니다.
  - CLAUDE.md에 적었던 "실차 FusedLocation 미지원(구현 가이드)"이 Android `FusedLocationProviderClient`를 말하는지, 이 Pleos SDK를 말하는지는 **미확인**입니다. Pleos FusedLocation SDK는 에뮬레이터에서 정상 동작했고, Pleos 문서는 이 SDK를 "차량 위치 데이터를 받는 API"로 소개합니다. 실차 지원은 Pleos에 확인해야 합니다.
  - `LocationSource` 구현체를 이 SDK로 만들면 됩니다.
- **`FUSED_LOCATION`은 dangerous 권한입니다.** 첫 실행 화면에서 런타임 권한을 요청해야 합니다(`CAR_SPEED`와 함께). 허용 전에는 호출 자체가 `SecurityException`이므로 감싸서 처리해야 합니다.
- **속도는 `LocationData`에 없습니다.** 저속 판정·급가감속은 CarProperty `PERF_VEHICLE_SPEED`를 씁니다.
- **heading은 에뮬레이터에서 항상 0이라** `HeadingTurnDetector`(보조)는 에뮬레이터로 검증할 수 없습니다. 회전 판정은 조향각 기준을 유지합니다.
- **백그라운드 수집은 포그라운드 서비스가 필요합니다.** 차량 신호 조사와 같은 결론입니다.
  - 일반 백그라운드는 직전 앱일 때만 우연히 받고, 그때도 지연이 548ms로 커집니다.
- **동결 해제 시 밀린 위치가 한꺼번에 올 수 있습니다.** 콜백 도착 시각이 아니라 `timestampMillis`로 거리·시간을 계산해야 합니다.
- **지역 리그용 시군구 코드**: 다각형 영역 판정이나 역지오코딩이 필요합니다. 근접 알림(원형)으로는 경계를 표현할 수 없습니다. 2차 범위 항목이며 방법은 미확인입니다.
- **근접 알림**은 "특정 지점 근처에 들어옴"(예: 내비 회전 지점, 학교 앞) 같은 원형 영역 미션에는 쓸 수 있습니다. 판정 지연은 수십 ms였습니다.
