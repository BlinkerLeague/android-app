#!/usr/bin/env python3
"""조사용 Logcat(VehicleTest·SpeedTest·NaviTest·ProbeInject)을 요약한다.

  adb logcat -d -s VehicleTest:I SpeedTest:I NaviTest:I ProbeInject:I > run.log
  python3 scripts/probe_log_summary.py run.log [--from HH:MM:SS] [--to HH:MM:SS]

출력
  1) 콜백·속성별 횟수, 간격(평균/최소/최대/중앙, 초), 처음·마지막 시각, 마지막 값
  2) 주입(ProbeInject) 대비 지연: 주입마다 관련 콜백 중 값이 처음 바뀐 것까지의 시간(ms). 다음 주입 전까지 바뀌지 않으면 '변화 없음'
     주입 시각은 호스트 시계를 기기 시계로 환산한 값이라 ±수십 ms 오차가 있음 → 주입 --tol ms 전부터 다음 주입 --tol ms 전까지를 그 주입의 반응으로 봄
     (지연이 음수거나 수 ms면 "시계 오차 범위 안에서 즉시"로 읽음)
  3) 프로세스 상태 기록(procState)
  4) --integrate: 트립 거리 콜백이 올 때마다 PERF_VEHICLE_SPEED(m/s) 적분값(km)과 나란히 보여줌
"""
import argparse
import re
import statistics
import sys

LINE = re.compile(r"\b(VehicleTest|SpeedTest|NaviTest|ProbeInject)\s*: (.*)$")
PROBE = re.compile(r"^(\d\d:\d\d:\d\d\.\d{3}) \+[\d.]+ dt=\S+ #\d+ (?:\[(\w+)\] )?(\S+) ?(.*)$")
INJECT = re.compile(r"^'?(\d\d:\d\d:\d\d\.\d{3}) (.*?)'?$")
SKIP = {"start", "initialize", "release", "stop", "requestRoute", "cancelRoute", "pollCurrentLocation",
        "getCurrentLocationInfo", "permission", "config", "registerCallback", "getPropertyList"}

# 주입 라벨 앞부분 → 반응을 볼 (출처, 이름)
TARGETS = {
    "turn": [("Vehicle", "onTurnSignalStateUpdated"), ("CarProperty", "TURN_SIGNAL_STATE"),
             ("Vehicle", "onTelltaleInfoUpdated")],
    "hazard": [("Vehicle", "onTurnSignalStateUpdated"), ("CarProperty", "TURN_SIGNAL_STATE"),
               ("Vehicle", "onTelltaleInfoUpdated")],
    "steer": [("Vehicle", "onSteeringWheelAngleUpdated"), ("CarProperty", "PERF_STEERING_ANGLE")],
    "speed": [("CarProperty", "PERF_VEHICLE_SPEED"), ("CarProperty", "PERF_VEHICLE_SPEED_DISPLAY")],
    "gear": [("Vehicle", "onSelected(Gear)"), ("CarProperty", "GEAR_SELECTION"), ("CarProperty", "CURRENT_GEAR")],
}


def ms(clock):
    h, m, s = clock.split(":")
    return (int(h) * 3600 + int(m) * 60) * 1000 + round(float(s) * 1000)


def norm(value):
    return re.sub(r" ts=\d+", "", value)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("file", nargs="?")
    p.add_argument("--from", dest="start")
    p.add_argument("--to", dest="end")
    p.add_argument("--width", type=int, default=60)
    p.add_argument("--tol", type=int, default=50, help="주입 시각 오차 허용(ms)")
    p.add_argument("--integrate", action="store_true", help="트립 거리와 속도 적분 비교")
    args = p.parse_args()

    events, injects, procs = {}, [], []
    for raw in open(args.file, encoding="utf-8") if args.file else sys.stdin:
        m = LINE.search(raw)
        if not m:
            continue
        tag, payload = m.groups()
        if tag == "ProbeInject":
            im = INJECT.match(payload.strip())
            if not im:
                continue
            clock, label = im.groups()
            if args.start and clock[:8] < args.start or args.end and clock[:8] > args.end:
                continue
            (procs if label.startswith("procState") else injects).append((ms(clock), clock, label))
            continue
        pm = PROBE.match(payload)
        if not pm:
            continue
        clock, source, name, value = pm.groups()
        source = source or "Navi"
        if name in SKIP or args.start and clock[:8] < args.start or args.end and clock[:8] > args.end:
            continue
        events.setdefault((source, name), []).append((ms(clock), clock, value))

    print("== 콜백·속성별")
    print(f"{'출처/이름':<46}{'횟수':>5} {'평균':>7}{'최소':>7}{'최대':>7}{'중앙':>7}  {'처음':<13}{'마지막':<13}마지막 값")
    for (src, name), rows in sorted(events.items(), key=lambda kv: kv[1][0][0]):
        gaps = [(b[0] - a[0]) / 1000 for a, b in zip(rows, rows[1:])]
        stat = (f"{statistics.mean(gaps):7.2f}{min(gaps):7.2f}{max(gaps):7.2f}{statistics.median(gaps):7.2f}"
                if gaps else f"{'-':>7}" * 4)
        print(f"{src + '/' + name:<46}{len(rows):>5} {stat}  {rows[0][1]:<13}{rows[-1][1]:<13}{rows[-1][2][:args.width]}")

    if injects:
        print("\n== 주입 대비 지연 (값이 처음 바뀐 콜백까지, ms)")
        for i, (t, clock, label) in enumerate(injects):
            key = label.split("=")[0]
            if key not in TARGETS:
                print(f"{clock}  {label}")
                continue
            nxt = next((u for u, _, l in injects[i + 1:] if l.split("=")[0] in TARGETS), t + 10000)
            lo, hi = t - args.tol, nxt - args.tol
            parts = []
            for target in TARGETS[key]:
                rows = events.get(target)
                if not rows:
                    continue
                before = [r for r in rows if r[0] < lo]
                last = norm(before[-1][2]) if before else None
                hit = next((r for r in rows if lo <= r[0] < hi and norm(r[2]) != last), None)
                name = f"{target[0]}/{target[1]}"
                parts.append(f"{name} {hit[0] - t:+d}ms → {hit[2][:40]}" if hit else f"{name} 변화 없음")
            print(f"{clock}  {label:<18} " + (" | ".join(parts) if parts else "(관련 콜백 없음)"))

    if args.integrate:
        integrate(events)

    if procs:
        print("\n== 프로세스 상태")
        for _, clock, label in procs:
            print(f"{clock}  {label}")


def integrate(events):
    speed = events.get(("CarProperty", "PERF_VEHICLE_SPEED"), [])
    trips = sorted(
        [(t, c, name, v) for (src, name), rows in events.items() if src == "Vehicle" and "Distance" in name
         for t, c, v in rows],
    )
    if not speed or not trips:
        print("\n== 적분 비교: 속도 또는 트립 거리 기록이 없습니다")
        return
    pts = []
    for t, _, v in speed:
        m = re.match(r"value=([-\d.]+)", v)
        if m:
            pts.append((t, float(m.group(1))))
    t0 = trips[0][0]
    first = {}
    print("\n== 트립 거리 vs 속도 적분 (첫 트립 콜백 시각부터, 속도는 직사각형 적분)")
    print(f"{'시각':<13}{'콜백':<36}{'값':>12}{'첫 값 대비':>12}{'속도 적분(km)':>15}")
    for t, clock, name, v in trips:
        try:
            val = float(v)
        except ValueError:
            continue
        first.setdefault(name, val)
        # 구간 [a, b)의 속도는 a 시점 값(m/s)으로 봄 → m, 다시 km
        km = sum(a[1] * (b[0] - a[0]) / 1000 for a, b in zip(pts, pts[1:]) if t0 <= a[0] < t) / 1000
        print(f"{clock:<13}{name:<36}{val:>12.3f}{val - first[name]:>12.3f}{km:>15.3f}")


if __name__ == "__main__":
    main()
