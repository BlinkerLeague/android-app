# BlinkerLeague (깜빡이리그)

차량 신호(방향지시등·브레이크·전조등·안전 경고)와 위치로 운행별 안전 습관을 측정하고,
점수·미션·포인트·지역 리그로 보상하는 Android Automotive OS 앱. 현대자동차 공모전 출품작 (Pleos Vehicle SDK).

- 작업 계획·일정·결정 사항: `plan.md`
- 커밋·브랜치·코드 컨벤션: `README.md`
- 원본 기획서와 UI 목업 링크: `plan.md` 상단

## 빌드 & 실행

```bash
./gradlew :automotive:assembleDebug     # 빌드
./gradlew :automotive:installDebug      # 기기/에뮬레이터 설치
./gradlew :automotive:testDebugUnitTest # 단위 테스트
```

- JDK 17로 빌드함. 기본 `java`가 다른 버전이면 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`

- 모듈은 `:automotive` 하나이고, 패키지 루트는 `com.example.blinkerleague`
- Vehicle SDK(`ai.pleos.playground:Vehicle`)는 Pleos Nexus 저장소에서 받음 (`settings.gradle.kts`)

## 역할 분담: 앱 vs 서버

- **앱**: 실시간성이 필요한 일. 신호·위치 수집, 운행 구분, 회전 감지, 깜빡이·가감속·전조등 판정, 로컬 선저장과 재전송, 정차 화면, 음성
- **서버**: 규칙이 바뀔 수 있는 일. 점수 계산, 미션 판정, 포인트 원장, 리그, 리포트
  - 별도 저장소 https://github.com/BlinkerLeague/server (Java 17 + Spring Boot 3.3. 기획서에는 Kotlin으로 적혀 있지만 서버는 Java로 확정됨)
  - API 요청·응답 형식은 서버 저장소 `plan.md` 3장이 기준. 앱 DTO도 그 형식에 맞춤. 형식을 바꿔야 하면 서버 plan.md와 함께 고침
  - 인증: `POST /api/drivers`에서 받은 토큰을 `Authorization: Bearer <token>` 헤더로 보냄
- 앱은 **판정된 이벤트만** 서버로 보냄. 원시 위치 좌표는 절대 서버로 보내지 않음

## 아키텍처: MVVM + Jetpack Compose

```
com.example.blinkerleague
├── data/
│   ├── vehicle/      # Vehicle SDK 래퍼: 지원 여부 확인(checkXXXCapability), 상태 변화 → Flow
│   ├── location/     # Fused Location 1초 수집 → Flow
│   ├── local/        # Room: 운행·이벤트 선저장, 업로드 대기열
│   ├── remote/       # 서버 API 클라이언트
│   └── repository/
├── domain/
│   ├── model/        # Trip, TripEvent, Turn, SignalState 등 (Android/SDK 의존성 없음)
│   ├── detection/    # 운행 구분, 회전 감지, 깜빡이 판정 등 판정 엔진 (순수 Kotlin)
│   └── config/       # 측정 기준값 (서버 설정, 로컬 기본값 fallback)
├── service/          # 운행 수집용 서비스 (백그라운드 수집 방식은 스파이크 결과에 따름)
├── voice/            # TextToSpeech 래퍼
└── ui/
    ├── theme/
    ├── navigation/
    └── <feature>/    # summary(운행 결과), missions(미션·포인트) …
                      # 화면별: XxxScreen.kt, XxxViewModel.kt, XxxUiState.kt
```

### 코드 규칙
- **Vehicle SDK·Location API는 `data/vehicle`, `data/location` 밖에서 쓰지 않음.** domain과 ViewModel은 SDK 타입을 import하지 않음.
  SDK 콜백(`onComplete`/`onFailed`)은 `callbackFlow`나 `suspendCancellableCoroutine`으로 감쌈.
- 신호 소스는 인터페이스로 추상화하고 Fake 구현을 둠. 실차 없이 시나리오(위치·신호 시계열)를 재생해서 테스트할 수 있어야 함.
- **판정 로직(`domain/detection`)은 순수 함수**로 작성함. 입력은 타임스탬프가 붙은 시계열이고, `System.currentTimeMillis()` 같은 시간 함수를 직접 호출하지 않음.
  판정 로직을 바꿀 때는 단위 테스트도 함께 작성함.
- 기준값(60°/10초, 5km/h, 5초 전 등)은 코드에 하드코딩하지 않고 `domain/config`의 설정 객체에서 읽음 (서버에서 내려받아 교체 가능해야 함).
- 운행 ID와 이벤트 ID는 클라이언트에서 생성하는 UUID. 업로드는 멱등이 전제이므로 재전송해도 문제없게 작성함.
- 지원하지 않는 신호는 측정에서 제외하고 `supported` 목록에 반영함. 앱이 크래시하거나 0점 처리되면 안 됨.
- ViewModel은 `StateFlow<XxxUiState>` 하나를 노출하고, Composable은 `collectAsStateWithLifecycle()`로 구독함.
  화면은 Screen(상태 연결)과 Content(stateless UI)로 분리함.

## 주행 안전 원칙 (어기면 안 됨)

- **주행 중에는 점수·알림 화면을 띄우지 않음.** 상세 화면은 정차 또는 기어 P일 때만 보여줌.
- 음성은 **시동 시 1회(미션 안내), 운행 종료 시 1회(결과)** 만 재생함. 내비 음성과 겹치지 않게 처리함.
- '빨리 도착'처럼 위험한 운전을 유도하는 미션이나 문구는 만들지 않음.
- UI 기준: 다크 테마(`ui/theme`), 큰 글씨와 큰 터치 영역(64dp 이상), 최소한의 텍스트. 목업은 1280×720 기준.
- Pleos 화면에서 앱은 전체 화면이 아니라 오른쪽 영역에만 그려짐(왼쪽은 차량 상태 패널). 고정 크기 대신 `fillMaxSize`, `weight` 기반의 유동 레이아웃으로 만듦.

## Pleos SDK 주의사항

- SDK API 이름이나 시그니처는 **추측하지 않음.** 문서나 SDK 클래스(Android Studio에서 decompiled 보기)로 확인한 뒤 사용함.
- 차량 속성 접근 권한은 `AndroidManifest.xml`에 선언함 (`pleos.car.permission.*`).

## 작업 방식

- `feature/*` 브랜치에서 작업하고 `develop`으로 PR을 보냄.
- 작업 전에 `plan.md`의 해당 단계를 확인하고, 끝나면 체크박스와 결정 기록을 갱신함.
- MVP 기한(11.15)이 촉박함. P0 범위 밖의 기능은 먼저 제안하지 말고 `plan.md`의 2차 목록에 적어둠.
