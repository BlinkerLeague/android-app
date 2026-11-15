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
  5) --compare-location: 주입 좌표(ProbeInject geo=) · FusedLocation onChanged · NaviHelper onCurrentLocationInfo 비교
     - Fused가 주입 좌표를 반영하기까지의 지연(같은 좌표가 처음 온 시각)
     - Navi 폴링 시각마다 직전 주입·Fused 값과의 거리(m)와 주소
"""
import argparse
import re
import statistics
import sys

LINE = re.compile(r"\b(VehicleTest|SpeedTest|NaviTest|LocationTest|ProbeInject)\s*: (.*)$")
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


_DAY = 86_400_000
_last_ms = [None, 0]  # [직전 시각(하루 안 ms), 누적 날짜 보정]


def ms(clock):
    """HH:mm:ss.SSS → ms. 로그가 자정을 넘으면(시각이 12시간 이상 거꾸로 가면) 하루를 더함"""
    h, m, s = clock.split(":")
    v = (int(h) * 3600 + int(m) * 60) * 1000 + round(float(s) * 1000)
    prev, offset = _last_ms
    if prev is not None and v < prev - _DAY // 2:
        offset += _DAY
    elif prev is not None and v > prev + _DAY // 2 and offset:
        offset -= _DAY  # 순서가 조금 섞여 자정 직전 줄이 나중에 온 경우
    _last_ms[0], _last_ms[1] = v, offset
    return v + offset


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
    p.add_argument("--compare-location", action="store_true", help="주입·Fused·Navi 위치 비교")
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
                if key != "geo":  # 위치 주입은 1초마다라 --compare-location에서 따로 봄
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

    if args.compare_location:
        compare_location(events, injects)

    if procs:
        print("\n== 프로세스 상태")
        for _, clock, label in procs:
            print(f"{clock}  {label}")


def _meters(a, b):
    import math
    dlat = (b[0] - a[0]) * 111_320
    dlng = (b[1] - a[1]) * 111_320 * math.cos(math.radians(a[0]))
    return math.hypot(dlat, dlng)


def compare_location(events, injects):
    geo = []
    for t, clock, label in injects:
        m = re.match(r"geo=([-\d.]+),([-\d.]+),([-\d.]+)", label)
        if m:
            geo.append((t, clock, float(m.group(1)), float(m.group(2)), float(m.group(3))))
    fused = []
    for t, clock, v in events.get(("Fused", "onChanged"), []):
        m = re.search(r"lat=([-\d.]+) lng=([-\d.]+) alt=([-\d.]+) heading=([-\d.]+) confidence=([-\d.]+)", v)
        if m:
            fused.append((t, clock, *map(float, m.groups())))
    navi = []
    for t, clock, v in events.get(("Navi", "onCurrentLocationInfo"), []):
        a = re.search(r'address="([^"]*)"', v)
        h = re.search(r"heading=([-\d.]+)", v)
        la = re.search(r"latitude=([-\d.]+)", v)
        lo = re.search(r"longitude=([-\d.]+)", v)
        if la and lo:
            navi.append((t, clock, float(la.group(1)), float(lo.group(1)), float(h.group(1)) if h else None,
                         a.group(1) if a else ""))
    if not geo:
        print("\n== 위치 비교: 주입 기록(geo=)이 없습니다")
        return

    print("\n== Fused 반영 지연: 주입 좌표가 onChanged로 처음 온 시각까지")
    lags, missed = [], 0
    for t, clock, lat, lng, alt in geo:
        hit = next((f for f in fused if f[0] >= t - 50 and abs(f[2] - lat) < 2e-6 and abs(f[3] - lng) < 2e-6), None)
        if hit:
            lags.append(hit[0] - t)
        else:
            missed += 1
    if lags:
        lags.sort()
        print(f"주입 {len(geo)}회 중 반영 {len(lags)}회, 안 옴 {missed}회. 지연(ms) 최소 {lags[0]}, "
              f"중앙 {lags[len(lags) // 2]}, 최대 {lags[-1]}")
    else:
        print(f"주입 {len(geo)}회 중 같은 좌표로 온 onChanged 없음")

    print("\n== Fused 값 샘플 (10개마다)")
    print(f"{'시각':<13}{'Fused 위도,경도':<26}{'고도':>7}{'heading':>9}{'신뢰도':>8}  {'직전 주입과 거리(m)':>18}  직전 주입 고도")
    for i, f in enumerate(fused):
        if i % 10:
            continue
        g = next((g for g in reversed(geo) if g[0] <= f[0] + 50), None)
        d = f"{_meters((g[2], g[3]), (f[2], f[3])):.1f}" if g else "-"
        print(f"{f[1]:<13}{f[2]:.6f},{f[3]:.6f}{'':<4}{f[4]:>7.1f}{f[5]:>9.1f}{f[6]:>8.1f}  {d:>18}  {g[4] if g else '-'}")

    if navi:
        print("\n== Navi getCurrentLocationInfo vs 주입 vs Fused (폴링마다)")
        print(f"{'시각':<13}{'Navi 위도,경도':<24}{'heading':>8}  {'주입과 거리(m)':>14}{'Fused와 거리(m)':>16}  주소")
        for t, clock, lat, lng, hd, addr in navi:
            g = next((g for g in reversed(geo) if g[0] <= t), None)
            f = next((f for f in reversed(fused) if f[0] <= t), None)
            dg = f"{_meters((g[2], g[3]), (lat, lng)):.0f}" if g else "-"
            df = f"{_meters((f[2], f[3]), (lat, lng)):.0f}" if f else "-"
            print(f"{clock:<13}{lat:.6f},{lng:.6f}  {hd if hd is not None else '-':>8}  {dg:>14}{df:>16}  {addr}")


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
