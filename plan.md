# 깜빡이리그 작업 계획

> 진행하면서 계속 갱신하는 문서입니다. 완료한 항목은 체크하고, 결정한 내용은 맨 아래 "결정 기록"에 남깁니다.

- 기획서 (원본): https://claude.ai/artifact/8xWTvcr2nsR3zC57iVAYgZ
- UI 목업 (주행 중 / 정차 후 결과 / 미션·포인트): https://claude.ai/artifact/EjjhjWCfMLr6d3aFThibn9
- 서버 저장소와 서버 계획: https://github.com/BlinkerLeague/server (`plan.md`). API 형식은 이 문서를 따름

## 일정

| 단계 | 기한 | 범위 |
| --- | --- | --- |
| MVP (1차 제출) | **11.15** | 운행 자동 구분, 회전 감지·깜빡이 판정, 운행 점수 화면, 오늘의 미션 1종, 음성 칭찬, 서버 이벤트 수집·점수 API |
| 2차 (PT) | 12.2 | 급가속·급감속, 야간 전조등, 안전 경고, 연속 기록·배지, 포인트 원장, 지역 리그, 주간 리포트 |
| 이후 | — | 차로 변경 측정(ADAS SDK), 제휴 보상, 가족·법인 관리 |

## MVP 측정 기준 (초기값, 튜닝 예정)

- **운행**: 시동 on 또는 주행 상태 진입 시 시작합니다. 시동 off 또는 P단이 3분 이상 유지되면 종료합니다. 1km 미만 운행은 점수 대상에서 제외합니다.
- **회전**: 속도 5km/h 이상에서 10초 안에 진행 방향(heading)이 60° 이상 바뀌면 회전입니다. 부호로 좌·우를 구분하고, 150° 이상이면 유턴입니다. 5km/h 미만 구간과 연속 회전(회전교차로)은 묶거나 제외합니다.
- **깜빡이 성공**: 회전 시작 5초 전부터 회전이 끝날 때까지, 같은 방향 방향지시등이 한 번이라도 켜지면 성공입니다.
- **MVP 점수**: 깜빡이 사용률 × 100입니다. 회전이 없는 운행은 "측정할 회전이 없었어요"로 표시합니다.

## 작업 목록 — 앱

### 0. 스파이크 (10.26 ~ 11.1): SDK 체크리스트
가장 먼저 확인할 항목은 1번과 2번입니다. 이 두 가지가 안 되면 앱이 성립하지 않습니다.
- [ ] 1. TurnSignal 상태를 실시간으로 구독할 수 있는지, 좌·우가 구분되는지
- [ ] 2. 앱이 백그라운드에 있을 때도 신호와 위치를 계속 받을 수 있는지
- [ ] 3. 주행 중 앱 실행 제한의 범위 (정차·P단 조건)
- [ ] 4. Fused Location의 갱신 주기와 heading·speed 제공 여부
- [ ] 5. Brake API가 페달 상태를 주는지
- [ ] 6. Light API로 전조등·오토라이트 상태를 알 수 있는지
- [ ] 7. Safety API가 주는 경고 종류
- [ ] 8. 시동·주행 상태(DrivingMode, P단)를 감지할 수 있는지
- [ ] 9. 각 기능의 checkXXXCapability 결과 (앱 시작 시 로그로 출력)
- [ ] 10. TextToSpeech 사용 조건과 내비 음성과 겹칠 때의 처리
- [ ] 11. 사용자 자산 관리·인앱 구매 API

### 1. 프로젝트 기반 정리
- [x] Compose 설정 추가 (Kotlin 1.9.22에 맞춰 Compose Compiler 1.5.10, Compose BOM)
- [x] JVM target을 1.8에서 17로 상향
- [x] 의존성 추가: activity-compose, lifecycle-viewmodel/runtime-compose, coroutines
- [ ] 의존성 추가: Room, Retrofit 또는 Ktor, play-services-location (해당 단계에서 추가)
- [ ] (선택) Version Catalog(`gradle/libs.versions.toml`) 도입
- [x] `MainActivity`를 Compose로 교체하고 Door 샘플 코드 제거
- [x] Manifest 정리: `appCategory="audio"`와 `CAR_DOORS` 권한 제거 (필요한 권한은 스파이크 결과에 따라 추가)
- [ ] 패키지 구조 생성 (CLAUDE.md 참고)

### 2. 수집과 판정 (11.2 ~ 11.5)
- [ ] `VehicleSignalSource`, `LocationSource` 인터페이스 작성 + SDK 구현체 + Fake 구현체
- [ ] 시나리오 재생기: "좌회전 + 깜빡이", "우회전 + 깜빡이 없음", "회전교차로", "주차장 이동"
- [ ] 운행 구분 (`TripSegmenter`)
- [ ] 회전 감지 (`TurnDetector`) + 단위 테스트
- [ ] 깜빡이 판정 (`SignalJudge`) + 단위 테스트
- [ ] Room에 운행·이벤트 선저장, 업로드 대기열 구현
- [ ] 운행 수집 서비스 (스파이크 2번 결과에 따라 방식 결정)

### 3. 서버 연동 (서버 feat 브랜치 순서에 맞춰 진행, ~11.8)
서버는 `driver-register` → `threshold-config` → `trip-upload` → `trip-score` → `daily-mission` 순서로 만들어집니다. 서버 API가 나오기 전까지 앱은 Fake API로 먼저 개발합니다.
- [ ] `POST /api/drivers`: 최초 실행 시 닉네임과 지역 코드(시군구 5자리)를 등록하고, 받은 토큰을 로컬(DataStore)에 저장. 이후 요청은 Bearer 헤더로 보냄
- [ ] `GET /api/config/thresholds`: 앱 시작 시 내려받음. `version`이 바뀌었을 때만 갱신하고, 받지 못하면 로컬 기본값 사용
- [ ] `POST /api/trips`: 운행이 끝나면 업로드하고, 실패하면 재전송 (WorkManager 검토). 응답에 담긴 점수를 바로 화면에 씀
- [ ] `GET /api/trips/{id}/score`: `status`가 `SCORED`, `NO_TURN`, `EXCLUDED_SHORT` 중 무엇인지에 따라 화면 분기
- [ ] `GET /api/missions/today`: 상태(`IN_PROGRESS`/`ACHIEVED`/`FAILED`)와 진행률 표시

### 4. 화면과 음성 (11.9 ~ 11.12)
- [x] 테마: 다크 배경 #101317, 카드 #1B2027, 강조색 #5AA2FF, 폰트 Noto Sans KR (목업 기준)
- [ ] 정차 후 운행 결과 화면: 회전 기록 목록(제때 켬 / 안 켬), 오늘 미션 진행률, 적립 포인트
- [ ] 미션 화면 (MVP에서는 오늘의 미션 1종만)
- [ ] 정차·P단 상태에서만 화면 진입 허용
- [ ] 음성: 시동 시 미션 안내, 운행 종료 시 결과 한마디

### 5. 1차 제출 (11.13 ~ 11.15)
- [ ] 시연 영상: "좌회전 깜빡이 성공 → 우회전 실패 → 운행 종료 → 점수·미션 화면"
- [ ] release APK 빌드
- [ ] README의 TODO 채우기 (소개, 스크린샷, 구조)
- [ ] 공모전 제출 양식 작성

## 서버와 맞춰야 할 항목
서버 `plan.md` 7장과 같은 목록입니다. 정해지면 양쪽 문서에 모두 반영합니다.

| 항목 | 서버 쪽 제안 | 필요 시점 |
| --- | --- | --- |
| 인증 방식과 토큰 보관 | 등록할 때 토큰 발급, 이후 Bearer 헤더 | 서버 `driver-register` 전 |
| `TURN` 이벤트 payload | `direction`(LEFT/RIGHT), `signalOn`, 필요하면 `uTurn` | 서버 `trip-upload` 전 |
| `supported` 신호 이름 목록 | 예: `TURN_SIGNAL`, `BRAKE` (전체 목록은 스파이크 9번 결과로 확정) | 서버 `trip-upload` 전 |
| 기준값 JSON 형식 | 서버 plan.md 3.3 (`trip`, `turn`, `harsh`, `weights`, `point`) | 서버 `threshold-config` 전 |
| `HARSH_*`, `SAFETY_WARNING`, `NIGHT_SEGMENT` payload | 스파이크 5~7번 결과 반영 | 2차 |

## 결정 필요

- [ ] **기획서와 목업이 서로 다른 부분** (어느 쪽을 따를지 정해야 함)
  - 음성: 기획서는 시동 시·종료 시 2회만이라고 되어 있지만, 목업 1번 화면은 회전 직후 "깜빡이 좋았어요"를 음성으로 안내함
  - 미션: 기획서 MVP는 1종(회전 깜빡이 완벽)인데, 목업에는 "회전 5번 제때", "내비 안내 회전 미리 켜기", "브레이크 부드럽게 정지" 3종이 있음
  - "내비 안내 회전 미리 켜기 · 보너스"는 기획서에 없음. 내비 안내 지점 정보를 받을 수 있는지도 확인 필요
- [ ] **게임 요소 확장 아이디어**: 목적지까지 달려서 점수가 가장 높은 사람이 그 도로(땅)를 차지하는 방식. 리그(FR-13)와 어떻게 합칠지, 2차 범위에 넣을지 결정
- [ ] **API 클라이언트**: Retrofit + kotlinx.serialization 또는 Ktor
- [ ] **DI**: Hilt 또는 수동 DI (MVP 일정을 생각하면 수동 DI도 고려)
- [ ] **정적 분석**: ktlint·detekt 사용 여부

## 결정 기록

| 날짜 | 결정 | 이유 |
| --- | --- | --- |
| 2026-11-01 | 앱 아키텍처는 MVVM, UI는 Jetpack Compose | — |
| 2026-11-01 | 판정은 앱에서, 점수·미션·포인트 계산은 서버에서 | 기획서 7장. 규칙을 바꿀 때 앱을 다시 배포하지 않아도 됨 |
| 2026-11-01 | 서버는 별도 저장소(BlinkerLeague/server)이고 Java 17 + Spring Boot 3.3 | 이미 만들어져 있음. 서버 plan.md에서 확정한 내용 |
