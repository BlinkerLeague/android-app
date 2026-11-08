# NaviHelper 이벤트 콜백 조사 (2026-11-03)

Pleos Connect 에뮬레이터에서 NaviHelper 이벤트 콜백이 언제, 얼마나 자주, 무엇을 주는지 확인한 기록입니다.

## 환경

| 항목 | 값 |
| --- | --- |
| SDK | `ai.pleos.playground:NaviHelper:3.0.5` (문서 기준 버전) |
| 에뮬레이터 내비 서비스 | `ai.pleos.playground.service.navi` 3.0.4 |
| 내비 앱 | `ai.umos.maps.android.navigation.app` 3.3.2-rc5-hotfix.3 |
| 권한 | `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`(목적지 설정용) |

- 콜백 26개를 모두 재정의해 Logcat `NaviTest` 태그로 남깁니다(`src/debug/…/data/navi/NaviEventLogger.kt`, 디버그 빌드 전용). 문서 목록은 25개이고, `onRouteEnded()`는 SDK 3.0.5 클래스에만 있습니다.
- 로그 한 줄에는 시각, 시작 후 경과 초, 같은 콜백의 직전 호출과의 간격(`dt`), 누적 횟수, 콜백 이름, 인자의 모든 필드(공개 getter를 리플렉션으로 펼침)가 들어갑니다.

## 측정 방법

내비 앱과 Virtual Console에는 모의 주행 기능이 없어서 아래처럼 재현했습니다.

1. **차량 상태**: Virtual Console API로 기어 D, 속도 40km/h를 넣었습니다(`PUT /vehicle/gear`, `PUT /vehicle/controls`). 콘솔은 브라우저 세션 신호(`POST /browser/session`, 3초 타임아웃)가 살아 있어야 쓰기를 받습니다. 세션 신호가 없으면 503을 돌려줍니다.
2. **위치**: 에뮬레이터 GPS를 1초마다 옮겼습니다(`adb emu geo fix`). 공개 OSRM 서버에서 받은 도로 좌표를 따라갑니다.
3. **목적지**: `requestRoute(RouteInfo(...))`로 문서 예시의 목적지(42dot, 서울 강남구)를 설정했습니다. adb로 요청을 보냅니다.
4. **로그 수신**: 앱이 백그라운드에 있어도 로그를 받도록 포그라운드 서비스(당시 `NaviLogService`, 지금은 `ProbeService`)에서 로거를 돌렸습니다. 일반 백그라운드 상태에서는 앱 프로세스가 cached 상태로 얼어서 콜백이 오지 않았습니다.

재현 명령:

```bash
# 앱 실행 후 조사용 서비스에서 내비 로거 시작 (자동 시작하지 않음)
adb shell am start -n com.example.blinkerleague/.MainActivity
adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService \
  -a com.example.blinkerleague.PROBE_START --es targets navi
adb logcat -c

# 1) 목적지 없이 주행
python3 scripts/navi_drive_sim.py --seconds 90 --speed 40 --to 37.48544722,127.03636666

# 2) 목적지 설정 후 주행
adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService \
  -a com.example.blinkerleague.NAVI_REQUEST_ROUTE --ed lat 37.48544722 --ed lng 127.03636666 --es name 42dot
sleep 12
python3 scripts/navi_drive_sim.py --seconds 150 --speed 40 --to 37.48544722,127.03636666

# 요약
adb logcat -d -s NaviTest:I | python3 scripts/probe_log_summary.py
# 목적지 취소
adb shell am start-foreground-service -n com.example.blinkerleague/.service.ProbeService -a com.example.blinkerleague.NAVI_CANCEL_ROUTE
```

## 결과

### 1) 목적지 없이 주행 (90초, D·40km/h, 도로 따라 약 1km)

| 콜백 | 횟수 | 간격(초) 평균 / 최소 / 최대 | 내용 |
| --- | --- | --- | --- |
| `onCurrentRoadSpeedLimit` | 99 | 1.00 / 0.98 / 1.02 | 항상 `0` |

- 이 밖의 콜백은 오지 않았습니다.
- `onCurrentLocationInfo`, `onTBTInfo`, `onDrivingInfo`는 목적지가 없을 때 저절로 오지 않습니다.
- 주행하지 않고 P단으로 서 있을 때도 같은 결과입니다. `onCurrentRoadSpeedLimit`(0)만 1초 간격으로 옵니다.

### 2) 목적지를 설정하고 경로 안내 중 주행 (150초, D·40km/h, 도로 따라 약 1.7km)

경로 요청 후 **약 5.8초 뒤 콜백이 1초 안에 몰려서 한 번씩** 왔습니다.

| 요청 후 시각 | 콜백 | 횟수 | 내용 |
| --- | --- | --- | --- |
| +5.79s | `onRouteStarted` | 1 | `RouteStartInfo{distance=12000, duration=1260, name="42dot", remainDestinationChargingValue=75774, tollFare=0}` |
| +5.79s | `onRouteEnded` | 1 | (인자 없음) `onRouteStarted`와 같은 ms에 옴 |
| +5.79s | `onNavigationStatus` | 1 | `ROUTE_DRIVE` |
| +5.85s | `onRouteStateInfo` | 1 | `naviMode=ROUTE_GUIDING`, `routeOption=RECOMMENDED`, 구간별 혼잡 정보(`trafficStatus.congestionItemList`: 도로명, 평균속도, offset 등) |
| +6.72s | `onDrivingInfo` | 1 | 목적지까지 거리·시간·도착 예상 배터리, 경유지 목록 |
| +6.77s | `onTBTInfo` | 1 | **경로 전체의 회전 목록**(아래) |
| 계속 | `onCurrentRoadSpeedLimit` | 172 | 1초 간격, 항상 `0` |

`onTBTInfo`는 다음 회전 하나가 아니라 **도착까지의 회전 전체를 순서대로** 줍니다. `distance`는 앞 회전에서 다음 회전까지의 거리로 보입니다.

```
[TBTInfo{description="기업지원허브 방면", distance=80.0, type=TURN_LEFT},
 TBTInfo{description="성남시청 방면", distance=390.0, type=TURN_LEFT},
 TBTInfo{description="분당내곡고속화도로 방면", distance=1100.0, type=TURN_LEFT},
 TBTInfo{description="남부순환로,성수대교 방면", distance=7400.0, type=TURN_ENTER_UNDER_L},
 TBTInfo{description="매봉역 방면", distance=1800.0, type=TURN_LEFT},
 TBTInfo{description="남부순환로359길", distance=1100.0, type=TURN_RIGHT},
 TBTInfo{description="강남대로42길", distance=90.0, type=TURN_LEFT},
 TBTInfo{description="이름 없는 도로", distance=20.0, type=TURN_LEFT},
 TBTInfo{description="이름 없는 도로", distance=20.0, type=TURN_RIGHT},
 TBTInfo{description="도착", distance=20.0, type=TURN_ARRIVE}]
```

- **주행하는 150초 동안 TBT, DrivingInfo, RouteStateInfo는 다시 오지 않았습니다.**
- 같은 시간 동안 내비 화면의 차 위치, "80m 좌회전" 안내, 도착 예정 시각도 바뀌지 않았습니다. 아래 "한계"를 참고하세요.

### 참고: 네트워크가 없을 때 목적지 설정

에뮬레이터가 Wi-Fi에 연결되지 않은 상태에서 `requestRoute`를 보내면 약 3.4초 뒤 아래 순서로 콜백이 오고 경로 탐색이 실패합니다.

1. `onRouteCancelled`
2. `onNavigationStatus FREE_DRIVE`
3. `onDestinationInfo`(빈 값)
4. `onWayPointInfo []`
5. `onRouteStateInfo`
6. `onError CAUSE_SEARCH_ROUTE`

경로 탐색에는 서버가 필요합니다. 에뮬레이터 Wi-Fi를 연결하는 경로는 설정 → 연결 → Wi-Fi → `AndroidWifi`입니다.

## 한계와 다음에 확인할 것

- **에뮬레이터 내비는 주입한 GPS를 따라 경로를 진행하지 않았습니다.**
  - 내비 앱은 GPS를 구독하고 있습니다. `dumpsys location` 기준으로 10초 간격, 10m 이상 이동 시 받고, 실제로 받은 횟수도 늘었습니다.
  - 그런데도 경로 안내 화면의 위치와 남은 거리는 그대로였습니다.
  - 그래서 "안내 중 TBT가 몇 초마다 갱신되는지"는 이 환경에서 측정하지 못했습니다. 실차 또는 Pleos 측 시뮬레이션 방법이 필요합니다(설명회·문의).
- **`onRoutingCompleted`는 한 번도 오지 않았습니다.** 대신 `onRouteStarted` 직후 같은 ms에 `onRouteEnded`가 왔습니다. 문서 설명(경로 탐색 시작 → 완료)과 순서가 다르니, 어떤 콜백으로 "경로 준비 완료"를 판단할지 정해야 합니다.
- **`onCurrentRoadSpeedLimit`는 항상 0입니다.** 에뮬레이터 지도에 제한속도 정보가 없거나 위치가 진행되지 않아서로 추정합니다.
- **`onCurrentLocationInfo`는 푸시로 오지 않았습니다.** 현재 위치·heading이 필요하면 `getCurrentLocationInfo()`를 주기적으로 불러서 받아야 할 것으로 보입니다(문서: get API의 결과는 대응 콜백으로 옴). 아직 시험하지 않았습니다.

## 앱 설계에 주는 의미

- **TBT 미리 켜기(2차)**: 경로 시작 시 회전 목록 전체를 받을 수 있습니다. 다만 주행 중 남은 거리가 갱신되는지는 실차 확인이 필요합니다.
- **위치(운행 거리, 저속 판단)**: 목적지가 없으면 내비 콜백으로 위치가 오지 않습니다. `getCurrentLocationInfo()` 폴링이나 Pleos fused location 서비스를 따로 검토해야 합니다.
- **백그라운드 수신(스파이크 2번)**: 포그라운드 서비스면 내비 앱이 앞에 있어도 콜백을 계속 받습니다. 일반 백그라운드에서는 cached 상태로 얼어서 받지 못합니다.
