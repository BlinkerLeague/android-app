# 리뷰: feature/readme-release-build (2026-11-11)

- 기준: develop...feature/readme-release-build (1개 커밋, c319166)
- 범위: README 6절(출시용 APK)을 키 만들기 → `local.properties` 4줄 → 빌드 → 서명 확인 → 설치 단계(6-1~6-5)로 다시 씀. 23줄 "해야 할 일"을 그 단계에 맞춤
- 판단: **머지 가능** (아래 지적은 모두 낮음. 같은 PR에서 고치면 더 좋지만 막을 정도는 아님)

## 지적 사항

### 1. keytool은 "키 비밀번호"를 묻지 않을 가능성이 큼 (심각도: 낮음)
- 위치: README.md:166 (6-1 표의 마지막 줄), README.md:181 (6-2 `keyPassword`)
- 문제: JDK 9부터 keytool의 기본 키 저장소 형식은 PKCS12입니다(`.jks` 확장자여도 `-storetype`을 주지 않으면 PKCS12). PKCS12에서는 키 비밀번호를 따로 두지 못해서 keytool이 `Enter key password for <alias> (RETURN if same as keystore password)` 질문을 건너뛰고 키 저장소 비밀번호를 그대로 씁니다. JDK 문서 기준 질문 순서는 키 저장소 비밀번호 → 다시 입력 → 이름 → 조직 단위 → 조직 → 구/군/시 → 시/도 → 국가 코드 → 맞습니까?이고, JDK 17에서는 여기서 끝납니다. (실행 확인은 하지 않았고, 키도 새로 만들지 않음)
- 실패 시나리오: 처음 하는 사람이 표의 마지막 줄을 기다리다가 "끝난 건가?" 하고 헷갈림. 더 중요한 것은 6-2에서 `keyPassword`에 무엇을 적을지 README에 직접 나오지 않아서 다른 값을 적으면 빌드가 서명 단계에서 실패함
- 제안: 표의 마지막 줄을 "(JDK 버전에 따라 나오지 않을 수 있음) 나오면 엔터"로 바꾸고, 6-2에 "`keyPassword`는 `storePassword`와 같은 값"을 한 줄 적음

### 2. 6-1이 JDK 17 지정(6-3)보다 앞에 있어 keytool이 없거나 다른 JDK일 수 있음 (심각도: 낮음)
- 위치: README.md:156-161, README.md:193
- 문제: `export JAVA_HOME=…`는 6-3에서야 나옵니다. macOS의 `/usr/bin/keytool`은 시스템 기본 Java를 쓰는 스텁이라, Android Studio 내장 JDK만 있는 PC에서는 "Java를 설치하라"는 창이 뜨거나 실행되지 않습니다. 또 6-1은 "macOS 터미널 앱"만 안내해서 Windows(README 1·2절에서 안내하는 대상)는 어디서 실행하는지 없습니다
- 실패 시나리오: 시스템 JDK가 없는 PC에서 6-1의 `keytool`이 `command not found`나 Java 설치 안내로 멈춤
- 제안: 6-1 명령 앞에 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`를 두고 `"$JAVA_HOME/bin/keytool"`로 부르거나, "keytool이 없으면 2절의 JDK 17을 먼저 지정"을 한 줄 적음. Windows는 2절 참고 한 줄. "Android Studio 안의 터미널이 아니라"는 근거가 있으면 이유를 짧게 적고, 없으면 "터미널에서"로 줄여도 됨

### 3. Android Studio "Generate Signed APK"로 만들면 6-3~6-5와 결과가 다름 (심각도: 낮음)
- 위치: README.md:202
- 문제: 이 메뉴는 `-Pblinker.serverUrl`을 넘기지 않으므로 `local.properties`의 `blinker.serverUrl`이 없으면 Fake 모드 APK가 나오고, 결과 위치도 기본값이 `automotive/release/`라서 6-4·6-5의 경로(`automotive/build/outputs/apk/release/`)와 다릅니다
- 실패 시나리오: 메뉴로 만든 사람이 6-4 명령을 그대로 실행하면 파일이 없다고 나오거나, 로컬 서버에 붙을 줄 알았는데 Fake로 동작함
- 제안: "이 방법은 서버 주소를 `local.properties`의 `blinker.serverUrl`에서만 읽고, 결과 위치가 다르다"를 덧붙이거나 이 줄을 빼서 한 가지 방법만 안내

### 4. 6-2 Windows 경로 표기 (심각도: 낮음)
- 위치: README.md:175
- 문제: `local.properties`는 Java Properties 형식이라 `\`가 이스케이프 문자입니다. Windows 사용자가 `C:\Users\...\keys\...jks`를 그대로 적으면 경로가 깨져 "storeFile 파일이 없습니다"로 빌드가 실패합니다(build.gradle.kts:106-108이 막아 주므로 조용히 틀리지는 않음)
- 제안: "Windows는 `C:/Users/<사용자>/keys/...`처럼 `/`로 적음" 한 줄

### 5. `adb uninstall`이 실패 메시지를 낼 수 있음 (심각도: 낮음)
- 위치: README.md:219-221
- 문제: debug 앱이 설치되어 있지 않으면 `adb uninstall`이 `Failure [DELETE_FAILED_INTERNAL_ERROR]`(또는 Unknown package)를 출력합니다. 다음 줄 설치에는 영향이 없지만 처음 하는 사람은 멈출 수 있음
- 제안: "앱이 없으면 Failure가 나와도 괜찮음" 한 줄

## 확인한 것

- **명령과 실제 동작 일치**
  - 서명 4줄 키 이름(`blinker.release.storeFile/storePassword/keyAlias/keyPassword`)과 "하나라도 있으면 4줄 모두, 빠진 키 이름만 알림, storeFile 없으면 실패, 하나도 없으면 서명 없이" — automotive/build.gradle.kts:96-125와 같음. 오류 메시지에 비밀번호 값이 들어가지 않는 것도 확인
  - `storeFile`은 `file()`로 읽으므로 `~`가 펼쳐지지 않음 → "절대 경로로 적음" 안내가 맞음
  - `-Pblinker.serverUrl`가 `local.properties`의 `blinker.serverUrl`보다 우선하고, 비어 있으면 Fake, http(s)·호스트가 없으면 빌드 실패(build.gradle.kts:18-35), http면 그 호스트만 평문 허용·https면 평문 없음(build.gradle.kts:48-60, src/release/AndroidManifest.xml) — README 설명과 같음
  - 결과 파일명 `automotive-release.apk` / 서명 없을 때 `automotive-release-unsigned.apk` — AGP 기본 이름과 같고, 메인 세션이 이 PC에서 서명된 `automotive-release.apk` 생성을 확인함
  - `BT=~/Library/Android/sdk/build-tools/34.0.0` — 이 PC에 34.0.0이 있음. `apksigner verify --print-certs`의 `Signer #1 certificate DN` 줄과 `C=KR`로 debug 키(`C=US, O=Android, CN=Android Debug`)와 구분하는 방법이 맞음
  - 6-5: debug·release의 `applicationId`가 같아(`com.example.blinkerleague`, suffix 없음) 서명이 달라 덮어쓸 수 없으므로 uninstall 후 설치 안내가 맞음. 데이터가 지워진다는 경고도 있음
  - 기본 빌드 명령이 `-Pblinker.serverUrl=http://10.0.2.2:8080`인 것은 plan.md 결정 기록(2026-11-11 "1차 제출 release는 로컬 서버(http)에 연결")과 docs/security-checklist.md:63과 맞음
- **기존 내용 보존**: 서명 없는 APK는 설치 불가, debug 키 서명 절차(zipalign → apksigner sign → adb install -r), 평문 허용 범위, Fake 모드, 10.0.2.2 설명, 형식이 틀린 주소는 빌드 실패, 릴리스에서 `src/debug`·시나리오 재생 버튼 제외 — 모두 남아 있고 뜻이 바뀌지 않음
- **비밀값 없음**: 비밀번호는 `…`로만 적힘. `pass:android`는 공개된 Android debug 키 기본값(기존 내용 그대로). `local.properties`는 .gitignore:15로 제외됨
- **링크·절 번호**: 23줄 "해야 할 일"의 "6. 출시용(release) APK"의 6-1~6-5가 실제 절 제목(README.md:150-224)과 맞고, 단계 이름 순서도 같음. "2. JDK 17 지정" 참조도 실제 절과 맞음. docs/security-checklist.md:57("README '해야 할 일'")·plan.md:143("README 6절") 참조도 그대로 유효
- **문서**: plan.md의 "출시 키 만들기·보관 (사용자)"는 백업이 사용자 몫이므로 미체크 유지가 맞음. 코드 변경이 없어 TDD 커밋·테스트 대상 아님. 커밋 접두사 `docs:` 적절

## 검증

- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug -q` (JDK 17) — 오류 출력 없이 끝남 (README만 바뀌어 영향 없음 확인용)
- `assembleRelease`·`apksigner verify`는 메인 세션이 이 PC에서 확인한 결과를 근거로 함. keytool 질문 순서는 실행하지 않고 JDK 문서 수준의 알려진 동작과 비교만 함(새 키 만들지 않음)

## 재검토 (2026-11-11)

- 기준: develop...feature/readme-release-build (2개 커밋, c319166 → 6304a78). 6304a78은 README.md만 변경(+7 −5)
- 판단: **머지 가능**

### 지적 반영 확인
1. 키 비밀번호 질문 — 표를 "(나오면) 엔터"로 바꿈(README.md:171). 6-2에 "`keyPassword`는 `storePassword`와 같은 값"을 추가함(README.md:186). 반영됨
2. JDK 17 지정 — 6-1 명령 앞에 `export JAVA_HOME=…`을 둠. "macOS 터미널 앱"은 "대화형 터미널"로 바꾸고 Windows는 2절을 참고하게 함. 반영됨
3. Generate Signed APK — 줄을 삭제해 빌드 방법을 하나로 맞춤. 반영됨
4. Windows 경로 — "`/`로 적음(예: `C:/keys/...`)"을 추가함. 반영됨
5. `adb uninstall` — "Failure가 나와도 괜찮음" 주석을 추가함. 반영됨

### 요청에 없던 변경: `export PATH="$JAVA_HOME/bin:$PATH"`
- 문제없음. 오히려 필요한 줄임. macOS의 `/usr/bin/keytool`은 `JAVA_HOME`을 따르는 스텁이지만, PATH 앞에 JDK 17의 `bin`을 두면 그 JDK의 keytool을 직접 부르게 되어 스텁·다른 JDK 문제를 확실히 피함. 그 터미널 세션에만 적용되고, 6-3의 `JAVA_HOME` 지정과도 충돌하지 않음

### 새 지적
- 없음. 기존 내용(서명 없는 APK, debug 키 서명, 평문 허용 범위, Fake 모드), 23줄 "해야 할 일"의 6-1~6-5 참조, 절 번호는 그대로이고 비밀값도 추가되지 않음

### 검증
- README만 바뀌어 Gradle은 다시 실행하지 않음(직전 검토에서 testDebugUnitTest · assembleDebug 통과)
