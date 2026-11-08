#!/usr/bin/env python3
"""차량 신호 주입 시나리오 (docs/spikes/vehicle-background-speed.md 실험 1·2).

Virtual Console로 기어·속도·방향지시등·비상등·조향각을 바꾸고, 주입 시각을 Logcat ProbeInject에 남긴다.
앱 쪽 프로브(VehicleTest·SpeedTest)를 먼저 켜 둔 뒤 실행한다.

  python3 scripts/vehicle_probe_sim.py turn    # 실험 1 시나리오 (약 30초)
  python3 scripts/vehicle_probe_sim.py speed   # 실험 2: 속도 0→20→40→60→30→0 (5초 간격) + 기어 P→R→N→D→P
  python3 scripts/vehicle_probe_sim.py range   # 값 범위·구분: 비상등, Flash 모드, 조향각 -470~+700
  python3 scripts/vehicle_probe_sim.py distance  # 트립 거리: 60km/h 60초 + 120km/h 30초 (약 2km)
  python3 scripts/vehicle_probe_sim.py hazard    # 비상등·깜빡이 조합 (경고등 확인용)
  python3 scripts/vehicle_probe_sim.py odometer  # 콘솔 오도미터 값을 직접 12.5 → 100 → 0 km로
  python3 scripts/vehicle_probe_sim.py trip      # 운행 수집 서비스 확인: 회전 3번(성공 2·실패 1) + 1km 넘게 주행 → P
  python3 scripts/vehicle_probe_sim.py turn speed   # 여러 개를 이어서

콘솔 엔드포인트 (콘솔 웹 화면의 요청과 /vehicle/catalog로 확인, 모두 PUT):
  기어   /vehicle/gear?gear=Park|Reverse|Neutral|Drive
  주행   /vehicle/controls?speed=<km/h>&accelerator=<%>&brake=<%>&steering=<deg>   (조향 범위 -470~500)
  깜빡이 /vehicle/turn-signal?mode=Off|BlinkLeft|BlinkRight|FlashLeft|FlashRight
  비상등 /vehicle/lights?feature=hazard&value=true|false
  오도미터 /vehicle/odometer?traveled_distance_km=<km>  (콘솔은 속도로 오도미터를 늘리지 않음)
"""
import sys
import os
import time

from vconsole import Console


class Car:
    def __init__(self, console):
        self.c = console
        self.speed, self.accel, self.brake, self.steer = 0, 0, 0, 0

    def controls(self, label, **kw):
        # /vehicle/controls는 빠진 값을 0으로 보는지 확인하지 않았으므로 항상 네 값을 함께 보냄
        for k, v in kw.items():
            setattr(self, k, v)
        q = f"speed={self.speed}&accelerator={self.accel}&brake={self.brake}&steering={self.steer}"
        self.c.put(f"/vehicle/controls?{q}", label)

    def gear(self, g):
        self.c.put(f"/vehicle/gear?gear={g}", f"gear={g}")

    def turn(self, mode):
        self.c.put(f"/vehicle/turn-signal?mode={mode}", f"turn={mode}")

    def hazard(self, on):
        self.c.put(f"/vehicle/lights?feature=hazard&value={'true' if on else 'false'}", f"hazard={'on' if on else 'off'}")

    def steer_to(self, deg):
        self.controls(f"steer={deg}", steer=deg)

    def speed_to(self, kmh):
        self.controls(f"speed={kmh}", speed=kmh, accel=20 if kmh else 0, brake=0 if kmh else 30)


def scenario_turn(car):
    """기어 D, 40km/h → 오른쪽 깜빡이 on → 3초 → 조향 +120 → 3초 → 0 → 깜빡이 off → 왼쪽 같은 순서 → 깜빡이 없이 +120 → 0"""
    car.c.mark("scenario turn start")
    car.gear("Drive"); time.sleep(1)
    car.speed_to(40); time.sleep(2)
    for mode, angle in (("BlinkRight", 120), ("BlinkLeft", -120)):
        car.turn(mode); time.sleep(3)
        car.steer_to(angle); time.sleep(3)
        car.steer_to(0); time.sleep(1)
        car.turn("Off"); time.sleep(2)
    car.steer_to(120); time.sleep(3)
    car.steer_to(0); time.sleep(2)
    car.speed_to(0); time.sleep(1)
    car.gear("Park")
    car.c.mark("scenario turn end")


def scenario_speed(car):
    car.c.mark("scenario speed start")
    car.gear("Drive"); time.sleep(2)
    for kmh in (0, 20, 40, 60, 30, 0):
        car.speed_to(kmh); time.sleep(5)
    for g in ("Park", "Reverse", "Neutral", "Drive", "Park"):
        car.gear(g); time.sleep(3)
    car.c.mark("scenario speed end")


def scenario_range(car):
    car.c.mark("scenario range start")
    car.gear("Drive"); time.sleep(1)
    car.hazard(True); time.sleep(3)
    car.turn("BlinkRight"); time.sleep(2)   # 비상등 켠 채 깜빡이
    car.turn("Off"); time.sleep(1)
    car.hazard(False); time.sleep(2)
    for mode in ("FlashRight", "Off", "FlashLeft", "Off"):
        car.turn(mode); time.sleep(2)
    for deg in (-470, -100, 100, 500, 700, 0):
        car.steer_to(deg); time.sleep(3)
    car.gear("Park")
    car.c.mark("scenario range end")


def scenario_distance(car):
    """60km/h로 60초(1km) + 120km/h로 30초(1km) → 트립 거리·속도 적분 비교"""
    car.c.mark("scenario distance start")
    car.gear("Drive"); time.sleep(2)
    car.speed_to(60); time.sleep(60)
    car.speed_to(120); time.sleep(30)
    car.speed_to(0); time.sleep(5)
    car.gear("Park"); time.sleep(5)
    car.c.mark("scenario distance end")


def scenario_trip(car):
    """운행 수집 서비스 확인용 한 운행: 우회전 성공 → 좌회전 성공 → 우회전 깜빡이 없음 → 1km 넘게 주행 → P.
    회전 사이를 8초 이상 띄워 연속 회전으로 묶이지 않게 함. 콘솔 조향 +는 오른쪽(SDK에서는 음수).
    P단 3분이 지나야 운행이 끝나므로, 끝난 뒤 3분 넘게 기다려야 저장·음성 안내를 볼 수 있음"""
    car.c.mark("scenario trip start")
    car.gear("Drive"); time.sleep(2)
    car.speed_to(40); time.sleep(5)
    for mode, angle in (("BlinkRight", 120), ("BlinkLeft", -120), (None, 120)):
        if mode:
            car.turn(mode); time.sleep(3)
        car.steer_to(angle); time.sleep(4)
        car.steer_to(0); time.sleep(1)
        if mode:
            car.turn("Off")
        time.sleep(8)
    # 60km/h × 70초 ≈ 1.2km. 서버 짧은 운행 기준(3km)을 넘기려면 TRIP_CRUISE_SEC=180
    cruise = int(os.environ.get("TRIP_CRUISE_SEC", "70"))
    car.speed_to(60); time.sleep(cruise)
    car.speed_to(0); time.sleep(3)
    car.gear("Park")
    car.c.mark("scenario trip end (P단 3분 뒤 운행 종료)")


def scenario_demo(car):
    """시연 영상용(scripts/record_demo.sh): 좌회전 깜빡이 켬(득점) → 우회전 깜빡이 없음(실점) → 1km 넘게 주행 → P.
    콘솔 조향 +는 오른쪽. 앱을 --ei parkEndSec 10으로 띄우면 P단 10초 뒤 운행이 끝남"""
    car.c.mark("scenario demo start")
    car.gear("Drive"); time.sleep(2)
    car.speed_to(40); time.sleep(4)
    car.turn("BlinkLeft"); time.sleep(3)
    car.steer_to(-120); time.sleep(4)
    car.steer_to(0); time.sleep(1)
    car.turn("Off"); time.sleep(8)
    car.steer_to(120); time.sleep(4)   # 우회전, 깜빡이 없음
    car.steer_to(0); time.sleep(4)
    car.speed_to(60); time.sleep(int(os.environ.get("DEMO_CRUISE_SEC", "65")))  # 약 1.1km
    car.speed_to(0); time.sleep(3)
    car.gear("Park")
    car.c.mark("scenario demo end")


def scenario_odometer(car):
    """콘솔 오도미터(PERF_ODOMETER, km)를 직접 바꿔 SDK 값·단위 확인"""
    car.c.mark("scenario odometer start")
    for km in (12.5, 100, 0):
        car.c.put(f"/vehicle/odometer?traveled_distance_km={km}", f"odometer={km}"); time.sleep(6)
    car.c.mark("scenario odometer end")


def scenario_hazard(car):
    car.c.mark("scenario hazard start")
    car.gear("Drive"); time.sleep(1)
    car.hazard(True); time.sleep(4)
    car.hazard(False); time.sleep(3)
    car.turn("BlinkRight"); time.sleep(4)
    car.turn("Off"); time.sleep(3)
    car.turn("BlinkLeft"); time.sleep(4)
    car.turn("Off"); time.sleep(3)
    car.hazard(True); time.sleep(2)
    car.turn("BlinkRight"); time.sleep(3)  # 비상등 + 오른쪽 깜빡이
    car.turn("Off"); time.sleep(2)
    car.hazard(False); time.sleep(2)
    car.gear("Park")
    car.c.mark("scenario hazard end")


SCENARIOS = {
    "turn": scenario_turn, "speed": scenario_speed, "range": scenario_range,
    "distance": scenario_distance, "hazard": scenario_hazard, "odometer": scenario_odometer,
    "trip": scenario_trip, "demo": scenario_demo,
}


def main():
    names = sys.argv[1:] or ["turn"]
    unknown = [n for n in names if n not in SCENARIOS]
    if unknown:
        sys.exit(f"모르는 시나리오: {unknown}. 가능: {list(SCENARIOS)}")
    console = Console()
    car = Car(console)
    console.proc_state("start")
    try:
        for n in names:
            SCENARIOS[n](car)
            console.proc_state(f"after {n}")
            time.sleep(1)
    finally:
        car.speed, car.accel, car.brake, car.steer = 0, 0, 30, 0
        car.controls("reset")
        car.gear("Park")
        car.turn("Off")
        time.sleep(1.5)
        console.close()


if __name__ == "__main__":
    main()
