# 리뷰: feature/data-extraction-rules (2026-11-10)

- 기준: develop...feature/data-extraction-rules (8개 커밋, 7f88eab..254bea6)
- 범위: API 31+ 클라우드 백업 · 기기 간 전송에서 앱 데이터 전체 제외(`res/xml/data_extraction_rules.xml`), 매니페스트 연결, lint 정리, 보안 대응표 · plan.md 갱신
- 판단: 머지 가능 (아래 지적은 모두 낮음, 후속으로 처리해도 됨)

## 지적 사항

### 1. 매니페스트의 `tools:ignore="DataExtractionRules"`가 연결이 빠지는 회귀도 가림 (심각도: 낮음)
- 위치: `automotive/src/main/AndroidManifest.xml:49` (`tools:ignore="DataExtractionRules"`), 연결은 같은 파일 43줄
- 문제: lint `DataExtractionRules` 이슈 하나가 "fullBackupContent도 두라"는 권고와 "targetSdk 31+인데 dataExtractionRules가 없다"는 경고를 함께 담당한다. 이를 `<application>` 전체에서 끄면 후자도 꺼진다. 그리고 매니페스트 연결은 단위 테스트로 확인하지 않는다(`DataExtractionRulesTest`는 xml 리소스 내용만 읽음).
- 실패 시나리오: 나중에 매니페스트를 정리하다 `android:dataExtractionRules` 줄이 빠져도 lint 경고 없이 테스트가 통과한다. 그러면 API 31+ 기기 간 전송으로 auth.xml · config.xml · blinkerleague.db가 새 기기로 옮겨진다(결정 기록의 "운행 DB만 옮겨지면 새로 등록한 운전자로 예전 운행이 올라갈 수 있음" 상황).
- 제안: 둘 중 하나. (a) 매니페스트 연결을 확인하는 테스트를 추가한다(Robolectric에서 `ApplicationInfo`의 `dataExtractionRulesRes`를 읽거나, `src/main/AndroidManifest.xml`을 파싱해 `android:dataExtractionRules="@xml/data_extraction_rules"`인지 확인). (b) `tools:ignore`를 지우고 모든 도메인을 제외하는 `fullBackupContent` xml을 하나 더 두어 권고 자체를 없앤다(allowBackup=false라 동작은 같고 lint 검사가 계속 살아 있음). 지금 결정 기록대로 (b)를 택하지 않는다면 (a)를 권한다.

### 2. data_extraction_rules.xml 루트의 `tools:ignore="FullBackupContent"` 범위 (심각도: 낮음, 현재는 문제 없음)
- 위치: `automotive/src/main/res/xml/data_extraction_rules.xml:6-7`
- 문제: 루트에 걸어 파일 전체의 FullBackupContent 검사(도메인 이름 오타, 잘못된 path 등)를 끈다.
- 실패 시나리오: 지금은 `DataExtractionRulesTest`가 두 절의 도메인 집합을 9개 상수와 정확히 비교하므로 오타 · 누락 · path 오류(path가 "."이 아니면 집합에서 빠져 실패)가 테스트에서 잡힌다. 다만 이 파일에 `include` 같은 다른 규칙을 추가하면 lint 보호 없이 테스트에만 기대게 된다.
- 제안: 지금 그대로 두되, 규칙을 바꿀 때는 테스트를 함께 고친다는 점을 주석에 한 줄 남기면 충분하다. 오탐 판단 자체는 맞다(아래 "확인한 것" 참고).

### 3. 결정 기록 표의 새 행 위치 (심각도: 낮음)
- 위치: `plan.md:274` (2026-11-10 "백업·기기 간 전송은 …" 행)
- 문제: 새 11-10 행이 기존 11-02 행 뒤에 붙어 날짜 순서가 섞였다(기존 표도 11-02 행이 이미 끝에 있어 원래부터 섞여 있었음).
- 제안: 다음 문서 정리 때 11-10 행들 사이로 옮긴다. 동작에 영향 없음.

### 4. 테스트의 `// given` 구역에 준비 코드가 없음 (심각도: 낮음)
- 위치: `automotive/src/test/java/com/example/blinkerleague/DataExtractionRulesTest.kt:37-38`, `49-50`
- 문제: tester.md는 "준비할 것이 없으면 `// given`을 생략"이라 했는데, 두 테스트 모두 `// given` 아래 설명 주석만 있다.
- 제안: 설명 주석을 `// when` 위나 `// then` 아래로 옮기고 `// given`을 지우거나 그대로 둔다. 규칙 해석 차이 수준.

## 확인한 것
- 도메인 목록: 두 절 모두 root · file · database · sharedpref · external · device_root · device_file · device_database · device_sharedpref 9개, 모두 `exclude` + `path="."`, 오타 없음. 테스트 상수 `ALL_DOMAINS`와 일치하고, 프레임워크 `FullBackup.java`(android-34) `getTokenForXmlDomain`(897~918줄) · `getDirectoryForCriteriaDomain`(954~975줄)이 받는 도메인 목록과도 정확히 같다. `exclude`만 있는 절은 나머지를 포함하므로, 9개 도메인을 모두 빼면 백업 · 전송 대상이 남지 않는다(cache · no_backup은 원래 대상 아님).
- lint FullBackupContent 무시 판단: 프레임워크가 device_* 도메인을 해석하므로 lint의 "Unexpected domain"은 오탐이 맞다. 무시가 타당하다.
- 매니페스트: `xmlns:tools` 추가, `android:allowBackup="false"` 유지, `android:dataExtractionRules="@xml/data_extraction_rules"`, `tools:targetApi="31"`, fullBackupContent 없음. 주석의 설명(API 28~30은 allowBackup=false로 꺼짐, API 31+ 기기 간 전송은 allowBackup으로 막히지 않음)이 실제 동작과 맞다.
- 규칙 xml은 `src/main/res/xml`에 있어 릴리스에 포함된다. debug APK aapt2로 `allowBackup=false`, `dataExtractionRules=@0x7f120000` 확인.
- 권한 · SDK 사용 변경 없음. 원시 좌표 · 토큰 로그 관련 변경 없음. 커밋된 비밀값 없음.
- TDD 커밋 순서: test(7f88eab, 빈 `<data-extraction-rules />`로 R.xml만 생성) → feat(dd76662) → test(96e0574) → feat(d1c72ad) → fix(4a9c156, 매니페스트) → docs(3efb2c7) → fix(e162ed4, lint) → docs(254bea6). red · green 분리 지켜짐.
- 테스트 이름은 한글 문장, when / then 구역 표시, 시간 의존 없음(고정 시계 불필요), Robolectric으로 리소스를 읽음(tester.md 44줄 기준과 맞음).
- `.idea/*`는 브랜치 커밋에 없음(작업 트리 변경으로만 남아 있음).
- `docs/security-checklist.md` "자동 백업 비활성화" 행과 `plan.md` 9b 미룬 항목 · lint 결정 기록(경고 16→15, DataExtractionRules 해소)이 실제 lint 결과와 맞다.
- 서버 형식 · `docs/server-requests.md`: 해당 없음(서버 변경 없음).

## 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:lintDebug` (JDK 17): 성공.
  - 단위 테스트 실패 0. `DataExtractionRulesTest` 2개 통과.
  - lint: 오류 0, 경고 15(GradleDependency 12 · InlinedApi 2 · ObsoleteLintCustomCheck 1). DataExtractionRules · FullBackupContent 없음, 새 경고 없음.
  - `aapt2 dump xmltree` (debug APK): `allowBackup=false`, `dataExtractionRules=@0x7f120000`.
- assembleRelease는 다시 돌리지 않음(개발자 보고: 성공, release APK에서도 같은 리소스 연결과 exclude 18개 확인). 규칙 xml이 `src/main`에 있어 debug와 같은 리소스가 들어간다.

## 재검토 (2026-11-10)

- 기준: develop...feature/data-extraction-rules (10개 커밋, 새 커밋 24e78b9 · e41ccfe)
- 판단: 머지 가능

### 지적 처리 결과
- 1번 (해결): `24e78b9` test — `DataExtractionRulesTest`에 `매니페스트가 백업·전송 규칙을 연결하고 백업을 끈다` 추가. `src/main/AndroidManifest.xml`을 namespace-aware DOM으로 파싱해 `android:dataExtractionRules == "@xml/data_extraction_rules"`, `android:allowBackup == "false"`를 확인한다.
  - 경로 탐색: 후보가 `src/main/AndroidManifest.xml`(작업 디렉터리 = 모듈. Gradle `testDebugUnitTest`와 Android Studio 기본 JUnit 설정 `$MODULE_WORKING_DIR$`)과 `automotive/src/main/AndroidManifest.xml`(작업 디렉터리 = 프로젝트 루트)이라 두 실행 위치 모두에서 찾는다. 못 찾으면 작업 디렉터리를 담은 `AssertionError`로 실패하므로 조용히 통과하는 경로가 없다. Gradle 실행에서 통과 확인.
  - 의미 있는 실패: 속성이 빠지면 `getAttributeNS`가 빈 문자열을 돌려줘 `expected:<[@xml/data_extraction_rules]> but was:<[]>`로 실패한다(tester 보고와 일치). 다른 xml을 가리키거나 `allowBackup`이 `true`/누락이어도 실패한다. 원본 매니페스트를 읽으므로 병합 매니페스트에서 라이브러리가 덮어쓰는 경우는 못 잡지만, 이번 회귀(연결 줄 삭제)를 잡는 목적에는 충분하다.
  - 매니페스트 연결이 이미 있던 상태라 이 `test:` 커밋은 처음부터 통과하는 회귀 방지 테스트다. tester가 연결 줄을 임시로 지워 red를 확인했으므로 TDD 규칙 위반으로 보지 않는다.
- 2번 (해결): `e41ccfe` — 규칙 xml 주석에 "규칙을 바꾸면 `ALL_DOMAINS`도 고칠 것, 테스트가 FullBackupContent 검사를 대신함"을 추가. 매니페스트 주석에도 연결 누락은 테스트가 확인한다고 한 줄 추가.
- 3번 (해결): `plan.md` 결정 기록에서 2026-11-02 Windows 행을 표 끝으로 옮겨 11-10 행끼리 붙었다. 내용 변경 없이 순서만 바뀜.
- 4번 (그대로 둠, 동의): 규칙 해석 차이 수준. 새 테스트는 `// given`에 실제 준비 코드(매니페스트 파일 찾기)가 있다.

### 새 지적
- 없음.

### 검증
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug :automotive:lintDebug` (JDK 17): 성공.
  - 단위 테스트 189개, 실패 0 · 오류 0. `DataExtractionRulesTest` 3개 통과.
  - lint: 0 errors, 15 warnings (첫 검토와 같음, 새 경고 없음).
