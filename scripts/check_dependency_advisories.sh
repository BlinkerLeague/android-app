#!/usr/bin/env bash
# 릴리스 빌드에 들어가는 라이브러리(releaseRuntimeClasspath)를 GitHub 보안 권고 DB(maven)와 대조.
# Pleos 앱 보안 가이드 "SDK·라이브러리 점검"(docs/security-checklist.md). 필요: JDK 17, gh(로그인)
# 사용: scripts/check_dependency_advisories.sh
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
./gradlew -q :automotive:dependencies --configuration releaseRuntimeClasspath > "$tmp/deps.txt"
# "group:artifact:version" 또는 "… -> 해석된 버전"에서 실제 버전을 뽑음 (제약 줄 "(c)"는 뺌)
grep -v "(c)" "$tmp/deps.txt" \
  | grep -oE '[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+( -> [A-Za-z0-9_.-]+)?' \
  | sed -E 's/^([^:]+:[^:]+):[^ ]+ -> (.+)$/\1:\2/' | sort -u > "$tmp/resolved.txt"
count=0; found=0
while read -r line; do
  pkg="${line%:*}"; ver="${line##*:}"; count=$((count + 1))
  out=$(gh api -X GET /advisories -f ecosystem=maven -f "affects=$pkg@$ver" -f per_page=20 \
        --jq '.[] | "  \(.ghsa_id) [\(.severity)] \(.summary)"')
  if [ -n "$out" ]; then found=$((found + 1)); echo "$pkg@$ver"; echo "$out"; fi
done < "$tmp/resolved.txt"
echo "점검: ${count}개, 보안 권고가 있는 라이브러리: ${found}개"
echo "(Pleos SDK처럼 공개 저장소 밖 라이브러리는 이 DB에 없음)"
