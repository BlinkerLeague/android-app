# 리뷰: feature/release-key-done (2026-11-11)

- 기준: develop...feature/release-key-done (1개 커밋, 6cb4137)
- 범위: 출시 키 백업 완료(11.11)를 plan.md · README.md · docs/security-checklist.md에 반영 (문서만)
- 판단: 머지 가능

## 지적 사항

막는 문제 없음. 참고 사항 하나만 적는다.

### 1. README "남은 항목" 요약에 Pleos 속도 권한 답변이 빠져 있음 (심각도: 낮음, 이번 변경 밖)
- 위치: README.md:28, docs/security-checklist.md:60-64
- 문제: 보안 점검표 "남은 일"에는 "CarPropertyManager(속도) 사용 허용 여부 — Pleos 답변"이 있는데, README 28줄 괄호 요약에는 없다. 이번 커밋에서 생긴 문제는 아니다(develop에도 같음).
- 실패 시나리오: README만 보고 제출 전 점검을 하면 Pleos 답변 대기 항목을 놓칠 수 있다.
- 제안: 다음 문서 작업 때 README 28줄 괄호에 이 항목을 더하거나 "남은 일 전체" 링크만 남긴다. 이번 PR을 막을 이유는 아니다.

## 확인한 것

- 체크박스 일관성
  - plan.md:144 `[x] 출시 키 만들기·백업 완료(11.11)`. 상위 plan.md:142 `[x] release APK 빌드`는 하위 5개(서명 설정, 출시 키, release 점검, 로컬 서버 연결, 에뮬레이터 설치)가 모두 `[x]`이므로 맞다.
  - README.md:25 `[x]`, 문구 "만들기·백업 완료(11.11)".
  - docs/security-checklist.md:57 배포용 인증서 ⏳ → ✅, "남은 일"에서 출시 키 줄을 지움. 남은 4줄(암호화 결정, 속도 권한 답변, 운영 HTTPS, 의존성 점검 재실행)은 그대로다.
- 다른 남은 일을 잘못 완료 처리하지 않음: 시연 영상(plan.md:139, 편집·자막 141), 공모전 제출 양식(plan.md:154, README.md:27), README 보안 남은 항목(README.md:28)은 모두 `[ ]` 그대로다.
- 비밀값 · 백업 위치 노출 없음: diff에 비밀번호, 저장 위치(드라이브 · USB · 클라우드 등), keystore 경로가 없다. 바뀐 파일은 문서 3개뿐이고 `local.properties` · `.jks` 커밋 없음.
- 문서 간 표현: 세 문서 모두 "만들기·(서명 확인·)백업 완료(11.11)"로 날짜와 뜻이 같다. README 항목 제목은 기존대로 "보관", 괄호와 plan은 "백업"이지만 뜻이 같아 문제 삼지 않는다. 남은 "백업 남음" 표현은 저장소 어디에도 없다(grep 확인). security-checklist.md:15의 "백업"은 Android 자동 백업 항목이라 무관하다.
- README 6-1의 백업 안내(README.md:173)는 그대로 유지되어, 완료 표시 뒤에도 방법 설명은 남는다.
- TDD · 테스트: 동작 변경 없는 문서 커밋(`docs:`)이라 사이클 대상 아님.

## 검증

- `git log develop..HEAD --oneline`, `git diff develop...HEAD`, 관련 문구 grep으로 확인.
- 단위 테스트 · 빌드는 실행하지 않음: 문서 3개만 바뀌어 빌드 결과에 영향이 없다.
