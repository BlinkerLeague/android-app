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

- Android Studio <!-- TODO: 버전 (예: Hedgehog 이상) -->
- JDK 17
- Android Automotive 에뮬레이터 또는 Pleos 지원 차량/디바이스

### Pleos Connect 에뮬레이터 설정

Pleos Connect Emulator(v2.1.0, API 34) 설치, AVD 생성·실행, Virtual Console 사용법은
👉 **[docs/emulator-setup.md](docs/emulator-setup.md)** 를 참고하세요.

```bash
# 에뮬레이터 실행 후 Virtual Console 사용
adb forward tcp:8088 tcp:8088   # 이후 http://127.0.0.1:8088/ 접속
```

### 빌드 및 실행

```bash
git clone <!-- TODO: 저장소 URL -->
cd BlinkerLeague

# 디버그 빌드
./gradlew :automotive:assembleDebug

# 연결된 기기/에뮬레이터에 설치
./gradlew :automotive:installDebug
```

> Vehicle SDK는 Pleos Nexus 저장소(`https://nexus-playground.pleos.ai/repository/maven-releases/`)에서 받아옵니다.
> <!-- TODO: 접근에 인증이 필요하다면 설정 방법 기재 -->

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
- 머지 방식: <!-- TODO: Squash merge / Merge commit 중 선택 -->

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
