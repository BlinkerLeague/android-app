#!/usr/bin/env python3
"""NaviTest 로그(NaviEventLogger)를 콜백별로 요약한다.

사용법:
  adb logcat -d -s NaviTest:I > navi.log
  python3 scripts/navi_log_summary.py navi.log            # 전체
  python3 scripts/navi_log_summary.py navi.log --from 05:10:00 --to 05:15:00   # 구간만

출력: 콜백별 횟수, 간격(평균/최소/최대/중앙값, 초), 처음·마지막 시각, 마지막 값 일부
"""
import argparse
import re
import statistics
import sys

LINE = re.compile(r"NaviTest: (\d\d:\d\d:\d\d\.\d{3}) \+([\d.]+) dt=\S+ #\d+ (\w+) ?(.*)$")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("file", nargs="?", help="logcat 파일 (없으면 표준 입력)")
    p.add_argument("--from", dest="start", help="이 시각(HH:MM:SS) 이후만")
    p.add_argument("--to", dest="end", help="이 시각(HH:MM:SS) 이전만")
    p.add_argument("--width", type=int, default=100, help="마지막 값 표시 길이")
    args = p.parse_args()

    src = open(args.file, encoding="utf-8") if args.file else sys.stdin
    events = {}
    for raw in src:
        m = LINE.search(raw)
        if not m:
            continue
        clock, elapsed, name, body = m.group(1), float(m.group(2)), m.group(3), m.group(4)
        if name in ("initialize", "release", "requestRoute", "cancelRoute"):
            continue
        if args.start and clock[:8] < args.start:
            continue
        if args.end and clock[:8] > args.end:
            continue
        events.setdefault(name, []).append((clock, elapsed, body))

    if not events:
        print("NaviTest 콜백이 없습니다.")
        return

    print(f"{'콜백':<26}{'횟수':>5}  {'평균':>7}{'최소':>7}{'최대':>7}{'중앙':>7}  {'처음':<13}{'마지막':<13}마지막 값")
    for name, rows in sorted(events.items(), key=lambda kv: kv[1][0][1]):
        gaps = [b[1] - a[1] for a, b in zip(rows, rows[1:])]
        stat = (
            f"{statistics.mean(gaps):7.2f}{min(gaps):7.2f}{max(gaps):7.2f}{statistics.median(gaps):7.2f}"
            if gaps else f"{'-':>7}" * 4
        )
        last = rows[-1][2][: args.width]
        print(f"{name:<26}{len(rows):>5}  {stat}  {rows[0][0]:<13}{rows[-1][0]:<13}{last}")


if __name__ == "__main__":
    main()
