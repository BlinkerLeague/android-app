#!/usr/bin/env python3
"""에뮬레이터 주행 시뮬레이션.

Virtual Console로 기어 D·속도를 넣고, 에뮬레이터 GPS(adb emu geo fix)를 1초마다 옮긴다.
Pleos 내비 앱과 Virtual Console에는 위치 시뮬레이션 기능이 없어서 이렇게 재현한다.

  # 1) 목적지 없이: 시작점에서 방위각 방향으로 직진
  python3 scripts/navi_drive_sim.py --seconds 60 --speed 40 --bearing 0

  # 2) 도로를 따라: 공개 OSRM 경로 서버로 시작점→목적지 도로 좌표를 받아 그 위를 달림
  #    (내비 경로와 완전히 같지는 않지만 직진보다 경로 이탈이 훨씬 적음)
  python3 scripts/navi_drive_sim.py --seconds 120 --speed 40 --to 37.48544722,127.03636666

전제: 에뮬레이터 실행 중. 8088 포트 포워딩은 스크립트가 함.
"""
import argparse
import time

from geo_route import road_points, straight, walk
from vconsole import Console, geo_fix


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--seconds", type=int, default=60)
    p.add_argument("--speed", type=float, default=40, help="km/h")
    p.add_argument("--bearing", type=float, default=0, help="직진 모드 방위각(북=0)")
    p.add_argument("--start", default="37.4420,127.1300", help="위도,경도 (기본: 내비 기본 위치 근처 성남 수정구)")
    p.add_argument("--to", help="위도,경도. 주면 도로를 따라 달림")
    args = p.parse_args()

    start = tuple(map(float, args.start.split(",")))
    step = args.speed / 3.6
    if args.to:
        pts = walk(road_points(start, tuple(map(float, args.to.split(",")))), step)
    else:
        pts = straight(start, args.bearing, step, args.seconds)
    pts = pts[: args.seconds + 1]

    console = Console()
    print(time.strftime("%H:%M:%S"), f"주행 시작: {len(pts) - 1}초, {args.speed:g}km/h, {'도로' if args.to else '직진'}")
    console.put("/vehicle/gear?gear=Drive", "gear=Drive")
    console.put(f"/vehicle/controls?speed={args.speed:g}&accelerator=20&brake=0&steering=0", f"speed={args.speed:g}")
    for lat, lng in pts:
        tick = time.monotonic()
        geo_fix(lat, lng)
        time.sleep(max(0.0, 1.0 - (time.monotonic() - tick)))
    console.put("/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0", "speed=0")
    console.put("/vehicle/gear?gear=Park", "gear=Park")
    time.sleep(1)
    console.close()
    print(time.strftime("%H:%M:%S"), f"주행 끝 (마지막 위치 {pts[-1][0]:.6f}, {pts[-1][1]:.6f})")


if __name__ == "__main__":
    main()
