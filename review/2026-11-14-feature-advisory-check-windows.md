# 리뷰: feature/advisory-check-windows (2026-11-14)

- 기준: develop(75bdf86)...feature/advisory-check-windows (2개 커밋)
- 범위: 의존성 보안 권고 점검 스크립트의 Windows Git Bash 멈춤 수정, 11.14 재점검 결과(148개 · 권고 0건) 문서 반영
- 판단: 머지 가능

## 지적 사항

없음.

참고(이번 변경 범위 밖, 수정 요구 아님): `scripts/check_dependency_advisories.sh`는 `mktemp -d`로 만든 임시 폴더를 지우지 않음(기존부터 그랬음). 필요하면 `trap 'rm -rf "$tmp"' EXIT`를 나중에 덧붙일 수 있음.

## 확인한 것

- 커밋 구성: 7dda367 `fix:`는 `scripts/check_dependency_advisories.sh`만, 0ae380d `docs:`는 `README.md` · `docs/security-checklist.md`만 바꿈. `.idea/` 파일은 커밋에 없음(작업 트리의 `.idea` 변경은 커밋되지 않은 상태로 남아 있음).
- 동작 변경 없는 스크립트 · 문서 수정이라 TDD 사이클 대상이 아님. 앱 코드(`automotive/`) 변경 없음.
- `gh api` 엔드포인트 `advisories`(앞의 `/` 없음): gh는 앞에 `/`가 없는 경로도 `https://api.github.com/advisories`로 만들어 보냄. 같은 형식으로 okhttp 3.12.0 → GHSA-3cqm-mf7h-prrj, protobuf-javalite 3.21.0 → GHSA-735f-pc8j-v9w8 외 3건이 나와 조회가 동작함을 직접 확인함.
- `export MSYS_NO_PATHCONV=1`의 부작용:
  - macOS · Linux: MSYS 런타임만 읽는 환경 변수라 아무 영향 없음.
  - Windows Git Bash에서 스크립트 안 다른 명령:
    - `cd`, `$(...)`, 리다이렉트(`> "$tmp/..."`, `< "$tmp/..."`)는 bash 안에서 처리되므로 경로 변환과 관계없음.
    - `mktemp -d`는 MSYS 프로그램이라 `/tmp/...` 경로를 그대로 다루고, `grep` · `sed`도 MSYS 프로그램이라 변환 없이 받음. 정규식 인자는 `/`로 시작하지 않음.
    - `./gradlew`는 MSYS · Cygwin에서 `cygpath`로 `APP_HOME` · `CLASSPATH`를 직접 바꾸므로 자동 변환이 없어도 됨. 인자(`-q :automotive:dependencies --configuration releaseRuntimeClasspath`)에는 경로가 없음.
    - 아래 전체 실행에서 gradlew 의존성 출력과 집계가 정상으로 나옴.
  - 변수를 `export`했지만 스크립트 프로세스와 그 자식에만 적용되고 호출한 셸에는 남지 않음. 앞의 `/`를 없앴으므로 이 변수는 이중 안전장치임.
- 문서 기록과 사실의 일치:
  - security-checklist 56줄 "148개 · 0건(11.14 재실행)"과 64줄 "남은 일" 완료 표시: 이번 리뷰에서 다시 돌려 148개 · 0건을 확인함.
  - 56줄 "okhttp 3.12.0, protobuf-javalite 3.21.0으로 조회하면 권고가 나옴": 둘 다 직접 확인함.
  - README 28줄: 남은 일 괄호에서 완료된 "제출 직전 점검 스크립트"만 빠지고, 남은 두 항목(운행 기록 암호화 여부, 운영 서버 HTTPS 주소)은 checklist "남은 일"의 ⚠️ · ⏳ 항목과 맞음. Pleos 답변을 기다리는 CarPropertyManager 항목은 원래 괄호에 없었으므로 이번 변경과 관계없음.
- 보안: 비밀값 · 토큰이 들어간 변경 없음. 스크립트는 공개 권고 DB를 조회만 하고, 보내는 것은 라이브러리 좌표뿐임.

## 검증

- `bash scripts/check_dependency_advisories.sh`(Windows Git Bash): `점검: 148개, 보안 권고가 있는 라이브러리: 0개`, exit 0, 약 1분 11초.
- `gh api -X GET advisories -f ecosystem=maven -f affects=com.squareup.okhttp3:okhttp@3.12.0` → GHSA-3cqm-mf7h-prrj. `affects=com.google.protobuf:protobuf-javalite@3.21.0` → 4건.
- `./gradlew :automotive:testDebugUnitTest :automotive:assembleDebug`: 실행하지 않음. 앱 소스 · 빌드 설정 변경이 없고(스크립트 · 문서만), 스크립트 실행 중에 gradlew 의존성 해석이 정상으로 끝남.
