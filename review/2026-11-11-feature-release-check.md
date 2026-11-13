# 리뷰: feature/release-check (2026-11-11)

- 기준: develop...feature/release-check (14개 커밋)
- 범위: MVP 제출 전 release 점검. 연결 사양 함수 분리(`connectionSpecsFor`)·테스트 3개, serverUrl 형식 검사, release `network_security_config` 빌드 때 생성(http면 그 호스트만 평문), 서명 정보 검증, R8 끔 기록, README 6절 · 보안 대응표 · plan.md
- 판단: 머지 가능 (아래 지적은 모두 낮음. 문서 · 빌드 스크립트 메시지 수준이라 머지 뒤 처리해도 됨)

## 지적 사항

### 1. 서명 정보 검증이 `storeFile`이 있을 때만 동작함. README · 주석은 "일부만 있으면 실패"라고 적음 (심각도: 낮음)
- **위치:** `automotive/build.gradle.kts:107-118`(`if (releaseStoreFile != null)` 안에서만 검사), `README.md:165`("4줄 중 일부만 있거나 … 빌드가 실패합니다")
- **문제:** `storePassword` · `keyAlias` · `keyPassword`는 있는데 `blinker.release.storeFile`이 없거나 키 이름에 오타가 있으면(예: `blinker.release.storefile`) 검사를 건너뛰고 조용히 서명 없는 APK가 나온다.
- **실패 시나리오:** 출시 키를 만들어 4줄을 적다가 첫 줄 키 이름을 틀림 → 빌드는 성공하고 `automotive-release-unsigned.apk`만 생김. 실패시키려던 경우("서명 없는 APK가 조용히 나오지 않게")가 그대로 남는다.
- **제안:** `blinker.release.*` 4개 중 하나라도 있으면 4개 모두 요구하도록 조건을 넓히거나, README · 주석을 "`storeFile`이 있는데 나머지가 빠졌거나 파일이 없으면 실패"로 실제 동작에 맞춘다. 메시지에 비밀번호 값을 넣지 않는 현재 방식은 유지.

### 2. 보안 대응표의 "암호화 프로토콜 · TLS 1.2 이상"이 ✅인데 1차 제출 빌드는 평문(http)으로 통신함 (심각도: 낮음)
- **위치:** `docs/security-checklist.md:31-32`, 같은 파일 "남은 일"(`1차 제출은 로컬 서버(http)에 연결하는 빌드`)
- **문제:** 같은 문서 안에서 필수 항목 상태는 ✅이고 남은 일에는 1차 제출이 http라고 적혀 있어, 표만 보면 제출 빌드가 TLS를 지키는 것으로 읽힌다.
- **실패 시나리오:** 제출 서류나 심사 대응을 표 기준으로 쓰면 "평문 없음"으로 잘못 답하게 된다. 실제 `-Pblinker.serverUrl=http://10.0.2.2:8080` 빌드는 그 호스트로 평문 통신한다.
- **제안:** 두 행을 ⚠️(또는 "조건부")로 바꾸고 "https 주소로 빌드하면 충족, 1차 제출 빌드(http 로컬 서버)는 미충족 — 11.11 사용자 결정"처럼 상태와 이유를 함께 적는다.

### 3. plan.md의 새 하위 체크박스 3개가 모두 비어 있음 (심각도: 낮음)
- **위치:** `plan.md:145-147`
- **문제:** tester가 release 점검(권한 14개 · 디버그 기능 · 서명 없음 빌드), 로컬 서버 연결(생성 xml 4경우, 10.0.2.2 평문 연결), 에뮬레이터 설치를 확인했는데 체크되지 않았다.
- **실패 시나리오:** 다음 세션의 lead가 끝난 작업을 남은 일로 보고 다시 나눈다.
- **제안:** 확인된 항목은 `[x]`로 바꾸고, 확인하지 못한 부분(release 홈 "시나리오 재생" 숨김 · guardBypass 무시를 화면으로 보지 못함, 코드로만 확인)은 하위 항목이나 메모로 남긴다.

### 4. README의 zipalign · apksigner 명령이 PATH에 있다고 가정함 (심각도: 낮음, tester 지적과 같음)
- **위치:** `README.md:167-174`
- **문제:** "`build-tools`는 Android SDK의 `build-tools/<버전>`"이라고만 적혀 있고, 그 안에 `zipalign` · `apksigner`가 있다는 것과 PATH에 없을 때의 실제 경로 예시가 없다.
- **실패 시나리오:** 기본 macOS 셸에서 그대로 붙여 넣으면 `zipalign: command not found`.
- **제안:** `BT=~/Library/Android/sdk/build-tools/34.0.0` 같은 예시를 한 줄 두고 `$BT/zipalign`, `$BT/apksigner`로 쓴다(Windows 경로도 한 줄).

### 참고 (지적 아님)
- debug의 `network_security_config`는 여전히 10.0.2.2 · localhost · 127.0.0.1 고정이라, LAN IP(http://192.168.x.x)로 빌드하면 release는 연결되고 debug는 평문이 막히는 비대칭이 있다. 이번 변경 이전부터의 동작이고 문서(보안 대응표 "디버그 빌드는 로컬 서버만 허용")와도 맞으므로 지금 고칠 필요는 없다. 필요해지면 debug도 같은 생성 태스크를 쓰는 방향을 검토.
- 서명 검증은 설정 단계에서 돌기 때문에 `local.properties`의 서명 정보가 깨져 있으면 debug 빌드 · 단위 테스트도 실패한다. 의도로 보이며 문제 삼지 않음.

## 확인한 것
- **평문 허용 범위:** 생성 태스크는 `base-config cleartextTrafficPermitted="false"`를 항상 쓰고, `domain-config`는 스킴이 http(대소문자 무시)일 때만 `uri.host` 하나를 `includeSubdomains="false"`로 넣는다. 현재 설정(주소 없음)으로 생성된 `build/generated/networkSecurity/release/xml/network_security_config.xml`이 base-config만 있는 것을 확인.
- **XML 주입:** 호스트는 `java.net.URI` 파싱을 통과한 값이고 형식 검사에서 http/https · 호스트 필수를 강제하므로 `<`, `"` 등이 들어갈 수 없다. 같은 검사로 `buildConfigField`의 문자열 깨짐도 막힘.
- **매니페스트:** `src/main/AndroidManifest.xml`에 `networkSecurityConfig` · `usesCleartextTraffic` · 새 권한 없음. `src/release/AndroidManifest.xml`은 `networkSecurityConfig`만 추가. 병합된 release 매니페스트 권한 14개가 보안 대응표(직접 9 + 라이브러리 5)와 일치.
- **Gradle 태스크 입력 · 출력:** `serverUrl`은 `@Input`, `outputDir`은 `@OutputDirectory`이고 태스크가 매번 파일 전체를 다시 쓰므로 serverUrl이 바뀌면 재생성된다(http → https로 바꿔도 이전 domain-config가 남지 않음). `res.srcDir`에 넣은 생성 디렉터리를 쓰는 태스크가 모두 생성 태스크 뒤에 오는지 `--dry-run`으로 확인: `mergeReleaseResources`, `mapReleaseSourceSetPaths`, `lintRelease`, `lintVitalAnalyzeRelease`, `testReleaseUnitTest` 모두 `generateReleaseNetworkSecurityConfig` 포함. 설정 캐시는 `gradle.properties`에서 켜져 있지 않음(켜더라도 `-P` 값 변경은 캐시를 무효화함).
- **비밀값:** 서명 오류 메시지는 `storeFile` 경로와 빠진 키 이름만 출력하고 비밀번호 값은 넣지 않음. serverUrl 오류 메시지는 주소만 출력(비밀값 아님). `local.properties` · 키 파일(`.jks`/`.keystore`)은 추적되지 않음. 작업 트리의 `.idea/gradle.xml` 수정과 `.idea/AndroidProjectSystem.xml` · `.idea/runConfigurations.xml`는 브랜치 커밋에 들어 있지 않음(커밋 시 함께 넣지 않도록 주의).
- **앱 코드:** `connectionSpecsFor`는 https(대소문자 무시)면 `MODERN_TLS`, 아니면 `CLEARTEXT`. 빌드 검사로 http/https만 들어오므로 그 밖의 스킴이 CLEARTEXT로 빠지는 경우는 없음. 인증서 검증을 끄는 코드 없음.
- **릴리스 디버그 기능:** 홈 "시나리오 재생"은 `showDemoEntry = BuildConfig.DEBUG`, release `DebugTools.onActivityCreated`는 빈 구현이라 `guardBypass` · `parkEndSec` 엑스트라를 처리하지 않음(코드로 확인). `AppLog`는 `BuildConfig.DEBUG`일 때만 로그.
- **TDD 커밋 순서:** `test:` 9f11806 → `feat:` c945d6c → `test:` fcece2c → `feat:` 13d6cde → `refactor:` 8dd1c3a → `test:` 849f95c → `feat:` 3158e23. 테스트 하나당 green 하나이고, 각 green이 그 테스트가 요구한 만큼만 바꿈(MODERN_TLS 고정 → 스킴 분기 → 대소문자 무시). 빌드 스크립트 · 문서 변경은 `fix:` · `docs:` · `chore:`로 분리.
- **테스트 규칙:** `HttpClientFactoryTest` 3개 모두 한글 이름, `// given` · `// when` · `// then`. 시간 · 코루틴 의존 없음.
- **CLAUDE.md:** 주행 안전 · 레이어 규칙에 닿는 변경 없음. 서버 형식(DTO) 변경 없음, `docs/server-requests.md` 갱신 필요 없음. R8 끔 · release 로컬 서버 연결은 plan.md 결정 기록에 남음.
- **문서와 동작:** README 6절의 서버 주소 4경우(없음 · https · http · 형식 오류) 설명이 빌드 스크립트와 일치. debug `network_security_config` 주석도 새 방식과 일치. 지적 1 · 2만 불일치.

## 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:assembleRelease` → BUILD SUCCESSFUL. 단위 테스트 192개, 실패 0 · 오류 0 (40개 결과 파일 합산). assembleRelease 안의 `lintVitalRelease` 통과.
- `./gradlew :automotive:<task> --dry-run` (lintRelease, mapReleaseSourceSetPaths, mergeReleaseResources, lintVitalAnalyzeRelease, testReleaseUnitTest) → 모두 `generateReleaseNetworkSecurityConfig` 포함.
- 병합된 release 매니페스트(`build/intermediates/merged_manifest/release/AndroidManifest.xml`) 권한 14개 · `networkSecurityConfig` 확인.
- 실행하지 않은 것: http · https · 형식 오류 serverUrl로 다시 빌드(tester가 4경우 확인함), 에뮬레이터 설치 · 연결(tester가 확인함), `lintDebug`(tester 결과 오류 0 · 경고 15).

## 재검토 (2026-11-11)

- 기준: develop...feature/release-check (18개 커밋. 지난 검토 뒤 1fa7755 · 03b7104 · 85cd4ec · e6713e8 4개 추가)
- 판단: 머지 가능

### 지적 사항 처리
1. **서명 검증 범위:** 해결 (1fa7755). `blinker.release.*` 4개 중 하나라도 값이 있으면 4개를 모두 요구하고, 빠진 키 이름만 메시지에 나옴(값은 출력하지 않음). 그 뒤에 `storeFile` 파일이 있는지 검사함. `signingConfigs` · `buildTypes`도 같은 `hasSigning` 조건을 써서 검사와 서명 적용 조건이 일치함. README 6절과 코드 주석도 이 동작으로 바뀜. 남는 경우는 4줄 키 이름을 모두 틀린 경우뿐이고, 이때는 "하나도 없음"으로 보고 서명 없이 빌드되는데 README에 적힌 동작과 같으므로 문제 삼지 않음.
2. **보안 대응표 상태:** 해결 (85cd4ec). 두 행이 ⚠️ "조건부"로 바뀌었고, https로 빌드하면 충족, 1차 제출 빌드는 평문 허용(11.11 사용자 결정)이라는 이유가 함께 적힘.
3. **plan.md 체크박스:** 해결 (e6713e8). 세 항목을 `[x]`로 표시했고, 코드로만 확인한 것(시나리오 재생 숨김 · guardBypass 무시)과 실서버 연결 E2E를 아직 확인하지 않았다는 메모를 남김.
4. **README build-tools 경로:** 해결 (03b7104). `BT=~/Library/Android/sdk/build-tools/34.0.0` 예시와 `$BT/zipalign` · `$BT/apksigner`, Windows 경로 안내가 추가됨.

### 새로 생긴 문제
- 없음. 빌드 스크립트 변경은 설정 단계의 검사뿐이라 앱 동작 · 생성되는 `network_security_config` · 매니페스트에 영향이 없음. 새 비밀값 출력 경로 없음.
- 작업 트리의 `.idea/gradle.xml` 수정과 `.idea/AndroidProjectSystem.xml` · `.idea/runConfigurations.xml`(추적되지 않는 파일)는 여전히 커밋되지 않은 상태. 리뷰 파일을 커밋할 때 함께 넣지 않도록 주의.

### 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:assembleRelease` → BUILD SUCCESSFUL. 단위 테스트 192개, 실패 0 · 오류 0 (현재 `local.properties`에는 서명 정보가 없음 → 서명 없는 release).
- 서명 정보가 일부만 있는 경우의 실패 메시지는 직접 재현하지 않음(`local.properties`를 고쳐야 해서 리뷰어 범위 밖). developer가 네 경우 모두 확인했고, 코드상으로도 메시지에는 키 이름과 `storeFile` 경로만 들어감.
