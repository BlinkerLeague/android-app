# 플랫폼 기능 확인표 (한 장 요약)

> 깜빡이리그가 쓰는 차량 기능이 **어떤 API로, 어떤 권한으로, 어떤 값으로** 오는지 정리한 표입니다.
> Pleos Connect 에뮬레이터(Android 14)에서 직접 측정했습니다. 실차에서는 다를 수 있습니다.
> 자세한 측정 과정은 맨 아래 "자세히 보기" 문서에 있습니다. (2026-11-05 기준)

**표 읽는 법**
- ✅ 됨
- ⚠️ 되지만 조건이 있음
- ❌ 안 됨
- ❓ 아직 확인 못 함

**권한 표기**
- *일반*: Manifest에 선언만 하면 됩니다.
- *런타임*: 사용자 허용이 필요합니다.
- *시스템*: 일반 앱은 받을 수 없습니다.

## 1. 기능별 확인 결과

| 기능 | 결과 | 쓸 API | 권한 | 값의 모양 | 주의할 점 |
| --- | :-: | --- | --- | --- | --- |
| **깜빡이** (방향지시등) | ✅ | Vehicle SDK `TurnSignal.registerTurnSignalState` → `onTurnSignalStateUpdated` | `pleos.car.permission.CAR_LIGHTS` (일반) | `OFF` / `LEFT` / `RIGHT`. 바뀌면 즉시 | 좌·우는 구분되지만 **비상등은 알 수 없음** |
| **핸들 각도** (조향각) | ✅ | Vehicle SDK `SteeringWheel.registerSteeringWheelAngle` → `onSteeringWheelAngleUpdated` | `pleos.car.permission.CAR_DRIVE` (일반) | 도(°). **오른쪽이 음수**(에뮬레이터). 바뀌면 즉시 + 변화 없어도 약 3초마다 | 실차 부호 ❓. 앱 설정 `steeringRightPositive=false` |
| **기어** (P·R·N·D) | ✅ | Vehicle SDK `Gear.registerGearSelection` → `onSelected` 또는 CarProperty `GEAR_SELECTION` | `CAR_DRIVE` (일반) / `android.car.permission.CAR_POWERTRAIN` (일반) | `GEAR_PARK` 등 / 숫자 P=4, R=2, N=1, D=8. 즉시 | CarProperty `CURRENT_GEAR`는 값이 안 바뀜 |
| **속도** | ✅ | CarPropertyManager `PERF_VEHICLE_SPEED` | `android.car.permission.CAR_SPEED` (**런타임**) | **m/s** (×3.6 = km/h). 약 10번/초 | Vehicle SDK에는 속도 API가 없음 |
| **운행 거리** | ⚠️ | 속도(`PERF_VEHICLE_SPEED`)를 시간으로 적분 | 속도와 같음 | km. 2km 구간 오차 0.05% | 트립 거리·시간 API는 에뮬레이터 ❌, 오도미터는 속도를 따라 늘지 않아 검증 ❓ |
| **비상등** | ❌ | 없음 (TurnSignal·Light·경고등 모두 확인) | - | - | 비상등 제외 규칙은 지금 동작하지 않음 |
| **위치** | ✅ | Pleos FusedLocation SDK `registerFusedLocationCallback` → `onChanged` | `pleos.car.permission.FUSED_LOCATION` (**런타임**) | 위도·경도·고도·heading·신뢰도·시각. 약 1번/초 | heading은 에뮬레이터에서 항상 0. **속도·주소 없음** |
| **지역 진입·이탈 알림** | ⚠️ | FusedLocation `addProximityAlert` → `onTransition` | `FUSED_LOCATION` | `ENTER` / `EXIT`. 경계를 넘으면 수십 ms 안에 옴 | **원형(중심+반경)만** 가능. 시군구 경계 같은 다각형은 ❌ |
| **내비 경로·다음 회전** (TBT) | ⚠️ | NaviHelper `onTBTInfo`, `onRouteStarted` | `pleos.car.permission.NAVI_ROUTE` (목적지 설정은 `NAVI_ROUTE_SEARCH`) | 경로 시작 때 **도착까지의 회전 목록 전체**가 한 번에 옴 | 목적지가 없으면 안 옴. 안내 중 갱신 주기 ❓. 네트워크 필요 |
| **내비 현재 위치** | ❌ | NaviHelper `getCurrentLocationInfo` | `NAVI_ROUTE` | 주소("구 동") 포함 | 에뮬레이터에서는 **고정값**이라 쓸 수 없음 |
| **음성 안내** (TTS) | ✅ | Pleos TextToSpeech SDK, `Mode.ON_DEVICE`, 언어 `ko-KR` | `pleos.car.permission.TTS_SERVICE` (일반) | 문장 재생 후 완료 콜백 | Android 기본 TTS는 에뮬레이터에서 ❌. 언어를 `ko`로만 주면 거부됨 |
| **시동 켜짐·꺼짐 감지** | ❓ | (DrivingMode 등 시험 안 함) | - | - | 지금은 기어 P/D로 운행을 구분함 |

## 2. 앱이 화면에 없을 때도 신호가 오는가

| 앱 상태 | 결과 | 설명 |
| --- | :-: | --- |
| 앱이 화면에 있음 | ✅ | 모든 신호가 즉시 옴 |
| **포그라운드 서비스**(`specialUse`)로 실행 중 | ✅ | 내비가 앞에 있어도 즉시 옴 → **운행 수집은 이 방식으로** |
| 일반 백그라운드 (다른 앱을 씀) | ❌ | 약 10초 뒤 앱이 **얼어서(동결)** 신호를 전부 못 받음. 다시 열면 밀린 신호가 몰려오거나, 속도처럼 잦은 신호는 앱이 종료됨 |
| 일반 백그라운드 (우리 앱이 바로 직전 앱) | ⚠️ | 얼지 않지만 지연이 0.5초 이상으로 늘어남. 믿고 쓰면 안 됨 |

## 3. 포그라운드 서비스를 언제 시작할 수 있는가 (Android 14)

| 시작 시점 | 결과 |
| --- | :-: |
| 앱 화면이 보일 때 | ✅ |
| 앱 화면에서 막 나간 직후 (몇 초) | ✅ |
| 기기 부팅 직후 (`BOOT_COMPLETED`) | ✅ |
| 앱 업데이트 직후 (`MY_PACKAGE_REPLACED`) | ✅ (사용자가 강제 종료한 앱은 ❌) |
| 앱이 얼어 있는 상태에서 이벤트로 깨어났을 때 | ❌ |
| 시동을 다시 켤 때 (Suspend에서 깨어날 때) | ❓ (Pleos 문의 필요) |

## 4. 처음 하는 사람이 자주 막히는 곳

1. **런타임 권한은 사용자별로 줘야 합니다.** 에뮬레이터는 사용자가 둘(시스템 u0, 운전자 u10)입니다.
   `adb shell pm grant --user current com.example.blinkerleague android.car.permission.CAR_SPEED`
   (`FUSED_LOCATION`도 같은 방식)
2. **Virtual Console**(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088)은 브라우저를 켜 둬야 값이 바뀝니다. 스크립트로 쓸 때는 1초마다 세션 신호(`POST /browser/session`)를 보내야 하는데, `scripts/vconsole.py`가 해 줍니다.
3. **에뮬레이터 Wi-Fi**가 꺼져 있으면 내비 경로 탐색이 실패합니다. 설정 → 연결 → Wi-Fi → `AndroidWifi`를 눌러 연결합니다.
4. **위치 주입**은 `adb emu geo fix 경도 위도 고도`입니다. 경도가 먼저입니다.
5. **SDK API 이름은 추측하지 마세요.** 문서에 없으면 SDK 파일을 받아 `javap`로 확인합니다. 권한도 `@RequiresPermission`에 적혀 있습니다.
6. **권한은 쓰는 것만 선언**: Pleos 심사는 Vehicle SDK 권한을 **사전 승인된 것만** 허용합니다.
   - 조사용 코드와 권한(`PRIVATE_CAR_SAFETY`, `NAVI_ROUTE` 등)은 `automotive/src/debug`에만 있어 릴리스 APK에는 들어가지 않습니다.
   - 새 권한을 추가할 때는 `src/main/AndroidManifest.xml`에 넣을지(실제 기능), `src/debug`에 넣을지(조사) 구분하세요.

## 5. 아직 확인 못 한 것 (실차·Pleos 문의 필요)

- 실차의 조향각 부호, 위치 heading, 각 신호의 갱신 주기
- 시동 Resume 때 앱을 깨우는 방법
- 실차에서 트립 거리·오도미터가 속도를 따라 늘어나는지
- 구현 가이드의 "실차 FusedLocation 미지원"이 무엇을 말하는지
- 안내 중 TBT 남은 거리가 몇 초마다 갱신되는지
- 비상등 상태를 읽을 다른 방법

## 자세히 보기

- [차량 신호: 백그라운드 수신·속도·오도미터·비상등·서비스 시작 경로](spikes/vehicle-background-speed.md)
- [위치: FusedLocation·근접 알림·NaviHelper 위치 비교](spikes/fused-location.md)
- [내비: NaviHelper 이벤트 콜백](spikes/navi-helper-events.md)
- [Vehicle SDK 전 도메인 실행 기록 (팀원 조사, 21개 도메인)](sdk-test-log.md)
- 측정 스크립트: `scripts/` (`vehicle_probe_sim.py`, `location_route_sim.py`, `navi_drive_sim.py`, `probe_log_summary.py`)
