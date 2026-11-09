# Pleos 앱 보안 가이드 대응표

> 출처: [Pleos 앱 보안 가이드](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide) (2026-11-09 확인).
> 기준: **릴리스 빌드**(`assembleRelease`). 조사용 코드·권한은 디버그 빌드(`src/debug`)에만 있습니다.
> 표시: ✅ 대응함 · ⚠️ 판단·확인 필요 · ⏳ 제출 전 할 일 · ➖ 해당 없음

## 1. 민감 데이터 관리

| 구분 | 항목 | 상태 | 앱 |
| --- | --- | :-: | --- |
| 필수 | 저장 데이터 암호화 | ✅ | 토큰(Access·Refresh)과 등록 정보(운전자 ID·닉네임·지역 코드)를 Android Keystore AES-256-GCM 키로 암호화해 SharedPreferences에 저장 (`data/auth/KeystoreTokenCipher`). 예전 평문 값은 처음 열 때 암호화 |
| 필수 | 저장 데이터 암호화 (운행 기록) | ⚠️ | Room DB에 운행 시각·거리·회전(방향, 깜빡이 여부)이 평문으로 남음. 원시 위치 좌표·식별자는 저장하지 않음. 민감정보로 볼지 판단 필요 (암호화하려면 SQLCipher 등 도입) |
| 필수 | 로그 관리 | ✅ | 토큰·좌표는 어디서도 로그로 찍지 않음. 릴리스 빌드는 정보 로그를 남기지 않고 경고·오류만 남김 (`AppLog`). HTTP 로그 인터셉터 없음 |
| 권장 | 캐시 | ✅ | 민감정보를 캐시에 두지 않음 |
| 권장 | 자동 백업 비활성화 | ✅ | `android:allowBackup="false"` |
| 권장 | 외부 저장소 | ✅ | 외부 저장소를 쓰지 않음 (앱 전용 내부 저장소만) |
| 필수 | URL 노출 방지 | ✅ | 토큰은 `Authorization: Bearer` 헤더로만 보냄 |

## 2. 안전한 암호화 사용

| 구분 | 항목 | 상태 | 앱 |
| --- | --- | :-: | --- |
| 권장 | 표준 알고리즘·모드 | ✅ | AES-256 / GCM / NoPadding (ECB 안 씀) |
| 권장 | IV | ✅ | 암호화마다 Keystore가 새 IV를 만들고, 저장 값에 함께 둠 (`v1:<iv>:<암호문>`) |
| 필수 | 키 관리 | ✅ | 키는 AndroidKeyStore 안에서 만들고 꺼낼 수 없음. 코드·리소스에 키 없음 |

## 3. 안전한 통신 환경

| 구분 | 항목 | 상태 | 앱 |
| --- | --- | :-: | --- |
| 필수 | 암호화 프로토콜 | ✅ | 릴리스는 평문(http) 차단(network security config 기본값). http는 디버그 빌드의 로컬 서버(10.0.2.2·localhost)만 허용 |
| 필수 | TLS 1.2 이상 | ✅ | OkHttp `ConnectionSpec.MODERN_TLS`(TLS 1.3·1.2)만 사용 |
| 권장 | TLS 1.3·Cipher Suite | ✅ | OkHttp MODERN_TLS 기본 Cipher Suite. 서버 쪽 설정은 서버 팀(서버 plan.md 9.4) |
| 필수 | 신뢰 서버 | ✅ | 빌드 설정의 서버 하나(`blinker.serverUrl`)와만 통신. 인증서 검증을 끄지 않음 |
| 필수 | 서버 원격 명령 | ➖ | 서버가 앱에 명령을 보내지 않음. 받는 것은 기준값(숫자)뿐이고, 서버가 주는 항목만 덮어씀 |

## 4. 컴포넌트 및 권한 관리

| 구분 | 항목 | 상태 | 앱 |
| --- | --- | :-: | --- |
| 필수 | 권한 최소화 | ✅ | 릴리스 권한: CAR_LIGHTS, CAR_DRIVE, FUSED_LOCATION, TTS_SERVICE, CAR_SPEED, INTERNET, 포그라운드 서비스·부팅. 사전 승인은 [`pleos-inquiries.md`](pleos-inquiries.md) 1번 |
| 필수 | 컴포넌트 외부 노출 제한 | ✅ | 외부 노출은 런처 `MainActivity`뿐. `TripCollectionService`·`BootReceiver`는 `exported=false`. 라이브러리 컴포넌트(WorkManager 등)는 `BIND_JOB_SERVICE`·`DUMP` 권한으로 보호 |
| 필수 | 민감정보 전달 | ✅ | 브로드캐스트로 민감정보를 보내지 않음 |
| 필수 | 접근 제어 | ➖ | 외부에 공개하는 데이터·기능 없음 (ContentProvider 없음) |
| 필수 | WebView | ➖ | WebView를 쓰지 않음 |

## 5. 안전한 개발 및 배포

| 구분 | 항목 | 상태 | 앱 |
| --- | --- | :-: | --- |
| 필수 | 승인된 API만 | ⚠️ | Pleos Vehicle·TextToSpeech·FusedLocation SDK와 Android `CarPropertyManager`(속도). CarPropertyManager 허용 여부는 [`pleos-inquiries.md`](pleos-inquiries.md) 1번으로 문의 |
| 필수 | 필요한 차량 API만 | ✅ | 방향지시등·핸들 각도·기어·속도만 구독. 조사용 API는 디버그 빌드에만 |
| 필수 | 하드코딩 금지 | ✅ | 토큰·비밀 키를 코드에 넣지 않음. 서명 키 정보는 `local.properties`(저장소 밖) |
| 권장 | 서버 주소 | ⏳ | 빌드 설정(`blinker.serverUrl`)으로 주입. 운영 도메인이 정해지면 그 값으로 릴리스 빌드 |
| 권장 | 네이티브 라이브러리 | ➖ | 앱 자체 네이티브 코드 없음 |
| 권장 | SDK·라이브러리 점검 | ⏳ | 제출 전 의존성 버전·알려진 취약점 확인 (Retrofit 2.11, OkHttp 4.12 등) |
| 필수 | 배포용 인증서 | ⏳ | 출시 키로 서명 (README "해야 할 일") |
| 필수 | 디버깅 비활성화 | ✅ | 릴리스 `debuggable` 꺼짐, WebView 없음, 조사용 코드·디버그 옵션(`DebugTools`)은 릴리스에서 빈 구현 |

## 남은 일
- ⚠️ 운행 기록(Room)을 민감정보로 보고 암호화할지 결정
- ⚠️ CarPropertyManager(속도) 사용 허용 여부 — Pleos 답변
- ⏳ 운영 서버 도메인(HTTPS)으로 릴리스 빌드, 출시 키 서명, 의존성 점검
