# Pleos SDK 실행 기록

Pleos SDK를 실제 에뮬레이터나 차량에서 실행해 확인한 내용을 기록합니다.
문서에 적힌 내용이 아니라 **직접 실행해서 확인한 결과**만 적습니다.
스파이크 체크리스트(`plan.md` 0장)의 근거 자료로 씁니다.

## 요약

| SDK | 버전 | 환경 | 결과 | 확인한 날 | 상세 |
|---|---|---|---|---|---|
| FusedLocation | 1.1.3 | 에뮬레이터 | ✅ 현재 위치 조회·1초 갱신 됨 | 2026-11-03 | [보기](#fusedlocation-113--에뮬레이터-2026-11-03) |
| FusedLocation | 1.1.3 | 실차 | ❓ 미확인 (구현 가이드에는 미지원) | — | — |
| TextToSpeech | 3.3.1 | 에뮬레이터 | ✅ `ON_DEVICE` + `ko-KR` 재생됨 | 2026-11-01 | [보기](#texttospeech-331--에뮬레이터-2026-11-01) |
| Vehicle | 2.3.2 | 에뮬레이터 | ❓ 미확인 | — | — |

## 공통 실행 환경

- 에뮬레이터: Pleos Connect v2.1.0 (API 34), AVD 이름 `Pleos_Connect_v2.1.0`
- 앱은 **user 10**으로 실행됨 (`adb shell am get-current-user` → `10`).
  권한을 허용하거나 테스트를 실행할 때 `--user 10`을 붙여야 함. user 0에만 허용하면 앱에는 적용되지 않음
- Windows 사용자 경로가 한글이면 에뮬레이터를 `C:\android\sdk`(SDK 연결 폴더)로 실행해야 함 → [emulator-setup.md](emulator-setup.md)

---

## FusedLocation 1.1.3 — 에뮬레이터 (2026-11-03)

**목적**: 스파이크 4번. 위치를 FusedLocation SDK로 받을 수 있는지, 갱신 주기와 받는 필드 확인

### 설정

```kotlin
// automotive/build.gradle.kts
implementation("ai.pleos.playground:FusedLocation:1.1.3")
```

```xml
<!-- AndroidManifest.xml. protectionLevel dangerous → 앱에서 런타임 요청 필요 -->
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION" />
```

- SDK가 바인딩하는 서비스: `ai.pleos.playground.service.location.fused`
  (에뮬레이터 설치 버전 1.0.0, SDK가 요구하는 최소 버전 0.5.0)

### API (SDK 클래스를 javap로 확인)

```
FusedLocation(Context)
  boolean isSupported()
  void initialize()
  void release()
  void getLastKnownLocation(FusedLocationListener)
  void registerFusedLocationCallback(FusedLocationListener)
  void unregisterFusedLocationCallback(FusedLocationListener)

interface FusedLocationListener
  void onChanged(LocationData?)   // null일 수 있음
  void onError(Exception)

LocationData(latitude: Double, longitude: Double, altitude: Double,
             heading: Float, confidence: Float, timestampMillis: Long)
```

- 갱신 주기를 지정하는 파라미터는 없음
- **속도 필드가 없음** → 속도는 Vehicle SDK에서 따로 받아야 함

### 실행 방법

테스트 코드: `automotive/src/androidTest/java/com/example/blinkerleague/FusedLocationSpikeTest.kt`

```powershell
$adb = 'C:\android\sdk\platform-tools\adb.exe'
.\gradlew.bat :automotive:installDebug :automotive:installDebugAndroidTest
& $adb shell pm grant --user 10 com.example.blinkerleague pleos.car.permission.FUSED_LOCATION
& $adb logcat -c
& $adb shell am instrument -w --user 10 -e class com.example.blinkerleague.FusedLocationSpikeTest `
    com.example.blinkerleague.test/androidx.test.runner.AndroidJUnitRunner
& $adb logcat -d -s FusedLocationSpike:*
```

### 결과: 테스트 2개 통과

| 확인 항목 | 결과 |
|---|---|
| `isSupported()` | `true` |
| `getLastKnownLocation()` | 약 20ms 안에 응답. 오류 없음 |
| `registerFusedLocationCallback()` | 15초 동안 13번 받음. 간격은 대부분 1000ms이고 약 2000ms가 두 번 있었음. 오류 없음 |
| 위치 바꾸기 (`adb emu geo fix 126.9780 37.5665 30`) | SDK 값이 바로 37.5665, 126.9780, 고도 30으로 바뀜 |

받은 값 예시:

```
LocationData(latitude=37.421998333333335, longitude=-122.084, altitude=5.0,
             heading=0.0, confidence=5.0, timestampMillis=1793717725436)
갱신 간격(ms): [1001, 1014, 1992, 1996, 1001, 1000, 1001, 1001, 1001, 1000, 1000, 1000]
```

- 처음 위치는 에뮬레이터 기본 좌표(미국 Google 본사)이고, 고도는 5m
- 정지 상태라서 `heading`은 0.0. 움직일 때 값이 바뀌는지는 아직 확인 안 함
- `confidence` 5.0의 단위와 의미는 문서에 없음

### 남은 확인

- [ ] 실차에서 `isSupported()` 결과 (구현 가이드에는 미지원으로 적혀 있음. false이면 NaviHelper로 대신 받음)
- [ ] 위치가 움직일 때 `heading` 값 (Virtual Console 경로 재생 또는 `geo fix` 연속 입력)
- [ ] 앱이 백그라운드에 있을 때도 콜백을 계속 받는지 (스파이크 2번)
- [ ] 권한 거부 시 `onError`로 오는 예외 종류

---

## TextToSpeech 3.3.1 — 에뮬레이터 (2026-11-01)

**목적**: 스파이크 10번. 시동·종료 음성 안내를 재생할 수 있는지 확인

- `Mode.ON_DEVICE` + `ko-KR`로 재생됨. 권한 `pleos.car.permission.TTS_SERVICE` 필요
- Android 표준 `TextToSpeech`는 엔진(caas SpeechService) 연결에 실패함 → Pleos SDK를 씀
- 에뮬레이터 caas 버전 4.2.10 (SDK 최소 요구 4.1.29)
- 구현: `voice/PleosTtsAnnouncer.kt`

### 남은 확인

- [ ] 내비 안내 음성이 나오는 중일 때 오디오 포커스 동작 (지연·거절)

---

## 기록 양식

새로 실행하면 위 요약 표에 한 줄을 추가하고, 아래 양식으로 절을 추가합니다.

```markdown
## <SDK> <버전> — <에뮬레이터|실차> (<YYYY-MM-DD>)

**목적**: 스파이크 N번. 무엇을 확인하려 했는지

### 설정
의존성, 권한, 필요한 서비스

### 실행 방법
테스트 코드 위치와 명령어

### 결과
확인 항목별 결과, 실제 로그 일부

### 남은 확인
- [ ] ...
```
