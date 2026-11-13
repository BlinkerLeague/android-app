# 서버 요청 사항 (앱 → 서버)

> 앱 팀이 서버 팀에 전달하는 문서입니다. 서버 저장소 `plan.md` 3장(P0 API)을 기준으로, 앱 구현 중에 바뀌었거나 정해야 하는 것만 적었습니다.
> 마지막 갱신: 2026-11-13 (15번 추가) · 2026-11-09 (10번 결정: 1km, 11~14번 추가, 로컬 서버 develop `5d1ad9c`로 상태 확인)

## 처리 상태 (2026-11-09, 서버 develop `5d1ad9c` plan.md 9장·실행 중인 서버로 확인)

| # | 상태 |
| --- | --- |
| 1, 7 | 반영됨 (서버 #43): `turn`에 `steeringEnterDeg` 90, `steeringExitDeg` 20, `mergeGapSec` 3, `steeringRightPositive` false. 앱이 이 값을 받아 씀 |
| 2 | 철회 (앱이 `ScoreItem`에 맞춤) |
| 3, 4, 5 | 확인됨: 업로드 형식·응답 일치, 실제 업로드 성공 |
| 6 | 해소: 서버 결정 D25 |
| 8 | 반영됨 (서버 #43): `plays[].direction` |
| 9 | 해소: 배열, `limit` 기본 10·최대 50(D24) |
| 10 | 반영됨(서버 D10 변경, 11.09): 1km |
| 11 | 답 받음(서버 D16·D17, 11.09): 이벤트 최대 2,000개, 운행 시각 **미래 5분·과거 7일**. ⚠️ 차량 시계가 5분 넘게 빠르거나 7일 넘게 못 올린 운행은 400 → 앱은 재전송하지 않고 REJECTED로 둠(기기에는 보존). 실차 시계 확인 필요 |
| **12** | **새로 추가(2차)**: 점수 응답에 이 운행의 적립 포인트 `earnedPoints` |
| **13** | **새로 추가**: 깜빡이 팁 문구가 앱 판정 기준과 다름 ("30m 전" vs 앱 "5초 전") |
| 14 | 해소(서버 #48 · #49): 서버가 다른 형식(`season` · `league` · `me` · `regions`, `docs/api/openapi.json` `CurrentLeagueResponse`)으로 구현 → 11.10 앱이 서버 형식에 맞춤(`feature/league-server-format`). 순위 공개는 `PUT /api/drivers/me/league-visibility` |
| **15** | **새로 추가(11.13)**: 배지 `STREAK_7` 설명 문구를 "오늘의 퀘스트"로 |

## 요약

| # | 항목 | 관련 API | 서버 plan.md | 필요 시점 | 종류 |
| --- | --- | --- | --- | --- | --- |
| 1 | 회전 감지 기준값을 핸들 각도 기준으로 변경 | `GET /api/config/thresholds` | 3.3 | `threshold-config` 전 | **변경** |
| 2 | ~~`supported` 신호 이름에 `STEERING_ANGLE` 추가~~ → **철회**: 앱이 서버 `ScoreItem`에 맞춤 | `POST /api/trips` | 3.4, 3.5 | — | 철회 |
| 3 | `TURN` payload의 `uTurn`은 보내지 않음 | `POST /api/trips` | 3.4 | `trip-upload` 전 | **변경** |
| 4 | 운행 업로드 요청 형식 확인 | `POST /api/trips` | 3.4 | `trip-upload` 전 | 확인 |
| 5 | 오류 응답 코드와 앱의 재전송 규칙 | 전체 | 3.1, 3.4 | `trip-upload` 전 | 확인 |
| 6 | 오늘의 미션: 운행이 없는 날의 응답 | `GET /api/missions/today` | 3.6 | `daily-mission` 전 | 확인 |
| 7 | 기준값 `turn`에 조향각 부호(`steeringRightPositive`) 추가 | `GET /api/config/thresholds` | 3.3 | `threshold-config` 전 | **변경** |
| 8 | 점수 응답 `plays`에 회전 방향 `direction` 추가 | `GET /api/trips/{id}/score`, `POST /api/trips` | 3.5 | `trip-score` 전 | **변경** |
| 9 | 최근 경기 목록 응답 모양 확인 | `GET /api/trips?limit=N` | 3.5 | `trip-score` 전 | 확인 |
| 10 | 짧은 운행 기준 `trip.minDistanceKm` 3.0 → **1.0** (11.09 결정) | `GET /api/config/thresholds` | 3.3 | 1차 제출 전 | **변경** |
| 11 | D16·D17 결정용 앱 정보 (운행당 이벤트 수, 오프라인 보관·시각) | `POST /api/trips` | 0장 D16·D17 | 배포 전 | 정보 |
| 12 | 점수 응답에 이 운행의 적립 포인트 `earnedPoints` 추가 | `GET /api/trips/{id}/score`, `POST /api/trips` | 3.5, 4.2 | 2차 PT(12.2) 전 | **변경** |
| 13 | 기준값 `tips.TURN_SIGNAL` 문구를 앱 판정 기준(회전 5초 전)에 맞춤 | `GET /api/config/thresholds` | 3.3, D18 | 2차 PT(12.2) 전 | **변경** |
| 14 | 지역 리그 응답 형식 제안 | `GET /api/leagues/current` | 4.4, D5·D8·D11·D13·D19·D29·D31 | 2차 PT(12.2) 전 | 제안 |
| 15 | 배지 `STREAK_7` 설명 문구를 퀘스트 용어로 | `GET /api/badges` | — | 2차 PT(12.2) 전 | **변경** |

---

## 1. 회전 감지 기준값을 핸들 각도 기준으로 변경 (변경)

**배경**: 구현 가이드에 실차는 FusedLocation이 미지원으로 표시돼 있어, 위치의 진행 방향(heading)으로 회전을 감지하는 방식을 버리고 **핸들 각도**로 감지하기로 했습니다.

**새 회전 기준**
- 핸들 각도 절댓값이 `steeringExitDeg`(20°)를 넘은 때부터 다시 그 아래로 내려온 때까지를 한 번의 조향으로 봅니다.
- 그 사이 `steeringEnterDeg`(90°) 이상 꺾였으면 회전입니다.
- 회전 시작 `signalLeadSec`(5초) 전 ~ 회전 끝 사이에 같은 방향 깜빡이가 켜지면 성공입니다.
- `mergeGapSec`(3초) 안에 이어진 회전은 같은 방향이면 하나로 합치고, 좌우가 섞이면(회전교차로) 제외합니다.
- 기어 P·R, 비상등 켜짐, 회전 내내 `minSpeedKmh`(5) 미만(속도를 알 때)인 회전은 제외합니다.

**요청**: `turn` 객체를 아래처럼 바꿔 주세요. 앱은 모르는 필드를 무시하므로 기존 필드(`windowSec`, `minAngleDeg`, `uTurnAngleDeg`)는 남겨도 되지만 MVP에서는 쓰지 않습니다.

```json
{
  "version": 2,
  "trip": { "minDistanceKm": 1.0, "parkEndMinutes": 3 },
  "turn": {
    "steeringEnterDeg": 90,
    "steeringExitDeg": 20,
    "signalLeadSec": 5,
    "minSpeedKmh": 5,
    "mergeGapSec": 3
  },
  "harsh": { "deltaKmhPerSec": 10 },
  "safetyWarning": { "mergeWindowSec": 10 },
  "weights": { "TURN_SIGNAL": 0.4, "SMOOTH_DRIVING": 0.3, "SAFETY_WARNING": 0.2, "NIGHT_HEADLIGHT": 0.1 },
  "point": { "tripMaxKm": 50, "dailyCap": 150 }
}
```

## 2. `supported` 신호 이름에 `STEERING_ANGLE` 추가 (철회, 2026-11-08)

> **철회**: 서버 `TripController`가 `supported`를 `Set<ScoreItem>`(`TURN_SIGNAL`, `SMOOTH_DRIVING`, `SAFETY_WARNING`, `NIGHT_HEADLIGHT`)으로 받는 것을 확인했습니다. 앱이 "측정 가능한 점수 항목"으로 바꿔 보냅니다(방향지시등 + 핸들 각도 → `TURN_SIGNAL`). 서버 변경은 필요 없습니다. 아래는 이전 내용입니다.

앱이 보내는 `supported` 값은 아래 중 차량이 지원하는 것입니다.

| 이름 | 뜻 |
| --- | --- |
| `TURN_SIGNAL` | 방향지시등 |
| `STEERING_ANGLE` | 핸들 각도 (**추가**) |
| `BRAKE` | 브레이크 페달 (2차) |
| `HEADLIGHT` | 전조등 (2차) |
| `SAFETY_WARNING` | 안전 경고 (2차) |

**요청**: 깜빡이 항목(`TURN_SIGNAL` 점수)은 `TURN_SIGNAL`과 `STEERING_ANGLE`이 **둘 다** `supported`에 있을 때만 측정 가능한 것으로 봐 주세요. 앱은 둘 중 하나라도 없으면 `TURN` 이벤트를 만들지 않습니다. 이 경우 가중치 재분배에서 제외되어야 하고 0점이 되면 안 됩니다.

## 3. `TURN` payload의 `uTurn`은 보내지 않음 (변경)

핸들 각도로는 유턴을 구분할 수 없어서 앱은 `uTurn` 필드를 **보내지 않습니다**. 서버는 이 필드를 선택 항목(없으면 null)으로 받아 주세요.

```json
{ "direction": "LEFT", "signalOn": true }
```

- `direction`: `LEFT` 또는 `RIGHT`
- `signalOn`: 깜빡이를 제때 켰으면 `true`. MVP 판정은 성공/실패 2단계입니다.

## 4. 운행 업로드 요청 형식 확인 (확인)

기획서 8.2의 요청 예시를 받지 못해서, 서버 plan.md 3.4의 테이블 컬럼을 기준으로 아래 형식으로 보내고 있습니다. 다르면 알려주세요.

```json
{
  "tripId": "0b6c1f5e-7a1d-4c8e-9f0a-2d3b4c5d6e7f",
  "startedAt": "2026-11-03T08:00:00+09:00",
  "endedAt": "2026-11-03T08:10:00+09:00",
  "distanceKm": 3.2,
  "supported": ["STEERING_ANGLE", "TURN_SIGNAL"],
  "events": [
    {
      "eventId": "5a7e2c90-1b3d-4f6a-8c9e-0d1f2a3b4c5d",
      "type": "TURN",
      "occurredAt": "2026-11-03T08:02:00+09:00",
      "payload": { "direction": "LEFT", "signalOn": true }
    }
  ]
}
```

- `tripId`, `eventId`: 앱에서 만든 UUID. 재전송 시 같은 값을 그대로 보냅니다.
- 시각: ISO-8601, 초 단위, 기기 시간대 오프셋 포함 (한국은 `+09:00`)
- `occurredAt`: 회전 시작 시각. 항상 `startedAt` ~ `endedAt` 안에 있습니다.
- `events`: 시각순 정렬. 회전이 없으면 빈 배열
- 원시 위치 좌표는 보내지 않습니다.

**응답**: 앱은 `status`, `score`만 저장하고, `turns`, `metrics`는 있으면 씁니다. `status`는 `SCORED`, `NO_TURN`, `EXCLUDED_SHORT` 중 하나라고 가정합니다.

## 5. 오류 응답 코드와 앱의 재전송 규칙 (확인)

앱은 운행을 먼저 기기에 저장하고 대기열에서 올립니다. 응답 코드에 따라 이렇게 처리합니다.

| 응답 | 앱 처리 |
| --- | --- |
| 2xx | 업로드 완료, 응답 점수 저장 |
| 네트워크 오류, 5xx, 401, 408, 429 | 대기열에 남겨 나중에 다시 보냄 |
| 그 외 4xx (400, 404, 409 등) | 다시 보내도 같은 결과라 보고 **재전송을 멈춤** (데이터는 기기에 남김) |

**요청**: 잠깐 지나면 풀리는 문제(DB 일시 장애, 동시 요청 충돌 등)에는 4xx가 아니라 5xx를 돌려주세요. 4xx를 받으면 그 운행은 다시 올라가지 않습니다.

## 6. 오늘의 미션: 운행이 없는 날의 응답 (확인)

앱은 서버 plan.md 3.6 형식 그대로 씁니다.

```json
{
  "missionId": 1,
  "title": "회전 깜빡이 완벽",
  "periodKey": "2026-11-03",
  "status": "IN_PROGRESS",
  "progress": { "turns": 2, "successTurns": 2, "requiredTurns": 3 }
}
```

**요청**: 그날 운행이 아직 없어도 404가 아니라 `status: "IN_PROGRESS"`, `turns: 0`, `successTurns: 0`인 응답을 주세요. 앱은 시동 시 이 값으로 미션을 안내합니다.

## 7. 기준값 `turn`에 조향각 부호 추가 (변경)

**배경**: 에뮬레이터에서 Vehicle SDK 조향각은 오른쪽으로 꺾을 때 **음수**로 옵니다(`docs/spikes/vehicle-background-speed.md`). 앱 기본값은 이 실측에 맞췄지만 실차 부호는 아직 확인하지 못했습니다. 실차가 반대로 나오면 앱을 다시 배포하지 않고 바꿀 수 있어야 합니다.

**요청**: 1번의 `turn` 객체에 아래 필드를 추가해 주세요. 값이 없으면 앱은 `false`(음수 = 오른쪽)로 봅니다.

```json
"turn": {
  "steeringEnterDeg": 90,
  "steeringExitDeg": 20,
  "steeringRightPositive": false,
  "signalLeadSec": 5,
  "minSpeedKmh": 5,
  "mergeGapSec": 3
}
```

## 8. 점수 응답 `plays`에 회전 방향 `direction` 추가 (변경)

**배경**: 플레이 타임라인(SCR-02a)은 카드마다 좌·우 아이콘을 그립니다(앱 `docs/screen-plan.md` 4.5). 서버 plan.md 3.5의 `plays` 항목에는 `occurredAt`, `type`, `result`만 있어서 방향을 알 수 없습니다.

**요청**: `TURN` 플레이에 앱이 보낸 `payload.direction`(`LEFT`/`RIGHT`)을 그대로 실어 주세요. 다른 종류의 플레이(P1 급가감속 등)는 빼도 됩니다.

```json
"plays": [
  { "occurredAt": "2026-11-03T08:12:30+09:00", "type": "TURN", "direction": "LEFT", "result": "GOAL" },
  { "occurredAt": "2026-11-03T08:15:02+09:00", "type": "TURN", "direction": "RIGHT", "result": "CONCEDED" }
]
```

- 앱은 `grade`, `summary`, `plays`, `mission`이 없어도 동작합니다(없으면 기기에 저장된 회전 기록으로 그림). 반칙(`fouls`)은 P0 화면에 표시하지 않습니다.
- 앱이 쓰는 등급 기준: 서버 3.3 `grades`(S95·A85·B70·C50), 50점 미만은 `grade: null`.

## 9. 최근 경기 목록 응답 모양 확인 (확인)

**배경**: 홈(SCR-01)의 최근 경기 2건에 `GET /api/trips?limit=2`를 씁니다. 서버 plan.md 3.5에 항목 필드는 있지만 응답 바깥 모양이 정해지지 않았습니다.

**앱이 가정한 형식**: 감싸는 객체 없이 배열, 종료 시각 최신순.

```json
[
  { "tripId": "5f1c...", "endedAt": "2026-11-03T18:10:00+09:00", "distanceKm": 12.4, "status": "SCORED", "score": 67, "grade": null },
  { "tripId": "9a2b...", "endedAt": "2026-11-03T08:42:00+09:00", "distanceKm": 0.6, "status": "EXCLUDED_SHORT", "score": null, "grade": null }
]
```

**확인 요청**: 배열이 맞는지(페이지 정보로 감싸면 그 형식), `limit` 기본값·최댓값, 업로드 대기 중이라 서버에 없는 운행은 앱이 로컬 기록과 합쳐 보여 준다는 점.

## 10. 짧은 운행 기준 `trip.minDistanceKm` → 1.0 (변경, 11.09 결정)

> **결정(11.09, 앱 팀)**: **1km**로 합니다. 서버 기준값 `trip.minDistanceKm`을 1.0으로 바꿔 주세요(서버 plan.md 9.2의 제안과 같음). 앱 기본값은 1.0이고, 서버 값을 받으면 그 값을 따릅니다.

이전 내용:

**배경**: 서버 `default-thresholds.json`의 `trip.minDistanceKm`은 **3.0**입니다. 앱 plan.md의 MVP 기준은 "**1km 미만** 운행은 점수 대상에서 제외"이고, 앱 기본값도 1.0입니다.

앱은 이제 이 값을 서버에서 받아 씁니다(종료 음성 "짧은 운행이라 점수에는 반영되지 않아요" 판단). 서버 점수 계산(`EXCLUDED_SHORT`)과 앱 안내가 같은 값을 써야 하므로 어느 쪽이 맞는지 정해 주세요.

**확인 요청**: 3.0이 의도한 값인지, 1.0으로 바꿀지. 3km면 집 근처 짧은 운행(마트 등)이 대부분 경기에서 빠집니다.

참고: 서버 `turn`에는 아직 핸들 각도 항목(1·7번)이 없어서, 앱은 서버가 주는 항목만 덮어쓰고 나머지는 앱 기본값을 씁니다.

## 11. D16·D17 결정에 필요한 앱 쪽 정보 (정보)

서버 plan.md 0장 D16(업로드 이벤트 개수·요청 크기 상한), D17(운행 시각 허용 범위)이 "앱 팀과 확인"으로 되어 있어 앱 동작을 적습니다.

**D16 — 운행 1건의 이벤트 수**
- 지금 앱은 `TURN` 이벤트만 보냅니다. 판정한 회전 1번 = 이벤트 1개입니다(연속 회전은 묶고, 회전교차로·저속 회전은 빠짐).
- 시내 1시간 운행에서 회전은 많아야 수십~100번 정도라, 2차 항목이 늘어도 운행 1건에 수백 개를 넘기 어렵습니다. 제안하신 **최대 2,000개**면 충분합니다.
- 앱은 운행 1건을 요청 1번으로 보냅니다(나눠 보내지 않음).

**D17 — 운행 시각과 오프라인 보관**
- 시각은 **차량(기기) 시계** 기준입니다(`2026-11-03T08:00:00+09:00` 형식). 에뮬레이터에서는 기기 시계가 PC보다 몇 분~수십 분 어긋나는 것을 봤습니다. 실차 시계도 어긋날 수 있으니 **미래 허용 오차는 5분보다 넉넉하게**(예: 1일) 두시길 권합니다.
- 올리지 못한 운행은 기기(Room)에 기간 제한 없이 남고, 네트워크가 연결되면 WorkManager가 다시 보냅니다(앱 시작·운행 종료 때마다 재예약). 며칠 뒤에 올라갈 수 있습니다.
- 앱은 4xx(401·408·429 제외)를 받으면 그 운행을 **다시 보내지 않고 멈춥니다**(기기에는 보존). 오래된 운행을 400으로 거부하면 그 운행은 영영 반영되지 않으므로, **과거 상한은 넉넉하게**(예: 30일) 두시길 권합니다.

## 12. 점수 응답에 이 운행의 적립 포인트 `earnedPoints` 추가 (변경, 2차)

**배경**: 경기 결과 화면(SCR-02)에 "이번 운행으로 +12P 적립"을 보여 주려 합니다(앱 plan.md 4장 "적립 포인트"). 그런데 `GET /api/points`의 원장 항목에는 어느 운행의 적립인지(`ref_id`/`tripId`)가 없어 결과 화면에서 고를 수 없습니다. 앱이 `floor(min(거리,50) × 평점 ÷ 100)`로 직접 계산하면 하루 상한(150P) 때문에 실제 적립과 달라지고, 포인트 규칙은 서버가 가진다는 원칙에도 어긋납니다.

**요청**: `TripResult`(`GET /api/trips/{tripId}/score`, `POST /api/trips` 응답)에 이 운행의 `TRIP` 적립액(상한 적용 후)을 넣어 주세요.

```json
{ "tripId": "…", "status": "SCORED", "score": 67, "grade": "C", "earnedPoints": 8, … }
```

- 점수가 나지 않은 운행(`NO_TURN`, `EXCLUDED_SHORT`, `UPLOADED`)은 `null` 또는 생략
- 상한에 걸려 적립하지 못했으면 `0` (앱은 "오늘 적립 한도를 채워 포인트는 쌓이지 않았어요")
- 미션·연속 보상은 결과 화면의 퀘스트 칩과 지갑에서 보여 주므로 여기에는 운행 적립만

앱은 이 필드가 없으면 결과 화면에 포인트를 표시하지 않습니다(지금 서버에서도 문제없이 동작).

## 13. 깜빡이 팁 문구를 앱 판정 기준에 맞춤 (변경)

**배경**: 항목 상세 화면(SCR-02b)에 기준값 `tips`의 문구를 보여 줍니다. 지금 서버(develop `5d1ad9c`)의 `tips.TURN_SIGNAL`은 **"회전 30m 전에 깜빡이를 켜 보세요."** 입니다(D18 확장안의 30m 기준). 그런데 앱은 아직 **회전 시작 5초 전**부터 켰는지로 판정하고(`turn.signalLeadSec` 5), 거리 기준 판정(`signalTiming`)은 보내지 않습니다.

사용자에게 "30m 전"을 안내하면서 실제로는 "5초 전" 기준으로 득점·실점을 매기게 됩니다.

**요청**: 앱이 거리 기준 판정(`signalTiming`)을 보내기 전까지는 `tips.TURN_SIGNAL`을 **"회전 5초 전에 깜빡이를 켜 보세요."** 로 두고, 2차에서 판정 기준을 바꿀 때 문구도 함께 바꿔 주세요. (서버 처음 기본값 문구가 이것이었습니다.)

앱은 서버 문구가 오면 그대로 보여 주고, 받지 못했을 때만 앱 기본 문구("회전 5초 전…")를 씁니다.

## 14. 지역 리그 `GET /api/leagues/current` 응답 형식 제안 (제안, 2차)

> **11.10 해소**: 서버는 아래 제안과 다른 형식으로 구현했고(서버 `docs/app-team-changes-2026-11-10.md`), 앱이 서버 형식에 맞췄습니다. 아래는 기록으로 남깁니다.

**배경**: 2차 PT에서 게임의 목표는 리그 순위입니다(D33). 서버 4.4가 아직 구현 전이라, 앱은 리그 화면(SCR-04)을 이 형식으로 먼저 만들었습니다(서버가 없을 때는 Fake). 서버 plan.md 4.4의 설명(시즌, 권역별 안전지수, 닉네임만, 내 순위 + 상위 N명)과 결정(D5·D8·D11·D13·D19·D29·D31)을 바탕으로 했습니다. **다르게 정하시면 알려 주세요. 앱은 DTO만 맞추면 됩니다.**

```json
{
  "season": { "seasonId": "2026-11", "startsAt": "2026-11-01T00:00:00+09:00", "endsAt": "2026-12-01T00:00:00+09:00" },
  "myLeague": {
    "region": "SEOUL_NORTHWEST", "name": "서울 서북권",
    "participants": 24, "rankPublic": true,
    "myRank": 3, "myScore": 88,
    "qualified": true, "seasonKm": 62.4, "seasonTrips": 12,
    "top": [ { "rank": 1, "nickname": "안전왕", "score": 95, "me": false }, { "rank": 3, "nickname": "깜빡왕", "score": 88, "me": true } ]
  },
  "regions": [ { "region": "SEOUL_SOUTHEAST", "name": "서울 동남권", "safetyIndex": 89, "rank": 1 } ]
}
```

| 필드 | 앱에서 쓰는 곳 | 근거 |
| --- | --- | --- |
| `season.endsAt` | "11월 시즌 · D-22" | D29 달력 월 |
| `myLeague.name` | 내 리그 이름 | D31 권역 |
| `participants`, `rankPublic` | 10명 미만이면 "참여자가 10명이 되면 순위가 공개돼요" | D11 |
| `myRank`, `myScore` | 내 순위, 리그 점수(공통 항목) | D19 |
| `qualified`, `seasonKm`, `seasonTrips` | "이번 시즌 50km 또는 10번 운행하면 순위에 올라요" + 진행 | D13 |
| `top` | 상위 N명(닉네임·점수, 내 줄 강조). 비공개면 빈 목록 | 4.4 닉네임만 |
| `regions` | 지역 안전지수 순위 | D5 |

- 순위 비공개 운전자(D8)는 `myLeague.rankPublic`과 별개로 `myRank` 없이 `top`에서 빠지면 됩니다. 공개 여부를 바꾸는 API가 생기면 앱 설정 화면에 토글을 붙입니다(제안: `PATCH /api/drivers/me` `{ "rankPublic": false }`).
- 등록 지역이 매핑표에 없으면 `myLeague: null`

## 15. 배지 `STREAK_7` 설명 문구를 퀘스트 용어로 (변경)

**배경**: 앱은 11.13에 화면과 음성의 "오늘의 미션"을 기획서 용어인 **"오늘의 퀘스트"** 로 바꿨습니다. 그런데 업적 화면(SCR-05)의 배지 설명은 `GET /api/badges`의 `BadgeResponse.condition`을 그대로 보여 주므로, `STREAK_7` 배지에는 아직 **"오늘의 미션 7일 연속 달성"** 이 남아 화면 안에서 용어가 섞입니다.

**요청**: `STREAK_7`의 `condition`을 **"오늘의 퀘스트 7일 연속 달성"** 으로 바꿔 주세요.

**바꾸지 않을 것**: 배지 코드 `STREAK_7`, 포인트 적립 사유 `DAILY_MISSION`, `/api/missions/*` 경로, 응답 필드 이름. 앱은 이 이름들을 그대로 씁니다.

- 앱은 `STREAK_7` 문구만 확인했습니다. 다른 배지(`docs/api/README.md` 기준 `FIRST_TRIP` · `SIGNAL_100` · `SEASON_CHAMPION`)의 `condition`에도 "미션"이 들어 있으면 같은 방식으로 "퀘스트"로 바꿔 주세요.
- 주간 리포트의 "맞춤 미션"(`lastMission`, `nextMission`)은 이 요청 대상이 아닙니다. 앱 화면도 "맞춤 미션"을 그대로 씁니다.

앱은 서버 문구를 그대로 보여 주므로 앱 쪽 추가 작업은 없습니다(앱의 Fake 서버와 Preview 문구는 먼저 바꿨습니다).
