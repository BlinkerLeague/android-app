# 리뷰: feature/untrack-record-demo (2026-11-14)

- 기준: develop(2f75169)...feature/untrack-record-demo (2개 커밋: 5fd5ab0 chore, 181cb90 docs)
- 범위: 시연 녹화 스크립트 `scripts/record_demo.py`를 저장소에서 빼고(.gitignore, 로컬 파일 유지) 남은 안내 · 주석을 정리
- 판단: 머지 가능 (아래 지적은 모두 낮음, 머지를 막지 않음)

## 지적 사항

### 1. README 시연 운행 안내에 Windows 준비(ADB · PYTHONUTF8)가 빠짐 (심각도: 낮음)
- 위치: README.md:146, README.md:26
- 문제: Windows 실행법(`PYTHONUTF8=1`, `ADB=<adb.exe 경로>`)은 record_demo.py docstring에만 있었는데, 그 파일이 저장소에서 빠지면서 저장소 안 어디에도 남지 않음.
- 실패 시나리오: Windows에서 README대로 `python3 scripts/vehicle_probe_sim.py demo`만 실행하면 `scripts/vconsole.py:15`의 기본 ADB가 macOS 경로(`~/Library/Android/sdk/platform-tools/adb`)라서 `Console()` → `_device_clock()`의 `adb shell date`에서 FileNotFoundError로 멈춤. 로컬 record_demo.py에는 docstring이 남아 있어 사용자 본인은 영향이 적음.
- 제안: README:146에 "Windows: `ADB=/c/android/sdk/platform-tools/adb.exe`(Git Bash) 지정, 필요하면 `PYTHONUTF8=1`" 한 줄 덧붙임.

### 2. sim 단독 실행 기본값으로는 결과 화면에 +P가 안 보일 수 있음 (심각도: 낮음)
- 위치: README.md:146, scripts/vehicle_probe_sim.py:147
- 문제: README는 `DEMO_CRUISE_SEC`로 길이를 조절한다고만 적었고, sim 기본값은 45초(회전 구간 포함 약 1.55km). 녹화 도구가 쓰던 70(약 2.2km, +1P 보이게)은 저장소 문서에서 plan.md:140과 sim 주석에만 남음.
- 실패 시나리오: README대로 기본값으로 재생하면 평점 50일 때 floor(1.55 × 50 / 100) = 0P → #76의 "0P면 포인트 줄 숨김"으로 결과 화면에 포인트 줄이 안 나옴. 시연 영상 시나리오와 다르게 보임.
- 제안: README:146에 "시연 영상과 같게 하려면 `DEMO_CRUISE_SEC=70`" 을 적음. (또는 2차에 sim 기본값을 70으로.)

### 3. README:146 문장 순서 (심각도: 낮음)
- 위치: README.md:146
- 문제: "재생 … 앱을 `--ei parkEndSec 10`으로 띄우면" 순서라, 실제로는 앱을 먼저 그 옵션으로 띄운 뒤 재생해야 한다는 점이 드러나지 않음(record_demo.py도 앱 시작 → 재생 순서였음). DebugTools.kt:34의 실행 예 형식(`adb shell am start -n com.example.blinkerleague/.MainActivity --ei parkEndSec 10`)을 함께 적으면 바로 따라 할 수 있음.

### 4. 다른 클론에서 pull하면 로컬 record_demo.py가 지워짐 (심각도: 낮음, 참고)
- 위치: 5fd5ab0 (`git rm --cached`)
- 문제: 이 작업 트리에서는 파일이 남지만, 다른 클론이 이 변경을 pull(또는 develop 머지 후 checkout)하면 git이 추적 해제된 파일을 작업 트리에서 지움. 이후에는 .gitignore로 무시됨.
- 제안: 다른 PC나 팀원이 이 스크립트를 쓰고 있다면 pull 전에 복사해 두라고 PR 설명에 한 줄 적음. 파일은 git 기록(2f75169 이전 커밋)에서 언제든 꺼낼 수 있음(비밀값 없음 확인).

## 확인한 것
- 로컬 파일 유지 · 무시: `scripts/record_demo.py`(8244바이트)가 작업 트리에 남아 있고, `git check-ignore -v` → `.gitignore:22:scripts/record_demo.py`. `git ls-files scripts/`에 없음.
- 깨진 참조 없음: `git grep record_demo HEAD` 결과는 .gitignore:22, plan.md:140(과거 기록 + "로컬 전용으로 전환"), review/ 과거 파일뿐. 다른 스크립트 · Gradle · 코드에서 record_demo.py를 import하거나 부르지 않음(record_demo.py가 vehicle_probe_sim.py를 부르는 한 방향뿐이었음). 없는 `scripts/record_demo.sh`를 가리키던 DebugTools.kt:34 주석도 정리됨.
- 문서 ↔ 실제 사용법: `vehicle_probe_sim.py`의 `SCENARIOS`에 `"demo": scenario_demo`가 있고(181줄) `main()`이 argv로 시나리오를 고름 → `python3 scripts/vehicle_probe_sim.py demo` 동작. `DEMO_CRUISE_SEC` 환경 변수는 147줄에서 읽음(기본 45, 주석 "70이면 약 2.2km"와 계산 일치). `--ei parkEndSec N`은 DebugTools.kt:38-41에서 읽어 `DetectionConfig.parkEndSec`(DetectionConfig.kt:37-39)으로 반영, 디버그 소스셋 전용이라 README의 "디버그 빌드 전용"과 맞음.
- DebugTools.kt 변경: KDoc 한 줄뿐, 코드 변화 없음. debug 소스셋이라 릴리스에도 영향 없음.
- 커밋 내용: 5fd5ab0은 .gitignore · scripts/record_demo.py, 181cb90은 README.md · plan.md · DebugTools.kt · vehicle_probe_sim.py뿐. `.idea/`, `__pycache__` 없음(작업 트리의 .idea 변경은 커밋되지 않음). `review/` 과거 파일은 수정되지 않음(과거 리뷰 속 record_demo.py 언급은 당시 기록이라 그대로 두는 것이 맞음).
- 커밋 유형: 동작 변경이 없는 정리라 TDD 사이클 대상 아님. chore(추적 해제) · docs(안내 정리) 구분 적절.
- plan.md:140: 체크된 항목에 "11.14: 로컬 전용으로 전환" 결정이 기록됨. 상위 "시연 영상" · "편집·자막·제출" 미체크 상태 유지 맞음.
- 보안: 삭제된 스크립트에 토큰 · 키 · 개인정보 없음. 주행 안전 원칙 · 앱 코드 · 서버 형식과 무관한 변경.

## 검증
- `./gradlew :automotive:assembleDebug`: BUILD SUCCESSFUL (8s). 코드 변경이 주석뿐이라 단위 테스트는 돌리지 않음.
- `git check-ignore -v scripts/record_demo.py`, `git ls-files scripts/`, `git grep -n record_demo HEAD`, `git show --stat 5fd5ab0 181cb90`로 위 항목 확인.
