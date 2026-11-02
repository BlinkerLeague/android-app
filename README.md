# BlinkerLeague

> <!-- TODO: 프로젝트를 한 줄로 소개해주세요. 예) 방향지시등 사용 습관을 게임처럼 즐기며 개선하는 Android Automotive 앱 -->

<!-- TODO: 스크린샷 또는 GIF 추가 -->
<!-- ![screenshot](docs/screenshot.png) -->

## 📌 주요 기능

- <!-- TODO: 기능 1 -->
- <!-- TODO: 기능 2 -->
- <!-- TODO: 기능 3 -->

## 🛠 기술 스택

| 구분 | 내용 |
| --- | --- |
| Language | Kotlin 1.9.22 |
| Platform | Android Automotive OS (minSdk 28 / targetSdk 34) |
| Build | Gradle (Kotlin DSL), AGP 8.2.2 |
| Vehicle SDK | Pleos Playground Vehicle SDK 2.3.2 |
| Architecture | <!-- TODO: 예) MVVM --> |

## 🚀 시작하기

### 요구 환경

| 항목 | 필요한 것 | 비고 |
| --- | --- | --- |
| Android Studio | 최신 버전 | |
| JDK | **17** | Android Studio에 들어 있는 JDK(최근 버전은 21~25)로는 Gradle 8.2가 돌지 않음 |
| Android SDK | Platform **API 34**, Build-Tools 34 | 첫 빌드 때 Gradle이 자동으로 설치함 (SDK 라이선스에 동의돼 있어야 함) |
| 시스템 이미지 | **Pleos Connect v2.1.0 (API 34)** | 일반 폰·Automotive 이미지로는 Vehicle SDK가 동작하지 않음 |
| Vehicle / TTS SDK | `ai.pleos.playground:Vehicle`, `TextToSpeech` | Pleos Nexus에서 자동으로 받음. **인증 없이 받을 수 있음** |
| 가속 | Windows: WHPX(Windows 하이퍼바이저 플랫폼) | `emulator -accel-check`로 확인 |

### 1. 경로에 한글이 없도록 하기 (Windows 필수)

Windows 사용자 이름이 한글이면(`C:\Users\홍길동\...`) 빌드나 테스트가 실패합니다.

| 증상 | 원인 |
| --- | --- |
| `Your project path contains non-ASCII characters` | 프로젝트 경로에 한글이 들어 있음 |
| `testDebugUnitTest`에서 `Could not write standard input to Gradle Test Executor` | Gradle 캐시(`C:\Users\<한글>\.gradle`) 경로에 한글이 들어 있음 |

**두 가지를 모두** 영문 경로로 옮겨야 합니다. 프로젝트만 옮기면 테스트는 계속 실패합니다.

```powershell
# 1) 프로젝트를 영문 경로에 clone (예: C:\dev)
git clone <저장소 URL> C:\dev\blinkerleague\android-app

# 2) Gradle 캐시 위치를 영문 경로로 지정 (한 번만 하면 됨. 설정 후 Android Studio와 터미널을 다시 열 것)
setx GRADLE_USER_HOME C:\gradle-home
```

> Android SDK 경로(`C:\Users\<한글>\AppData\Local\Android\Sdk`)는 그대로 둬도 됩니다.
> `android.overridePathCheck=true`로 검사만 끄는 방법은 쓰지 마세요. APK 빌드는 되지만 단위 테스트가 실패합니다.

### 2. JDK 17 지정

- **Android Studio**: Settings → Build, Execution, Deployment → Build Tools → **Gradle** → **Gradle JDK**에서 JDK 17 선택
  (목록에 없으면 같은 메뉴에서 Download JDK → 버전 17)
- **터미널**에서 `./gradlew`를 쓸 때

  ```bash
  # macOS
  export JAVA_HOME=$(/usr/libexec/java_home -v 17)
  # Windows (Git Bash)
  export JAVA_HOME="/c/Program Files/Java/jdk-17.0.1"   # 설치된 경로에 맞게
  ```
  ```powershell
  # Windows (PowerShell)
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.1"
  ```

> Android Studio가 AGP 업그레이드(Upgrade Assistant)를 권하더라도 팀 합의 없이 올리지 마세요. Kotlin·Compose 컴파일러 버전이 함께 맞물려 있습니다.

### 3. Pleos Connect 에뮬레이터 설치

Pleos Connect Emulator(v2.1.0, API 34) 시스템 이미지 설치, AVD 생성·실행, Virtual Console 사용법은
👉 **[docs/emulator-setup.md](docs/emulator-setup.md)** 를 참고하세요.

요약: SDK Manager → SDK Update Sites에 Pleos 이미지 URL 추가 → SDK Platforms에서 API 34의 **Pleos Connect** 이미지 설치 → Device Manager에서 Automotive / Pleos Connect v2.1.0 AVD 생성 (RAM 4GB, Cold boot)

```bash
# 에뮬레이터 실행 후 Virtual Console 사용
adb forward tcp:8088 tcp:8088   # 이후 http://127.0.0.1:8088/ 접속
```

> Windows에서 `adb`를 찾을 수 없다면 `%LOCALAPPDATA%\Android\Sdk\platform-tools`를 PATH에 추가하세요.

### 4. 빌드 및 실행

```bash
# 디버그 빌드
./gradlew :automotive:assembleDebug

# 실행 중인 Pleos 에뮬레이터에 설치
./gradlew :automotive:installDebug

# 단위 테스트
./gradlew :automotive:testDebugUnitTest
```

Android Studio에서는 상단 기기 목록에서 `Pleos_Connect_v2.1.0_API_34`를 고르고 **Run ▶**(`automotive` 구성)을 누르면 됩니다.

### 5. 화면 확인 방법

| 방법 | 용도 | 에뮬레이터 필요 |
| --- | --- | --- |
| **Compose Preview** | 화면 디자인만 빠르게 확인. `DemoScreen.kt`, `MissionsScreen.kt`, `MissionCard.kt` 등을 열고 Split/Design 탭 | ✗ |
| **데모 화면 (시나리오 재생)** | 앱 첫 화면(시나리오 재생)에서 "좌회전 + 깜빡이" 같은 시나리오를 고르면 가짜 신호로 운행을 재생하고 결과 화면으로 넘어감. 미션 화면은 첫 화면의 버튼으로 이동 | ✓ |
| **Virtual Console** | 기어·속도·방향지시등 등 차량 신호를 브라우저에서 직접 바꿔 가며 확인 | ✓ |

> Pleos 화면에서 앱은 오른쪽 영역에만 그려집니다. Preview 크기(960×720)는 이 영역 기준입니다.

## 📁 프로젝트 구조

```
BlinkerLeague
├── automotive/                     # Automotive 앱 모듈
│   └── src/main/java/com/example/blinkerleague/
│       └── MainActivity.kt
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

<!-- TODO: 패키지 구조가 잡히면 업데이트 -->

## 🌿 브랜치 전략

| 브랜치 | 설명 |
| --- | --- |
| `main` | 배포 가능한 안정 버전 |
| `develop` | 다음 버전 개발 통합 브랜치 |
| `feature/기능명` | 기능 개발 (`develop`에서 분기) |
| `fix/버그명` | 버그 수정 |

- 작업은 `feature/*` 브랜치에서 진행한 뒤 `develop`으로 PR을 보냅니다.
- 머지 방식: **Squash merge** (PR당 커밋 1개로 `develop`에 반영, 머지 후 브랜치 삭제)

## 📝 커밋 컨벤션

```
<type>: <subject>

<body (선택)>
```

| Type | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변화 없음) |
| `style` | 코드 포맷팅, 세미콜론 누락 등 (로직 변화 없음) |
| `docs` | 문서 수정 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드 설정, 의존성, 기타 작업 |

**예시**

```
feat: 방향지시등 점등 이벤트 감지 추가
fix: 앱 재시작 시 점수가 초기화되는 문제 수정
```

- 제목은 50자 이내로 간결하게 작성합니다.
- 제목 끝에 마침표를 붙이지 않습니다.
- 이슈와 연결할 경우 본문에 `#이슈번호`를 적습니다.

## 🎨 코드 컨벤션

- [Kotlin 공식 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html)을 따릅니다.
- 클래스: `PascalCase` / 함수·변수: `camelCase` / 상수: `UPPER_SNAKE_CASE`
- 리소스 네이밍
  - 레이아웃: `activity_main.xml`, `fragment_home.xml`, `item_score.xml`
  - 아이콘·이미지: `ic_blinker.xml`, `img_background.webp`
  - 문자열 ID: `화면_용도` (예: `home_title`)
- <!-- TODO: ktlint / detekt 사용 여부 -->
