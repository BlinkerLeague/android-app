# 깜빡이리그 작업 계획

> 진행하면서 계속 갱신하는 문서입니다. 완료한 항목은 체크하고, 결정한 내용은 맨 아래 "결정 기록"에 남깁니다.

- 기획서 (원본): https://claude.ai/artifact/8xWTvcr2nsR3zC57iVAYgZ
- UI 목업 (주행 중 / 정차 후 결과 / 미션·포인트): https://claude.ai/artifact/EjjhjWCfMLr6d3aFThibn9
- 구현 가이드 (10/27, 기획서 문서의 탭): SDK 사용 흐름, 핸들 각도 판정, 확인 목록
- 서버 저장소와 서버 계획: https://github.com/BlinkerLeague/server (`plan.md`). API 형식은 이 문서를 따름

## 일정

| 단계 | 기한 | 범위 |
| --- | --- | --- |
| MVP (1차 제출) | **11.15** | 운행 자동 구분, 회전 감지·깜빡이 판정, 운행 점수 화면, 오늘의 미션 1종, 음성 칭찬, 서버 이벤트 수집·점수 API |
| 2차 (PT) | 12.2 | 급가속·급감속, 야간 전조등, 안전 경고, 연속 기록·배지, 포인트 원장, 지역 리그, 주간 리포트 |
| 이후 | — | 차로 변경 측정(ADAS SDK), 제휴 보상, 가족·법인 관리 |

## MVP 측정 기준 (초기값, 튜닝 예정)

- **운행**: 시동 on 또는 주행 상태 진입 시 시작합니다. 시동 off 또는 P단이 3분 이상 유지되면 종료합니다. 1km 미만 운행은 점수 대상에서 제외합니다.
- **회전 (핸들 각도)**: 핸들 각도 절댓값이 20°를 넘은 때부터 다시 20° 아래로 내려올 때까지를 한 번의 조향으로 보고, 그 사이 90° 이상 꺾였으면 회전입니다. 부호로 좌·우를 구분합니다(부호 방향은 스파이크로 확인). 유턴은 구분하지 않습니다.
- **회전 제외**: 기어 P·R, 비상등 켜짐, (속도를 알 때) 회전 내내 5km/h 미만. 3초 이내 연속 회전은 같은 방향이면 합치고 좌우가 섞이면(회전교차로) 제외합니다.
- (보조) 진행 방향(heading) 기반 감지 `HeadingTurnDetector`: 10초 안에 60° 이상 변화. 위치에 heading이 있을 때 정확도 보강용으로 남겨둠, 현재 미사용
- **깜빡이 성공**: 회전 시작 5초 전부터 회전이 끝날 때까지, 같은 방향 방향지시등이 한 번이라도 켜지면 성공입니다.
- **MVP 점수**: 깜빡이 사용률 × 100입니다. 회전이 없는 운행은 "측정할 회전이 없었어요"로 표시합니다.

## 작업 목록 — 앱

### 0. 스파이크 (10.26 ~ 11.1): SDK 체크리스트
가장 먼저 확인할 항목은 1번과 2번입니다. 이 두 가지가 안 되면 앱이 성립하지 않습니다.
실제로 실행해 확인한 결과는 [`docs/sdk-test-log.md`](docs/sdk-test-log.md)에 기록합니다.
- [ ] 1. TurnSignal 상태를 실시간으로 구독할 수 있는지, 좌·우가 구분되는지
- [ ] 2. 앱이 백그라운드에 있을 때도 신호와 위치를 계속 받을 수 있는지
- [ ] 3. 주행 중 앱 실행 제한의 범위 (정차·P단 조건)
- [ ] 4. 위치: 실차는 FusedLocation 미지원(구현 가이드) → NaviHelper `getCurrentLocationInfo`의 필드(속도 포함 여부)와 갱신 주기
  - [x] 에뮬레이터: Pleos FusedLocation SDK 1.1.3으로 현재 위치 조회와 1초 갱신이 됨 (`isSupported()`=true). `adb emu geo fix`로 위치를 바꿀 수 있음. `LocationData`에 속도 필드는 없음. 권한 `pleos.car.permission.FUSED_LOCATION`(dangerous, 런타임 요청 필요)
  - [ ] 실차에서 FusedLocation `isSupported()` 결과 (false이면 NaviHelper)
  - [ ] NaviHelper `getCurrentLocationInfo`의 필드와 갱신 주기
- [ ] 5. Brake API가 페달 상태를 주는지
  - [x] 에뮬레이터: `Brake.registerBrakePedalStatus` → 눌림 여부 `Boolean`만 옴 (밟은 정도 없음). 권한 `CAR_DRIVE`
- [ ] 6. Light API로 전조등·오토라이트 상태를 알 수 있는지
  - [x] 에뮬레이터: 하향등·미등·상향등 `LightState`(OFF/ON/DAYTIME_RUNNING) 조회·콜백 됨. **오토라이트 API는 없음**, 안개등 미지원. 권한 `CAR_LIGHTS`
- [ ] 7. Safety API가 주는 경고 종류
  - [x] 에뮬레이터: 경고등(`registerTelltales`)은 켜진 것만 목록으로 옴(`TelltaleType`+`OFF/ON/BLINK`). 오일·워셔액 경고는 미지원. 비상등 API 없음
- [ ] 8. 시동·주행 상태(DrivingMode, P단)를 감지할 수 있는지
  - [x] 에뮬레이터: Vehicle SDK에 **시동 상태 API 없음**(`IgnitionState` enum만 있음). `DrivingMode`는 에코·스포츠 모드이고 미지원. P단은 Gear로 됨
  - [ ] CarProperty `IGNITION_STATE` 읽기 권한·가능 여부
- [ ] 9. 각 기능의 checkXXXCapability 결과 (앱 시작 시 로그로 출력)
  - [x] 에뮬레이터: 21개 도메인 전수 조사 (`VehicleSdkSurveyTest`). 미지원은 `onFailed`로 오는데 오류 모양이 두 가지라 클래스로 구분하면 안 됨
  - [ ] 실차에서 같은 테스트로 비교
- [ ] 10. TextToSpeech 사용 조건과 내비 음성과 겹칠 때의 처리
  - [x] 에뮬레이터: Pleos TextToSpeech SDK 3.3.1 `Mode.ON_DEVICE` + `ko-KR`로 재생됨. 권한 `pleos.car.permission.TTS_SERVICE` 필요. Android 표준 TextToSpeech는 엔진(caas SpeechService) 연결 실패
  - [ ] 내비 안내 음성 중일 때 오디오 포커스 동작 (지연·거절) 확인
- [ ] 11. 사용자 자산 관리·인앱 구매 API
- [ ] 12. 핸들 각도의 단위·범위·부호(양수가 오른쪽인지)와 갱신 주기 → `TurnConfig.steeringRightPositive`
- [ ] 13. 차량 속도를 받는 API (저속 조향 제외 조건)
- [ ] 14. `getTBTInfo` 응답 필드(회전 방향, 남은 거리)와 갱신 주기 (내비 미리 켜기, 2차)

### 1. 프로젝트 기반 정리
- [x] Compose 설정 추가 (Kotlin 1.9.22에 맞춰 Compose Compiler 1.5.10, Compose BOM)
- [x] JVM target을 1.8에서 17로 상향
- [x] 의존성 추가: activity-compose, lifecycle-viewmodel/runtime-compose, coroutines
- [x] 의존성 추가: Room 2.6.1(KSP), kotlinx.serialization, Robolectric(테스트)
- [ ] 의존성 추가: Retrofit 또는 Ktor, play-services-location (해당 단계에서 추가)
- [ ] (선택) Version Catalog(`gradle/libs.versions.toml`) 도입
- [x] `MainActivity`를 Compose로 교체하고 Door 샘플 코드 제거
- [x] Manifest 정리: `appCategory="audio"`와 `CAR_DOORS` 권한 제거 (필요한 권한은 스파이크 결과에 따라 추가)
- [x] 패키지 구조 생성: `domain/model·config·detection`, `data/vehicle·location·scenario`. 나머지(`local`, `remote`, `service`, `voice`)는 해당 단계에서 만듦

### 2. 수집과 판정 (11.2 ~ 11.5)
- [x] `VehicleSignalSource`, `LocationSource` 인터페이스 + Fake 구현체 (시나리오 재생)
- [x] 핸들 각도 회전 감지 (`SteeringTurnDetector`) + 단위 테스트, 운행 조립을 핸들 감지로 전환
- [ ] SDK 구현체 (`data/vehicle`, 핸들 각도 포함), NaviHelper 위치 구현체 (`data/location`): 스파이크 1·4·8·9·12번 결과 필요
- [x] 시나리오 재생기 (`data/scenario`, 테스트와 시연에 공용): "좌회전 + 깜빡이", "우회전 + 깜빡이 없음", "회전교차로", "주차장 이동"
- [x] 운행 구분 (`TripSegmenter`) + 단위 테스트
- [x] 회전 감지 (`TurnDetector`) + 단위 테스트
- [x] 깜빡이 판정 (`SignalJudge`) + 단위 테스트
- [x] Room에 운행·이벤트 선저장, 업로드 대기열 구현 (`TripRepository`, 재전송 트리거는 3단계에서)
- [x] 끝난 운행 → `Trip` 조립 (`TripAssembler`: 회전 감지 + 깜빡이 판정 + 거리)
- [x] 운행 기록 흐름 `TripRecorder` (`service/`): 공급원 → 운행 구분 → 조립 → 로컬 저장. Android Service와 분리돼 Fake로 시연·테스트 가능
- [ ] 운행 수집 서비스: `TripRecorder`를 감싸는 백그라운드 수집 (스파이크 2번 결과에 따라 방식 결정)

### 3. 서버 연동 (서버 feat 브랜치 순서에 맞춰 진행, ~11.8)
서버는 `driver-register` → `threshold-config` → `trip-upload` → `trip-score` → `daily-mission` 순서로 만들어집니다. 서버 API가 나오기 전까지 앱은 Fake API로 먼저 개발합니다.
- [ ] `POST /api/drivers`: 최초 실행 시 닉네임과 지역 코드(시군구 5자리)를 등록하고, 받은 토큰을 로컬(DataStore)에 저장. 이후 요청은 Bearer 헤더로 보냄
- [ ] `GET /api/config/thresholds`: 앱 시작 시 내려받음. `version`이 바뀌었을 때만 갱신하고, 받지 못하면 로컬 기본값 사용
- [ ] `POST /api/trips`: 운행이 끝나면 업로드하고, 실패하면 재전송 (WorkManager 검토). 응답에 담긴 점수를 바로 화면에 씀
  - [x] DTO, `TripApi` 인터페이스, 서버 규칙을 흉내 낸 `FakeTripApi`, 대기열 업로드 로직
  - [ ] HTTP 구현체 (API 클라이언트 결정 후), 재전송 트리거 (WorkManager)
- [ ] `GET /api/trips/{id}/score`: `status`가 `SCORED`, `NO_TURN`, `EXCLUDED_SHORT` 중 무엇인지에 따라 화면 분기
- [ ] `GET /api/missions/today`: 상태(`IN_PROGRESS`/`ACHIEVED`/`FAILED`)와 진행률 표시
  - [x] DTO, `MissionApi`, 서버 규칙(3.6)을 흉내 낸 `FakeMissionApi`, `MissionRepository`
  - [ ] HTTP 구현체 (API 클라이언트 결정 후)

### 4. 화면과 음성 (11.9 ~ 11.12)
- 화면 구현 계획: [`docs/screen-plan.md`](docs/screen-plan.md) (주행 안전 가드 → 홈 → 결과·타임라인 → 퀘스트 → 온보딩 순)
- [x] 테마: 다크 배경 #101317, 카드 #1B2027, 강조색 #5AA2FF, 폰트 Noto Sans KR (목업 기준)
- [ ] 정차 후 운행 결과 화면: 회전 기록 목록(제때 켬 / 안 켬), 오늘 미션 진행률, 적립 포인트
  - [x] 점수·상태 안내(점수 계산 중 / 회전 없음 / 1km 미만), 깜빡이 사용 횟수, 회전 기록 목록 (`ui/summary`)
  - [x] 오늘 미션 진행률 (업로드 후 미션 조회)
  - [ ] 적립 포인트 (포인트 원장 P1)
- [x] 시나리오 재생 화면 (`ui/demo`): Fake 신호로 운행을 1·5·10배속 재생 → 결과 화면. 시연·화면 테스트용
- [x] 미션 화면 (MVP에서는 오늘의 미션 1종만, `ui/missions`)
- [ ] 정차·P단 상태에서만 화면 진입 허용
- [x] 음성: 시동 시 미션 안내, 운행 종료 시 결과 한마디 (`voice/`, `service/TripVoiceGuide`). 운행당 1회씩, 오디오 포커스를 받은 뒤에만 재생

### 5. 1차 제출 (11.13 ~ 11.15)
- [ ] 시연 영상: "좌회전 깜빡이 성공 → 우회전 실패 → 운행 종료 → 점수·미션 화면"
- [ ] release APK 빌드
- [ ] README의 TODO 채우기 (소개, 스크린샷, 구조)
- [ ] 공모전 제출 양식 작성

## 서버와 맞춰야 할 항목
서버 `plan.md` 7장과 같은 목록입니다. 서버 작업은 우리가 하지 않으므로, 서버에 필요한 내용은 [`docs/server-requests.md`](docs/server-requests.md)에 정리해 전달합니다.

| 항목 | 서버 쪽 제안 | 필요 시점 |
| --- | --- | --- |
| 인증 방식과 토큰 보관 | 등록할 때 토큰 발급, 이후 Bearer 헤더 | 서버 `driver-register` 전 |
| `TURN` 이벤트 payload | `direction`(LEFT/RIGHT), `signalOn`, 필요하면 `uTurn`. 핸들 감지는 유턴을 몰라서 앱은 `uTurn`을 보내지 않음 | 서버 `trip-upload` 전 |
| `POST /api/trips` 요청 형식 | 앱 DTO(`data/remote/dto/TripDtos.kt`): `tripId`, `startedAt`, `endedAt`, `distanceKm`, `supported`, `events[{eventId, type, occurredAt, payload}]`. 시각은 `2026-11-03T08:00:00+09:00` 형식. 서버 plan.md 3.4 테이블 기준으로 만듦 → 기획서 8.2와 대조 필요 | 서버 `trip-upload` 전 |
| `supported` 신호 이름 목록 | 예: `TURN_SIGNAL`, `BRAKE` (전체 목록은 스파이크 9번 결과로 확정) | 서버 `trip-upload` 전 |
| 기준값 JSON 형식 | 서버 plan.md 3.3 (`trip`, `turn`, `harsh`, `weights`, `point`) | 서버 `threshold-config` 전 |
| 기준값 `turn` 항목 (핸들 각도 전환) | 앱 `TurnConfig`에 `steeringEnterDeg`(90), `steeringExitDeg`(20), `mergeGapSec`(3) 추가. 서버 plan.md 3.3의 `windowSec`·`minAngleDeg`·`uTurnAngleDeg`는 진행 방향 기준이라 MVP에서 안 씀 → **서버 plan.md 3.3 갱신 필요** | 서버 `threshold-config` 전 |
| `supported` 신호 이름 | 앱에 `STEERING_ANGLE` 추가. 회전 측정은 `TURN_SIGNAL`과 `STEERING_ANGLE`이 모두 있어야 가능 | 서버 `trip-upload` 전 |
| `HARSH_*`, `SAFETY_WARNING`, `NIGHT_SEGMENT` payload | 스파이크 5~7번 결과 반영 | 2차 |

## 결정 필요

- [ ] **기획서와 목업이 서로 다른 부분** (어느 쪽을 따를지 정해야 함)
  - 음성: 기획서는 시동 시·종료 시 2회만이라고 되어 있지만, 목업 1번 화면은 회전 직후 "깜빡이 좋았어요"를 음성으로 안내함
  - 미션: 기획서 MVP는 1종(회전 깜빡이 완벽)인데, 목업에는 "회전 5번 제때", "내비 안내 회전 미리 켜기", "브레이크 부드럽게 정지" 3종이 있음
  - "내비 안내 회전 미리 켜기 · 보너스"는 기획서에 없음. 내비 안내 지점 정보를 받을 수 있는지도 확인 필요
- [ ] **게임 요소 확장 아이디어**: 목적지까지 달려서 점수가 가장 높은 사람이 그 도로(땅)를 차지하는 방식. 리그(FR-13)와 어떻게 합칠지, 2차 범위에 넣을지 결정
- [ ] **점수 항목 추가 후보 (2차 검토)**: SDK 조사로 찾은 데이터. 근거는 `docs/sdk-test-log.md` "점수화에 쓸 수 있는 다른 SDK 데이터"
  - 과속 비율 (NaviHelper `onCurrentRoadSpeedLimit` + CarProperty 속도), 단속 카메라·구간단속 준수 (`CameraAlert.isOverSpeed`), 어린이보호구역 감속 (`CautionPoint SCHOOL_ZONE`)
    → 에뮬레이터에서는 값이 안 옴(제한속도 항상 0). 실차 확인 필요
  - 출발 전 안전벨트 착용 (Vehicle `Seat.registerSeatBeltBuckledState`) → 조회는 됨, 변경 콜백은 실차 확인 필요
  - **확장안 전체: [`docs/scoring-criteria.md`](docs/scoring-criteria.md)** (11개 항목, 법규 기준, 가중치 초안, 새 이벤트 형식). 팀 확정 후 `server-requests.md`로 전달
  - 기존 규칙과 충돌: 오토라이트 API가 없어 서버 4.1의 "오토라이트면 100점"·`autoLight` 필드는 쓸 수 없음. 비상등 API가 없어 "비상등 켜진 회전 제외"도 불가
- [ ] **API 클라이언트**: Retrofit + kotlinx.serialization 또는 Ktor
- [ ] **DI**: Hilt 또는 수동 DI (MVP 일정을 생각하면 수동 DI도 고려)
- [ ] **정적 분석**: ktlint·detekt 사용 여부

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
| 2026-11-01 | 서버는 별도 저장소(BlinkerLeague/server)이고 Java 17 + Spring Boot 3.3 | 이미 만들어져 있음. 서버 plan.md에서 확정한 내용 |
| 2026-11-02 | Windows 개발 환경은 프로젝트와 Gradle 캐시(`GRADLE_USER_HOME`)를 모두 영문 경로에 둠. Gradle JDK는 17 | 사용자 이름이 한글이면 AGP 경로 검사와 단위 테스트 실행기가 실패함. `overridePathCheck`로는 테스트가 해결되지 않음 |
