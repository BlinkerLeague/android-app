#!/usr/bin/env python3
"""위치 주입 시나리오 (docs/spikes/fused-location.md).

에뮬레이터 GPS(adb emu geo fix 경도 위도 고도)를 1초마다 도로 경로를 따라 옮기고, 주입 값을 Logcat ProbeInject에 남긴다.
기본 경로(공개 OSRM 도로 경로, 약 750m): 신촌역 남쪽 마포구 노고산동 → 신촌로 북쪽 서대문구 창천동.
신촌로 부근에서 마포구 → 서대문구 경계를 넘는다. (연세대 정문 앞 연세로는 차량 통행 제한이라 OSRM이 2km 넘게 우회해서 피함)

  python3 scripts/location_route_sim.py                       # 기본 경로, 30km/h, 고도 20→60m
  python3 scripts/location_route_sim.py --speed 40 --hold 10   # 출발 전 10초 정지
  python3 scripts/location_route_sim.py --no-console           # Virtual Console(기어·속도) 없이 GPS만

주입 라벨: geo=위도,경도,고도 (1초마다)
"""
import argparse
import subprocess
import time

from geo_route import bearing_deg, road_points, walk
from vconsole import ADB, Console

NOGOSAN = (37.5535, 126.9373)    # 신촌역 남쪽, 마포구 노고산동
CHANGCHEON = (37.5580, 126.9330)  # 신촌로 북쪽, 서대문구 창천동


def geo_fix(lat, lng, alt):
    subprocess.run([ADB, "emu", "geo", "fix", f"{lng:.7f}", f"{lat:.7f}", f"{alt:.1f}"], check=False, capture_output=True)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--from", dest="start", default=f"{NOGOSAN[0]},{NOGOSAN[1]}")
    p.add_argument("--to", dest="dest", default=f"{CHANGCHEON[0]},{CHANGCHEON[1]}")
    p.add_argument("--speed", type=float, default=30, help="km/h")
    p.add_argument("--alt-start", type=float, default=20)
    p.add_argument("--alt-end", type=float, default=60)
    p.add_argument("--hold", type=int, default=5, help="출발 전·도착 후 정지 초")
    p.add_argument("--no-console", action="store_true")
    args = p.parse_args()

    start = tuple(map(float, args.start.split(",")))
    dest = tuple(map(float, args.dest.split(",")))
    pts = walk(road_points(start, dest), args.speed / 3.6)
    n = len(pts)
    console = Console()
    print(f"경로 {n - 1}초, {args.speed:g}km/h")
    if not args.no_console:
        console.put("/vehicle/gear?gear=Drive", "gear=Drive")
    console.mark("scenario route start")
    for _ in range(args.hold):
        geo_fix(*pts[0], args.alt_start)
        console.mark(f"geo={pts[0][0]:.6f},{pts[0][1]:.6f},{args.alt_start:.1f} hold")
        time.sleep(1)
    if not args.no_console:
        console.put(f"/vehicle/controls?speed={args.speed:g}&accelerator=20&brake=0&steering=0", f"speed={args.speed:g}")
    for i, (lat, lng) in enumerate(pts):
        tick = time.monotonic()
        alt = args.alt_start + (args.alt_end - args.alt_start) * i / max(1, n - 1)
        geo_fix(lat, lng, alt)
        brg = bearing_deg(pts[i - 1], pts[i]) if i else 0.0
        console.mark(f"geo={lat:.6f},{lng:.6f},{alt:.1f} bearing={brg:.0f}")
        time.sleep(max(0.0, 1.0 - (time.monotonic() - tick)))
    if not args.no_console:
        console.put("/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0", "speed=0")
    for _ in range(args.hold):
        geo_fix(*pts[-1], args.alt_end)
        time.sleep(1)
    if not args.no_console:
        console.put("/vehicle/gear?gear=Park", "gear=Park")
    console.mark("scenario route end")
    time.sleep(1)
    console.close()


if __name__ == "__main__":
    main()
