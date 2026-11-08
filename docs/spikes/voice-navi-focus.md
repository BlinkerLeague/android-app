# 스파이크 10: 우리 음성 안내와 내비 안내 음성이 겹칠 때

2026-11-08, Pleos Connect 에뮬레이터(API 34). 실차에서는 다를 수 있습니다.

## 결론

| 항목 | 결과 |
| --- | --- |
| 내비 안내 음성의 오디오 포커스 | `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, `GAIN_TRANSIENT_MAY_DUCK` (`ai.umos.maps…ConnectTTSRepositoryImpl`) |
| 우리가 `USAGE_ASSISTANT`로 말하는 중 내비 안내 | **내비 포커스 요청 거부(`AUDIOFOCUS_REQUEST_FAILED`) → "이번 신호에서 좌회전입니다." 안내가 빠짐** |
| `USAGE_MEDIA` / `USAGE_NOTIFICATION` / `USAGE_ASSISTANCE_SONIFICATION`으로 말하는 중 내비 안내 | 내비 요청 허용. 단 **우리 쪽에 포커스 손실이 오지 않아 두 음성이 동시에 나옴** |
| `USAGE_ANNOUNCEMENT` | 시스템 전용. 일반 앱이 `AudioAttributes`에 쓰면 `IllegalArgumentException`(앱 크래시) |
| 다른 앱(내비)의 재생 상태 | `AudioManager.registerAudioPlaybackCallback`로 용도(`audioAttributes.usage`)가 보임 → 내비 안내 시작을 감지할 수 있음 |
| TTS 멈춤 | Pleos TextToSpeech SDK 3.3.1 `stop()` (javap로 확인) |

## 앱에 반영 (`voice/PleosTtsAnnouncer`)

1. 용도를 `USAGE_ASSISTANT` → **`USAGE_MEDIA`**: 내비 안내를 막지 않음. 허용되는 세 용도 중 운전자가 음량을 켜 두는 경우가 가장 많은 미디어를 고름
2. 읽는 동안 내비 안내 재생이 시작되면 **즉시 `stop()`** (에뮬레이터에서 내비 포커스 요청과 같은 순간, 수 ms 안)
3. 내비가 끝나면 **처음부터 한 번 더** 읽음 (`MAX_ATTEMPTS = 2`). 운행당 안내 1회 원칙은 그대로
4. 내비가 이미 말하는 중이면 끝날 때까지(최대 15초) 기다렸다가 읽음

## 실험 기록

- 방법: 디버그 빌드 `--es speak '문장'`(바로 음성 재생, `--ei speakUsage N`으로 용도 바꿈) + 조사용 `NAVI_REQUEST_ROUTE`(경로 요청 약 5~6초 뒤 내비가 "이번 신호에서 좌회전입니다." 안내)
- 로그: `adb logcat | grep -E "PleosTtsAnnouncer|ConnectTTSRepositoryImpl|CarAudioFocus"`, 포커스 이력 `adb shell dumpsys car_service --services CarAudioService`

| 경우 | 기록 |
| --- | --- |
| ASSISTANT, 우리 먼저 | 22:48:32 우리 GRANTED → 22:48:38 내비 요청 **FAILED** ("requestAudioFocus fail script: 이번 신호에서 좌회전입니다.") |
| MEDIA, 우리 먼저 (수정 후) | 22:52:15 우리 시작 → 22:52:22.563 "내비 안내가 시작돼 멈춤" → 내비 GRANTED → 22:52:24 다시 읽기 → 22:52:34 완료 |
| MEDIA, 내비 먼저 (수정 후) | 22:54:03.84 내비 시작 → 22:54:05.99 내비 끝 → 22:54:06.19 우리 포커스 요청 → 22:54:09 완료 (겹침 없음) |

## 아직 모르는 것
- 실차의 포커스 정책(OEM이 바꿀 수 있음)과 미디어 용도 음량이 실제로 들리는지
- 내비 외 다른 안내(전화, 경고음)와 겹칠 때
