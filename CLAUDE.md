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
  - API 요청·응답 형식은 서버 Swagger 명세(`docs/api/openapi.json`, 서버 저장소에도 같은 파일)가 기준. 앱 DTO도 그 형식에 맞춤
  - **서버 작업은 이 팀이 하지 않음.** 형식 변경이나 서버에 필요한 것은 `docs/server-requests.md`에 적어 서버 팀에 전달함 (서버 저장소는 수정하지 않음)
  - 인증: `POST /api/drivers`에서 받은 토큰을 `Authorization: Bearer <token>` 헤더로 보냄
- 앱은 **판정된 이벤트만** 서버로 보냄. 원시 위치 좌표는 절대 서버로 보내지 않음

## 아키텍처: MVVM + Jetpack Compose

```
com.example.blinkerleague
├── data/
│   ├── vehicle/      # Vehicle SDK 래퍼: 지원 여부 확인(checkXXXCapability), 상태 변화 → Flow
│   ├── location/     # 위치 1초 수집 → Flow (Pleos FusedLocation SDK, docs/spikes/fused-location.md)
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
- **권한은 실제로 쓰는 것만 `src/main`에 선언함.** Pleos 심사는 승인되지 않은 Vehicle SDK 권한을 선언하면 탈락시킴.
  SDK 조사용 코드(프로브, `ProbeService`)와 권한은 `automotive/src/debug`에만 둠 (릴리스 APK에 들어가지 않음).
  main 코드는 debug 코드를 직접 참조하지 않고 `DebugTools`(debug: 실제 구현, release: 빈 구현)로만 부름.

## 작업 방식

- `feature/*` 브랜치에서 작업하고 `develop`으로 PR을 보냄.
- 동작이 바뀌는 코드는 TDD(red → green → refactor)로 만듦. 역할별 서브에이전트 `.claude/agents/`(lead · tester · developer · reviewer)가 규칙의 원본임.
- **모든 개발 작업(기능, 버그 수정, 점검 · 마무리, 리뷰 지적 수정)은 서브에이전트로 함:** lead → tester → developer → reviewer.
  1. **lead**: `plan.md` · screen-plan · 코드를 읽고(필요하면 lint · 테스트를 돌려 현황을 봄) 작업을 나눔. 결과는 TDD 사이클 목록과 "테스트 없이 고칠 것" 목록.
  2. **tester(red)**: 동작이 바뀌는 사이클마다 실패 테스트 하나를 쓰고 실패를 확인해 `test:` 커밋.
  3. **developer(green · refactor)**: 그 테스트를 통과시키고 `feat:` · `fix:` 커밋. 동작이 바뀌지 않는 일(lint 경고, 문구, 문서, Preview)은 테스트 없이 developer가 바로 고치고 `fix:` · `docs:` · `chore:` 커밋.
  4. 2~3을 사이클마다 번갈아 반복하고, 끝나면 tester가 전체 단위 테스트 · 빌드(· lint)로 검증함.
  5. **reviewer**: 브랜치 전체를 검토하고 결과를 `review/<YYYY-MM-DD>-<브랜치 이름의 / 를 - 로>.md`로 남김. "수정 후 머지"면 lead → tester → developer로 고친 뒤 reviewer가 같은 파일에 "재검토" 절을 덧붙임.
  6. **메인 세션**: 역할을 대신하지 않음. 에이전트를 부르고, 결과를 다음 역할에 전달하고, 리뷰 파일 커밋 · push · PR · 머지만 함. 머지 전에 develop과 충돌 여부와 테스트 결과를 확인함.
- 서브에이전트 호출: Agent 도구로 역할 이름(`lead` 등)을 부름. 세션 시작 뒤에 에이전트 파일이 생겨 이름으로 부를 수 없으면 general-purpose 에이전트에게 해당 `.claude/agents/<역할>.md`를 먼저 읽고 따르라고 지시함.
  에이전트들은 같은 작업 트리를 쓰므로 한 번에 하나씩 순서대로 실행함(병렬 실행은 읽기만 하는 reviewer끼리만). 에이전트는 브랜치를 바꾸거나 push하지 않음.
- 테스트 작성 규칙(한글 이름, `// given` · `// when` · `// then`)은 `tester.md`, green · refactor 규칙은 `developer.md`를 따름.
  red(`test:`) · green(`feat:`/`fix:`) · refactor(`refactor:`)는 커밋을 나눔. develop에는 squash merge되므로 이 커밋들은 PR의 Commits 탭에 남음.
- 서버 API 형식은 `docs/api/openapi.json`(서버 Swagger에서 받은 명세)이 기준. 화면과 API 대응은 `docs/api/README.md`.
- 작업 전에 `plan.md`의 해당 단계를 확인하고, 끝나면 체크박스와 결정 기록을 갱신함.
- MVP 기한(11.15)이 촉박함. P0 범위 밖의 기능은 먼저 제안하지 말고 `plan.md`의 2차 목록에 적어둠.
