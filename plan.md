# 깜빡이리그 작업 계획

> 진행하면서 계속 갱신하는 문서입니다. 완료한 항목은 체크하고, 결정한 내용은 맨 아래 "결정 기록"에 남깁니다.

- 기획서 (원본): https://claude.ai/artifact/8xWTvcr2nsR3zC57iVAYgZ
- UI 목업 (참고용, 11.08 개정): https://claude.ai/artifact/EjjhjWCfMLr6d3aFThibn9
- **화면 기준 문서: [`docs/screen-plan.md`](docs/screen-plan.md)** (목업과 다르면 이 문서를 따름)
- 구현 가이드 (10/27, 기획서 문서의 탭): SDK 사용 흐름, 핸들 각도 판정, 확인 목록
- Pleos 앱 보안 가이드 대응표: [`docs/security-checklist.md`](docs/security-checklist.md)
- Pleos 문의 사항(실차·SDK·심사): [`docs/pleos-inquiries.md`](docs/pleos-inquiries.md)
- 서버 저장소와 서버 계획: https://github.com/BlinkerLeague/server (`plan.md`)
- **서버 API 명세: [`docs/api/openapi.json`](docs/api/openapi.json)** (서버 Swagger, 11.10 기준 13개). 화면별 대응과 앱 연결 상태는 [`docs/api/README.md`](docs/api/README.md)
- 역할별 서브에이전트와 TDD 규칙: `.claude/agents/` (lead · tester · developer · reviewer)

## 일정

| 단계 | 기한 | 범위 |
| --- | --- | --- |
| MVP (1차 제출) | **11.15** | 운행 자동 구분, 회전 감지·깜빡이 판정, 운행 점수 화면, 오늘의 미션 1종, 음성 칭찬, 서버 이벤트 수집·점수 API |
| 2차 (PT) | 12.2 | 급가속·급감속, 야간 전조등, 안전 경고, 연속 기록·배지, 포인트 원장, 지역 리그, 주간 리포트 |
| 이후 | — | 차로 변경 측정(ADAS SDK), 제휴 보상, 가족·법인 관리 |

## MVP 측정 기준 (초기값, 튜닝 예정)

- **운행**: 기어가 P가 아닌 단(D·R·N)이 되면 시작하고, P단이 3분 이상 유지되면 종료합니다(종료 시각은 P단에 들어간 시각). Pleos SDK에 시동 상태 API가 없어 실제로는 기어만으로 구분하고, 시동을 알 수 있는 경우(시나리오)에만 시동 on/off도 씁니다. 거리는 차량 속도 적분이고, **1km 미만** 운행은 점수 대상에서 제외합니다(11.09 결정, 기준값 `trip.minDistanceKm`. 서버 기본값 3.0은 서버 요청 10번으로 1.0 요청).
- **회전 (핸들 각도)**: 핸들 각도 절댓값이 20°를 넘은 때부터 다시 20° 아래로 내려올 때까지를 한 번의 조향으로 보고, 그 사이 90° 이상 꺾였으면 회전입니다. 부호로 좌·우를 구분합니다(에뮬레이터 실측: SDK 음수가 오른쪽 → `steeringRightPositive=false`). 유턴은 구분하지 않습니다.
- **회전 제외**: 기어 P·R, 비상등 켜짐, (속도를 알 때) 회전 내내 5km/h 미만. ※ 비상등은 TurnSignal로 감지 불가(SDK 값 없음, Light 도메인 API 없음) → 비상등 규칙은 현재 동작하지 않음. 3초 이내 연속 회전은 같은 방향이면 합치고 좌우가 섞이면(회전교차로) 제외합니다.
- (보조) 진행 방향(heading) 기반 감지 `HeadingTurnDetector`: 10초 안에 60° 이상 변화. 위치에 heading이 있을 때 정확도 보강용으로 남겨둠, 현재 미사용
- **깜빡이 성공**: 회전 시작 5초 전부터 회전이 끝날 때까지, 같은 방향 방향지시등이 한 번이라도 켜지면 성공입니다.
- **MVP 점수**: 깜빡이 사용률 × 100입니다. 회전이 없는 운행은 "측정할 회전이 없었어요"로 표시합니다.

## 작업 목록 — 앱

### 0. 스파이크 (10.26 ~ 11.1): SDK 체크리스트
가장 먼저 확인할 항목은 1번과 2번입니다. 이 두 가지가 안 되면 앱이 성립하지 않습니다.
실제로 실행해 확인한 결과는 [`docs/sdk-test-log.md`](docs/sdk-test-log.md)에 기록합니다.
- [x] 1. TurnSignal 상태를 실시간으로 구독할 수 있는지, 좌·우가 구분되는지 — 에뮬레이터: 즉시 옴, OFF/RIGHT/LEFT 구분. 비상등은 TurnSignal 값에 없음 ([조사](docs/spikes/vehicle-background-speed.md))
- [x] 2. 앱이 백그라운드에 있을 때도 신호와 위치를 계속 받을 수 있는지 — 포그라운드 서비스로 해결(`TripCollectionService`)
  - [x] NaviHelper 콜백: 일반 백그라운드는 cached로 얼어 못 받음, 포그라운드 서비스면 받음 ([조사 기록](docs/spikes/navi-helper-events.md))
  - [x] Vehicle SDK·CarProperty도 같음: 일반 백그라운드는 약 10초 뒤 동결 → 콜백 전부 누락, 동결 해제 시 프로세스 종료. 포그라운드 서비스면 모두 즉시 옴 ([조사](docs/spikes/vehicle-background-speed.md))
- [x] 3. 주행 중 앱 실행 제한의 범위 (정차·P단 조건) — 에뮬레이터: 기어 D(속도 0 포함)면 시스템이 앱 화면을 "주행 중에는 사용할 수 없습니다"로 덮음(앱이 distraction-optimized가 아니라서). P로 돌아오면 보던 화면 복귀. 앱 가드(`DrivingGuard`)는 이와 별개로 주행 중 화면으로 바꿔 이중으로 막음. 앱을 DO로 표시하지 않음(그러면 시스템 차단이 풀려 앱 가드만 남음)
- [ ] 4. 위치: 실차는 FusedLocation 미지원(구현 가이드) → NaviHelper `getCurrentLocationInfo`의 필드(속도 포함 여부)와 갱신 주기
  - [x] 에뮬레이터: Pleos FusedLocation SDK 1.1.3으로 현재 위치 조회와 1초 갱신이 됨 (`isSupported()`=true). `adb emu geo fix`로 위치를 바꿀 수 있음. `LocationData`에 속도 필드는 없음. 권한 `pleos.car.permission.FUSED_LOCATION`(dangerous, 런타임 요청 필요)
  - [x] Pleos FusedLocation SDK 1.1.3: 에뮬레이터에서 주입 좌표를 1초마다 따라옴(지연 중앙 12ms), 고도 반영, heading 항상 0, 속도·주소 없음. 권한 `FUSED_LOCATION`은 dangerous. 근접 알림은 원형(중심+반경). NaviHelper 위치는 고정값 ([조사](docs/spikes/fused-location.md))
  - [ ] 실차에서 FusedLocation `isSupported()` 결과 (false이면 NaviHelper)
  - [ ] NaviHelper `getCurrentLocationInfo`의 필드와 갱신 주기
  - [ ] 실차 지원 여부(구현 가이드의 "FusedLocation 미지원"이 무엇을 말하는지), 실차 heading·갱신 주기
- [ ] 5. Brake API가 페달 상태를 주는지
  - [x] 에뮬레이터: `Brake.registerBrakePedalStatus` → 눌림 여부 `Boolean`만 옴 (밟은 정도 없음). 권한 `CAR_DRIVE`
- [ ] 6. Light API로 전조등·오토라이트 상태를 알 수 있는지
  - [x] 에뮬레이터: 하향등·미등·상향등 `LightState`(OFF/ON/DAYTIME_RUNNING) 조회·콜백 됨. **오토라이트 API는 없음**, 안개등 미지원. 권한 `CAR_LIGHTS`
  - [x] 비상등: Light 도메인에 API 없음, Safety 경고등은 에뮬레이터에서 빈 목록 → 감지 불가 ([조사](docs/spikes/vehicle-background-speed.md))
- [ ] 7. Safety API가 주는 경고 종류
  - [x] 에뮬레이터: 경고등(`registerTelltales`)은 켜진 것만 목록으로 옴(`TelltaleType`+`OFF/ON/BLINK`). 오일·워셔액 경고는 미지원. 비상등 API 없음
- [ ] 8. 시동·주행 상태(DrivingMode, P단)를 감지할 수 있는지
  - [x] 에뮬레이터: Vehicle SDK에 **시동 상태 API 없음**(`IgnitionState` enum만 있음). `DrivingMode`는 에코·스포츠 모드이고 미지원. P단은 Gear로 됨
  - [x] 기어: Vehicle SDK `onSelected`와 CarProperty `GEAR_SELECTION` 모두 즉시 옴. `CURRENT_GEAR`는 안 바뀜
  - [x] CarProperty `IGNITION_STATE` 읽기 권한·가능 여부 — 권한 `CAR_POWERTRAIN`(normal)은 허용되지만 에뮬레이터 속성 목록에 **없음**(등록 실패). Virtual Console 전원 상태(`/vehicle/power-system/simulator`, Off/Ready/On)는 차량 HAL 로그에만 찍히고 Android는 계속 깨어 있음(앱 서비스·프로세스 유지) → **에뮬레이터로는 시동 꺼짐·Resume을 재현할 수 없음.** 실차·Pleos 문의
- [ ] 9. 각 기능의 checkXXXCapability 결과 (앱 시작 시 로그로 출력)
  - [x] 에뮬레이터: 21개 도메인 전수 조사 (`VehicleSdkSurveyTest`). 미지원은 `onFailed`로 오는데 오류 모양이 두 가지라 클래스로 구분하면 안 됨
  - [ ] 실차에서 같은 테스트로 비교
- [x] 10. TextToSpeech 사용 조건과 내비 음성과 겹칠 때의 처리 ([조사](docs/spikes/voice-navi-focus.md))
  - [x] 에뮬레이터: Pleos TextToSpeech SDK 3.3.1 `Mode.ON_DEVICE` + `ko-KR`로 재생됨. 권한 `pleos.car.permission.TTS_SERVICE` 필요. Android 표준 TextToSpeech는 엔진(caas SpeechService) 연결 실패
  - [x] 내비 안내 음성 중일 때 오디오 포커스 동작 — `USAGE_ASSISTANT`로 말하면 내비 회전 안내가 **거부돼 빠짐**. `USAGE_MEDIA`로 바꾸고, 내비 안내가 시작되면 멈췄다가 끝난 뒤 다시 읽음. 내비가 말하는 중이면 기다림
- [ ] 11. 사용자 자산 관리·인앱 구매 API
- [x] 12. 핸들 각도의 단위·범위·부호(양수가 오른쪽인지)와 갱신 주기 → `TurnConfig.steeringRightPositive` — 에뮬레이터: 도(°), 콘솔 오른쪽(+) → SDK 음수, 변화 시 즉시 + 약 3.1초 주기 재전송. 실차 부호는 미확인
- [x] 13. 차량 속도를 받는 API (저속 조향 제외 조건) — CarProperty `PERF_VEHICLE_SPEED`(m/s, 약 10Hz), `CAR_SPEED` 런타임 권한 필요. Vehicle SDK 2.3.2에는 없음
- [ ] 14. `getTBTInfo` 응답 필드(회전 방향, 남은 거리)와 갱신 주기 (내비 미리 켜기, 2차)
  - [x] 이벤트 콜백 조사: 경로 시작 시 `onTBTInfo`로 회전 목록 전체가 1회 옴. 목적지 없으면 `onCurrentRoadSpeedLimit`(1초)만 옴 ([조사 기록](docs/spikes/navi-helper-events.md))
  - [ ] 안내 중 TBT 갱신 주기: 에뮬레이터 내비가 주입한 GPS로 진행하지 않아 미측정 → 실차 또는 Pleos 문의
  - [x] `getCurrentLocationInfo()` 폴링: 호출마다 `onCurrentLocationInfo` 옴. 에뮬레이터에선 위치 고정(수정구 시흥동), 속도 필드 없음

### 1. 프로젝트 기반 정리
- [x] Compose 설정 추가 (Kotlin 1.9.22에 맞춰 Compose Compiler 1.5.10, Compose BOM)
- [x] JVM target을 1.8에서 17로 상향
- [x] 의존성 추가: activity-compose, lifecycle-viewmodel/runtime-compose, coroutines
- [x] 의존성 추가: Room 2.6.1(KSP), kotlinx.serialization, Robolectric(테스트)
- [x] 의존성 추가: Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization 변환기 (play-services-location은 쓰지 않음, 위치는 Pleos FusedLocation)
- [ ] (선택) Version Catalog(`gradle/libs.versions.toml`) 도입
- [x] 조사용 코드·권한을 디버그 빌드 전용(`src/debug`)으로 분리. 릴리스 APK에는 실제 기능 권한(TTS_SERVICE, FUSED_LOCATION, CAR_LIGHTS, CAR_DRIVE, CAR_SPEED, 포그라운드 서비스·부팅)만 들어감. NaviHelper SDK는 `debugImplementation`
- [x] `MainActivity`를 Compose로 교체하고 Door 샘플 코드 제거
- [x] Manifest 정리: `appCategory="audio"`와 `CAR_DOORS` 권한 제거 (필요한 권한은 스파이크 결과에 따라 추가)
- [x] 패키지 구조 생성: `domain/model·config·detection`, `data/vehicle·location·scenario`. 나머지(`local`, `remote`, `service`, `voice`)는 해당 단계에서 만듦

### 2. 수집과 판정 (11.2 ~ 11.5)
- [x] `VehicleSignalSource`, `LocationSource` 인터페이스 + Fake 구현체 (시나리오 재생)
- [x] 핸들 각도 회전 감지 (`SteeringTurnDetector`) + 단위 테스트, 운행 조립을 핸들 감지로 전환
- [x] SDK 구현체 `PleosVehicleSignalSource`(방향지시등·핸들 각도·기어 + CarProperty 속도, `CapabilityReport`), 위치 구현체 `PleosLocationSource`(Pleos FusedLocation SDK). 에뮬레이터 계측 테스트 `PleosSourcesTest`
- [x] 시동 없이 운행 구분(`VehicleStateSample.ignitionOn`은 알 수 없으면 null), 속도 Flow 추가, 저속 회전 제외·운행 거리를 차량 속도로 계산
- [x] 시나리오 재생기 (`data/scenario`, 테스트와 시연에 공용): "좌회전 + 깜빡이", "우회전 + 깜빡이 없음", "회전교차로", "주차장 이동"
- [x] 운행 구분 (`TripSegmenter`) + 단위 테스트
- [x] 회전 감지 (`TurnDetector`) + 단위 테스트
- [x] 깜빡이 판정 (`SignalJudge`) + 단위 테스트
- [x] Room에 운행·이벤트 선저장, 업로드 대기열 구현 (`TripRepository`, 재전송 트리거는 3단계에서)
- [x] 끝난 운행 → `Trip` 조립 (`TripAssembler`: 회전 감지 + 깜빡이 판정 + 거리)
- [x] 운행 기록 흐름 `TripRecorder` (`service/`): 공급원 → 운행 구분 → 조립 → 로컬 저장. Android Service와 분리돼 Fake로 시연·테스트 가능
- [x] 운행 수집 서비스 `TripCollectionService`: 실제 공급원으로 `TripRecorder`를 도는 포그라운드 서비스(`specialUse`). 앱 화면이 보일 때 + 부팅·앱 업데이트(`BootReceiver`)에 시작. 운행 상태를 음성 안내로 넘기고 운행이 끝나면 업로드. 기록 상태(`AppContainer.recordingStatus`: OFF/WAITING/RECORDING)를 화면에 노출. 에뮬레이터에서 내비를 앞에 둔 채 한 운행 끝까지 확인(`scripts/vehicle_probe_sim.py trip`)
- [ ] 시동 Resume 때 서비스를 다시 깨우는 경로 (Pleos 문의) — 에뮬레이터 전원 Off/On으로는 절전이 일어나지 않아 확인 불가(11.08)

### 3. 서버 연동 (서버 feat 브랜치 순서에 맞춰 진행, ~11.8)

**로컬 서버 연동 확인 (11.08)**: 서버 develop(`12885b2`)을 내 PC에서 띄우고 앱을 `-Pblinker.serverUrl=http://10.0.2.2:8080`으로 빌드해 확인. 온보딩 등록 → 운행(3.56km, 회전 3번 중 2번 켬) → 업로드 → 서버 점수 "C · 평점 67 · 득점 2 · 실점 1", 최근 경기, 오늘 미션(`IN_PROGRESS`, 3/3·66%), 기준값 v1(`minDistanceKm` 3.0) 모두 앱 화면과 일치

서버는 `driver-register` → `threshold-config` → `trip-upload` → `trip-score` → `daily-mission` 순서로 만들어집니다. 서버 API가 나오기 전까지 앱은 Fake API로 먼저 개발합니다.
- [x] `POST /api/drivers`: 최초 실행 시 닉네임과 지역 코드(시군구 5자리)를 등록하고, 받은 토큰을 로컬에 저장. 이후 요청은 Bearer 헤더로 보냄
  - [x] `DriverRepository.register`(결과: 등록됨/닉네임 중복/입력 오류/오프라인), 토큰은 `PrefsTokenStore`(SharedPreferences), `registered` Flow로 온보딩·홈 분기. Access 만료 시 자동 재발급
  - [x] 로컬 서버로 토큰 재발급 확인(11.09): 만료된 Access로 401 → `POST /api/auth/refresh` → 새 토큰 저장·재요청 성공. Refresh까지 거부되면 토큰을 지우고 온보딩으로 돌아감
  - [x] 토큰을 Android Keystore(AES-256-GCM) 키로 암호화해 저장(서버 plan.md 7장 "Keystore 등 안전한 저장소"). 예전 평문 토큰은 처음 열 때 암호화, 복호화 실패면 재등록
  - [x] (11.09 버그) 서버가 측정하지 못한 항목을 `metrics`에 null 값으로 담기 시작해 앱이 업로드 응답을 못 읽고 운행을 REJECTED로 멈추던 문제 수정. `MetricDto` 값 선택값·`status`/`reason`/`detail` 추가, JSON `coerceInputValues`. 로컬 서버로 업로드 성공 확인
  - [x] 온보딩 화면 연결 (`ui/onboarding`). Fake 모드도 등록 정보를 저장(`auth_fake`)해 재시작 시 온보딩이 반복되지 않음
  - [ ] 지역 목록: 지금은 서울 25개 구만. 나머지 시·도는 법정동 코드 파일로 채움 (코드를 추측해 넣지 않음)
- [x] `GET /api/config/thresholds`: 앱 시작 시 내려받음. `version`이 바뀌었을 때만 갱신하고, 받지 못하면 로컬 기본값 사용 — `ConfigRepository`(저장 값 → 기본값), 서버가 주는 항목만 덮어씀(핸들 각도는 서버 요청 1·7 전까지 기본값), 다음 운행부터 적용. Fake 모드는 받지 않음
- [x] `POST /api/trips`: 운행이 끝나면 업로드하고, 실패하면 재전송 (WorkManager 검토). 응답에 담긴 점수를 바로 화면에 씀
  - [x] DTO, `TripApi` 인터페이스, 서버 규칙을 흉내 낸 `FakeTripApi`, 대기열 업로드 로직
  - [x] 재전송 트리거 `TripUploadWorker`(WorkManager): 네트워크 연결 시 실행, 대기열이 남으면 지수 백오프(30초~) 재시도, 최대 10회. 운행 종료·앱 시작 때 예약. 업로드는 한 번에 하나만(Mutex)
  - [x] HTTP 구현체 (`data/remote/http`, Retrofit)
- [x] `GET /api/trips/{id}/score`: `status`가 `SCORED`, `NO_TURN`, `EXCLUDED_SHORT` 중 무엇인지에 따라 화면 분기
  - [x] `TripApi.getScore`·`getRecentTrips`(`GET /api/trips?limit=N`), 응답 DTO에 `grade`·`summary`·`plays`·`mission`(모두 선택값), `FakeTripApi` 구현(등급 S95·A85·B70·C50, 득점=켠 회전·실점=안 켠 회전). 서버 요청 8(`plays.direction`)·9(목록 모양)
  - [x] HTTP 구현체 (`data/remote/http`, Retrofit)
- [x] `GET /api/missions/today`: 상태(`IN_PROGRESS`/`ACHIEVED`/`FAILED`)와 진행률 표시
  - [x] 서버 미션 규칙 변경(D25, 11.08) 반영: 운행 1건 = 1라운드, 그날 한 라운드라도 채우면 달성, 실패 라운드가 있어도 그날은 진행 중, 지난 날 미달성은 FAILED. FakeMissionApi·시동 음성("이번 운행에서 회전 세 번…")·화면 문구 수정
  - [x] DTO, `MissionApi`, 서버 규칙(3.6)을 흉내 낸 `FakeMissionApi`, `MissionRepository`
  - [x] HTTP 구현체 (`data/remote/http`, Retrofit)

### 4. 화면과 음성 (11.9 ~ 11.12)
- 화면 구현 계획: [`docs/screen-plan.md`](docs/screen-plan.md) (주행 안전 가드 → 홈 → 결과·타임라인 → 퀘스트 → 온보딩 순)
- [x] 테마: 다크 배경 #101317, 카드 #1B2027, 강조색 #5AA2FF, 폰트 Noto Sans KR (11.01 목업 기준)
- [x] 정차 후 운행 결과 화면: 회전 기록 목록(제때 켬 / 안 켬), 오늘 미션 진행률, 적립 포인트
  - [x] 점수·상태 안내(점수 계산 중 / 회전 없음 / 1km 미만), 깜빡이 사용 횟수, 회전 기록 목록 (`ui/summary`)
  - [x] 오늘 미션 진행률 (업로드 후 미션 조회)
  - [x] 적립 포인트 (2차): 점수 응답 `earnedPoints`(서버 요청 12번)로 "+NP 적립" / 0P면 표시 안 함(11.14)
  - [x] 11.08 SCR-02로 교체(`ui/summary` → `ui/result`): 상태별 표시, 항목별 점수(P1은 "준비 중", 미지원은 "이 차량 미지원"), SCR-02a 타임라인(`result/{tripId}/plays`)
- [x] 시나리오 재생 화면 (`ui/demo`): Fake 신호로 운행을 1·5·10배속 재생 → 결과 화면. 시연·화면 테스트용
- [x] 미션 화면 (MVP에서는 오늘의 미션 1종만) → SCR-03 퀘스트 상세 `ui/quest`로 교체 (#34)
- [x] 정차·P단 상태에서만 화면 진입 허용 — `DrivingGuard`(순수 함수) + `DrivingGuardViewModel` + 주행 중 화면(`ui/driving`), `MainActivity` 최상위에서 덮음. 디버그 우회 `--ez guardBypass true`
- [x] 음성: 시동 시 미션 안내, 운행 종료 시 결과 한마디 (`voice/`, `service/TripVoiceGuide`). 운행당 1회씩, 오디오 포커스를 받은 뒤에만 재생

### 5. 1차 제출 (11.13 ~ 11.15)
- [ ] 시연 영상: "좌회전 깜빡이 성공 → 우회전 실패 → 운행 종료 → 점수·미션 화면"
  - [x] 녹화 도구 `scripts/record_demo.py` (디버그 `--ei parkEndSec`로 P단 종료 대기 단축, 화면은 글자로 찾아 누름). 에뮬레이터에서 2분 30초 녹화 확인. 11.14: 업적(배지) 화면까지, 주행 약 2.2km(+1P 보이게, `DEMO_CRUISE_SEC` 기본 70). 11.14: Windows adb 출력 인코딩 수정(UTF-8, replace), 화면 대기 4초로 녹화 약 2분 40초. 11.14: 로컬 전용으로 전환(저장소에서 제외, `.gitignore`)
  - [ ] 편집·자막·제출 (사용자)
- [x] release APK 빌드
  - [x] 서명 설정(`local.properties`의 `blinker.release.*`, 키는 저장소에 넣지 않음), README 6절
  - [x] 출시 키 만들기·백업 완료(11.11) (사용자, 방법은 README 6절)
  - [x] release 점검(권한·디버그 기능·서명 없음 빌드)
    - 홈 "시나리오 재생" 숨김과 guardBypass 무시를 실행 화면으로 확인(11.11): 홈 UI 덤프에 "시나리오" 0건, `--ez guardBypass true`로 실행해도 주행 중에 "기록 중" 가드가 뜸
  - [x] release 로컬 서버 연결(호스트 제한 평문)
    - 실서버 연결 E2E 확인(11.11, 에뮬레이터): `assembleRelease -Pblinker.serverUrl=http://10.0.2.2:8080`, 출시 키로 서명
      - 온보딩 등록(`POST /api/drivers`)
      - 운행(좌회전 깜빡이 켬, 우회전 안 켬, 2.0km) → P단 3분 뒤 종료 → 업로드 성공
      - 서버 점수 50(C), 득점 1·실점 1, 미션 "회전 1/3"
      - 크래시 0, 앱 태그 로그 0건(W·E 포함)
  - [x] release APK 에뮬레이터 설치 확인
- [x] README의 TODO 채우기 (소개, 스크린샷 5장 `docs/screenshots`, 기술 스택, 구조, 화면 확인·출시 빌드 방법)
- [ ] 공모전 제출 양식 작성

## 목업 개정과의 차이 (2026-11-08, 참고)

화면은 **`docs/screen-plan.md`(11.05)를 기준**으로 만듭니다. 11.08에 개정된 목업과 다른 부분은 아래와 같고, 모두 screen-plan대로 합니다.

| 화면 | 개정 목업 | 따르는 것 (screen-plan) |
| --- | --- | --- |
| 테마 | 강조 `#FBB024`, 득점 초록·실점 빨강, 배경 `#0B1020` | 기존 팔레트 유지: 배경 `#101317`, 카드 `#1B2027`, 강조 `#5AA2FF`, 득점 파랑·실점 주황 (6장) |
| SCR-00a | 위치 정보 동의 화면 신설 | P0에 없음. 온보딩 4단계에서 속도 권한만 요청 (4.2) |
| SCR-00 | 측정 가능 항목: 브레이크·전조등·안전 경고 | `CapabilityReport` 기준, 미지원은 "이 차량 미지원" (4.2) |
| SCR-01 | 하단 탭 5개, 연승 "5일" | 탭 없음, 연승 숨김 (4.3, 9장 D6) → 2차(11.09)에서 하단 탭 5개(홈·리그·업적·지갑·설정)와 연승 카드(D6) 반영 |
| SCR-02 | "반칙 2", 가감속·안전 경고 점수 | 득점·실점만, P1 항목은 회색 "준비 중" (4.4, 9장) |
| SCR-02a | 급가속·급감속 카드 | 회전 카드만 (4.5) |
| SCR-03 | "+20 코인" | "+20P" (P1 표시는 #61에서 제거) |

## 남은 작업 (11.13 기준, MVP 11.15)

화면 작업 순서는 screen-plan 7장을 따릅니다.

| # | 작업 | 담당 | 선행 | 상태 |
| --- | --- | --- | --- | --- |
| 1 | 업로드 자동 재전송 (WorkManager) | 신호 | — | 완료 (#25) |
| 2 | `TripApi.getScore`·`getRecentTrips` + Fake (screen-plan 4.3·4.4 데이터) | 신호 | — | 완료 (#28) |
| 3 | 속도 권한(`CAR_SPEED`) 상태 Flow: 홈 안내 "속도 권한이 없어…"·온보딩 4단계용 | 신호 | — | 완료: `container.permissions`, `rememberPermissionRequester` |
| 4 | 주행 안전 가드 `DrivingGuard` + DRV 화면 + `MainActivity` 연결 (7장 1) | 신호(혼자 개발) | 기어·속도 Flow(완료) | 완료 (#31) |
| 5 | NavHost 재구성, SCR-01 홈 (7장 2) | 신호(혼자 개발) | 4 | 완료 (#32): 시작 화면 `home`, 퀘스트 카드·최근 경기 2건·기록 상태·안내(속도 권한/기록 꺼짐), 시나리오 재생은 디버그 빌드 홈 버튼 |
| 6 | SCR-02 확장 + SCR-02a (7장 3) | 신호(혼자 개발) | 5, 2번 | 완료 (#33): `ui/result`(등급·평점·득점실점·항목 행·퀘스트·플레이 보기), 타임라인 카드. "깜빡이 켠 시점(N초 전)"은 데이터가 없어 켬/안 켬만. 제때·늦게는 SCR-02b(#58) |
| 7 | SCR-03 퀘스트 상세 (7장 4) | 신호(혼자 개발) | 5 | 완료 (#34): `ui/quest`, 조건별 진행도(서버 `conditions`, 없으면 회전 수·사용률로), 보상 "+20P", 재도전 버튼 |
| 8 | SCR-00 온보딩 (7장 5) | 신호(혼자 개발) | 등록 API Fake, 3번 | 완료 (#35): 닉네임·지역(서울 25개 구)·측정 가능 항목·속도 권한·리그 입장. 등록 전이면 시작 화면이 온보딩 |
| 9 | HTTP 구현체 (`TripApi`·`MissionApi`·등록) | 신호 | — | 완료: Retrofit, 토큰 재발급, `DriverRepository`. 서버 주소 설정 시 사용 |
| 9b | 마무리 (7장 6): 예외 문구 점검, 실제 앱 폭 측정(1682×1210dp), 시연 리허설, lint 오류 0 | 신호(혼자 개발) | 4~8 | 완료 (11.10, feature/p0-finish): lint 오류 0(경고 16은 2차, #68 뒤 15), 예외 문구 7종 확인, 좁은 폭 Preview, 시연 결과 회귀 테스트(C · 50 · 득점 1 · 실점 1 · 카드 2장) |
| 10 | 시연 영상, release APK, README, 제출 양식 (5장) | 함께 | 4~8 | 진행 중: release APK · README 완료(#69~#72, 5장). 남은 것: 시연 영상 편집 · 제출 양식 · 보안 체크리스트 남은 일(README '해야 할 일') |
| 11 | 기획서 정합성 수정 ("현황 점검" 미구현 1~4): 용어 퀘스트 통일, SCR-02 머리말 "오늘의 N번째 경기", SCR-02b 적용 가중치, 연속 보상 주기 · 보상 포인트를 기준값 `point.streakRewardDays` · `streakRewardPoint`로 | 신호(혼자 개발) | — | 완료 (feature/plan-gap-fixes) |

- 권한 사용법(화면 쪽): 상태는 `container.permissions.status(RuntimePermission.SPEED)`(`GRANTED`/`DENIED`/`NOT_REQUESTED`), 요청은 `rememberPermissionRequester(container.permissions, RuntimePermission.SPEED)`. 허용되면 운행 수집이 재시작 없이 속도를 받기 시작함
- **주의(온보딩 문구)**: `CAR_SPEED` 시스템 요청 창은 "이 기기의 **위치 정보**에 액세스하도록 허용하시겠습니까?"로 뜸(에뮬레이터). 요청 전 앱 화면에서 "속도를 쓰기 위해 묻는 것"이라고 먼저 설명해야 함
- 11.08부터 앱 개발은 1인. 화면(4~8)도 screen-plan 7장 순서대로 진행

## 현황 점검 (2026-11-10, 기획서 · 코드 대조)

기획서 원본과 `develop`(`ad8c2c7`, #67) 코드를 대조한 결과입니다. 화면별로 나눈 표는 [`docs/screen-plan.md`](docs/screen-plan.md) 10장에 있습니다.

### 화면 구현 현황

| 구분 | 화면 | 상태 | PR |
| --- | --- | --- | --- |
| P0 | DRV 주행 중 화면 | 완료 | #31 |
| P0 | SCR-00 온보딩 | 완료 (지역은 서울 25개 구만) | #35 |
| P0 | SCR-01 홈 | 완료 (연승 카드 #54) | #32 |
| P0 | SCR-02 경기 결과 | 완료 (적립 포인트 #56) | #33 |
| P0 | SCR-02a 플레이 타임라인 | 완료 | #33 |
| P0 | SCR-03 퀘스트 상세 | 완료 (보상 P 표시 정리 #61) | #34 |
| P1 | SCR-02b 항목 상세 | 완료 | #58 |
| P1 | SCR-04 리그 | 완료 (서버 형식 #64, 자격 기준값 #66) | #60 |
| P1 | SCR-05 업적 | 완료 | #54 |
| P1 | SCR-06 지갑 | 완료 | #53 |
| P1 | SCR-07 주간 리포트 | 완료 | #55 |
| P1 | SCR-08 설정 | 완료 (순위 공개 토글 #65) | #59 |

기획서의 화면은 모두 있습니다. 남은 것은 화면 안의 일부 요소와 2차 측정 항목입니다.

### 미구현 — 지금 할 수 있음

| # | 내용 | 기획서 | 코드 위치 | 시기 | 크기 |
| --- | --- | --- | --- | --- | --- |
| 1 | 용어 "미션" → "퀘스트" 통일 ("오늘의 미션" · 일일 미션 계열: 화면 · 시동 음성 · 지갑 적립 사유 · 업적 "미션 연속 달성"). 제외: 주간 리포트 "맞춤 미션"(`strings.xml` 172 · 176, 서버 주간 미션 개념), 서버가 주는 배지 조건 문구 → server-requests 15로 요청(11.13, feature/badge-quest-wording) | 전반 | `voice/VoiceScript.kt:15-18`, `strings.xml` 64 · 70 · 72 · 73 · 152 · 154 · 155 · 219 | 완료 (11.13, feature/plan-gap-fixes): 화면 · 음성 문구만, 식별자 · 리소스 키 · 서버 API 이름은 mission 유지 | 소 |
| 2 | SCR-02 머리말 "오늘의 N번째 경기 종료" (지금 "경기 종료 · N km") | 3장 | `strings.xml:15` `result_header`, `ui/result` | 완료 (11.13, feature/plan-gap-fixes): 기기 기록으로 그날 시작 순서를 셈. 오늘이 아닌 운행 · 짧은 이동은 N 없이 | 소 |
| 3 | SCR-02b 적용 가중치 표시 (`metrics[].weight`를 받지만 `MetricDetail`에서 버림) | 5장 | `TripRepository.metricDetails`, `domain/model/MetricDetail.kt`, `MetricDetailScreen` | 완료 (11.13, feature/plan-gap-fixes): "적용 가중치 N%", 항목이 SCORED이고 가중치가 0보다 클 때만 표시, 아니면 줄 숨김 | 소 |
| 4 | 연속 보상 하드코딩 제거: 주기 `STREAK_REWARD_DAYS = 7`과 보상 문구 "+50P" → 기준값 `point.streakRewardDays` · `streakRewardPoint` (지갑 사유 "7일 연속 달성"(`strings.xml:220`)은 서버 사유 코드 `STREAK_7`의 이름표이고 과거 적립 행에 붙으므로 제외) | (코드 규칙) | `ui/achievements/AchievementsUiState.kt:19 · 32`, `ConfigDtos.kt`(`ThresholdsResponse`에 `point` 없음), `strings.xml:156` | 완료 (11.13, feature/plan-gap-fixes): `PointConfig`(기본 7일 · 50P), `STREAK_REWARD_DAYS` 삭제 | 소 |
| 5 | SCR-08 음성 안내 끄기 토글 | 5 · 10장 | `ui/settings`, `service/TripVoiceGuide` | 2차 | 중 |
| 6 | SCR-08 내 차량 측정 항목 목록 (`CapabilityReport`, 온보딩 3단계 재사용) | 2 · 5장 | `ui/settings` | 2차 | 소 |
| 7 | SCR-05 연승 7일 진행 점 | 8장 | `ui/achievements` | 2차 | 소 |
| 8 | SCR-03 연승 영향 문구 ("달성하면 연승 N일째") | 6장 | `ui/quest` | 2차 | 소 |
| 9 | VOC-02 종료 음성: "퀘스트 달성! 연승 N일째"를 먼저 말하기, 퍼펙트(모든 회전 득점) 문구 | 10장 | `VoiceScript.tripEnd` | 2차 | 중 |
| 10 | SCR-01 퀘스트 진행 링 (지금 막대, 낮음) | 6장 | `ui/quest/MissionCard.kt:65` | 2차 | 소 |
| 11 | SCR-04 육각 타일 지역 지도 (지금은 지역 안전지수 목록) | 8장 | `ui/league` `RegionsPanel` | 2차 | 대 |
| 12 | 2차 측정 판정 엔진: 급가속 · 급감속(`HARSH_*`), 야간 전조등(`NIGHT_SEGMENT`), 안전 경고(`SAFETY_WARNING`). 지금은 신호 `SignalType`(`BRAKE` · `HEADLIGHT` · `SAFETY_WARNING`)에 정의만 있고 이벤트(`TripEvent`)는 `TurnEvent`뿐이며 결과 행은 항상 "준비 중"(`ResultUiState.kt:110-112`). 서버는 이벤트 종류를 이미 받음 | 2 · 4장 | `domain/detection`, `data/vehicle`, `ScoreItem`, `ui/result` | 2차 | 완료(11.15, "2차 앱 작업" 20): 판정 · 업로드, 결과 행 값은 서버 `metrics`. 8행 확장은 21 |

### 미구현 — 막힘

| 내용 | 막힌 이유 |
| --- | --- |
| 지역 전국 확장 (지금 서울 25개 구) | 법정동 코드 자료 필요 (코드를 추측해 넣지 않음) |
| 결과 화면 적립 포인트 실서버 표시 | 서버 요청 12번(`earnedPoints`) 미반영 |
| 깜빡이 팁 문구 "30m 전" ↔ 앱 "5초 전" | 서버 요청 13번 미반영 |
| 리그 상위 지역 보상 표시 | 서버 응답에 필드 없음 |
| 위치 실차 출처, Brake · Light · Safety 실차 값, 시동 · Resume, capability 실차 비교, TBT 갱신 주기 | 실차 · Pleos 문의 (스파이크 4 · 5 · 6 · 7 · 8 · 9 · 14, "시동 Resume 경로") |
| 실차 시계 오차 (서버 미래 5분 · 과거 7일 밖이면 400 → REJECTED) | 실차 확인 (서버 요청 11번) |
| 깜빡이 4단계 판정(`signalTiming`), 2차 반칙 성격 카드 색, `CAR_SPEED` 거부 시 처리, 터치 영역 기준, 백그라운드 측정 누락 안내 | 사용자 결정 ("결정 필요") |
| 게임 요소 확장, 점수 항목 추가(확장안), DI, ktlint · detekt | 사용자 결정 (기존 "결정 필요") |

### 사용하지 않는 API · 필드

엔드포인트 13개는 모두 호출합니다(`POST /api/auth/refresh`는 401 때 `HttpClientFactory`에서 호출). 아래는 받거나 보낼 수 있는데 쓰지 않는 필드입니다.

| API | 필드 | 분류 | 이유 · 계획 |
| --- | --- | --- | --- |
| `TripResult` (업로드 · 점수) | `metrics[].weight` | 씀 | SCR-02b "적용 가중치 N%" (미구현 3, 11.13) |
| 〃 | `metrics[].value` | 안 씀 | 점수 `score`와 근거 `detail`로 표시 |
| 〃 | `grade` | 안 씀 | 앱이 `config.grades`로 계산 (결정 11.08) |
| 〃 | `turns`, `summary`(`goals` · `conceded` · `fouls`) | 안 씀 | 기기 회전 기록으로 셈. `fouls`는 "반칙" 용어를 쓰지 않아서 |
| 〃 | `plays` | 2차 검토 | SCR-02a는 기기 `TurnRecord`만 씀. 2차 항목 카드가 생길 때 서버 `plays`를 쓸지 정함 |
| 〃 | `mission` | 안 씀 | 결과 화면은 `GET /api/missions/today`로 받음 |
| 〃 | `earnedPoints` | 서버 대기 | 앱 DTO에만 있음. openapi에 없음 (서버 요청 12번) |
| `GET /api/trips` | `grade` · `endedAt` · `distanceKm` | 안 씀 | 기기 기록을 먼저 보여 주고 `status` · `score`만 갱신 (결정 11.08) |
| `GET /api/missions/today` | `missionId` · `periodKey` | 안 씀 | 화면에 필요 없음 (`rewardPoint`는 SCR-03에서 씀) |
| `GET /api/config/thresholds` | `point.streakRewardDays` · `streakRewardPoint` | 씀 | SCR-05 연속 보상 주기 · 보상 포인트 (미구현 4, 11.13) |
| 〃 | `point.dailyCap` · `tripMaxKm`, `weights`, `minSamples`, `league.commonItems` · `topN`, `harsh.penaltyPer10Km`, `safetyWarning.penaltyPer100Km` | 안 씀 | 서버 계산용. 하루 상한은 `GET /api/points`의 `dailyCap`, 가중치는 `metrics[].weight`로 받음 |
| 〃 | `harsh.deltaKmhPerSec` | 씀 | 급가속 · 급감속 판정 (2차 앱 작업 11, 11.15) |
| 〃 | `signalCancel`(forgetSec · minSpeedKmh) | 씀 | 깜빡이 끄기 판정 (2차 앱 작업 20, 11.15) |
| 〃 | `rain.minWiperSec` | 씀 | 우천 구간 판정 (2차 앱 작업 20, 11.15) |
| 〃 | `safetyWarning.mergeWindowSec` | 안 씀 | 같은 경고 묶기는 서버가 함. 앱은 경고등이 한 번 켜진 주기(켜짐 → 꺼짐)마다 주행 중이 된 첫 시각에 `SAFETY_WARNING` 1건 |
| 〃 | `turn.signalLeadDistanceM` · `lateSignalScore` | 결정 대기 | 깜빡이 4단계(`signalTiming`) 결정 뒤 |
| 〃 | `turn.turnSpeedKmh`, `seatBelt`, `rain`의 나머지(speedRatio 등), `signalCancel`의 penalty | 안 씀 | 서버 계산용. 앱은 회전 최고 속도 `maxSpeedKmh` · 벨트 시간 · 우천 구간만 보냄 |
| 〃 | `trip.maxEvents` | 씀 | 운행 한 건의 이벤트 상한(기본 2000, 0 이하 무시). 넘치면 조립 단계에서 자름 (11.15) |
| 〃 | `trip.maxFutureMinutes` · `maxPastDays` | 안 씀 | 서버 검증용. 실차 시계 확인 뒤 업로드 전 안내에 쓸지 검토 |
| 〃 | `turn.windowSec` · `minAngleDeg` · `uTurnAngleDeg` | 읽기만 함 | 진행 방향 감지(`HeadingTurnDetector`, 미사용)용 |
| `GET /api/leagues/current` | `league.safetyIndex` · `league.rank`, `rankings[].averageScore` · `points`, `season.startsAt` | 안 씀 | 내 권역 지수는 `regions` 목록에 같은 값. 상위 순위는 리그 점수 `score`만 표시 |
| `GET /api/reports/weekly` | `metrics[].lastWeek`, `lastMission` · `nextMission`의 `week` · `metric` | 안 씀 | 화면은 이번 주 값과 증감 `change`만 표시 |
| `POST /api/trips` 요청 | 이벤트 `HARSH_ACCEL` · `HARSH_BRAKE` · `NIGHT_SEGMENT` · `SAFETY_WARNING` · `SIGNAL_LEFT_ON` · `SEAT_BELT` · `RAIN_SEGMENT`, `supported`의 8개 항목 | 씀 | payload는 openapi `TripEventRequest.payload` 형식 (11.15). `supported`는 `ScoreItem.measurable`(항목마다 필요한 신호가 모두 있을 때) |
| 〃 | 이벤트 `LANE_CHANGE` | 안 씀 | 차로 변경 측정은 "이후"(ADAS SDK) |
| 〃 | `TURN` payload `signalTiming` · `leadDistanceM` | 결정 대기 | 깜빡이 4단계 결정 뒤. `maxSpeedKmh`는 보냄(11.15). `uTurn`은 알 수 없어 보내지 않음 (서버 요청 3번) |

주의: 업로드 `supported`는 `ScoreItem.measurable(CapabilityReport.supportedSignals)`로 정합니다(11.15). 항목을 `supported`에 넣을 때는 그 판정 이벤트도 함께 보내야 합니다(지원만 보내면 서버가 이벤트 0건을 위반 없음으로 채점할 수 있음). 출발 시 벨트 상태를 모르는 운행은 `SEAT_BELT`를 뺍니다.

### 의도적으로 다름

색(득점 파랑 · 실점 주황), "반칙" 용어 없음, 코인 → P, 짧은 운행 1km, 달력 월 시즌, 위치 동의 화면 없음, 오토라이트 · 비상등 API 없음. 자세한 내용은 위 "목업 개정과의 차이" 표와 결정 기록에 있습니다.

적용 중(결정 기록 없음, screen-plan 9장): 회전 0회 평점 없음, 50점 미만 등급 비움, 정차 = P단만.

## 2차 (12.2 PT) 앱 작업

11.09 결정: P0 앱 작업이 끝나 2차 화면을 시작. 서버 2차 API(포인트·연속 달성·배지·주간 리포트)는 이미 있음. 리그(4.4)는 11.10 서버 #48 · #49 형식에 맞춰 연결함(9번).

| # | 작업 | 상태 |
| --- | --- | --- |
| 1 | 데이터: `RewardApi`(`/api/points`, `/api/streak`, `/api/badges`, `/api/reports/weekly`) Http·Fake, `RewardRepository`. Fake는 서버 규칙(운행 `floor(min(km,50)×평점÷100)`, 미션 20P, 7일 연속 50P, 하루 150P, 배지 3종)을 흉내 냄 | 완료 |
| 2 | 하단 탭(홈·업적·지갑) + SCR-06 지갑(잔액, 오늘 적립/일일 상한, 원장 "거리 × 평점") | 완료: 탭은 홈·지갑(업적은 3번에서 추가) |
| 3 | SCR-05 업적(연속 달성, 배지 진열장 잠금·조건) + 홈 연승 카드(D6: P1에서 다시 보임) | 완료: 업적 탭 추가, 다음 보상까지 남은 날 |
| 4 | SCR-07 주간 리포트(항목별 전주 대비, 가장 약한 항목, 다음 주 맞춤 미션 "등록됨" 안내 — 서버 D7) | 완료: 업적 화면 "주간 리포트" 버튼, 이전 주·다음 주(지난주까지) |
| 5 | 결과 화면 적립 포인트(4장 "적립 포인트") | 완료(앱): 점수 응답 `earnedPoints`를 표시. 서버 요청 12번 반영 전에는 실서버에서 표시 안 됨, Fake는 표시 |
| 6 | SCR-02b 항목 상세(결과 항목 행·타임라인 카드에서 진입): 점수·판정 근거(서버 `metrics.detail` 제때·늦게·안 켬, 없으면 기기 기록)·회전 목록·팁(기준값 `tips`) | 완료. 서버 팁 문구 불일치는 서버 요청 13번 |
| 7 | SCR-08 설정: 내 정보(닉네임·지역), 속도 권한 다시 요청(거부가 반복되면 시스템 설정 열기), 운행 기록 상태, 앱 정보(버전·연결 모드), "리그 순위 공개" 토글(서버 D8: 기본 공개, `PUT /api/drivers/me/league-visibility`, 현재 값은 리그 조회 `me.visible`) | 완료(토글 11.10, `feature/league-visibility`) |
| 8 | SCR-04 리그(하단 탭 두 번째): 시즌·D-day, 내 리그(권역)·리그 점수·순위(10명 미만 비공개)·순위 자격 진행, 상위 순위, 지역 안전지수. 서버 4.4 미구현 → 응답 형식을 서버 요청 14번으로 제안, Fake는 나 혼자(순위 비공개) | 완료(앱). 서버 형식 연결은 9번 |
| 9 | 리그 응답을 서버 형식으로 맞춤(서버 #48 · #49, `CurrentLeagueResponse`): `league` · `me` · `regions`, 리그 점수 = 평균 + 시즌 포인트(D34) 표시, 지난 시즌 우승 칭호, 순위 목록의 내 행은 닉네임으로 찾음 | 완료(11.10, `feature/league-server-format`) |
| 10 | 리그 순위 자격(시즌 50km 또는 10회)·순위 공개 인원(10명)을 하드코딩 대신 기준값 `league`(`minSeasonDistanceKm`·`minSeasonTrips`·`minParticipants`)에서 읽음. 화면 문구와 Fake 판정 모두 | 완료(11.10, `feature/league-qualify-config`) |
| 11 | 급가속 · 급감속 판정(`HARSH_ACCEL` · `HARSH_BRAKE`): 차량 속도 시계열에서 기준값 `harsh.deltaKmhPerSec`로 판정(순수 함수 + 테스트), 이벤트 업로드, `supported`에 `SMOOTH_DRIVING`, 결과 · 항목 상세 행 연결. 속도 권한(`CAR_SPEED`)이 없으면 측정에서 제외 | 완료(판정 · 업로드 11.15, `feature/score-metrics-expansion`). 결과 행은 서버 `metrics`로 채움 |
| 12 | 야간 전조등 판정(`NIGHT_SEGMENT`): 하향등 `LightState`로 야간 구간의 전조등 꺼짐 시간 판정, `supported`에 `NIGHT_HEADLIGHT`, 결과 행 연결. 야간 판단 기준(시각 · 일몰)은 기준값으로. 오토라이트 API 없음 | 완료(11.15): 시·도 대표 좌표의 일몰 ~ 일출 |
| 13 | 안전 경고 판정(`SAFETY_WARNING`): Safety 경고등(`registerTelltales`) 켜짐을 `safetyWarning.mergeWindowSec`로 묶어 이벤트로, `supported`에 `SAFETY_WARNING`, 결과 행 연결. 실차 경고등 종류는 스파이크 7 확인 필요 | 완료(11.15): `getTelltalesInfo` 1초 폴링(등록 API는 PRIVATE 권한이라 안 씀). 실차 경고등 종류 확인 필요 |
| 14 | 11~13의 이벤트 payload 형식을 서버와 확인(`docs/server-requests.md` 새 항목, 서버 D21: 서버 반영 확인 뒤에만 보냄) | 완료: openapi `TripEventRequest.payload`에 형식이 들어와 그대로 맞춤(11.15) |
| 15 | SCR-08 음성 안내 끄기 토글 · 내 차량 측정 항목 목록 ("현황 점검" 미구현 5 · 6) | 예정 |
| 16 | 연승 표시 보강: SCR-05 7일 진행 점, SCR-03 연승 영향 문구, SCR-01 퀘스트 진행 링 (미구현 7 · 8 · 10) | 예정 |
| 17 | VOC-02 종료 음성: 퀘스트 달성 · 연승을 먼저 말하고, 퍼펙트 문구 추가 (미구현 9). 음성은 종료 시 1회 원칙 유지 | 예정 |
| 18 | SCR-04 육각 타일 지역 지도 (미구현 11) | 예정 |
| 19 | 포인트를 리워드처럼 보이게 하는 화면 문구 · 구성 재검토(지갑 탭 "내 포인트 · 오늘 적립 · 적립 한도 · 적립 내역", 퀘스트 보상 +20P, 업적 +50P). 포인트는 리그 순위 가산점으로만 쓰는 방향(11.14 결정) | 검토 |
| 20 | 점수 항목 확장(`docs/scoring-criteria.md`): `SIGNAL_CANCEL`(`SIGNAL_LEFT_ON`) · `TURN_SPEED`(TURN `maxSpeedKmh`) · `SMOOTH_DRIVING`(11) · `NIGHT_HEADLIGHT`(12) · `SEAT_BELT` · `SAFETY_WARNING`(13) · `RAIN_DRIVING`(`RAIN_SEGMENT`) 판정 · 업로드, Pleos SDK 브레이크 · 하향등 · 안전벨트 · 경고등 · 와이퍼 연결 | 완료(11.15, `feature/score-metrics-expansion`) |
| 21 | 결과 화면(SCR-02) 8행 확장: 서버 8개 항목을 행으로 모두 표시(사용자 결정). 지금은 4행, 행 값은 서버 `metrics`로 채움 | 보류: UI 수정 후 (H4~H7) |

- 리그 순위 공개 후속 (리뷰 낮음, 11.10): 공개를 끈 상태를 리그 화면에 안내 · 오프라인으로 설정을 열면 토글이 숨겨지고 다시 불러오지 않음 · 화면을 떠나면 진행 중 저장이 취소될 수 있음(다시 들어오면 서버 값으로 맞춰짐)
- 9b에서 미룬 것 (11.10): 라이브러리 버전 올리기(GradleDependency 12) · Compose BOM 올려 Compose lint 다시 켜기 · dataExtractionRules xml(기기 간 전송에서 토큰 · 설정 제외) (완료 11.10, `feature/data-extraction-rules`) · TripCollectionService API 34 미만 SPECIAL_USE 분기 · 2차 화면 5개(업적 · 리그 · 주간 리포트 · 설정 · 지갑) 좁은 폭 Preview
- 점수 항목 확장 후속 (11.15): 온보딩 측정 가능 항목에 새 신호 5개(브레이크 · 하향등 · 안전벨트 · 경고등 · 와이퍼) 표시 · 가상 콘솔의 계단식 속도 변화로 급가속이 과다 판정됨(실차 확인 필요) · 30m 4단계 `signalTiming`(결정 필요 "깜빡이 4단계 판정") · 실차 확인(벨트 변경 반영 · 경고등 종류 · 와이퍼 영역 · 운전석 `ROW_1_LEFT`) · 릴리스 빌드에서 새 권한이 허용되는지 확인
- 점수 항목 확장 리뷰 후속 (11.15, 리뷰 낮음): 급가감속 쿨다운(연속 판정 간격) · 신호 capability 확인을 병렬로 · 경고등 · 벨트 폴링 실패 재시도와 같은 값 중복 제거(`distinctUntilChanged`) · 대표 좌표가 없을 때 그 운행의 `supported`에서 `NIGHT_HEADLIGHT` 제외
- 좁은 폭(720dp) 레이아웃 (리뷰 낮음, 11.10, 실제 앱 영역 1682dp에서는 문제없음): 온보딩 측정 가능 항목 행에서 상태 문구가 항목 이름을 눌러 버림(OnboardingScreen CapabilityLine) · 결과 화면 항목 행 이름 칸 고정 width(200.dp)로 상태 문구가 여러 줄로 접힘(ResultScreen) · NO_TURN · EXCLUDED_SHORT 좁은 폭 Preview

## 서버와 맞춰야 할 항목
서버 `plan.md` 7장과 같은 목록입니다. 서버 작업은 우리가 하지 않으므로, 서버에 필요한 내용은 [`docs/server-requests.md`](docs/server-requests.md)에 정리해 전달합니다.

| 항목 | 서버 쪽 제안 | 필요 시점 |
| --- | --- | --- |
| 인증 방식과 토큰 보관 | 등록할 때 토큰 발급, 이후 Bearer 헤더 | 서버 `driver-register` 전 |
| `TURN` 이벤트 payload | `direction`(LEFT/RIGHT), `signalOn`, 필요하면 `uTurn`. 핸들 감지는 유턴을 몰라서 앱은 `uTurn`을 보내지 않음 | 서버 `trip-upload` 전 |
| `POST /api/trips` 요청 형식 | 앱 DTO(`data/remote/dto/TripDtos.kt`): `tripId`, `startedAt`, `endedAt`, `distanceKm`, `supported`, `events[{eventId, type, occurredAt, payload}]`. 시각은 `2026-11-03T08:00:00+09:00` 형식. 서버 plan.md 3.4 테이블 기준으로 만듦 → 기획서 8.2와 대조 필요 | 서버 `trip-upload` 전 |
| `supported` 신호 이름 목록 | ~~예: `TURN_SIGNAL`, `BRAKE`~~ → 신호 이름이 아니라 서버 `ScoreItem` 이름으로 보냄(결정 11.08, 서버 요청 2번 철회) | 서버 `trip-upload` 전 |
| 기준값 JSON 형식 | 서버 plan.md 3.3 (`trip`, `turn`, `harsh`, `weights`, `point`) | 서버 `threshold-config` 전 |
| 기준값 `turn` 항목 (핸들 각도 전환) | 앱 `TurnConfig`에 `steeringEnterDeg`(90), `steeringExitDeg`(20), `mergeGapSec`(3) 추가. 서버 plan.md 3.3의 `windowSec`·`minAngleDeg`·`uTurnAngleDeg`는 진행 방향 기준이라 MVP에서 안 씀 → 반영됨(서버 #43, 서버 요청 1 · 7번) | 서버 `threshold-config` 전 |
| `supported` 신호 이름 | ~~앱에 `STEERING_ANGLE` 추가~~ → 철회(서버 요청 2번). 회전 측정은 앱 안에서 방향지시등 + 핸들 각도가 모두 있을 때만 `TURN_SIGNAL`을 넣음 | 서버 `trip-upload` 전 |
| `HARSH_*`, `SAFETY_WARNING`, `NIGHT_SEGMENT` payload | 스파이크 5~7번 결과 반영. 서버 enum에는 이미 있음 → payload 형식은 2차 앱 작업 14번에서 확인 | 2차 |

## 결정 필요

- [x] ~~**기획서와 목업이 서로 다른 부분**~~ (회전 직후 음성, 미션 3종, 내비 회전 미리 켜기) → 11.08 개정 목업에서 빠짐. 음성은 시동·종료 2회, 퀘스트는 "회전 깜빡이 완벽" 1종
- [ ] **게임 요소 확장 아이디어**: 목적지까지 달려서 점수가 가장 높은 사람이 그 도로(땅)를 차지하는 방식. 리그(FR-13)와 어떻게 합칠지, 2차 범위에 넣을지 결정
- [ ] **점수 항목 추가 후보 (2차 검토)**: SDK 조사로 찾은 데이터. 근거는 `docs/sdk-test-log.md` "점수화에 쓸 수 있는 다른 SDK 데이터"
  - 과속 비율 (NaviHelper `onCurrentRoadSpeedLimit` + CarProperty 속도), 단속 카메라·구간단속 준수 (`CameraAlert.isOverSpeed`), 어린이보호구역 감속 (`CautionPoint SCHOOL_ZONE`)
    → 에뮬레이터에서는 값이 안 옴(제한속도 항상 0). 실차 확인 필요
  - 출발 전 안전벨트 착용 (Vehicle `Seat.registerSeatBeltBuckledState`) → 조회는 됨, 변경 콜백은 실차 확인 필요
  - **확장안 전체: [`docs/scoring-criteria.md`](docs/scoring-criteria.md)** (11개 항목, 법규 기준, 가중치 초안, 새 이벤트 형식). 팀 확정 후 `server-requests.md`로 전달
  - 기존 규칙과 충돌: 오토라이트 API가 없어 서버 4.1의 "오토라이트면 100점"·`autoLight` 필드는 쓸 수 없음. 비상등 API가 없어 "비상등 켜진 회전 제외"도 불가
- [x] **API 클라이언트**: Retrofit + OkHttp + kotlinx.serialization (11.08)
- [ ] **DI**: Hilt 또는 수동 DI (MVP 일정을 생각하면 수동 DI도 고려)
- [ ] **정적 분석**: ktlint·detekt 사용 여부
- [ ] **깜빡이 4단계 판정**: 제때 · 늦게 · 반대 방향 · 안 켬(`signalTiming`, 30m 기준 `turn.signalLeadDistanceM`)을 2차에 도입할지. 지금은 성공/실패 2단계(결정 11.01). 도입하면 서버 요청 13번 팁 문구도 함께 바꿈
- [ ] **2차 플레이 카드 색 · 이름**: 급가속 · 급감속 같은 "반칙" 성격 카드를 실점(주황)과 같게 할지 별도 색으로 할지. "반칙" 용어는 쓰지 않는 결정은 유지
- [ ] **`CAR_SPEED` 거부 시 처리**: 제안은 기어 D일 때만 회전으로 보고 "판정 정확도 제한"을 표시하는 것(screen-plan 9장). 급가속 · 급감속(2차 11번)은 측정 제외
- [ ] **터치 영역 기준**: 기획서 76px와 CLAUDE.md 64dp 이상 중 무엇을 따를지 (지금 코드는 64dp)
- [ ] **백그라운드 측정 누락 안내**: 기록 서비스가 꺼져 있던 동안의 운행이 빠졌을 때 알릴지와 문구 (screen-plan 5장)

## 결정 기록

| 날짜 | 결정 | 이유 |
| --- | --- | --- |
| 2026-11-01 | 앱 아키텍처는 MVVM, UI는 Jetpack Compose | — |
| 2026-11-01 | 판정은 앱에서, 점수·미션·포인트 계산은 서버에서 | 기획서 7장. 규칙을 바꿀 때 앱을 다시 배포하지 않아도 됨 |
| 2026-11-01 | 연속 회전은 간격 3초 이내면 묶음. 같은 방향이면 합치고, 좌우가 섞이면(회전교차로·S자) 판정에서 제외 | 회전교차로의 깜빡이 규칙은 복잡해서 MVP에서 감점하지 않음 |
| 2026-11-01 | 비상등(HAZARD)은 깜빡이로 인정하지 않음. 운행 종료 시각은 P단에 들어간 시각 | — |
| 2026-11-01 | JSON은 kotlinx.serialization | Retrofit·Ktor 어느 쪽을 골라도 같이 쓸 수 있음. HTTP 클라이언트 결정과 별개 |
| 2026-11-01 | 업로드 실패 처리: 네트워크 오류·5xx·401·408·429는 대기열에 남겨 재전송, 그 외 4xx는 `REJECTED`로 멈춤(데이터는 보존) | 같은 요청을 다시 보내도 결과가 같은 오류를 무한 재시도하지 않기 위함 |
| 2026-11-01 | Room 스키마 내보내기는 MVP에서 끔(`exportSchema = false`). DAO 테스트는 Robolectric으로 JVM에서 실행 | 일정. 출시 후 스키마가 바뀌면 Migration 도입 |
| 2026-11-01 | 회전 감지는 핸들 각도(진입 90°·해제 20°)로 함. 진행 방향 감지는 보조로 남김 | 구현 가이드: 실차는 FusedLocation 미지원. 핸들 각도는 Virtual Console로 조작 가능해 시연에도 유리 |
| 2026-11-01 | MVP 깜빡이 판정은 성공/실패 2단계 (회전 시작 5초 전 ~ 회전 끝). 4단계(제때·늦게·반대 방향·안 켬)는 2차 검토 | 서버 `signalOn` 형식과 점수 규칙을 그대로 쓸 수 있음 |
| 2026-11-01 | DI는 임시로 수동 DI(`AppContainer`). 화면 전환은 navigation-compose | Hilt 도입 여부는 미정. 객체가 적은 지금은 수동으로 충분 |
| 2026-11-01 | 업로드는 기록(`TripRecorder`)에서 하지 않고 결과 화면 진입 시 트리거 (나중에 WorkManager) | 기록과 업로드의 수명이 다름. 기록이 끝나 취소돼도 업로드가 끊기지 않게 |
| 2026-11-01 | 서버 작업은 앱 팀이 하지 않음. 서버 요청은 `docs/server-requests.md`로 전달 | 서버는 다른 팀 담당 |
| 2026-11-01 | 음성은 Pleos TextToSpeech SDK(ON_DEVICE)로. 종료 안내는 서버 점수를 기다리지 않고 기기 판정 결과로 말함 | 표준 TTS는 에뮬레이터에서 동작 안 함. 서버 응답이 늦거나 오프라인이어도 종료 안내는 1회 나가야 함 |
| 2026-11-03 | `TurnConfig.steeringRightPositive` 기본값 false | 에뮬레이터 실측: 콘솔 오른쪽(+) → Vehicle SDK 음수. 실차가 다르면 서버 설정으로 변경 |
| 2026-11-03 | 비상등 제외 규칙은 남기되 "TurnSignal로 감지 불가"로 표시 | SDK TurnSignal 값·Light 도메인에 비상등 없음. 출처가 생기면 HAZARD 샘플로 연결 |
| 2026-11-03 | 운행 거리는 `PERF_VEHICLE_SPEED` 적분 | 트립 거리 API는 에뮬레이터 미지원, 오도미터는 속도를 따라 늘지 않아 검증 불가 |
| 2026-11-04 | 위치 출처는 Pleos FusedLocation SDK (NaviHelper 위치 아님) | 에뮬레이터에서 FusedLocation은 주입 좌표를 따라오고 NaviHelper 위치는 고정. 실차 지원은 Pleos 확인 필요 |
| 2026-11-08 | 운행 구분은 기어 기준(시동은 알 수 있을 때만), 공급원에 속도 Flow 추가, 지원 여부는 `CapabilityReport`로 노출 | Pleos SDK에 시동 상태 API 없음(스파이크 8). 화면 계획(`docs/screen-plan.md`)이 기어·속도 Flow와 CapabilityReport를 신호 쪽에 요청 |
| 2026-11-08 | 조사 코드·권한은 `src/debug`, main은 `DebugTools`로만 부름 | Pleos 심사: 승인되지 않은 권한 선언 시 탈락. 릴리스 APK에 조사용 권한·클래스가 들어가지 않게 |
| 2026-11-08 | 업로드는 WorkManager(`TripUploadWorker`)에 맡기고 운행 종료·앱 시작 때 예약 | 운행이 끝날 때 오프라인이어도 연결되면 자동으로 올라감. 앱이 동결돼도 WorkManager가 깨워 실행 |
| 2026-11-01 | 서버는 별도 저장소(BlinkerLeague/server)이고 Java 17 + Spring Boot 3.3 | 이미 만들어져 있음. 서버 plan.md에서 확정한 내용 |
| 2026-11-08 | 화면·테마는 `docs/screen-plan.md`(11.05)를 기준으로 함. 11.08 개정 목업과 다르면 screen-plan을 따름 | 팀 결정: screen-plan이 최신 기준 |
| 2026-11-08 | HTTP는 Retrofit + OkHttp. 서버 주소(`blinker.serverUrl`)가 없으면 Fake API. 401이면 `POST /api/auth/refresh`로 한 번 재발급, 그것도 거부되면 토큰 삭제 → 재등록 | kotlinx.serialization을 그대로 씀. 시연은 서버 없이도 되게 |
| 2026-11-08 | 업로드 `supported`는 신호 이름이 아니라 서버 점수 항목(`ScoreItem`: TURN_SIGNAL 등) | 서버가 `Set<ScoreItem>`으로 받음. 신호 이름(`STEERING_ANGLE`)을 보내면 400으로 거부돼 업로드가 멈춤 |
| 2026-11-08 | 주행 안전 가드는 정차 화면(NavHost)을 그대로 두고 주행 중 화면으로 덮음(터치·접근성 차단). 앱은 distraction-optimized로 표시하지 않음 | P로 돌아오면 보던 화면 복귀(screen-plan 3장). 시스템 차단 + 앱 가드 이중 보호 |
| 2026-11-08 | 등급은 앱이 점수와 `config.grades`(S95·A85·B70·C50)로 계산. 최근 경기는 기기 기록을 먼저 보여주고 서버 최근 경기로 점수만 갱신 | Room 스키마 변경 없이 등급 표시. 오프라인이어도 홈이 열림 |
| 2026-11-08 | 음성 안내 용도는 `USAGE_MEDIA`, 내비 안내가 시작되면 멈추고 끝난 뒤 한 번 더 | `USAGE_ASSISTANT`는 내비 회전 안내를 막음(에뮬레이터 실측). 주행 안전 원칙 "내비 음성과 겹치지 않게" |
| 2026-11-08 | 미션은 서버 라운드 판정(D25)을 따름. 시동 음성은 "N번 남았어요" 대신 이번 운행 기준 | 하루 합산이 아니라 운행 1건 안에서 판정하므로 지난 운행 진행도는 이번 운행과 무관 |
| 2026-11-09 | 짧은 운행 기준은 **1km** (사용자 결정). 서버 기준값 1.0 요청(서버 요청 10번) | 기획서 MVP 기준. 서버 D10(3km)은 서버 11.09 정리에서 앱 요청 10번 결정 대기·1.0 제안으로 바뀜. 시연 시나리오는 3km 넘게 두어 서버 값과 상관없이 점수가 나옴 |
| 2026-11-09 | 토큰은 Keystore 키로 암호화해 SharedPreferences에 저장 (`KeystoreTokenCipher`) | 서버 인증 결정(D2) 전제. androidx security-crypto는 지원 종료라 쓰지 않고 Keystore를 직접 씀 |
| 2026-11-10 | 리그 순위 목록의 "나"는 닉네임이 같은 행으로 표시 | 서버 `rankings`는 닉네임만 줌(4.4). 닉네임은 등록 때 중복이 막혀 권역 안에서 유일 |
| 2026-11-10 | 순위 공개 토글의 현재 값은 리그 조회(`me.visible`)에서 읽고, 저장에 실패하면 토글을 바꾸지 않고 안내 | 서버에 조회 전용 API가 없음. 기기에 따로 저장하면 서버 값과 어긋날 수 있음 |
| 2026-11-10 | 순위 공개 저장 중에는 토글을 막음(leagueVisibilitySaving) | 앞 요청을 취소해도 이미 보낸 PUT은 서버에서 처리돼 순서를 보장할 수 없음. 요청이 늘 하나라 단순하고 저장 중임이 보임 (review/2026-11-10-feature-league-visibility.md 1번) |
| 2026-11-10 | PUT /api/drivers/me/league-visibility 응답의 visible은 선택값으로 읽고 성공 응답이면 저장 성공 | openapi LeagueVisibilityResponse에 required 없음 (리뷰 4번) |
| 2026-11-10 | lint 경고 15건(라이브러리 버전 12 · InlinedApi 2 · Compose lint 비활성 1)은 MVP 뒤로 미룸. DataExtractionRules는 dataExtractionRules 연결로 해결(뒤이은 fullBackupContent 권고는 API 28~30이 allowBackup=false라 tools:ignore), FullBackupContent 오류(device_* 도메인)는 오탐이라 tools:ignore | 오류 0. 버전 올리기는 compileSdk 상향과 회귀 위험이 있고, Pleos 대상은 API 34라 SPECIAL_USE 상수 문제가 실제로 나지 않음 |
| 2026-11-10 | 홈 최근 경기 행은 업로드 대기 · 회전 0회 문구의 앞부분만 보여 줌 | 한 줄짜리 행. 전체 문구는 SCR-02에서 보임 (screen-plan 5장) |
| 2026-11-10 | 백업·기기 간 전송은 파일별이 아니라 모든 도메인 제외(dataExtractionRules), fullBackupContent는 두지 않음 | allowBackup=false와 같은 뜻. 파일 이름 나열은 새 저장소가 빠질 위험. 운행 DB만 옮겨지면 새로 등록한 운전자로 예전 운행이 올라갈 수 있음. API 28~30은 allowBackup=false로 충분. lint FullBackupContent는 device_* 도메인 오탐이라 무시(프레임워크 FullBackup.java는 해석함) |
| 2026-11-11 | MVP release는 R8(minify·난독화)을 끔 | 기한 4일, Pleos SDK keep 규칙 미확인, 보안 가이드에 난독화 필수 항목 없음. 2차에 검토 |
| 2026-11-11 | 1차 제출 release는 로컬 서버(http, -Pblinker.serverUrl)에 연결할 수 있게 함. 평문은 그 호스트에만 빌드 때 허용 | 사용자 결정. 보안 가이드(TLS) 때문에 평문 전체 허용은 하지 않음 |
| 2026-11-13 | 용어는 화면 · 음성 문구만 "미션"→"퀘스트". 코드 식별자 · 리소스 키 · 서버 API 이름(`mission`)은 유지. 주간 리포트 "맞춤 미션", 지갑 사유 "7일 연속 달성"(`STREAK_7`), 서버가 주는 배지 문구는 바꾸지 않음 | 기획서 용어와 맞춤. 이름을 바꾸면 서버 형식 · DB와 어긋남 |
| 2026-11-13 | 배지 `STREAK_7` 설명은 서버에 퀘스트 용어로 바꿔 달라고 요청함(server-requests 15). 앱 Fake · Preview는 먼저 바꿈. 앱은 여전히 서버 문구를 덮어쓰지 않음(위 결정 유지) | 배지 설명은 서버 문구를 그대로 보여 주므로 화면 용어를 맞추려면 서버 변경이 필요함 |
| 2026-11-13 | SCR-02b 적용 가중치는 서버 `metrics[].weight`를 백분율 정수로 표시("적용 가중치 N%"). 항목이 SCORED이고 가중치가 0보다 클 때만 보이고, 아니면(값 없음 · 0 · 미측정) 줄을 숨김 | 가중치는 서버 계산 규칙이라 앱이 셈하지 않음 |
| 2026-11-13 | "오늘의 N번째 경기"는 기기(Room)에 있는 그날 운행을 시작 시각 순으로, 짧은 이동도 포함해 셈. 자정을 넘긴 운행은 시작한 날로 셈. 오늘이 아닌 운행 · 짧은 이동 결과는 N 없이 기존 머리말 | 사용자 결정. 결과 화면은 업로드 전 · 오프라인에도 열리므로 기기 기록으로 셈 |
| 2026-11-13 | point 기준값 검사: streakRewardDays는 1 이상, streakRewardPoint는 0 이상만 받고 아니면 로컬 기본값 | 리뷰 지적 1. 주기 0은 나눗셈 오류, 음수 보상은 잘못된 문구가 됨 |
| 2026-11-13 | 적용 가중치는 항목이 SCORED이고 0보다 클 때만 표시 | 리뷰 지적 2. 미측정 항목에 가중치가 보이거나 "0%"가 보이지 않게 함 |
| 2026-11-02 | Windows 개발 환경은 프로젝트와 Gradle 캐시(`GRADLE_USER_HOME`)를 모두 영문 경로에 둠. Gradle JDK는 17 | 사용자 이름이 한글이면 AGP 경로 검사와 단위 테스트 실행기가 실패함. `overridePathCheck`로는 테스트가 해결되지 않음 |
| 2026-11-14 | 결과 화면은 적립 포인트가 0이면 포인트 줄을 숨김("적립 한도" 문구 삭제). 서버 요청 12번 본문도 맞춤 | 0 하나로는 버림(짧은 거리 · 낮은 평점)과 하루 한도를 구분할 수 없어 첫 경기에도 틀린 안내가 나옴. 포인트 비중이 낮음 |
| 2026-11-14 | 포인트는 제휴와 관계된 것으로 리그 순위 가산점으로만 쓰는 방식을 지향함. 리워드(교환 · 사용)는 없음. 리워드처럼 보이는 문구 · 구성은 2차 19번에서 재검토 | 사용자 결정 |
| 2026-11-15 | 점수 항목 7개(`SIGNAL_CANCEL` · `TURN_SPEED` · `SMOOTH_DRIVING` · `NIGHT_HEADLIGHT` · `SEAT_BELT` · `SAFETY_WARNING` · `RAIN_DRIVING`)를 앱에서 판정해 서버로 보냄 | 서버가 8개 항목 점수를 이미 계산함. 이벤트 payload는 openapi 형식 |
| 2026-11-15 | `supported` 항목은 필요한 신호가 모두 있을 때만 넣음(`ScoreItem.measurable`): 깜빡이 = 방향지시등 + 핸들 각도, 깜빡이 끄기 · 회전 속도 = 그에 더해 속도, 부드러운 주행 = 속도 + 브레이크, 안전 경고 = 경고등 + 속도, 야간 = 하향등, 안전벨트 = 벨트 + 속도, 우천 = 와이퍼 + 하향등 | 판정에 필요한 신호가 없는데 넣으면 이벤트 0건이 만점으로 채점됨 |
| 2026-11-15 | 깜빡이 끄기(`SIGNAL_LEFT_ON`)는 마지막 회전이 끝난 뒤 `signalCancel.minSpeedKmh` 이상으로 달린 시간만 셈. `forgetSec`를 넘으면 1건 | 회전 전에 미리 켜 둔 깜빡이나 정차 중 켜 둔 깜빡이를 잊은 것으로 보지 않음 |
| 2026-11-15 | 급감속은 브레이크를 밟고 있을 때만 셈. 속도 샘플 간격이 `harsh.maxGapSec`(앱 내부 2초)보다 길면 판정하지 않음 | 타력 감속 · 신호 끊김을 급감속으로 오판하지 않게 함 |
| 2026-11-15 | 야간은 등록 지역 시·도 대표 좌표의 일몰 ~ 다음 일출로 판정(NOAA 근사식). 실제 위치는 쓰지 않음 | 원시 위치를 쓰지 않는 원칙. 시·도 안의 일몰 차이는 몇 분 |
| 2026-11-15 | 안전벨트는 `getCurrentSeatBeltBuckledState`(운전석)를 1초마다 읽음. 출발 시 상태를 모르면 그 운행의 `supported`에서 `SEAT_BELT`를 뺌 | 변경 콜백은 실차 확인 전. 모르는 상태를 미착용으로 채점하지 않음 |
| 2026-11-15 | 경고등은 `getTelltalesInfo`를 1초마다 읽음(`registerTelltales`는 PRIVATE 권한이라 안 씀). 기본 대상은 ABS · ESC · TPMS · PARKING_BRAKE · ELECTRONIC_PARKING_BRAKE | 승인되지 않은 권한은 심사 탈락. 정보성 표시등은 빼고 안전 관련만 |
| 2026-11-15 | 우천 `RAIN_SEGMENT`의 `knownLimitSec` · `overRainLimitSec`는 0 | 제한속도를 알 수 없음(에뮬레이터에서 항상 0, 실차 확인 전) |
| 2026-11-15 | 하향등 상태를 모르는 시간은 꺼짐으로 세지 않음 | 미지원 · 신호 끊김을 위반으로 채점하지 않음 |
| 2026-11-15 | 결과 화면 · 항목 상세의 행 값은 서버 `metrics`로 채움(점수 · 미지원 · 해당 없음). 8행 확장(H4~H7)은 UI 수정 후로 보류 | 점수는 서버가 계산. 사용자가 UI를 따로 고치는 중 |
| 2026-11-15 | 안전 경고는 경고등 종류마다 한 번 켜진 주기(켜짐 → 꺼짐)에 1건. 속도가 기준을 넘나들어도 다시 세지 않음 | 리뷰 C1. 정체 구간에서 10km/h를 넘나들 때마다 세면 수천 건이 되어 운행 전체가 거부됨 |
| 2026-11-15 | 운행 이벤트 상한은 서버 `trip.maxEvents`(기본 2000, 0 이하 무시). 넘치면 횟수형(급가속 · 급감속 · 안전 경고)을 늦은 것부터 빼고, 그래도 넘치면 이른 순으로 maxEvents개만 남김. 조립 단계에서 잘라 Room 저장 = 업로드 | 리뷰 C1. 2000건 초과는 서버 400 → `REJECTED`로 깜빡이까지 채점되지 않음 |
| 2026-11-15 | 지원한다고 했어도 기록 샘플이 0개인 신호(브레이크 · 하향등 · 경고등 · 와이퍼 · 속도)는 그 운행의 `supported`에서 빼고 판정에도 쓰지 않음 | 리뷰 C2. 수집 실패를 이벤트 0건 = 만점으로 채점하지 않음 |
| 2026-11-15 | 이미 `REJECTED`인 운행은 이번 변경 뒤에도 재전송하지 않음 | 400 · 413은 같은 요청을 다시 보내도 거부됨. 기존 운행을 다시 조립하지 않음 |
