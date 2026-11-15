# Pleos 문의 사항 (깜빡이리그)

> 깜빡이리그 앱 팀이 Pleos(포티투닷)에 묻고 싶은 내용입니다. 에뮬레이터(Pleos Connect v2.1.0, API 34)에서 직접 확인한 결과를 함께 적었습니다.
> 작성: 2026-11-08. 근거 문서: [`platform-capabilities.md`](platform-capabilities.md), [`spikes/`](spikes/), [`sdk-test-log.md`](sdk-test-log.md)

## 앱 소개 (한 줄)

차량 신호(방향지시등·핸들 각도·기어·속도)로 회전할 때 깜빡이를 제때 켰는지 운행마다 판정하고, 정차 후 점수·퀘스트로 보여주는 Android Automotive 앱입니다. 주행 중에는 화면을 띄우지 않고, 음성은 시동 때 1회·운행 종료 때 1회만 냅니다.

## 요약

| # | 질문 | 분류 | 1차 제출(11.15) 영향 |
| --- | --- | --- | --- |
| 1 | 아래 권한을 쓰는 앱이 심사를 통과할 수 있는지 | 심사 | **높음** |
| 2 | 운행 기록용 포그라운드 서비스(`specialUse`)와 부팅 시 자동 시작이 허용되는지 | 심사 | **높음** |
| 3 | 시동을 다시 켤 때(Suspend → Resume) 앱 서비스가 이어지는지, 깨우는 방법 | 실차 동작 | **높음** |
| 4 | 실차 핸들 각도의 부호(오른쪽이 음수인지) | 실차 값 | **높음** (회전 방향 판정) |
| 5 | 실차에서 FusedLocation 지원 여부 | 실차 값 | 중간 |
| 6 | 비상등 상태를 읽는 방법 | SDK | 중간 |
| 7 | 시동 상태를 앱이 아는 방법 | SDK | 중간 |
| 8 | 음성 안내의 오디오 용도·포커스 권장 방식 | 심사·실차 | 중간 |
| 9 | 실차 각 신호의 갱신 주기 | 실차 값 | 낮음 |
| 10 | 내비(NaviHelper) 안내 중 TBT 갱신 주기, 에뮬레이터 모의 주행 방법 | SDK | 낮음 (2차) |
| 11 | 트립 거리·오도미터, 안전벨트·경고등 실차 동작 | 실차 값 | 낮음 (2차) |

---

## 1. 권한 사전 승인 (심사)

**배경**: Pleos 심사는 사전 승인되지 않은 Vehicle SDK 권한을 선언하면 통과할 수 없다고 안내받았습니다. 릴리스 APK에는 실제로 쓰는 권한만 넣었습니다(조사용 권한은 디버그 빌드에만 있음).

| 권한 | 용도 |
| --- | --- |
| `pleos.car.permission.CAR_LIGHTS` | Vehicle SDK `TurnSignal.registerTurnSignalState` (방향지시등), `Light.registerLowBeamLightState` (하향등, 야간 전조등 판정) |
| `pleos.car.permission.CAR_DRIVE` | Vehicle SDK `SteeringWheel.registerSteeringWheelAngle`(핸들 각도), `Gear.registerGearSelection`(기어), `Brake.registerBrakePedalStatus`(브레이크 페달, 급감속 판정) |
| `pleos.car.permission.CAR_SEATS` | Vehicle SDK `Seat.getCurrentSeatBeltBuckledState`(운전석 안전벨트 착용, 주기 조회) |
| `pleos.car.permission.CAR_SAFETY` | Vehicle SDK `Safety.getTelltalesInfo`(경고등 목록, 주기 조회). 등록 API의 `PRIVATE_CAR_SAFETY`는 선언하지 않음 |
| `pleos.car.permission.CAR_WINDOWS` | Vehicle SDK `Wiper.registerWiperState`(앞 유리 와이퍼 작동 상태, 우천 구간 판정) |
| `pleos.car.permission.FUSED_LOCATION` | Pleos FusedLocation SDK (런타임 권한) |
| `pleos.car.permission.TTS_SERVICE` | Pleos TextToSpeech SDK (음성 안내) |
| `android.car.permission.CAR_SPEED` | `CarPropertyManager` `PERF_VEHICLE_SPEED` (속도, 런타임 권한). Vehicle SDK 2.3.2에 속도 API가 없어 Android 표준 API를 씀 |
| `android.permission.FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | 운행 기록 서비스 |
| `android.permission.RECEIVE_BOOT_COMPLETED` | 부팅 후 운행 기록 서비스 시작 |
| `android.permission.INTERNET` | 판정 결과(이벤트) 업로드. 원시 위치 좌표는 보내지 않음 |

**질문**
- 위 권한으로 심사를 통과할 수 있나요? 사전 승인 신청이 필요한 권한과 절차를 알려 주세요.
- Vehicle SDK에 속도 API가 없어 `android.car` `CarPropertyManager`(`PERF_VEHICLE_SPEED`)를 쓰는데, 이 방식이 허용되나요? 권장하는 속도 출처가 따로 있나요?

## 2. 포그라운드 서비스와 부팅 시 시작 (심사)

**배경 (에뮬레이터 실측)**
- 앱이 일반 백그라운드에 있으면 약 10초 뒤 프로세스가 얼어(동결) Vehicle SDK·CarProperty 콜백을 하나도 받지 못합니다.
- 포그라운드 서비스(`foregroundServiceType="specialUse"`) 안에서 구독하면 내비를 앞에 둔 채로도 모든 신호가 즉시 옵니다.
- 서비스는 앱 화면이 보일 때, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`에서 시작합니다. 알림은 "깜빡이리그 · 기록 중" 한 줄이고 점수는 알림으로 보여주지 않습니다.

**질문**
- 운행 기록을 위한 상시 포그라운드 서비스(`specialUse`)가 Pleos 정책상 허용되나요? 권장 서비스 타입이나 다른 방식(예: Pleos가 제공하는 백그라운드 수신 방법)이 있나요?
- 부팅 시 자동 시작(`RECEIVE_BOOT_COMPLETED`)이 허용되나요?

## 3. 시동 Resume 때 앱 서비스 (실차 동작)

**배경**: 실차는 시동을 끄면 Suspend 상태로 들어가고, 다시 켜면 부팅 없이 Resume할 수 있다고 알고 있습니다. 이때 `BOOT_COMPLETED`가 오지 않으면 운행 기록 서비스를 다시 시작할 방법이 없습니다.
- 에뮬레이터에서 Virtual Console 전원 상태(`/vehicle/power-system/simulator`, Off/Ready/On)를 바꿔 봤지만 Android는 계속 깨어 있어(`mWakefulness=Awake`) Suspend·Resume을 재현할 수 없었습니다.

**질문**
- 실차에서 시동을 끄고 켤 때 Suspend-to-RAM → Resume이 일어나나요? 그때 포그라운드 서비스는 살아 있나요, 종료되나요?
- Resume 시 앱이 받을 수 있는 브로드캐스트나 콜백(예: `CarPowerManager` 상태 리스너, Pleos 전용 이벤트)이 있나요? 일반 앱이 쓸 수 있나요?
- 에뮬레이터에서 Suspend·Resume을 재현하는 방법이 있나요?

## 4. 실차 핸들 각도 부호 (실차 값)

**배경**: 에뮬레이터에서 Virtual Console 조향을 오른쪽(+)으로 꺾으면 `onSteeringWheelAngleUpdated`는 **음수**를 줍니다. 단위는 도(°)이고, 값이 바뀔 때 즉시 + 약 3.1초마다 재전송됩니다. 앱은 이 실측에 맞춰 "음수 = 오른쪽"으로 판정하고, 서버 설정으로 바꿀 수 있게 해 두었습니다.

**질문**
- 실차에서도 오른쪽으로 꺾으면 음수인가요? 값의 범위(최대 각도)와 갱신 주기도 알려 주세요.

## 5. 실차 FusedLocation 지원 (실차 값)

**배경**: 구현 가이드에 "실차는 FusedLocation 미지원"이라고 적혀 있습니다. 에뮬레이터에서는 FusedLocation SDK 1.1.3 `isSupported()`=true이고, 주입한 좌표를 1초마다 따라옵니다(heading은 항상 0, 속도 없음). NaviHelper `getCurrentLocationInfo`는 에뮬레이터에서 고정값입니다.
- MVP의 회전 판정은 핸들 각도로 하므로 위치가 없어도 동작합니다.

**질문**
- 실차에서 FusedLocation `isSupported()`는 false인가요? "미지원"이 정확히 무엇을 뜻하나요?
- 실차에서 위치를 얻는 권장 방법(NaviHelper 등)과 갱신 주기, heading 제공 여부를 알려 주세요.

## 6. 비상등 상태 (SDK)

**배경**: `TurnSignal` 값은 `OFF`/`LEFT`/`RIGHT`뿐이라 비상등을 구분할 수 없습니다. Light 도메인에도 비상등 API가 없고, Safety 경고등(`registerTelltales`)은 에뮬레이터에서 평소 빈 목록입니다. 앱은 "비상등 켠 채 회전은 판정에서 제외"하고 싶은데 지금은 동작하지 않습니다.

**질문**
- 비상등 상태를 읽을 방법이 있나요? 실차에서 비상등을 켜면 `TurnSignal`이나 경고등(`TURN_SIGNAL_LEFT`·`RIGHT` 동시 `BLINK`)으로 어떻게 오나요?

## 7. 시동 상태 (SDK)

**배경**: Vehicle SDK 2.3.2에는 `IgnitionState` enum(`LOCK/OFF/ACC/ON/START`)은 있지만 이를 돌려주는 메서드가 없습니다. CarProperty `IGNITION_STATE`는 `CAR_POWERTRAIN` 권한이 허용된 상태에서도 에뮬레이터 속성 목록에 없습니다. 그래서 운행 종료를 "기어 P가 3분 유지"로 판단합니다.

**질문**
- 일반 앱이 시동 켜짐·꺼짐을 알 수 있는 API가 있나요? (향후 SDK 버전 포함)

## 8. 음성 안내의 오디오 용도 (심사·실차)

**배경 (에뮬레이터 실측)**: Pleos TextToSpeech SDK(`Mode.ON_DEVICE`, `ko-KR`)로 안내합니다.
- `AudioAttributes.USAGE_ASSISTANT`로 읽는 동안 내비가 회전 안내("이번 신호에서 좌회전입니다.")를 하려 하면 내비의 오디오 포커스 요청이 **거부되어 안내가 빠졌습니다.**
- 그래서 `USAGE_MEDIA`로 바꾸고, 내비 안내 재생이 시작되면 우리 음성을 `stop()`했다가 내비가 끝난 뒤 다시 읽습니다. `USAGE_ANNOUNCEMENT`는 시스템 전용이라 쓸 수 없었습니다.

**질문**
- 서드파티 앱의 짧은 음성 안내에 권장하는 오디오 용도와 포커스 방식이 있나요?
- 실차의 오디오 포커스 정책(내비 안내와 겹칠 때)이 에뮬레이터와 같은가요?

## 9. 실차 신호 갱신 주기 (실차 값)

**질문**: 실차에서 방향지시등, 핸들 각도, 기어, `PERF_VEHICLE_SPEED`(에뮬레이터 약 10Hz)가 얼마나 자주 오나요?

## 10. 내비 안내 정보 (SDK, 2차 기능)

**배경**: NaviHelper `onTBTInfo`는 경로 시작 때 회전 목록 전체가 한 번 옵니다. 에뮬레이터 내비는 주입한 GPS로 진행하지 않아 안내 중 갱신 주기를 재지 못했습니다. 제한속도·단속카메라 정보도 에뮬레이터에서는 오지 않습니다(제한속도 항상 0).

**질문**
- 안내 중 TBT(다음 회전까지 남은 거리)는 몇 초마다 갱신되나요?
- 에뮬레이터에서 내비 경로를 따라 모의 주행하는 방법이 있나요?

## 11. 기타 실차 확인 (2차 기능)

- 트립 거리·트립 시간 API가 에뮬레이터에서는 미지원이고, 오도미터는 속도를 따라 늘지 않았습니다. 실차에서는 운행 거리에 쓸 수 있나요? (지금은 속도를 시간으로 적분)
- 운전석 안전벨트 착용·해제 시 `Seat.registerSeatBeltBuckledState` 콜백이 오나요?
- 오토라이트일 때 하향등 `LightState`가 실제 점등을 따라가나요?
