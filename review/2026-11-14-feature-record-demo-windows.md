# 리뷰: feature/record-demo-windows (2026-11-14)

- 기준: develop(ee8809b)...feature/record-demo-windows (2개 커밋: c526b0c fix, 248741b docs)
- 범위: Windows에서 시연 녹화 스크립트가 logcat 비UTF-8 바이트로 멈추던 문제 수정, 화면 대기 4초 · 한 파일 180초로 녹화 시간 단축, 관련 문서 갱신 (앱 코드 변경 없음)
- 판단: 머지 가능

## 지적 사항

막는 문제는 없다. 참고 수준만 적는다.

### 1. 문서의 "화면마다 4초"와 실제 노출 시간 차이 (심각도: 낮음)
- 위치: scripts/record_demo.py:13 (docstring), README.md:146
- 문제: `DEMO_SCREEN_SEC`(기본 4초)는 탭 뒤 대기 시간이고, 다음 `tap_text()`의 `uiautomator dump` 시간(tester 측정 2~4초)이 더해져 실제 노출은 6~8초다.
- 실패 시나리오: 편집하는 사람이 화면당 4초로 알고 컷을 잡거나, 더 줄이려고 값을 낮췄다가 기대와 다른 길이가 나옴. 동작 오류는 아님.
- 제안: 다음에 문서를 만질 때 "탭 뒤 대기 시간(화면 인식 시간이 더해져 실제로는 몇 초 더 김)" 정도로 보충.

### 2. 녹화 여유가 16초 정도 (심각도: 낮음)
- 위치: scripts/record_demo.py:40 (`SEGMENT_SEC = 180`)
- 문제: 실측 약 164초로 180초 한도까지 약 16초 여유. 서버 모드(`DEMO_CRUISE_SEC=115`)나 느린 PC에서는 넘어서 `_2.mp4`로 나뉜다.
- 실패 시나리오: 넘으면 파일이 둘로 나뉘고, 이어 녹화 사이(rm + screenrecord 재시작) 1초 안팎이 빠질 수 있음. 이전부터 있던 동작이고 문서("3분을 넘으면 _2.mp4")에도 적혀 있어 문제는 아님.
- 제안: 그대로 두어도 된다. 새로 추가된 "녹화 약 N초" 로그로 판단 가능.

### 3. 같은 패턴이 남은 곳: scripts/geo_route.py:32 (심각도: 낮음, 범위 밖)
- 위치: scripts/geo_route.py:32 (`text=True`, encoding 미지정)
- 문제: curl 응답을 시스템 기본 인코딩(Windows cp949)으로 디코딩함. 이번 브랜치 범위(녹화)와 관련 없고 시연 녹화 경로에서 쓰지 않음.
- 제안: Windows에서 쓸 일이 생기면 같은 방식(`encoding="utf-8"`)으로 맞춤. 지금은 조치 불필요.

## 확인한 것
- adb() 두 곳 모두 수정: scripts/record_demo.py:48-50, scripts/vconsole.py:20-22 모두 `encoding="utf-8", errors="replace"`. 나머지 adb 호출(screenrecord, `log -t`, `emu geo fix`)은 출력을 디코딩하지 않거나 bytes로 받으므로 영향 없음.
- logcat `-s` 필터 태그가 앱 코드와 일치: `TripCollectionService.TAG = "TripCollection"`(service/TripCollectionService.kt:106, "운행 끝 ..." 로그는 :81-84), `PleosTtsAnnouncer.TAG = "PleosTtsAnnouncer"`(voice/PleosTtsAnnouncer.kt:203, "안내 완료/실패" 로그는 :80). `AppLog.i`는 디버그 빌드에서 `Log.i(tag, ...)`로 그대로 찍으므로 태그 변형 없음. 기본 threadtime 형식에 `태그: 메시지`가 들어가므로 `r"TripCollection: 운행 끝"`, `r"PleosTtsAnnouncer: 안내"`, `after=r"TripCollection: 운행 끝"` 정규식은 필터 뒤에도 유지됨. 필터 덕분에 다른 프로세스의 깨진 바이트가 아예 들어오지 않아 디코딩 위험도 줄어듦.
- 주행 안전: 화면 터치(`tap_text`, BACK)는 모두 `wait_logcat("운행 끝")` → `wait_logcat("안내", after=운행 끝)` → 3초 뒤에만 있음. 주행 재생(vehicle_probe_sim.py demo) 중에는 터치 없음. 앱 코드 · 문구 · 음성 횟수 변경 없음.
- 이어 녹화 루프 · finally 흐름 유지: `Recorder._loop`는 그대로, `started`는 `start()`에서만 설정되고 `stop_and_pull()`은 None을 0으로 처리. `stop_and_pull()`은 여전히 `finally`에서 호출되어 중간 예외에도 녹화를 멈추고 가져옴. `SEGMENT_SEC=180`은 screenrecord `--time-limit` 최대값.
- 문서 일관성: docstring "전체 약 2분 40초, 보통 _1.mp4 하나", README 두 곳(약 2분 40초, 3분을 넘으면 `_2.mp4`, `DEMO_SCREEN_SEC` 기본 4초), plan.md:140(약 2분 40초), vehicle_probe_sim.py 주석(record_demo.py는 70을 넘김, 약 2.2km)이 코드(`DEMO_CRUISE_SEC` 기본 70, `SCREEN_SEC` 4, `SEGMENT_SEC` 180)와 서로 맞음. tester 실측(약 164초 ≈ 2분 44초, 2.27km)과도 맞음. 이전 mp4 삭제 안내도 코드(main의 `os.remove`)와 일치.
- 커밋 내용: 두 커밋의 파일은 README.md, plan.md, scripts/record_demo.py, scripts/vconsole.py, scripts/vehicle_probe_sim.py뿐. `.idea/`, `__pycache__` 없음(작업 트리의 `.idea` 변경은 커밋되지 않음).
- TDD: 동작이 바뀐 앱 코드가 없고 개발용 스크립트 · 문서만 바뀌어 테스트 없이 `fix:` · `docs:` 커밋한 것은 규칙에 맞음.
- 보안: 비밀값 · 토큰 · 좌표 전송 관련 변경 없음.

## 검증
- 변경된 세 스크립트 `ast.parse` 문법 검사: 모두 통과 (`__pycache__`를 만들지 않으려고 py_compile 대신 사용).
- Gradle 빌드 · 단위 테스트: 앱 코드(automotive/) 변경이 없어 실행하지 않음.
- 에뮬레이터 실녹화: reviewer는 실행하지 않음. tester 보고 기준 exit 0, 예외 없음, "녹화 약 164초", _1.mp4 하나, 거리 2.27km, "+1P 적립", 업적 화면 진입, 탭 못 찾음 0건.
